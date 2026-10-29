import java.util.Properties
import org.gradle.jvm.tasks.Jar
import org.gradle.api.tasks.testing.Test
import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipFile
import java.security.MessageDigest
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

plugins {
    id("com.android.library") version "9.2.1"
    id("org.jetbrains.dokka") version "2.2.0"
    id("org.jetbrains.dokka-javadoc") version "2.2.0"
    `maven-publish`
    signing
}

abstract class ReleaseClassPrivacy : AsmClassVisitorFactory<InstrumentationParameters.None> {
    companion object {
        // Exact labels from the SHA-pinned private input. Error kind, cause,
        // severity, formatting placeholders and all payload bytes stay intact.
        val privateLabels = mapOf(
            "CodedOutputStream was writing to a flat byte array and ran out of space." to
                "Runtime byte encoder ran out of output space.",
            "CodedOutputStream was writing to a flat byte array and ran out of space.: " to
                "Runtime byte encoder ran out of output space.: ",
            "Converting ill-formed UTF-16. Your Protocol Buffer will not round trip correctly!" to
                "Converting ill-formed UTF-16. Encoded text will not round trip correctly!",
            "ByteString" to "runtime buffer",
            "<ByteString@%s size=%d contents=\"%s\">" to "<RuntimeBuffer@%s size=%d contents=\"%s\">",
        )
    }

    override fun isInstrumentable(classData: ClassData): Boolean = true

    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor,
    ): ClassVisitor = object : ClassVisitor(Opcodes.ASM9, nextClassVisitor) {
        val privateCodec = classContext.currentClassData.className.startsWith("com.google.protobuf.")
        fun privateLabel(value: Any?): Any? =
            if (privateCodec && value is String) privateLabels[value] ?: value else value

        // Kotlin's SMAP annotation is debug material, unlike its public type
        // metadata and nullability annotations. Only the debug annotation goes.
        override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? =
            if (descriptor == "Lkotlin/jvm/internal/SourceDebugExtension;") null
            else super.visitAnnotation(descriptor, visible)

        override fun visitSource(source: String?, debug: String?) = Unit

        override fun visitField(
            access: Int, name: String?, descriptor: String?, signature: String?, value: Any?,
        ): FieldVisitor? = super.visitField(access, name, descriptor, signature, privateLabel(value))

        override fun visitMethod(
            access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?,
        ): MethodVisitor? = super.visitMethod(access, name, descriptor, signature, exceptions)?.let {
            object : MethodVisitor(Opcodes.ASM9, it) {
                override fun visitLdcInsn(value: Any?) = super.visitLdcInsn(privateLabel(value))
                override fun visitLineNumber(line: Int, start: Label?) = Unit
                override fun visitLocalVariable(
                    name: String?, descriptor: String?, signature: String?, start: Label?, end: Label?, index: Int,
                ) = Unit
            }
        }
    }
}

abstract class AbsorbCodecClasses : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputJars: ListProperty<RegularFile>
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDirectories: ListProperty<Directory>
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val privateRuntime: RegularFileProperty
    @get:OutputFile abstract val outputJar: RegularFileProperty

    @TaskAction fun absorb() {
        // Build-only program-input assembly. R8 owns all bytecode rewriting;
        // duplicate classes fail instead of shadowing a dependency silently.
        val classes = sortedMapOf<String, ByteArray>()
        val runtime = privateRuntime.get().asFile
        require(runtime.length() in 1..(16L * 1024 * 1024))
        val digest = MessageDigest.getInstance("SHA-256").digest(runtime.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        require(digest == "5681aa7829262b272cbde2a4fcac38070b063ef7d9abbe1a0d112d0df8193cb3") {
            "private runtime input differs from the qualified release pin"
        }
        ZipFile(runtime).use { jar ->
            val owner = checkNotNull(jar.getEntry("com/google/protobuf/Protobuf.class"))
            require(jar.getInputStream(owner).use { it.readBytes() }
                .toString(Charsets.ISO_8859_1).contains("assumeLiteRuntime"))
            val classText = jar.entries().asSequence().filter { it.name.endsWith(".class") }
                .map { entry -> jar.getInputStream(entry).use { it.readBytes() }.toString(Charsets.ISO_8859_1) }
                .toList()
            require(ReleaseClassPrivacy.privateLabels.keys.all { label -> classText.any { label in it } }) {
                "private diagnostic inventory differs from the qualified input"
            }
        }
        var expanded = 0L
        fun add(name: String, bytes: ByteArray) {
            require(name.endsWith(".class") && !name.startsWith('/') && ".." !in name.split('/'))
            require(bytes.size <= 2 * 1024 * 1024 && classes.size < 2048)
            expanded += bytes.size
            require(expanded <= 32L * 1024 * 1024) { "library class input exceeds its bound" }
            require(classes.putIfAbsent(name, bytes) == null) { "duplicate class in library input: $name" }
        }
        (inputJars.get().map { it.asFile } + runtime).forEach { input ->
            ZipFile(input).use { jar ->
                jar.entries().asSequence().filter { it.name.endsWith(".class") }.forEach { entry ->
                    require(entry.size in 0..(2L * 1024 * 1024))
                    add(entry.name, jar.getInputStream(entry).use { it.readBytes() })
                }
            }
        }
        inputDirectories.get().forEach { directory ->
            directory.asFile.walkTopDown().filter { it.isFile && it.extension == "class" }.forEach { input ->
                add(input.relativeTo(directory.asFile).invariantSeparatorsPath, input.readBytes())
            }
        }
        val output = outputJar.get().asFile
        output.parentFile.mkdirs()
        JarOutputStream(output.outputStream()).use { jar ->
            classes.forEach { (name, bytes) ->
                jar.putNextEntry(JarEntry(name).apply { time = 0 })
                jar.write(bytes)
                jar.closeEntry()
            }
        }
    }
}

