import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import java.util.jar.Manifest
import java.util.zip.ZipFile
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.10"
    id("com.google.protobuf") version "0.9.5"
    id("org.jetbrains.dokka") version "2.2.0"
    id("org.jetbrains.dokka-javadoc") version "2.2.0"
    `java-library`
    `maven-publish`
}

val runtimeVersion = providers.gradleProperty("coakkaV2JvmVersion")
val nativeCoreVersion = providers.gradleProperty("coakkaV2NativeCoreVersion")
val nativeGitCommit = providers.gradleProperty("coakkaV2NativeGitCommit")
val nativePackageVersion = nativeCoreVersion.zip(nativeGitCommit) { coreVersion, gitCommit ->
    "$coreVersion+$gitCommit"
}
val sourceRepository = providers.gradleProperty("coakkaRuntimeJvmSourceRepository")
val sourceTag = providers.gradleProperty("coakkaRuntimeJvmSourceTag")
val sourcePath = providers.gradleProperty("coakkaRuntimeJvmSourcePath")
val sourceUrl = providers.provider {
    "${sourceRepository.get()}/tree/${sourceTag.get()}/${sourcePath.get()}"
}
// Binary native implementation inputs remain separate from managed source.
// The independently pinned distribution is read only for its exact resources,
// never placed on the compilation classpath or used instead of these sources.
val nativeInputJar = providers.gradleProperty("coakkaNativeInputJar").map(::File)
val nativeInputSha256 = providers.gradleProperty("coakkaNativeInputJarSha256")
val generatedPackagingSourceDir = layout.buildDirectory.dir("generated/sources/runtime-packaging/main/kotlin")
val generatedNativeResourcesDir = layout.buildDirectory.dir("generated/native-resources/main")

group = "io.github.phuong-tran.coakka"
version = runtimeVersion.get()

base {
    archivesName.set("runtime")
}

repositories {
    mavenCentral()
}

val protobufVersion = "4.31.1"
val coroutinesVersion = "1.10.2"

sourceSets {
    main {
        kotlin.srcDirs("src/main/kotlin", generatedPackagingSourceDir)
        resources.srcDir(generatedNativeResourcesDir)
    }
    test {
        kotlin.srcDir("src/test/kotlin")
        // Independent wire oracles are test tools, never production bindings.
        proto {
            include("coakka/v2/transport.proto", "coakka/v2/control.proto")
        }
    }
}

