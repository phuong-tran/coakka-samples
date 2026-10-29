import java.security.MessageDigest
import org.gradle.api.tasks.Exec
import java.util.jar.Manifest
import java.util.zip.ZipFile
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.jvm.tasks.Jar

plugins {
    base
}

val adapterVersion = providers.gradleProperty("coakkaAdapterVersion").get()
val runtimeGroup = providers.gradleProperty("coakkaRuntimeGroup").get()
val runtimeArtifact = providers.gradleProperty("coakkaRuntimeArtifact").get()
val runtimeVersion = providers.gradleProperty("coakkaRuntimeVersion").get()
val nativeCoreVersion = providers.gradleProperty("coakkaNativeCoreVersion")
val nativeGitCommit = providers.gradleProperty("coakkaNativeGitCommit")
val nativeGeneration = nativeCoreVersion.zip(nativeGitCommit) { coreVersion, gitCommit ->
    "$coreVersion+$gitCommit"
}.get()
val springBootVersion = providers.gradleProperty("coakkaSpringBootVersion").get()
val quarkusVersion = providers.gradleProperty("coakkaQuarkusVersion").get()
val selectedRuntimeSha256 = providers.gradleProperty("coakkaRuntimeJarSha256").get()
val selectedRuntimeRepository = providers.gradleProperty("coakkaRuntimeRepository").get()
require(Regex("[0-9a-f]{64}").matches(selectedRuntimeSha256))
val outputRoot = providers.gradleProperty("coakkaFrameworkBuildRoot").map { file(it) }
allprojects {
    if (outputRoot.isPresent) {
        layout.buildDirectory.set(outputRoot.get().resolve(if (path == ":") "root" else path.removePrefix(":")))
    }
}