val releaseIdentityFile = layout.projectDirectory.file("release-identity.properties")
val releaseIdentity = Properties().apply {
    releaseIdentityFile.asFile.inputStream().use(::load)
}
fun requiredReleaseIdentity(key: String): String =
    requireNotNull(releaseIdentity.getProperty(key)?.trim()?.takeIf(String::isNotEmpty)) {
        "release-identity.properties requires $key"
    }

val androidConnectorVersion = requiredReleaseIdentity("connector.version")
val expectedNativeRuntimeVersion = requiredReleaseIdentity("core.version")
val nativeRuntimeGitCommit = requiredReleaseIdentity("core.commit")
val includedAndroidAbis = requiredReleaseIdentity("android.abis").split(',')
require(androidConnectorVersion.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) {
    "connector.version must be semantic versioning without a suffix"
}
require(expectedNativeRuntimeVersion.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) {
    "core.version must be semantic versioning without a suffix"
}
require(nativeRuntimeGitCommit.matches(Regex("[0-9a-f]{40}"))) {
    "core.commit must be an exact lowercase 40-character commit"
}
require(includedAndroidAbis == listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")) {
    "android.abis must preserve the audited four-ABI order"
}

group = "io.github.phuong-tran.coakka"
version = androidConnectorVersion

val configuredCoreRepository = providers.gradleProperty("coakkaCoreRepository")
    .orElse(providers.environmentVariable("COAKKA_CORE_REPO"))
    .orElse("../../..")
val coreRepositoryDir = layout.dir(
    providers.provider { file(configuredCoreRepository.get()) },
).get()
val pinnedNativeSourceRoot = layout.projectDirectory.dir(
    ".native-source/$nativeRuntimeGitCommit",
)
val pinnedNativeV2Root = pinnedNativeSourceRoot.dir("v2")
val nativeRuntimeVersion = providers.exec {
    workingDir(coreRepositoryDir)
    commandLine("git", "show", "$nativeRuntimeGitCommit:v2/CMakeLists.txt")
}.standardOutput.asText.get()
    .let { contents ->
        requireNotNull(
            Regex("""project\(CoAkkaCoreV2 VERSION ([0-9]+\.[0-9]+\.[0-9]+)""")
                .find(contents)
                ?.groupValues
                ?.get(1),
        ) { "v2/CMakeLists.txt does not declare the native runtime version" }
    }
require(nativeRuntimeVersion == expectedNativeRuntimeVersion) {
    "core.version=$expectedNativeRuntimeVersion does not match Core $nativeRuntimeGitCommit ($nativeRuntimeVersion)"
}
val connectorGitCommit = providers.exec {
    workingDir(projectDir)
    commandLine("git", "rev-parse", "HEAD")
}.standardOutput.asText.map(String::trim)
val connectorSourceTreeDirty = providers.exec {
    workingDir(projectDir)
    commandLine("git", "status", "--porcelain", "--untracked-files=normal")
}.standardOutput.asText.map { it.isNotBlank() }
val coreCheckoutGitCommit = providers.exec {
    workingDir(coreRepositoryDir)
    commandLine("git", "rev-parse", "HEAD")
}.standardOutput.asText.map(String::trim)
val coreSourceTreeDirty = providers.exec {
    workingDir(coreRepositoryDir)
    commandLine("git", "status", "--porcelain", "--untracked-files=normal")
}.standardOutput.asText.map { it.isNotBlank() }
val generatedPackageMetadataDir = layout.buildDirectory.dir("generated/coakkaPackageMetadata")
val packageLegalFiles = mapOf(
    "LICENSE" to coreRepositoryDir.file("public-docs/legal/LICENSE"),
    "NATIVE-LICENSE.md" to coreRepositoryDir.file("public-docs/legal/coakka-native-artifact-license-1.2.md"),
    "PACKAGE-LICENSE.md" to coreRepositoryDir.file("public-docs/legal/PACKAGE-LICENSE.md"),
    "NOTICE" to layout.projectDirectory.file("NOTICE"),
)

android {
    namespace = "coakka.v2.android"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            abiFilters += includedAndroidAbis
        }
        externalNativeBuild {
            cmake {
                arguments += "-DCOAKKA_RUNTIME_ROOT=${pinnedNativeV2Root.asFile.absolutePath}"
            }
        }
    }

    ndkVersion = "29.0.14206865"

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            // The app optimizer template widens private access. Library rules
            // preserve the published surface while hiding its implementation.
            proguardFiles("library-release-rules.pro")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    publishing {
        singleVariant("release")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets {
        getByName("main").java.directories.add(
            "build/generated/source/coakkaProto/main/java",
        )
        getByName("main").assets.directories.add(
            "build/generated/coakkaPackageMetadata",
        )
    }

    lint {
        // All four standard ABIs are packaged; lint cannot resolve the shared list above.
        disable += "ChromeOsAbiSupport"
    }
}

