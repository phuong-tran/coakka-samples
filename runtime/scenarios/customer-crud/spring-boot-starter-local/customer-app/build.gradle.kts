plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":runtime:scenarios:customer-crud:spring-boot-spring-boot:customer-contract"))
    implementation("coakka.spring:coakka-spring-boot-starter:0.1.0-ge2b402e")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}

kotlin {
    jvmToolchain(17)
}
