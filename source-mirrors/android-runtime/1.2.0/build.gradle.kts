import java.util.Properties
import org.gradle.jvm.tasks.Jar
import org.gradle.api.tasks.testing.Test

plugins {
    id("com.android.library") version "9.2.1"
    id("org.jetbrains.dokka") version "2.2.0"
    id("org.jetbrains.dokka-javadoc") version "2.2.0"
    `maven-publish`
    signing
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
    "NOTICE" to coreRepositoryDir.file("public-docs/legal/NOTICE"),
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

dependencies {
    implementation("com.google.protobuf:protobuf-javalite:$protobufVersion")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    add(protocCompiler.name, "com.google.protobuf:protoc:$protobufVersion:$protocClassifier@exe")
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

val stageNativeRuntime by tasks.registering(Exec::class) {
    inputs.property("nativeRuntimeGitCommit", nativeRuntimeGitCommit)
    inputs.file("scripts/build-native-runtime.sh")
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
                url.set("https://github.com/phuong-tran/coakka-samples/tree/android-runtime-1.2.0/source-mirrors/android-runtime/1.2.0")
                licenses {
                    license {
                        name.set("CoAkka Package License Map (free for application use)")
                        url.set("https://github.com/phuong-tran/coakka-samples/blob/licenses-1.2/PACKAGE-LICENSE.md")
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
                    url.set("https://github.com/phuong-tran/coakka-samples/tree/android-runtime-1.2.0/source-mirrors/android-runtime/1.2.0")
                    tag.set("android-runtime-1.2.0")
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