val protobufVersion = "4.31.1"
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.instrumentation.transformClassesWith(
            ReleaseClassPrivacy::class.java, InstrumentationScope.PROJECT,
        ) {}
        val absorb = tasks.register<AbsorbCodecClasses>("absorbReleaseCodecClasses") {
            privateRuntime.set(layout.file(provider { embeddedCodecRuntime.singleFile }))
        }
        variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT).use(absorb).toTransform(
            ScopedArtifact.CLASSES,
            AbsorbCodecClasses::inputJars, AbsorbCodecClasses::inputDirectories, AbsorbCodecClasses::outputJar,
        )
    }
}

val protocClassifier = when {
    System.getProperty("os.name").startsWith("Mac", ignoreCase = true) &&
        System.getProperty("os.arch") in setOf("aarch64", "arm64") -> "osx-aarch_64"
    System.getProperty("os.name").startsWith("Mac", ignoreCase = true) -> "osx-x86_64"
    System.getProperty("os.name").startsWith("Linux", ignoreCase = true) &&
        System.getProperty("os.arch") in setOf("aarch64", "arm64") -> "linux-aarch_64"
    System.getProperty("os.name").startsWith("Linux", ignoreCase = true) -> "linux-x86_64"
    System.getProperty("os.name").startsWith("Windows", ignoreCase = true) -> "windows-x86_64"
    else -> error("unsupported protoc host: ${System.getProperty("os.name")}/${System.getProperty("os.arch")}")
}
val protocCompiler = configurations.create("protocCompiler")
val managedApiTools = configurations.create("managedApiTools")
val managedSmokeCompiler = configurations.create("managedSmokeCompiler")
val managedSmokeRuntime = configurations.create("managedSmokeRuntime")
val managedHostRuntime = configurations.create("managedHostRuntime")
managedSmokeRuntime.extendsFrom(managedHostRuntime)
val embeddedCodecRuntime = configurations.create("embeddedCodecRuntime")

dependencies {
    // A private implementation dependency is a local AGP program input: R8
    // absorbs/renames it into the AAR instead of exporting a Maven dependency.
    add(embeddedCodecRuntime.name, "com.google.protobuf:protobuf-javalite:$protobufVersion")
    compileOnly(files(embeddedCodecRuntime))
    testImplementation("junit:junit:4.13.2")
    testImplementation(files(embeddedCodecRuntime))
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    add(protocCompiler.name, "com.google.protobuf:protoc:$protobufVersion:$protocClassifier@exe")
    // Build-only visibility inspection; never an Android runtime/POM dependency.
    add(managedApiTools.name, "org.jetbrains.kotlin:kotlin-metadata-jvm:2.2.10")
    add(managedSmokeCompiler.name, "org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.10")
    add(managedHostRuntime.name, "org.jetbrains.kotlin:kotlin-stdlib:2.2.10")
    add(managedSmokeRuntime.name, "com.google.protobuf:protobuf-javalite:$protobufVersion")
}

val generatedProtoDir = layout.buildDirectory.dir("generated/source/coakkaProto/main/java")

val generateCoAkkaPackageMetadata by tasks.registering {
    val outputFile = generatedPackageMetadataDir.map {
        it.file("coakka/runtime-package.json")
    }
    inputs.property("connectorVersion", provider { project.version.toString() })
    inputs.property("nativeRuntimeVersion", nativeRuntimeVersion)
    inputs.property("nativeRuntimeGitCommit", nativeRuntimeGitCommit)
    inputs.property("includedAndroidAbis", includedAndroidAbis)
    inputs.property("connectorGitCommit", connectorGitCommit)
    inputs.property("connectorSourceTreeDirty", connectorSourceTreeDirty)
    inputs.property("coreCheckoutGitCommit", coreCheckoutGitCommit)
    inputs.property("coreSourceTreeDirty", coreSourceTreeDirty)
    inputs.file(releaseIdentityFile)
    inputs.files(packageLegalFiles.values.map { it.asFile })
    outputs.files(
        outputFile,
        packageLegalFiles.keys.map { name -> generatedPackageMetadataDir.map { it.file(name) } },
    )
    doLast {
        val connectorCommit = connectorGitCommit.get()
        val connectorDirty = connectorSourceTreeDirty.get()
        val coreCheckoutCommit = coreCheckoutGitCommit.get()
        val coreDirty = coreSourceTreeDirty.get()
        val sourceTreeDirty = connectorDirty || coreDirty
        val metadata = """
            {
              "schema_version": 2,
              "connector_version": "${project.version}",
              "bundled_native_package_version": "$nativeRuntimeVersion+$nativeRuntimeGitCommit",
              "bundled_native_git_commit": "$nativeRuntimeGitCommit",
              "connector_source_git_commit": "$connectorCommit",
              "connector_source_tree_dirty": $connectorDirty,
              "core_source_git_commit": "$nativeRuntimeGitCommit",
              "core_checkout_git_commit": "$coreCheckoutCommit",
              "core_source_tree_dirty": $coreDirty,
              "source_tree_dirty": $sourceTreeDirty,
              "native_source_verified": true,
              "included_android_abis": [${includedAndroidAbis.joinToString { "\"$it\"" }}]
            }
        """.trimIndent() + "\n"
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(metadata)
        }
        packageLegalFiles.forEach { (name, source) ->
            source.asFile.copyTo(
                generatedPackageMetadataDir.get().file(name).asFile,
                overwrite = true,
            )
        }
    }
}

