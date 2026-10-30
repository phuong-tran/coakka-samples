import org.gradle.api.tasks.compile.JavaCompile
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

val workRoot = providers.environmentVariable("COAKKA_SAMPLE_WORK_ROOT").orNull
allprojects {
    val outputs = workRoot?.let { file(it).resolve("build") } ?: rootDir.resolve("build")
    layout.buildDirectory.set(outputs.resolve(if (path == ":") "root" else path.removePrefix(":").replace(':', '/')))
}
subprojects {
    repositories { mavenCentral() }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            allWarningsAsErrors.set(true)
        }
    }
    tasks.withType<JavaCompile>().configureEach { options.release.set(17) }
    tasks.withType<Jar>().configureEach { archiveVersion.set("") }
}
