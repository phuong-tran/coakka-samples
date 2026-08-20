import groovy.json.JsonSlurper
import java.io.File
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
val nativeInputRoot = providers.gradleProperty("coakkaNativeInputRoot")
    .map(::File)
    .orElse(
        providers.provider {
            layout.projectDirectory.dir(".native-input/${nativePackageVersion.get()}").asFile
        },
    )
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
val jnaVersion = "5.17.0"
val serializationVersion = "1.7.3"

sourceSets {
    main {
        kotlin.srcDirs("src/main/kotlin", generatedPackagingSourceDir)
        proto {
            srcDir("src/main/proto")
            include("coakka/v2/*.proto")
        }
        resources.srcDir(generatedNativeResourcesDir)
    }
    test {
        kotlin.srcDir("src/test/kotlin")
    }
}

dependencies {
    api(kotlin("stdlib"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    api("net.java.dev.jna:jna:$jnaVersion")
    api("com.google.protobuf:protobuf-java:$protobufVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")

    testImplementation(kotlin("test"))
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
    }
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
    inputs.dir(nativeInputRoot).optional()
    inputs.property("nativePackageVersion", nativePackageVersion)
    outputs.dir(generatedNativeResourcesDir)
    doLast {
        val inputRoot = nativeInputRoot.get().absoluteFile
        val manifestFile = inputRoot.resolve("manifest.json")
        if (!manifestFile.isFile) {
            error(
                "missing native input manifest at ${manifestFile.absolutePath}; provide the exact five-platform " +
                    "Core release directory with -PcoakkaNativeInputRoot=/abs/path/to/native-generation",
            )
        }
        val manifest = JsonSlurper().parse(manifestFile) as? Map<*, *>
            ?: error("native input manifest must contain a JSON object")
        if (manifest["nativePackageVersion"] != nativePackageVersion.get() ||
            manifest["nativeCoreVersion"] != nativeCoreVersion.get() ||
            manifest["nativeGitCommit"] != nativeGitCommit.get()
        ) {
            error("native input manifest identity does not match ${nativePackageVersion.get()}")
        }
        val libraries = (manifest["libraries"] as? List<*>)
            ?.map { it as? Map<*, *> ?: error("native input library row must be an object") }
            ?: error("native input manifest is missing libraries")
        val byPlatform = libraries.associateBy { it["platform"]?.toString().orEmpty() }
        if (libraries.size != nativePlatforms.size || byPlatform.keys != nativePlatforms.keys) {
            error("native input manifest must contain exactly ${nativePlatforms.keys}; found ${byPlatform.keys}")
        }

        val outputRoot = generatedNativeResourcesDir.get().asFile
        delete(outputRoot)
        nativePlatforms.forEach { (platform, fileName) ->
            val row = byPlatform.getValue(platform)
            if (row["fileName"] != fileName || row["role"] != "runtime") {
                error("native input manifest has an invalid $platform file/role")
            }
            val source = inputRoot.resolve("$platform/$fileName")
            if (!source.isFile) {
                error("missing native input for $platform at ${source.absolutePath}")
            }
            val expectedSha256 = row["sha256"]?.toString().orEmpty()
            if (!Regex("[0-9a-f]{64}").matches(expectedSha256) || sha256(source) != expectedSha256) {
                error("native input SHA-256 mismatch for $platform")
            }
            val extension = fileName.substringAfterLast('.')
            val versionedName = "libcoakka_runtime_v2-${nativePackageVersion.get()}.$extension"
            project.copy {
                from(source)
                into(outputRoot.resolve("native/$platform"))
                rename { versionedName }
            }
        }
    }
}

tasks.processResources {
    dependsOn(prepareRuntimeNativeResources)
}

tasks.test {
    useJUnitPlatform()
    doFirst {
        val platform = runtimePlatformId()
        val fileName = nativePlatforms[platform]
        val runtimeLibrary = fileName?.let { nativeInputRoot.get().resolve("$platform/$it") }
        if (runtimeLibrary?.isFile == true) {
            systemProperty("coakka.runtime.lib", runtimeLibrary.absolutePath)
            systemProperty("coakka.fileLane.runtime.lib", runtimeLibrary.absolutePath)
            systemProperty("coakka.streamLane.runtime.lib", runtimeLibrary.absolutePath)
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

val runtimeSourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from("src/main/kotlin")
    from("README.md")
    from("CONSUMING.md")
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
                path == "SOURCE-MANIFEST.sha256" || path.startsWith(".gradle/") ||
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
    dependsOn(tasks.jar, runtimeSourcesJar, verifySourceManifest)
    doLast {
        val runtimeJar = tasks.named<Jar>("jar").get().archiveFile.get().asFile
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
                if (nativeEntries.count { it.startsWith("native/$platform/") } != 1) {
                    error("reconstructed Runtime jar must contain exactly one $platform native")
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
                listOf("LICENSE", "NATIVE-LICENSE.md", "PACKAGE-LICENSE.md", "NOTICE").any { it !in entries }
            ) {
                error("reconstructed Runtime sources jar is incomplete")
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("runtimeJvmCentral") {
            from(components["java"])
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