val generateCoAkkaProto by tasks.registering(Exec::class) {
    val protoRoot = pinnedNativeV2Root.dir("proto")
    val outputDir = generatedProtoDir.get().asFile
    inputs.files(
        protocCompiler,
        protoRoot.file("coakka/v2/control.proto"),
        protoRoot.file("coakka/v2/transport.proto"),
    )
    outputs.dir(outputDir)
    doFirst {
        outputDir.mkdirs()
        val protoc = protocCompiler.singleFile
        protoc.setExecutable(true)
        commandLine(
            protoc.absolutePath,
            "--java_out=lite:${outputDir.absolutePath}",
            "-I",
            protoRoot.asFile.absolutePath,
            protoRoot.file("coakka/v2/control.proto").asFile.absolutePath,
            protoRoot.file("coakka/v2/transport.proto").asFile.absolutePath,
        )
    }
}

// This independent wire reader is test-only. Neither the AAR nor target APK
// includes it; it checks renamed library encoders without exposing their types.
val compileSmokeCodec by tasks.registering(JavaCompile::class) {
    dependsOn(generateCoAkkaProto)
    source(generatedProtoDir)
    classpath = managedSmokeRuntime
    destinationDirectory.set(layout.buildDirectory.dir("private/smokeCodec"))
    options.release.set(11)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror", "-g:none"))
}
val prepareDeviceSmokeCodec by tasks.registering(Jar::class) {
    dependsOn(compileSmokeCodec)
    archiveFileName.set("coakka-android-test-codec.jar")
    destinationDirectory.set(layout.buildDirectory.dir("private"))
    from(compileSmokeCodec.flatMap { it.destinationDirectory })
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
val extractManagedSmokeAar by tasks.registering(Sync::class) {
    dependsOn("bundleReleaseAar")
    from(provider { zipTree(layout.buildDirectory.file("outputs/aar/coakka-runtime-android-release.aar")) }) {
        include("classes.jar")
    }
    into(layout.buildDirectory.dir("private/managedAar"))
}
val managedSmokeClasspath = files(
    layout.buildDirectory.file("private/managedAar/classes.jar"),
    prepareDeviceSmokeCodec.flatMap { it.archiveFile },
    managedSmokeRuntime,
)
val compileManagedJavaSmoke by tasks.registering(JavaCompile::class) {
    dependsOn(extractManagedSmokeAar, prepareDeviceSmokeCodec)
    source(fileTree("managed-smoke") { include("*.java") })
    classpath = managedSmokeClasspath
    destinationDirectory.set(layout.buildDirectory.dir("private/managedJava"))
    options.release.set(11)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
val compileManagedKotlinSmoke by tasks.registering(JavaExec::class) {
    dependsOn(extractManagedSmokeAar, prepareDeviceSmokeCodec)
    classpath = managedSmokeCompiler
    mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
    inputs.files(fileTree("managed-smoke") { include("*.kt") }, managedSmokeClasspath)
    val destination = layout.buildDirectory.dir("private/managedKotlin")
    outputs.dir(destination)
    doFirst {
        destination.get().asFile.mkdirs()
        args("-no-stdlib", "-no-reflect", "-jvm-target", "11", "-Werror",
            "-api-version", "2.2", "-language-version", "2.2",
            "-classpath", managedSmokeClasspath.asPath,
            "-d", destination.get().asFile.absolutePath)
        args(fileTree("managed-smoke") { include("*.kt") }.files.sorted().map { it.absolutePath })
    }
}
val verifyManagedJavaConsumer by tasks.registering(JavaExec::class) {
    dependsOn(compileManagedJavaSmoke)
    classpath = managedSmokeClasspath + files(compileManagedJavaSmoke.flatMap { it.destinationDirectory })
    mainClass.set("ManagedJavaConsumer")
}
val verifyManagedKotlinConsumer by tasks.registering(JavaExec::class) {
    dependsOn(compileManagedKotlinSmoke)
    classpath = managedSmokeClasspath + files(layout.buildDirectory.dir("private/managedKotlin"))
    mainClass.set("ManagedKotlinConsumerKt")
}
val verifyManagedClosedConsumer by tasks.registering(JavaExec::class) {
    dependsOn(compileManagedJavaSmoke)
    // Only the processed AAR and host-language runtime: no independent codec or
    // external implementation runtime can mask an incomplete absorption.
    classpath = files(layout.buildDirectory.file("private/managedAar/classes.jar"),
        compileManagedJavaSmoke.flatMap { it.destinationDirectory }, managedHostRuntime)
    mainClass.set("ManagedClosedConsumer")
}
val verifyManagedConsumers by tasks.registering {
    group = "verification"
    description = "Compiles and runs Kotlin and Java consumers of the exact processed AAR."
    dependsOn(verifyManagedJavaConsumer, verifyManagedKotlinConsumer, verifyManagedClosedConsumer)
}
val verifyManagedHardeningNegativeControls by tasks.registering(Exec::class) {
    dependsOn(extractManagedSmokeAar)
    workingDir = projectDir
    inputs.files("scripts/test-library-managed.py", "scripts/verify-library-managed.py")
    inputs.file(layout.buildDirectory.file("private/managedAar/classes.jar"))
    commandLine("python3", "-B", "scripts/test-library-managed.py", "--classes",
        layout.buildDirectory.file("private/managedAar/classes.jar").get().asFile.absolutePath,
        "--connector-root", projectDir.absolutePath)
}

val stageNativeRuntime by tasks.registering(Exec::class) {
    inputs.property("nativeRuntimeGitCommit", nativeRuntimeGitCommit)
    inputs.file("scripts/build-native-runtime.sh")
    inputs.file("scripts/stage-prebuilt-core.py")
    inputs.file("scripts/prebuilt-core-input-jni.exports.map")
    inputs.file("scripts/verify-release-aar.py")
    inputs.property("prebuiltCoreSha256", providers.environmentVariable("COAKKA_ANDROID_PREBUILT_CORE_SHA256").orElse("source-build"))
    inputs.file(providers.environmentVariable("COAKKA_ANDROID_PREBUILT_CORE_AAR").map(::File)).optional()
    outputs.files(
        file("src/main/jniLibs/arm64-v8a/libcoakka_runtime_v2.so"),
        file("src/main/jniLibs/armeabi-v7a/libcoakka_runtime_v2.so"),
        file("src/main/jniLibs/x86/libcoakka_runtime_v2.so"),
        file("src/main/jniLibs/x86_64/libcoakka_runtime_v2.so"),
    )
    workingDir = projectDir
    commandLine("bash", "scripts/build-native-runtime.sh")
    doFirst {
        val configuredSdk = System.getenv("ANDROID_SDK_ROOT")
            ?: System.getenv("ANDROID_HOME")
            ?: file("local.properties").takeIf(File::isFile)?.inputStream()?.use { input ->
                Properties().apply { load(input) }.getProperty("sdk.dir")
            }
        if (!configuredSdk.isNullOrBlank()) {
            environment("ANDROID_SDK_ROOT", configuredSdk)
        }
        environment("COAKKA_V2_NATIVE_GIT_COMMIT", nativeRuntimeGitCommit)
        environment("COAKKA_ANDROID_ABIS", includedAndroidAbis.joinToString(" "))
        environment("COAKKA_CORE_REPO", coreRepositoryDir.asFile.absolutePath)
    }
}

val verifyPinnedNativeSource by tasks.registering(Exec::class) {
    inputs.property("nativeRuntimeGitCommit", nativeRuntimeGitCommit)
    inputs.file("scripts/build-native-runtime.sh")
    inputs.file("scripts/stage-prebuilt-core.py")
    inputs.file("scripts/prebuilt-core-input-jni.exports.map")
    inputs.file("scripts/verify-release-aar.py")
    inputs.property("prebuiltCoreSha256", providers.environmentVariable("COAKKA_ANDROID_PREBUILT_CORE_SHA256").orElse("source-build"))
    inputs.file(providers.environmentVariable("COAKKA_ANDROID_PREBUILT_CORE_AAR").map(::File)).optional()
    inputs.file("scripts/load-release-identity.sh")
    inputs.file(releaseIdentityFile)
    outputs.files(
        pinnedNativeSourceRoot.file(".complete"),
        pinnedNativeV2Root.file("include/coakka/v2/runtime.h"),
        pinnedNativeV2Root.file("include/coakka/v2/file_lane.h"),
        pinnedNativeV2Root.file("include/coakka/v2/stream_lane.h"),
        pinnedNativeV2Root.file("proto/coakka/v2/control.proto"),
        pinnedNativeV2Root.file("proto/coakka/v2/transport.proto"),
    )
    workingDir = projectDir
    commandLine("bash", "scripts/build-native-runtime.sh", "--verify-source")
    environment("COAKKA_V2_NATIVE_GIT_COMMIT", nativeRuntimeGitCommit)
    environment("COAKKA_CORE_REPO", coreRepositoryDir.asFile.absolutePath)
}

stageNativeRuntime.configure {
    dependsOn(verifyPinnedNativeSource)
}

val verifyJniLifecycle by tasks.registering(Exec::class) {
    inputs.files(
        "scripts/verify-jni-lifecycle.sh",
        "src/main/cpp/coakka_android_file_lane_jni.cpp",
        "src/main/cpp/coakka_android_stream_lane_jni.cpp",
    )
    workingDir = projectDir
    commandLine("bash", "scripts/verify-jni-lifecycle.sh")
}

val verifyJniStringBoundary by tasks.registering(Exec::class) {
    inputs.files(
        "scripts/verify-jni-string-boundary.sh",
        "src/main/cpp/coakka_android_jni_support.h",
    )
    workingDir = projectDir
    commandLine("bash", "scripts/verify-jni-string-boundary.sh")
}

val verifyReleaseSourceIdentity by tasks.registering(Exec::class) {
    inputs.files(
        releaseIdentityFile,
        "scripts/load-release-identity.sh",
        "scripts/verify-release-source-identity.sh",
        "src/main/cpp/CMakeLists.txt",
        "src/main/java/coakka/v2/android/FileLane.kt",
        "src/main/java/coakka/v2/android/StreamLane.kt",
    )
    workingDir = projectDir
    commandLine("bash", "scripts/verify-release-source-identity.sh")
    environment("COAKKA_CORE_REPO", coreRepositoryDir.asFile.absolutePath)
}

val hostJniProfiles = linkedMapOf(
    "strict" to "",
    "asan" to "Asan",
    "ubsan" to "Ubsan",
    "tsan" to "Tsan",
)
val hostJniTestClass = "coakka.v2.android.HostJniLaneIntegrationTest"
val isMacHost = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)
val hostCCompiler = providers.environmentVariable("CC").orElse("cc")
val hostJavaHome = providers.systemProperty("java.home").map(::file)
val diagnosticJavaHome = layout.buildDirectory.dir("host-jni/diagnostic-java")
val diagnosticJavaExecutable = diagnosticJavaHome.map { it.file("bin/java") }
val prepareHostJniSanitizerJavaLauncher = tasks.register<Exec>(
    "prepareHostJniSanitizerJavaLauncher",
) {
    onlyIf { isMacHost }
    inputs.file(hostJavaHome.map { it.resolve("bin/java") })
    inputs.file("scripts/prepare-host-jni-java.sh")
    outputs.file(diagnosticJavaExecutable)
    workingDir = projectDir
    commandLine(
        "bash",
        "scripts/prepare-host-jni-java.sh",
        diagnosticJavaHome.get().asFile.absolutePath,
        hostJavaHome.get().absolutePath,
    )
}

fun hostSanitizerRuntime(profile: String): Provider<String> {
    val runtimeStem = when (profile) {
        "lsan" -> "asan"
        "ubsan" -> if (isMacHost) "ubsan" else "ubsan_standalone"
        else -> profile
    }
    val linuxRuntimeArch = when (System.getProperty("os.arch")) {
        "aarch64", "arm64" -> "aarch64"
        "amd64", "x86_64" -> "x86_64"
        else -> error("Unsupported Linux sanitizer host architecture: ${System.getProperty("os.arch")}")
    }
    return providers.exec {
        if (isMacHost) {
            commandLine(hostCCompiler.get(), "-print-resource-dir")
        } else {
            commandLine(
                hostCCompiler.get(),
                "-print-file-name=libclang_rt.$runtimeStem-$linuxRuntimeArch.so",
            )
        }
    }.standardOutput.asText.map(String::trim).map { output ->
        if (isMacHost) {
            "$output/lib/darwin/libclang_rt.${runtimeStem}_osx_dynamic.dylib"
        } else {
            output
        }
    }
}

hostJniProfiles.forEach { (profile, suffix) ->
    val profileBuildDirectory = layout.buildDirectory.dir("host-jni/$profile")
    val profileLibraryDirectory = profileBuildDirectory.map { it.dir("lib") }
    val sanitizerRuntime = if (profile == "strict") null else hostSanitizerRuntime(profile)
    val verifySanitizer = if (profile == "strict") {
        null
    } else {
        tasks.register<Exec>("verifyHostJni${suffix}Support") {
            group = "verification"
            description = "Proves the $profile runtime detects faults and can start the test JVM."
            if (isMacHost) {
                dependsOn(prepareHostJniSanitizerJavaLauncher)
            }
            inputs.property("profile", profile)
            inputs.property("hostCCompiler", hostCCompiler)
            inputs.file("scripts/verify-host-jni-sanitizer.sh")
            inputs.file(
                if (isMacHost) {
                    diagnosticJavaExecutable
                } else {
                    hostJavaHome.map { it.resolve("bin/java") }
                },
            )
            outputs.upToDateWhen { false }
            workingDir = projectDir
            commandLine(
                "bash",
                "scripts/verify-host-jni-sanitizer.sh",
                profile,
                if (isMacHost) {
                    diagnosticJavaExecutable.get().asFile.absolutePath
                } else {
                    hostJavaHome.get().resolve("bin/java").absolutePath
                },
            )
            environment("CC", hostCCompiler.get())
        }
    }
    val buildTask = tasks.register<Exec>("buildHostJni$suffix") {
        dependsOn(verifyPinnedNativeSource)
        if (verifySanitizer != null) {
            dependsOn(verifySanitizer)
        }
        inputs.property("nativeRuntimeGitCommit", nativeRuntimeGitCommit)
        inputs.property("profile", profile)
        inputs.property("hostCCompiler", hostCCompiler)
        inputs.property(
            "hostCxxCompiler",
            providers.environmentVariable("CXX").orElse("c++"),
        )
        inputs.files(
            "host-test/CMakeLists.txt",
            "scripts/build-host-jni.sh",
            "src/main/cpp/coakka_android_jni.cpp",
            "src/main/cpp/coakka_android_message_jni.cpp",
            "src/main/cpp/coakka_android_file_lane_jni.cpp",
            "src/main/cpp/coakka_android_stream_lane_jni.cpp",
            "src/main/cpp/coakka_android_jni_support.h",
            "src/hostTest/cpp/coakka_android_host_test_jni.cpp",
        )
        outputs.files(
            profileLibraryDirectory.map { it.file(System.mapLibraryName("coakka_runtime_v2")) },
            profileLibraryDirectory.map { it.file(System.mapLibraryName("coakka_android_jni")) },
        )
        workingDir = projectDir
        commandLine("bash", "scripts/build-host-jni.sh", profile)
        environment("COAKKA_V2_NATIVE_GIT_COMMIT", nativeRuntimeGitCommit)
        environment("COAKKA_CORE_REPO", coreRepositoryDir.asFile.absolutePath)
    }

    tasks.register<Test>("testHostJni$suffix") {
        group = "verification"
        description = "Runs the exact-Core host JNI lane tests with profile $profile."
        dependsOn("testDebugUnitTest", buildTask)
        val debugUnitTest = tasks.named<Test>("testDebugUnitTest").get()
        testClassesDirs = debugUnitTest.testClassesDirs
        classpath = debugUnitTest.classpath
        filter {
            includeTestsMatching(hostJniTestClass)
        }
        maxParallelForks = 1
        forkEvery = 1
        outputs.upToDateWhen { false }
        systemProperty("coakka.android.hostJni", "true")
        jvmArgs("-Djava.library.path=${profileLibraryDirectory.get().asFile.absolutePath}")
        if (isMacHost) {
            environment("DYLD_LIBRARY_PATH", profileLibraryDirectory.get().asFile.absolutePath)
        } else {
            environment("LD_LIBRARY_PATH", profileLibraryDirectory.get().asFile.absolutePath)
        }
        when (profile) {
            "asan" -> {
                environment("ASAN_OPTIONS", "detect_leaks=0:halt_on_error=1:abort_on_error=1:symbolize=1")
            }
            "ubsan" -> {
                environment("UBSAN_OPTIONS", "halt_on_error=1:print_stacktrace=1")
            }
            "tsan" -> {
                environment("TSAN_OPTIONS", "halt_on_error=1:second_deadlock_stack=1")
            }
        }
        if (sanitizerRuntime != null) {
            if (isMacHost) {
                executable = diagnosticJavaExecutable.get().asFile.absolutePath
                environment("DYLD_INSERT_LIBRARIES", sanitizerRuntime.get())
            } else {
                environment("LD_PRELOAD", sanitizerRuntime.get())
            }
        }
        testLogging {
            events("passed", "skipped", "failed", "standardOut", "standardError")
            showStandardStreams = true
        }
    }
}

val verifyHostJniLsanSupport = tasks.register<Exec>("verifyHostJniLsanSupport") {
    group = "verification"
    description = "Proves LeakSanitizer detects leaks and can start the test JVM."
    if (isMacHost) {
        dependsOn(prepareHostJniSanitizerJavaLauncher)
    }
    inputs.property("hostCCompiler", hostCCompiler)
    inputs.file("scripts/verify-host-jni-sanitizer.sh")
    inputs.file(
        if (isMacHost) {
            diagnosticJavaExecutable
        } else {
            hostJavaHome.map { it.resolve("bin/java") }
        },
    )
    outputs.upToDateWhen { false }
    workingDir = projectDir
    commandLine(
        "bash",
        "scripts/verify-host-jni-sanitizer.sh",
        "lsan",
        if (isMacHost) {
            diagnosticJavaExecutable.get().asFile.absolutePath
        } else {
            hostJavaHome.get().resolve("bin/java").absolutePath
        },
    )
    environment("CC", hostCCompiler.get())
}

tasks.register<Test>("testHostJniLsan") {
    group = "verification"
    description = "Runs explicit recoverable leak checks after the host JNI lane tests."
    dependsOn("testDebugUnitTest", "buildHostJniAsan", verifyHostJniLsanSupport)
    val debugUnitTest = tasks.named<Test>("testDebugUnitTest").get()
    val asanLibraryDirectory = layout.buildDirectory.dir("host-jni/asan/lib")
    testClassesDirs = debugUnitTest.testClassesDirs
    classpath = debugUnitTest.classpath
    filter {
        includeTestsMatching(hostJniTestClass)
    }
    maxParallelForks = 1
    forkEvery = 1
    outputs.upToDateWhen { false }
    systemProperty("coakka.android.hostJni", "true")
    systemProperty("coakka.android.hostLsan", "true")
    jvmArgs("-Djava.library.path=${asanLibraryDirectory.get().asFile.absolutePath}")
    if (isMacHost) {
        executable = diagnosticJavaExecutable.get().asFile.absolutePath
        environment("DYLD_LIBRARY_PATH", asanLibraryDirectory.get().asFile.absolutePath)
        environment("DYLD_INSERT_LIBRARIES", hostSanitizerRuntime("lsan").get())
    } else {
        environment("LD_LIBRARY_PATH", asanLibraryDirectory.get().asFile.absolutePath)
        environment("LD_PRELOAD", hostSanitizerRuntime("lsan").get())
    }
    environment(
        "ASAN_OPTIONS",
        "detect_leaks=1:leak_check_at_exit=0:halt_on_error=1:abort_on_error=1:symbolize=1:max_leaks=20",
    )
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        showStandardStreams = true
    }
}

