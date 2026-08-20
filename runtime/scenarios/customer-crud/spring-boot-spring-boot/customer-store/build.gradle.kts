plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":runtime:scenarios:customer-crud:spring-boot-spring-boot:customer-contract"))
    implementation("io.github.phuong-tran.coakka:runtime:2.5.2")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
