plugins {
    kotlin("jvm") version "2.3.21"
    application
}

repositories {
    mavenCentral()
}

val coakkaHttpJar = providers.gradleProperty("coakkaHttpJar")
providers.gradleProperty("sampleBuildDir").orNull?.let {
    layout.buildDirectory.set(file(it))
}

dependencies {
    implementation(files(coakkaHttpJar))
    implementation("io.netty:netty-codec-http:4.1.137.Final")
    implementation("org.eclipse.jetty:jetty-server:12.1.13")
}

kotlin {
    jvmToolchain(17)
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

application {
    mainClass.set("benchmark.MainKt")
}