tasks.register("testHostJniAllSanitizers") {
    group = "verification"
    description = "Runs host JNI lane tests separately under ASan, LSan, UBSan, and TSan."
    dependsOn("testHostJniAsan", "testHostJniLsan", "testHostJniUbsan", "testHostJniTsan")
}

generateCoAkkaProto.configure {
    dependsOn(verifyPinnedNativeSource)
}

generateCoAkkaPackageMetadata.configure {
    dependsOn(verifyPinnedNativeSource)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(
        stageNativeRuntime,
        verifyPinnedNativeSource,
        verifyJniLifecycle,
        verifyJniStringBoundary,
        verifyReleaseSourceIdentity,
        generateCoAkkaProto,
        generateCoAkkaPackageMetadata,
    )
}

tasks.matching { it.name.startsWith("configureCMake") }.configureEach {
    dependsOn(stageNativeRuntime, verifyPinnedNativeSource)
}

val verifyReleaseAarHardening by tasks.registering(Exec::class) {
    group = "verification"
    description = "Verifies the exact release AAR ABI, exports, dependencies, and disclosure policy."
    dependsOn("bundleReleaseAar")
    inputs.files(
        "scripts/verify-release-aar.py",
        "scripts/verify-library-managed.py",
        "scripts/InspectKotlinVisibility.java",
        "release-api.txt", "release-api.jvm", "release-api.kotlin",
        "src/main/cpp/coakka_android_jni.exports.map",
        releaseIdentityFile,
    )
    inputs.file(layout.buildDirectory.file("outputs/aar/coakka-runtime-android-release.aar"))
    workingDir = projectDir
    val verifyArguments = mutableListOf(
        "python3",
        "-B",
        "scripts/verify-release-aar.py",
        "--aar",
        layout.buildDirectory.file("outputs/aar/coakka-runtime-android-release.aar").get().asFile.absolutePath,
        "--connector-root",
        projectDir.absolutePath,
    )
    val allowDirtyPreflight = System.getenv("COAKKA_ANDROID_ALLOW_DIRTY_RELEASE_AAR") == "1"
    inputs.property("allowDirtyPreflight", allowDirtyPreflight)
    if (allowDirtyPreflight) {
        verifyArguments.add("--allow-dirty")
    }
    commandLine(verifyArguments)
    doFirst { environment("COAKKA_ANDROID_API_TOOL_CLASSPATH", managedApiTools.asPath) }
}

