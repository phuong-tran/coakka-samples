plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":runtime:scenarios:customer-crud:spring-boot-spring-boot:customer-contract"))
    implementation("coakka.v2:coakka-jvm-native-runtime-v2:0.2.0-gc124a9e-2bab9ee")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

kotlin {
    jvmToolchain(17)
}
