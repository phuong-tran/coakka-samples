import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.10"
    application
}

repositories { mavenCentral() }
dependencies { implementation("io.github.phuong-tran.coakka:runtime:3.0.0") }
kotlin { jvmToolchain(17) }

// Reuse the ordinary examples so the registry path teaches the same API.
sourceSets["main"].java.srcDir("../java-basic/src/main/java")
kotlin.sourceSets["main"].kotlin.srcDir("../basic/src/main/kotlin")
tasks.withType<JavaCompile>().configureEach { options.release.set(8) }
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        freeCompilerArgs.add("-Xjdk-release=8")
        allWarningsAsErrors.set(true)
    }
}
providers.environmentVariable("COAKKA_SAMPLE_WORK_ROOT").orNull?.let {
    layout.buildDirectory.set(file(it).resolve("build"))
}
application { mainClass.set("coakka.samples.runtime.jvm.basic.MainKt") }
tasks.register<JavaExec>("runJava") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("coakka.samples.runtime.jvm.javabasic.Main")
}
tasks.withType<JavaExec>().configureEach {
    providers.environmentVariable("COAKKA_SAMPLE_JAVA_HOME").orNull?.let {
        setExecutable(file(it).resolve("bin/java").absolutePath)
    }
    providers.environmentVariable("COAKKA_SAMPLE_WORK_ROOT").orNull?.let {
        systemProperty("java.io.tmpdir", it)
        systemProperty("coakka.runtime.nativeDir", file(it).resolve("native-cache").absolutePath)
    }
}