subprojects {
    pluginManager.apply("java-library")

    group = "io.github.phuong-tran.coakka"
    version = adapterVersion

    repositories {
        mavenCentral()
        exclusiveContent {
            forRepository { maven { name = "selected-runtime"; url = uri(selectedRuntimeRepository) } }
            filter { includeModule(runtimeGroup, runtimeArtifact) }
        }
    }

    dependencies {
        add("api", "$runtimeGroup:$runtimeArtifact:$runtimeVersion")
        when (name) {
            "spring-boot-starter" -> {
                add("compileOnly", platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
                add("compileOnly", "org.springframework.boot:spring-boot-autoconfigure")
                add("compileOnly", "org.springframework:spring-aop")
                add("compileOnly", "com.fasterxml.jackson.core:jackson-databind")
                add("testImplementation", platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
                add("testImplementation", "org.springframework.boot:spring-boot-autoconfigure")
                add("testImplementation", "org.springframework:spring-aop")
                add("testImplementation", "com.fasterxml.jackson.core:jackson-databind")
                add("testImplementation", "org.junit.jupiter:junit-jupiter")
                add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
            }
            "quarkus-extension" -> {
                add("compileOnly", platform("io.quarkus.platform:quarkus-bom:$quarkusVersion"))
                add("compileOnly", "io.quarkus:quarkus-arc")
                add("compileOnly", "io.quarkus:quarkus-jackson")
                add("compileOnly", "com.fasterxml.jackson.core:jackson-databind")
                add("compileOnly", "org.osgi:org.osgi.annotation.bundle:2.0.0")
                add("testImplementation", platform("io.quarkus.platform:quarkus-bom:$quarkusVersion"))
                add("testImplementation", "io.quarkus:quarkus-arc")
                add("testImplementation", "io.quarkus:quarkus-jackson")
                add("testImplementation", "com.fasterxml.jackson.core:jackson-databind")
                add("testCompileOnly", "org.osgi:org.osgi.annotation.bundle:2.0.0")
                add("testImplementation", "org.junit.jupiter:junit-jupiter:5.12.2")
                add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher:1.12.2")
            }
            else -> error("unexpected framework adapter project '$name'")
        }
    }

    extensions.configure<JavaPluginExtension> {
        withSourcesJar()
        withJavadocJar()
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(17)
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    }

    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).addBooleanOption("Werror", true)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        val temporary = layout.buildDirectory.dir("test-temp")
        systemProperty("java.io.tmpdir", temporary.get().asFile.absolutePath)
        systemProperty("coakka.runtime.nativeDir", temporary.get().dir("native-cache").asFile.absolutePath)
        doFirst { temporary.get().asFile.mkdirs() }
    }

    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    val moduleName = when (name) {
        "spring-boot-starter" -> "coakka.spring.boot.starter"
        "quarkus-extension" -> "coakka.quarkus.extension"
        else -> error("unexpected framework adapter project '$name'")
    }
    val legalFiles = listOf("LICENSE", "NATIVE-LICENSE.md", "PACKAGE-LICENSE.md", "NOTICE")
        .associateWith { rootProject.layout.projectDirectory.file(it) }
    val frameworkKind = if (name == "spring-boot-starter") "spring" else "quarkus"
    val consumerRules = rootProject.layout.projectDirectory.file("build-support/$frameworkKind-consumer.pro")

    tasks.named<Jar>("jar") {
        manifest {
            attributes(
                "Automatic-Module-Name" to moduleName,
                "Coakka-Java-Baseline" to "17",
                "Coakka-Runtime-Jvm-Dependency" to runtimeVersion,
                "Coakka-Native-Generation" to nativeGeneration,
            )
            if (project.name == "quarkus-extension") {
                attributes("Coakka-Quarkus-Platform" to quarkusVersion)
            }
        }
        legalFiles.forEach { (packagedName, source) ->
            from(source) { into("META-INF"); rename { packagedName } }
        }
    }

    tasks.named<Jar>("sourcesJar") {
        legalFiles.forEach { (packagedName, source) ->
            from(source) { rename { packagedName } }
        }
    }

    tasks.named<Jar>("javadocJar") {
        legalFiles.forEach { (packagedName, source) ->
            from(source) { into("META-INF"); rename { packagedName } }
        }
    }

    val verifyRuntimeDependency = tasks.register("verifyRuntimeDependency") {
        group = "verification"
        description = "Verifies the exact public Runtime dependency used by the source mirror."
        doLast {
            val matches = configurations.getByName("runtimeClasspath")
                .resolvedConfiguration
                .resolvedArtifacts
                .filter { artifact ->
                    artifact.moduleVersion.id.group == runtimeGroup && artifact.name == runtimeArtifact
                }
            if (matches.size != 1 || matches.single().moduleVersion.id.version != runtimeVersion) {
                val found = matches.joinToString { artifact -> artifact.moduleVersion.id.toString() }
                error("expected exactly $runtimeGroup:$runtimeArtifact:$runtimeVersion; found $found")
            }
            val file = matches.single().file
            require(file.length() <= 64L * 1024 * 1024 &&
                MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) } == selectedRuntimeSha256) {
                "selected Runtime package digest differs"
            }
        }
    }

    val distribution = layout.buildDirectory.file("distribution/$name-$adapterVersion.jar")
    val shrink = tasks.register<Exec>("shrinkDistribution") {
        dependsOn(tasks.named("jar"), verifyRuntimeDependency)
        val tool = providers.gradleProperty("coakkaProguardJar").map(::File)
        val javaHome = providers.gradleProperty("coakkaJava17Home").map(::File)
        inputs.file(tasks.named<Jar>("jar").flatMap { it.archiveFile })
        inputs.files(configurations.named("compileClasspath"))
        inputs.file(consumerRules)
        inputs.files(rootProject.fileTree("build-support"))
        inputs.file(tool)
        inputs.file(javaHome.map { it.resolve("release") })
        inputs.dir(javaHome.map { it.resolve("jmods") })
        outputs.file(distribution)
        outputs.dir(layout.buildDirectory.dir("private-shrink"))
        doFirst {
            val args = mutableListOf("python3", rootProject.file("build-support/shrink-adapter.py").absolutePath,
                "--jar", tasks.named<Jar>("jar").get().archiveFile.get().asFile.absolutePath,
                "--output", distribution.get().asFile.absolutePath,
                "--source-root", layout.projectDirectory.dir("src/main/java").asFile.absolutePath,
                "--consumer-rules", consumerRules.asFile.absolutePath,
                "--java-home", javaHome.get().absolutePath, "--proguard-jar", tool.get().absolutePath,
                "--evidence", layout.buildDirectory.dir("private-shrink").get().asFile.absolutePath)
            configurations.named("compileClasspath").get().files.sortedBy { it.absolutePath }.forEach {
                args.addAll(listOf("--library", it.absolutePath))
            }
            commandLine(args)
        }
    }
    tasks.named("compileJava") { dependsOn(verifyRuntimeDependency) }
    tasks.named<Jar>("jar") {
        from(consumerRules) { into("META-INF/proguard"); rename { "coakka-$frameworkKind.pro" } }
    }
    listOf("apiElements", "runtimeElements").forEach { variant ->
        configurations.named(variant) {
            outgoing.artifacts.clear()
            outgoing.artifact(distribution) { builtBy(shrink) }
        }
    }

    val verifyAdapterJar = tasks.register("verifyAdapterJar") {
        group = "verification"
        description = "Verifies public adapter identity, Java baseline, and source-only artifact shape."
        dependsOn(shrink)
        doLast {
            val jarFile = distribution.get().asFile
            ZipFile(jarFile).use { zip ->
                val manifest = Manifest(zip.getInputStream(zip.getEntry("META-INF/MANIFEST.MF")))
                if (manifest.mainAttributes.getValue("Automatic-Module-Name") != moduleName) {
                    error("${jarFile.name} has the wrong Automatic-Module-Name")
                }
                if (manifest.mainAttributes.getValue("Coakka-Runtime-Jvm-Dependency") != runtimeVersion) {
                    error("${jarFile.name} has the wrong Runtime dependency identity")
                }
                if (manifest.mainAttributes.getValue("Coakka-Native-Generation") != nativeGeneration) {
                    error("${jarFile.name} has the wrong native generation identity")
                }
                val nativeEntries = zip.entries().asSequence()
                    .filter { entry ->
                        !entry.isDirectory && entry.name.substringAfterLast('.').lowercase() in
                            setOf("so", "dylib", "dll")
                    }
                    .map { it.name }
                    .toList()
                if (nativeEntries.isNotEmpty()) {
                    error("${jarFile.name} embeds native files: ${nativeEntries.joinToString()}")
                }
                val tooNew = zip.entries().asSequence()
                    .filter { entry -> !entry.isDirectory && entry.name.endsWith(".class") }
                    .mapNotNull { entry ->
                        val bytes = zip.getInputStream(entry).readBytes()
                        val major = ((bytes[6].toInt() and 0xff) shl 8) or (bytes[7].toInt() and 0xff)
                        if (major > 61) "${entry.name}:$major" else null
                    }
                    .toList()
                if (tooNew.isNotEmpty()) {
                    error("${jarFile.name} contains classes newer than Java 17: ${tooNew.joinToString()}")
                }
            }
        }
    }

    tasks.named("check") {
        dependsOn(verifyRuntimeDependency, verifyAdapterJar)
    }
}
