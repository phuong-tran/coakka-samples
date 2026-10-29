import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.security.MessageDigest
import java.time.Duration
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.10" apply false
}

fun requiredPath(name: String, directory: Boolean = true): File {
    val value = System.getenv(name) ?: error("set explicit $name")
    val path = File(value)
    require(path.isAbsolute) { "$name must be absolute" }
    if (directory) require(Files.isDirectory(path.toPath(), NOFOLLOW_LINKS)) { "$name must be an existing directory" }
    return path
}

// Cold artifact admission only. Runtime/default/capability truth stays in Core.
val candidate = requiredPath("COAKKA_JVM_CANDIDATE_HOME")
val work = requiredPath("COAKKA_SAMPLE_WORK_ROOT")
requiredPath("GRADLE_USER_HOME")
val runtimeHome = requiredPath("COAKKA_SAMPLE_JAVA_HOME")
val nativeDirectory = requiredPath("COAKKA_SAMPLE_JVM_NATIVE_DIR", false)
require(nativeDirectory.parentFile?.isDirectory == true) { "native cache parent must exist" }
val windowsBuild = System.getProperty("os.name").startsWith("Windows")
require(!windowsBuild || nativeDirectory.path.all { it.code < 128 }) { "Windows Java8 native cache path must be ASCII" }
val runtimeJava = runtimeHome.resolve("bin/" + if (windowsBuild) "java.exe" else "java")
require(runtimeJava.isFile) { "consumer Java executable is missing" }
for ((key, value) in System.getenv()) {
    require(value.isBlank() || (!key.startsWith("LD_", true) && !key.startsWith("DYLD_", true) &&
        key.uppercase() !in setOf("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH", "COAKKA_RUNTIME_LIB", "COAKKA_FILE_LANE_RUNTIME_LIB", "COAKKA_STREAM_LANE_RUNTIME_LIB"))) {
        "remove sample override: $key"
    }
}
for (key in listOf("coakka.runtime.lib", "coakka.runtime.bridge", "coakka.fileLane.runtime.lib", "coakka.streamLane.runtime.lib")) {
    require(System.getProperty(key) == null) { "remove sample native override: $key" }
}
val catalog = rootDir.resolve("../artifacts/runtime-jvm-candidate.properties")
require(Files.isRegularFile(catalog.toPath(), NOFOLLOW_LINKS) && catalog.length() in 1..65_536) { "bounded candidate catalog required" }
val properties = object : Properties() {
    override fun put(key: Any, value: Any): Any? {
        require(!containsKey(key)) { "duplicate candidate catalog field" }
        return super.put(key, value)
    }
}.apply { catalog.inputStream().use { load(it) } }
val expectedKeys = mutableSetOf("format", "state", "version", "core.source", "files")
repeat(5) { index -> for (field in listOf("name", "bytes", "sha256")) expectedKeys.add("file.$index.$field") }
require(properties.stringPropertyNames() == expectedKeys &&
    properties.getProperty("format") == "coakka.runtime.jvm-candidate.v1" &&
    properties.getProperty("state") == "private-candidate" &&
    properties.getProperty("version") == "3.0.0" &&
    properties.getProperty("core.source") == "1522b4a9f30da1039ef8c7d198bbecfc749a2456" &&
    properties.getProperty("files") == "5") { "invalid private JVM candidate identity" }
var totalBytes = 0L
val admitted = (0 until 5).map { index ->
    val name = properties.getProperty("file.$index.name")
    require(name.length in 1..160 && !name.contains('\\') && !name.contains(':') && name.endsWith(".jar") &&
        name.split('/').all { it.isNotEmpty() && it != "." && it != ".." }) { "invalid candidate member name" }
    val extent = properties.getProperty("file.$index.bytes").toLongOrNull() ?: error("invalid candidate extent")
    val expected = properties.getProperty("file.$index.sha256")
    require(extent in 1..8_388_608 && expected.matches(Regex("[0-9a-f]{64}"))) { "invalid candidate member identity" }
    totalBytes += extent
    require(totalBytes <= 33_554_432) { "candidate extent exceeds bound" }
    val file = candidate.resolve(name)
    require(Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS) && file.length() == extent) { "missing or non-regular candidate member: $name" }
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(65_536)
    var read = 0L
    file.inputStream().use { input ->
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            read += count
            require(read <= extent) { "candidate member grew: $name" }
            digest.update(buffer, 0, count)
        }
    }
    val actual = digest.digest().joinToString("") { "%02x".format(it) }
    require(read == extent && actual == expected) { "candidate member identity differs: $name" }
    file
}
require(admitted.map { it.name }.toSet().size == 5 && properties.getProperty("file.0.name") == "runtime.jar") { "candidate classpath must contain five distinct artifacts" }
layout.buildDirectory.set(work.resolve("build-root"))
subprojects {
    layout.buildDirectory.set(work.resolve("build-$name"))
    // Kotlin build tools use this repository; --offline forbids downloads.
    // Consumer dependencies below are exclusively the admitted local files.
    repositories { mavenCentral() }
    pluginManager.withPlugin("java") {
        dependencies.add("implementation", files(admitted))
        tasks.withType<JavaCompile>().configureEach {
            options.release.set(8)
            options.encoding = "UTF-8"
            options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
        }
        tasks.withType<JavaExec>().configureEach {
            setExecutable(runtimeJava.absolutePath)
            jvmArgs("-Dfile.encoding=UTF-8", "-Dcoakka.runtime.nativeDir=${nativeDirectory.absolutePath}")
            timeout.set(Duration.ofSeconds(30))
        }
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        tasks.withType<KotlinCompile>().configureEach {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_1_8)
                freeCompilerArgs.add("-Xjdk-release=8")
                allWarningsAsErrors.set(true)
            }
        }
    }
}
