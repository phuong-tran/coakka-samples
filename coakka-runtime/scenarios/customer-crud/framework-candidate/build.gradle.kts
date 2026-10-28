import groovy.json.JsonSlurper
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.security.MessageDigest
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.10" apply false
    kotlin("plugin.spring") version "2.3.10" apply false
    kotlin("plugin.allopen") version "2.3.10" apply false
    id("org.springframework.boot") apply false
    id("io.quarkus") apply false
}

fun requiredDirectory(name: String): File {
    val value = System.getenv(name) ?: error("set explicit $name")
    val file = File(value)
    require(file.isAbsolute && Files.isDirectory(file.toPath(), NOFOLLOW_LINKS)) {
        "$name must name an existing absolute directory"
    }
    return file.absoluteFile
}

val candidateHome = requiredDirectory("COAKKA_FRAMEWORK_CANDIDATE_HOME")
val work = requiredDirectory("COAKKA_SAMPLE_WORK_ROOT")
requiredDirectory("GRADLE_USER_HOME")
require(!work.toPath().startsWith(candidateHome.toPath()) &&
    !candidateHome.toPath().startsWith(work.toPath())) { "sample work and candidate input directories must be separate" }

for ((key, value) in System.getenv()) {
    require(value.isBlank() || (!key.startsWith("LD_", true) && !key.startsWith("DYLD_", true) &&
        key.uppercase() !in setOf("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH",
            "COAKKA_RUNTIME_LIB", "COAKKA_FILE_LANE_RUNTIME_LIB", "COAKKA_STREAM_LANE_RUNTIME_LIB"))) {
        "remove sample native/JVM override: $key"
    }
}
for (key in listOf("coakka.runtime.lib", "coakka.runtime.bridge", "coakka.fileLane.runtime.lib", "coakka.streamLane.runtime.lib")) {
    require(System.getProperty(key) == null) { "remove sample native override: $key" }
}

val catalog = rootDir.toPath().resolve("../../../artifacts/runtime-frameworks-candidate.json").normalize().toFile()
val admissionProcess = providers.exec {
    isIgnoreExitValue = true
    commandLine(System.getenv("COAKKA_FRAMEWORK_PYTHON") ?: "python3", "-I", "-B",
        rootDir.resolve("candidate-admission.py").absolutePath,
        "--catalog", catalog.absolutePath, "--home", candidateHome.absolutePath)
}
require(admissionProcess.result.get().exitValue == 0) {
    "candidate admission refused: " + admissionProcess.standardError.asText.get().take(2048)
}
val admission = admissionProcess.standardOutput.asText.get()
@Suppress("UNCHECKED_CAST")
val admitted = JsonSlurper().parseText(admission) as Map<String, Any>
@Suppress("UNCHECKED_CAST")
val memberRows = (admitted["artifacts"] as List<Map<String, Any>>).associateBy { it["path"] as String }
val coakkaGroup = "io.github.phuong-tran.coakka"
val libraries = mapOf(
    "org.jetbrains:annotations" to "23.0.0",
    "org.jetbrains.kotlin:kotlin-stdlib" to "2.3.10",
    "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm" to "1.10.2",
    "org.jetbrains.kotlinx:kotlinx-coroutines-jdk8" to "1.10.2",
)
// Gradle selects the common module before its JVM variant. Keep that metadata
// redirect at the same version as the admitted JVM JAR.
val metadataAliases = mapOf("org.jetbrains.kotlinx:kotlinx-coroutines-core" to "1.10.2")
// Application JSON support must use reflection from the selected compiler and
// standard-library line. This is an ordinary app dependency, not a catalog member.
val hostLibraries = mapOf("org.jetbrains.kotlin:kotlin-reflect" to "2.3.10")

fun checkedResolvedFile(file: File, member: Map<String, Any>) {
    val extent = (member["bytes"] as Number).toLong()
    require(Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS) && file.length() == extent) {
        "resolved candidate member extent differs: ${file.name}"
    }
    val digest = MessageDigest.getInstance("SHA-256")
    var bytes = 0L
    file.inputStream().use { input ->
        val buffer = ByteArray(65_536)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            bytes += count
            require(bytes <= extent) { "resolved candidate member grew" }
            digest.update(buffer, 0, count)
        }
    }
    val actual = digest.digest().joinToString("") { "%02x".format(it) }
    require(bytes == extent && actual == member["sha256"]) { "resolved candidate member hash differs: ${file.name}" }
}