dependencies {
    api(kotlin("stdlib"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutinesVersion")

    testImplementation(kotlin("test"))
    testImplementation("com.google.protobuf:protobuf-java:$protobufVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
}

kotlin {
    jvmToolchain(17)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
}

tasks.withType<KotlinCompile>().configureEach {
    dependsOn("generateRuntimePackagingSource")
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        freeCompilerArgs.addAll("-Xjdk-release=8", "-Xjsr305=strict")
        // Match the owning compilation unit so internal JVM names reproduce.
        moduleName.set("coakka-jvm-native-runtime-v2")
    }
}

tasks.named<KotlinCompile>("compileKotlin") {
    compilerOptions.allWarningsAsErrors.set(true)
    compilerOptions.freeCompilerArgs.add("-Xno-source-debug-extension")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val verifyReleaseIdentity by tasks.registering {
    group = "verification"
    description = "Checks the immutable Runtime, native Core, and public SCM identities."
    inputs.property("runtimeVersion", runtimeVersion)
    inputs.property("nativeCoreVersion", nativeCoreVersion)
    inputs.property("nativeGitCommit", nativeGitCommit)
    inputs.property("sourceRepository", sourceRepository)
    inputs.property("sourceTag", sourceTag)
    inputs.property("sourcePath", sourcePath)
    doLast {
        val releaseVersion = runtimeVersion.get()
        if (!Regex("[0-9]+\\.[0-9]+\\.[0-9]+").matches(releaseVersion)) {
            error("Runtime source mirror requires a clean semantic release version")
        }
        if (!Regex("[0-9]+\\.[0-9]+\\.[0-9]+").matches(nativeCoreVersion.get())) {
            error("Runtime source mirror requires a clean native Core version")
        }
        if (!Regex("[0-9a-f]{40}").matches(nativeGitCommit.get())) {
            error("Runtime source mirror requires a full lowercase native Core commit")
        }
        if (sourceRepository.get() != "https://github.com/phuong-tran/coakka-samples" ||
            sourceTag.get() != "runtime-jvm-$releaseVersion" ||
            sourcePath.get() != "source-mirrors/runtime-jvm/$releaseVersion"
        ) {
            error("Runtime source mirror SCM identity does not match release $releaseVersion")
        }
    }
}

val generateRuntimePackagingSource by tasks.registering {
    dependsOn(verifyReleaseIdentity)
    inputs.property("runtimeVersion", runtimeVersion)
    inputs.property("nativeCoreVersion", nativeCoreVersion)
    inputs.property("nativePackageVersion", nativePackageVersion)
    outputs.dir(generatedPackagingSourceDir)
    doLast {
        val packageDir = generatedPackagingSourceDir.get().asFile.resolve("coakka/v2/connector")
        packageDir.mkdirs()
        packageDir.resolve("NativeRuntimePackaging.kt").writeText(
            """
            package coakka.v2.connector

            internal object NativeRuntimePackaging {
                const val jvmArtifactVersion = "${runtimeVersion.get()}"
                const val nativeCoreVersion = "${nativeCoreVersion.get()}"
                const val nativePackageVersion = "${nativePackageVersion.get()}"
            }
            """.trimIndent() + "\n",
        )
    }
}

val nativePlatforms = linkedMapOf(
    "macos-aarch64" to "libcoakka_runtime_v2.dylib",
    "linux-aarch64" to "libcoakka_runtime_v2.so",
    "linux-x86_64" to "libcoakka_runtime_v2.so",
    "windows-aarch64" to "libcoakka_runtime_v2.dll",
    "windows-x86_64" to "libcoakka_runtime_v2.dll",
)

val prepareRuntimeNativeResources by tasks.registering {
    dependsOn(verifyReleaseIdentity)
    inputs.file(nativeInputJar).optional()
    inputs.property("nativeInputSha256", nativeInputSha256)
    outputs.dir(generatedNativeResourcesDir)
    doLast {
        val source = nativeInputJar.orNull ?: error("provide the qualified distribution with -PcoakkaNativeInputJar=/absolute/runtime.jar")
        require(source.isFile && source.length() in 1..33_554_432 &&
            Files.isRegularFile(source.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            "bounded regular native input distribution required"
        }
        val expectedHash = nativeInputSha256.get()
        require(expectedHash.matches(Regex("[0-9a-f]{64}")) && sha256(source) == expectedHash) {
            "native input distribution differs from the frozen source manifest"
        }
        val payload = linkedMapOf<String, ByteArray>()
        ZipFile(source).use { zip ->
            val entries = zip.entries().asSequence().take(4097).toList()
            require(entries.size <= 4096 && entries.map { it.name }.toSet().size == entries.size) { "invalid archive inventory" }
            val expected = nativePlatforms.flatMap { (platform, core) ->
                val bridge = if (platform.startsWith("windows-")) "coakka_runtime_jvm.dll"
                    else "libcoakka_runtime_jvm." + if (platform.startsWith("macos-")) "dylib" else "so"
                listOf("native/$platform/$core", "native/$platform/$bridge", "native/$platform/coakka-runtime-native.properties")
            }.toSet()
            require(entries.filter { !it.isDirectory && it.name.startsWith("native/") }.map { it.name }.toSet() == expected) {
                "exact five Core/bridge resource closures required"
            }
            var total = 0L
            for (name in expected.sorted()) {
                val entry = zip.getEntry(name)
                require(entry.size in 1..67_108_864) { "native resource extent exceeds its bound" }
                total += entry.size
                require(total <= 134_217_728) { "native resource family exceeds its bound" }
                val bytes = zip.getInputStream(entry).use { it.readNBytes(entry.size.toInt() + 1) }
                require(bytes.size.toLong() == entry.size) { "native resource extent differs" }
                payload[name] = bytes
            }
        }
        val output = generatedNativeResourcesDir.get().asFile
        delete(output)
        payload.forEach { (name, bytes) ->
            output.resolve(name).apply { parentFile.mkdirs(); writeBytes(bytes) }
        }
    }
}

tasks.processResources {
    dependsOn(prepareRuntimeNativeResources)
}

tasks.test {
    dependsOn(prepareRuntimeNativeResources)
    useJUnitPlatform()
    doFirst {
        val platform = runtimePlatformId()
        val fileName = nativePlatforms[platform]
        val nativeRoot = generatedNativeResourcesDir.get().asFile.resolve("native/$platform")
        val runtimeLibrary = fileName?.let { nativeRoot.resolve(it) }
        if (runtimeLibrary?.isFile == true) {
            systemProperty("coakka.runtime.lib", runtimeLibrary.absolutePath)
            systemProperty("coakka.fileLane.runtime.lib", runtimeLibrary.absolutePath)
            systemProperty("coakka.streamLane.runtime.lib", runtimeLibrary.absolutePath)
            val bridgeName = if (platform.startsWith("windows-")) "coakka_runtime_jvm.dll"
                else "libcoakka_runtime_jvm." + if (platform.startsWith("macos-")) "dylib" else "so"
            systemProperty("coakka.runtime.bridge", nativeRoot.resolve(bridgeName).absolutePath)
        }
    }
}

tasks.named<Jar>("jar") {
    exclude("coakka/v2/connector/demo/**")
    from("LICENSE") { into("META-INF") }
    from("NATIVE-LICENSE.md") {
        into("META-INF")
        rename { "COAKKA-NATIVE-LICENSE.md" }
    }
    from("PACKAGE-LICENSE.md") { into("META-INF") }
    from("NOTICE") { into("META-INF") }
    manifest {
        attributes(
            "Main-Class" to "coakka.v2.connector.JvmRuntimeJarSmoke",
            "Implementation-Title" to "CoAkka JVM Native Runtime V2",
            "Implementation-Version" to project.version.toString(),
            "Automatic-Module-Name" to "coakka.v2.runtime",
            "Coakka-V2-Jvm-Version" to runtimeVersion.get(),
            "Coakka-V2-Native-Core-Version" to nativeCoreVersion.get(),
            "Coakka-V2-Native-Package-Version" to nativePackageVersion.get(),
        )
    }
}

val shrunkRuntimeJar = layout.buildDirectory.file("distribution/runtime-${project.version}.jar")
val sourceJava8Home = providers.gradleProperty("coakkaRuntimeJava8Home").map(::File)
val sourceProguardJar = providers.gradleProperty("coakkaRuntimeProguardJar").map(::File)
val shrinkRuntimeJvm by tasks.registering(Exec::class) {
    dependsOn(tasks.jar)
    inputs.files(tasks.jar.flatMap { it.archiveFile }, configurations.runtimeClasspath,
        file("src/main/kotlin"), file("src/main/resources/META-INF/proguard/coakka-runtime.pro"), file("scripts/shrink-runtime.py"))
    outputs.file(shrunkRuntimeJar)
    doFirst {
        val java8 = sourceJava8Home.orNull ?: error("-PcoakkaRuntimeJava8Home is required")
        val tool = sourceProguardJar.orNull ?: error("-PcoakkaRuntimeProguardJar is required")
        val args = mutableListOf(
            "python3", file("scripts/shrink-runtime.py").absolutePath,
            "--jar", tasks.jar.get().archiveFile.get().asFile.absolutePath,
            "--output", shrunkRuntimeJar.get().asFile.absolutePath,
            "--source-root", file("src/main/kotlin").absolutePath,
            "--consumer-rules", file("src/main/resources/META-INF/proguard/coakka-runtime.pro").absolutePath,
            "--java8-home", java8.absolutePath, "--proguard-jar", tool.absolutePath,
            "--evidence", layout.buildDirectory.dir("private-shrink").get().asFile.absolutePath,
        )
        configurations.runtimeClasspath.get().files.forEach { args.addAll(listOf("--library", it.absolutePath)) }
        commandLine(args)
    }
}

val smokeShrunkRuntimeJvmJava8 by tasks.registering(Exec::class) {
    dependsOn(shrinkRuntimeJvm)
    doFirst {
        val java8 = sourceJava8Home.orNull ?: error("-PcoakkaRuntimeJava8Home is required")
        val temporary = layout.buildDirectory.dir("java8-smoke-temp").get().asFile
        temporary.mkdirs()
        commandLine(java8.resolve("bin/java").absolutePath,
            "-Djava.io.tmpdir=" + temporary.absolutePath,
            "-Dcoakka.runtime.nativeDir=" + layout.buildDirectory.dir("java8-native-cache").get().asFile.absolutePath,
            "-cp", files(shrunkRuntimeJar.get().asFile, configurations.runtimeClasspath).asPath,
            "coakka.v2.connector.JvmRuntimeJarSmoke")
    }
}


val runtimeSourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from("src/main/kotlin")
    from("README.md")
    from("CONSUMING.md")
    from("RELEASE.md")
    from("JVM_COMPATIBILITY.md")
    from("MAVEN_CENTRAL.md")
    from("LICENSE")
    from("NATIVE-LICENSE.md")
    from("PACKAGE-LICENSE.md")
    from("NOTICE")
    exclude("coakka/v2/connector/demo/**")
}

val runtimeJavadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    dependsOn("dokkaGeneratePublicationJavadoc")
    from(tasks.named("dokkaGeneratePublicationJavadoc"))
    from("LICENSE") { into("META-INF") }
    from("NATIVE-LICENSE.md") {
        into("META-INF")
        rename { "COAKKA-NATIVE-LICENSE.md" }
    }
    from("PACKAGE-LICENSE.md") { into("META-INF") }
    from("NOTICE") { into("META-INF") }
}

val verifySourceManifest by tasks.registering {
    group = "verification"
    description = "Checks every projected source, legal, documentation, and build file against the mirror manifest."
    inputs.file("SOURCE-MANIFEST.sha256")
    doLast {
        val root = layout.projectDirectory.asFile
        val manifestFile = root.resolve("SOURCE-MANIFEST.sha256")
        val expected = manifestFile.readLines()
            .filter(String::isNotBlank)
            .associate { line ->
                val match = Regex("^([0-9a-f]{64})  ([A-Za-z0-9._/-]+)$").matchEntire(line)
                    ?: error("invalid source manifest row: '$line'")
                match.groupValues[2] to match.groupValues[1]
            }
        val actualFiles = root.walkTopDown()
            .filter(File::isFile)
            .filterNot { file ->
                val path = file.relativeTo(root).invariantSeparatorsPath
                path == "SOURCE-MANIFEST.sha256" || path.startsWith(".gradle/") || path.startsWith(".kotlin/") ||
                    path.startsWith("build/") || path.startsWith(".native-input/")
            }
            .associateBy { it.relativeTo(root).invariantSeparatorsPath }
        if (expected.keys != actualFiles.keys) {
            error("source manifest paths drifted; expected=${expected.keys.sorted()} actual=${actualFiles.keys.sorted()}")
        }
        actualFiles.forEach { (path, file) ->
            if (sha256(file) != expected[path]) {
                error("source manifest SHA-256 drifted for $path")
            }
        }
    }
}

val verifyCentralArtifactShape by tasks.registering {
    group = "verification"
    description = "Checks the reconstructed Central Runtime jar and sources jar shape."
    dependsOn(shrinkRuntimeJvm, runtimeSourcesJar, verifySourceManifest, smokeShrunkRuntimeJvmJava8)
    doLast {
        val runtimeJar = shrunkRuntimeJar.get().asFile
        ZipFile(runtimeJar).use { zip ->
            val manifest = Manifest(zip.getInputStream(zip.getEntry("META-INF/MANIFEST.MF")))
            if (manifest.mainAttributes.getValue("Automatic-Module-Name") != "coakka.v2.runtime" ||
                manifest.mainAttributes.getValue("Coakka-V2-Native-Package-Version") != nativePackageVersion.get()
            ) {
                error("reconstructed Runtime jar manifest identity is invalid")
            }
            val nativeEntries = zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith("native/") }
                .map { it.name }
                .toList()
            nativePlatforms.keys.forEach { platform ->
                if (nativeEntries.count { it.startsWith("native/$platform/") } != 3) {
                    error("reconstructed Runtime jar must contain two $platform images and their integrity record")
                }
            }
            val tooNew = zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".class") && it.name != "module-info.class" }
                .mapNotNull { entry ->
                    val bytes = zip.getInputStream(entry).readBytes()
                    val major = ((bytes[6].toInt() and 0xff) shl 8) or (bytes[7].toInt() and 0xff)
                    if (major > 52) "${entry.name}:$major" else null
                }
                .toList()
            if (tooNew.isNotEmpty()) {
                error("reconstructed Runtime jar contains classes newer than Java 8: ${tooNew.joinToString()}")
            }
        }
        ZipFile(runtimeSourcesJar.get().archiveFile.get().asFile).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toSet()
            if (entries.none { it.endsWith(".kt") } ||
                listOf("README.md", "CONSUMING.md", "RELEASE.md", "LICENSE", "NATIVE-LICENSE.md", "PACKAGE-LICENSE.md", "NOTICE").any { it !in entries }
            ) {
                error("reconstructed Runtime sources jar is incomplete")
            }
        }
    }
}

