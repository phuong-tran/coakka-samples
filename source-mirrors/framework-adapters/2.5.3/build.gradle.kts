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

subprojects {
    pluginManager.apply("java-library")

    group = "io.github.phuong-tran.coakka"
    version = adapterVersion

    repositories {
        mavenCentral()
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
    val artifactLicense = rootProject.layout.projectDirectory.file("LICENSE")

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
        from(artifactLicense) {
            into("META-INF")
            rename { "LICENSE" }
        }
    }

    tasks.named<Jar>("sourcesJar") {
        from(artifactLicense) {
            rename { "LICENSE" }
        }
    }

    tasks.named<Jar>("javadocJar") {
        from(artifactLicense) {
            into("META-INF")
            rename { "LICENSE" }
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
        }
    }

    val verifyAdapterJar = tasks.register("verifyAdapterJar") {
        group = "verification"
        description = "Verifies public adapter identity, Java baseline, and source-only artifact shape."
        dependsOn(tasks.named("jar"))
        doLast {
            val jarFile = tasks.named<Jar>("jar").get().archiveFile.get().asFile
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
