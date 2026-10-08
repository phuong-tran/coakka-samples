plugins {
    kotlin("jvm") version "2.3.21"
    application
}

repositories {
    mavenCentral()
}

val coakkaHttpJar = providers.gradleProperty("coakkaHttpJar")
    .orElse(providers.environmentVariable("COAKKA_HTTP_JAR"))
val sampleBuildDir = providers.gradleProperty("sampleBuildDir")
val coakkaHttpHost = providers.gradleProperty("coakkaHttpHost")
val coakkaHttpBridge = providers.gradleProperty("coakkaHttpBridge")

layout.buildDirectory.set(file(sampleBuildDir.get()))

dependencies {
    implementation(files(coakkaHttpJar))
}

kotlin {
    jvmToolchain(17)
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    compilerOptions.freeCompilerArgs.add("-Xjdk-release=8")
    compilerOptions.allWarningsAsErrors.set(true)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

application {
    mainClass.set("sample.MainKt")
    applicationDefaultJvmArgs = listOf(
        "-Dcoakka.http.host.path=${coakkaHttpHost.get()}",
        "-Dcoakka.http.bridge.path=${coakkaHttpBridge.get()}",
    )
}

tasks.withType<JavaExec>().configureEach {
    systemProperty("coakka.http.host.path", coakkaHttpHost.get())
    systemProperty("coakka.http.bridge.path", coakkaHttpBridge.get())
}
