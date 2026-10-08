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
    implementation("org.eclipse.jetty:jetty-server:12.1.13")
}

kotlin {
    // This local benchmark app is built and run on Trixie's JDK 21. The
    // separately verified connector JAR retains its Java 8 bytecode contract.
    jvmToolchain(21)
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
}

application {
    mainClass.set("benchmark.MainKt")
}