// The Java component must describe the hardened primary artifact as well.
// Replacing publication files alone leaves Gradle metadata pointing at the
// unshrunk JAR and makes module metadata invalid.
listOf("apiElements", "runtimeElements").forEach { name ->
    configurations.named(name) {
        outgoing.artifacts.clear()
        outgoing.artifact(shrunkRuntimeJar) { builtBy(shrinkRuntimeJvm) }
    }
}


publishing {
    publications {
        create<MavenPublication>("runtimeJvmCentral") {
            from(components["java"])
            setArtifacts(emptyList<Any>())
            artifact(shrunkRuntimeJar) { builtBy(shrinkRuntimeJvm) }
            artifact(runtimeSourcesJar)
            artifact(runtimeJavadocJar)
            groupId = "io.github.phuong-tran.coakka"
            artifactId = "runtime"
            version = project.version.toString()
            pom {
                name.set("coakka.runtime")
                description.set("Java 8-compatible JVM connector with packaged native runtimes for CoAkka Runtime v2.")
                url.set("https://github.com/phuong-tran/coakka-publish")
                inceptionYear.set("2025")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://github.com/phuong-tran/coakka-samples/blob/main/LICENSE")
                        distribution.set("repo")
                        comments.set("Applies to the language-level JVM connector material.")
                    }
                    license {
                        name.set("CoAkka Native Artifact License 1.2")
                        url.set("https://github.com/phuong-tran/coakka-samples/blob/main/NATIVE-LICENSE.md")
                        distribution.set("repo")
                        comments.set("Applies to bundled CoAkka native artifacts; see META-INF/PACKAGE-LICENSE.md.")
                    }
                }
                developers {
                    developer {
                        id.set("phuong-tran")
                        name.set("Phuong Tran")
                        email.set("gabrielgun1983@gmail.com")
                        url.set("https://github.com/phuong-tran")
                    }
                }
                scm {
                    connection.set("scm:git:${sourceRepository.get()}.git")
                    developerConnection.set("scm:git:ssh://git@github.com/phuong-tran/coakka-samples.git")
                    url.set(sourceUrl.get())
                    tag.set(sourceTag.get())
                }
                withXml {
                    val properties = asNode().appendNode("properties")
                    properties.appendNode("coakka.documentation.url", "https://github.com/phuong-tran/coakka-samples/tree/main/docs")
                    properties.appendNode("coakka.samples.url", "https://github.com/phuong-tran/coakka-samples")
                    properties.appendNode("coakka.source.url", sourceUrl.get())
                }
            }
        }
    }
    repositories {
        maven {
            name = "reproduction"
            url = uri(layout.buildDirectory.dir("central-reproduction"))
        }
    }
}

val assembleCentralBaseArtifacts by tasks.registering {
    group = "distribution"
    description = "Rebuilds the unsigned Maven Central base artifacts from public source and exact native inputs."
    dependsOn(
        verifyCentralArtifactShape,
        runtimeJavadocJar,
        "publishRuntimeJvmCentralPublicationToReproductionRepository",
    )
}

tasks.check {
    dependsOn(verifySourceManifest, verifyReleaseIdentity)
}

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun runtimePlatformId(): String {
    val os = System.getProperty("os.name").lowercase().let { name ->
        when {
            name.contains("mac") || name.contains("darwin") -> "macos"
            name.contains("linux") -> "linux"
            name.contains("windows") -> "windows"
            else -> error("unsupported os.name=${System.getProperty("os.name")}")
        }
    }
    val arch = when (System.getProperty("os.arch").lowercase()) {
        "aarch64", "arm64" -> "aarch64"
        "x86_64", "amd64" -> "x86_64"
        else -> error("unsupported os.arch=${System.getProperty("os.arch")}")
    }
    return "$os-$arch"
}