val verifyReleaseAarHardeningNegativeControls by tasks.registering(Exec::class) {
    group = "verification"
    description = "Proves the AAR hardening gate refuses dirty metadata and unexpected native entries."
    dependsOn("bundleReleaseAar")
    inputs.files("scripts/verify-release-aar.py", "scripts/test-verify-release-aar.py")
    inputs.file(layout.buildDirectory.file("outputs/aar/coakka-runtime-android-release.aar"))
    workingDir = projectDir
    commandLine(
        "python3",
        "-B",
        "scripts/test-verify-release-aar.py",
        "--aar",
        layout.buildDirectory.file("outputs/aar/coakka-runtime-android-release.aar").get().asFile.absolutePath,
        "--connector-root",
        projectDir.absolutePath,
    )
    doFirst { environment("COAKKA_ANDROID_API_TOOL_CLASSPATH", managedApiTools.asPath) }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    dependsOn(verifyReleaseAarHardening, verifyReleaseAarHardeningNegativeControls,
        verifyManagedConsumers, verifyManagedHardeningNegativeControls)
}

val androidConnectorSourcesJar by tasks.registering(Jar::class) {
    dependsOn(generateCoAkkaProto)
    archiveClassifier.set("sources")
    from("src/main/java")
    from(generatedProtoDir)
    from("src/main/cpp") {
        into("src/main/cpp")
    }
    from("src/hostTest/cpp") {
        into("src/hostTest/cpp")
    }
    from("host-test") {
        into("host-test")
    }
    from("scripts") {
        into("scripts")
    }
    from("device-smoke/build.gradle.kts") {
        into("device-smoke")
    }
    from("device-smoke/settings.gradle.kts") {
        into("device-smoke")
    }
    from("device-smoke/proguard-smoke-rules.pro") {
        into("device-smoke")
    }
    from("device-smoke/test-proguard-rules.pro") {
        into("device-smoke")
    }
    from("device-smoke/src") {
        into("device-smoke/src")
    }
    from(
        "build.gradle.kts",
        "release-identity.properties",
        "maven-central.init.gradle.kts",
        "maven-central.gradle.kts",
        "settings.gradle.kts",
        "consumer-rules.pro",
        "README.md",
        "SYSTEMS-AUDIT.md",
        "src/main/AndroidManifest.xml",
    )
    packageLegalFiles.forEach { (packagedName, source) ->
        from(source.asFile) {
            rename { packagedName }
        }
    }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val androidConnectorJavadocJar by tasks.registering(Jar::class) {
    group = "documentation"
    description = "Packages Dokka's Javadoc output for Maven Central."
    archiveClassifier.set("javadoc")
    dependsOn("dokkaGeneratePublicationJavadoc")
    from(tasks.named("dokkaGeneratePublicationJavadoc"))
    packageLegalFiles.forEach { (packagedName, source) ->
        from(source.asFile) {
            into("META-INF")
            rename {
                if (packagedName == "NATIVE-LICENSE.md") {
                    "COAKKA-NATIVE-LICENSE.md"
                } else {
                    packagedName
                }
            }
        }
    }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = "coakka-runtime-android"
            version = project.version.toString()
            artifact(androidConnectorSourcesJar)
            artifact(androidConnectorJavadocJar)
            pom {
                name.set("CoAkka Runtime Android")
                description.set(
                    "Free for application use, including commercial and production use. " +
                        "Android JNI/Kotlin connector for CoAkka Runtime Core.",
                )
                url.set("https://github.com/phuong-tran/coakka-samples/tree/android-runtime-3.0.0/source-mirrors/android-runtime/3.0.0")
                licenses {
                    license {
                        name.set("CoAkka Package License Map (free for application use)")
                        url.set("https://github.com/phuong-tran/coakka-samples/blob/d401e4a62359dcf245ffda093f2cbea21fe5f39a/docs/package-licensing.md")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("phuong-tran")
                        name.set("Phuong Tran")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/phuong-tran/coakka-samples.git")
                    developerConnection.set("scm:git:ssh://git@github.com/phuong-tran/coakka-samples.git")
                    url.set("https://github.com/phuong-tran/coakka-samples/tree/android-runtime-3.0.0/source-mirrors/android-runtime/3.0.0")
                    tag.set("android-runtime-3.0.0")
                }
            }
        }
    }
}

afterEvaluate {
    publishing.publications.named<MavenPublication>("release") {
        from(components["release"])
    }
}