layout.buildDirectory.set(work.resolve("build/root"))
subprojects {
    layout.buildDirectory.set(work.resolve("build/" + path.removePrefix(":").replace(':', '/')))
    repositories {
        // These modules must come from the admitted local repository. A missing
        // candidate or forced BOM downgrade must not resolve from a public repo.
        exclusiveContent {
            forRepository {
                maven {
                    name = "admittedFrameworkCandidate"
                    url = uri(candidateHome)
                    metadataSources { mavenPom(); gradleMetadata() }
                }
            }
            filter {
                includeGroup(coakkaGroup)
                (libraries.keys + metadataAliases.keys).forEach { module ->
                    val parts = module.split(':')
                    includeModule(parts[0], parts[1])
                }
            }
        }
        mavenCentral()
    }
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == coakkaGroup) {
                require(requested.name in setOf("runtime", "spring-boot-starter", "quarkus-extension") &&
                    requested.version == "3.0.0") { "only declared CoAkka 3.0.0 candidate modules are allowed" }
            }
        }
        resolutionStrategy.force(*(libraries + metadataAliases + hostLibraries).map { (module, version) -> "$module:$version" }.toTypedArray())
    }
    pluginManager.withPlugin("java") {
        tasks.withType<JavaCompile>().configureEach {
            options.release.set(17)
            options.encoding = "UTF-8"
            options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
        }
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        dependencies.add("implementation", "org.jetbrains.kotlin:kotlin-stdlib:2.3.10")
        tasks.withType<KotlinCompile>().configureEach {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
                freeCompilerArgs.add("-Xjdk-release=17")
                allWarningsAsErrors.set(true)
            }
        }
    }
    if (name == "customer-app") {
        val ownAdapter = if (path.contains(":spring-boot-starter-local:")) "spring-boot-starter" else "quarkus-extension"
        val verify = tasks.register("verifyCandidateClasspaths") {
            group = "verification"
            description = "Checks actual compile/runtime candidate files after dependency resolution."
            doLast {
                for (configurationName in listOf("compileClasspath", "runtimeClasspath")) {
                    val found = mutableSetOf<String>()
                    configurations.getByName(configurationName).incoming.artifacts.artifacts.forEach { artifact ->
                        val module = artifact.id.componentIdentifier as? ModuleComponentIdentifier
                        if (module != null) {
                            val coordinate = "${module.group}:${module.module}"
                            if (hostLibraries.containsKey(coordinate)) {
                                require(module.version == hostLibraries.getValue(coordinate)) {
                                    "resolved app-host $coordinate version differs"
                                }
                            }
                            val selected = module.group == coakkaGroup || libraries.containsKey(coordinate)
                            if (selected) {
                                val version = if (module.group == coakkaGroup) "3.0.0" else libraries.getValue(coordinate)
                                require(module.version == version) { "resolved $coordinate version differs" }
                                val relative = "${module.group.replace('.', '/')}/${module.module}/$version/${module.module}-$version.jar"
                                val member = memberRows[relative] ?: error("unadmitted candidate dependency: $coordinate")
                                checkedResolvedFile(artifact.file, member)
                                found.add(coordinate)
                            }
                        }
                    }
                    val allowed = libraries.keys + setOf("$coakkaGroup:runtime", "$coakkaGroup:$ownAdapter")
                    // Runtime exposes jdk8 support only in its runtime variant;
                    // a framework may also request it while compiling. Check its
                    // bytes whenever present without adding a compile dependency.
                    val required = if (configurationName == "compileClasspath") {
                        allowed - "org.jetbrains.kotlinx:kotlinx-coroutines-jdk8"
                    } else allowed
                    require(found.containsAll(required) && allowed.containsAll(found)) {
                        "resolved $configurationName must contain its declared runtime/adapter libraries"
                    }
                }
            }
        }
        tasks.matching { it.name in setOf("compileKotlin", "compileJava", "bootJar", "quarkusBuild") }.configureEach {
            dependsOn(verify)
        }
        pluginManager.withPlugin("org.springframework.boot") {
            tasks.named<Jar>("bootJar") { archiveFileName.set("customer-app.jar") }
        }
    }
}

tasks.register("verifyFrameworkCandidates") {
    dependsOn(subprojects.filter { it.name == "customer-app" }.map { it.tasks.named("verifyCandidateClasspaths") })
}
