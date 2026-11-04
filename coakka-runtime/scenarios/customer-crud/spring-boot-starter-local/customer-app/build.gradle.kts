import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
}

val coakkaRuntimeVersion = rootProject.extra.properties["coakkaSampleRuntimeVersion"] as? String ?: "3.2.1"

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation(project(":runtime:scenarios:customer-crud:spring-boot-spring-boot:customer-contract"))
    implementation("io.github.phuong-tran.coakka:spring-boot-starter:$coakkaRuntimeVersion")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}

kotlin {
    jvmToolchain(17)
}

kotlin.sourceSets.named("main") {
    kotlin.srcDir(projectDir.resolve("../../framework-candidate/diagnostics"))
}
