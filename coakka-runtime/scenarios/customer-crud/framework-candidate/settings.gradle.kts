pluginManagement {
    repositories { gradlePluginPortal(); mavenCentral() }
    val spring = System.getenv("COAKKA_SAMPLE_SPRING_BOOT_VERSION") ?: "3.5.16"
    val quarkus = System.getenv("COAKKA_SAMPLE_QUARKUS_VERSION") ?: "3.35.2"
    require(spring in setOf("3.2.7", "3.4.13", "3.5.16")) { "unqualified Spring Boot sample version" }
    require(quarkus in setOf("3.20.4", "3.27.4", "3.35.2")) { "unqualified Quarkus sample version" }
    plugins {
        id("org.springframework.boot") version spring
        id("io.quarkus") version quarkus
    }
}

rootProject.name = "coakka-runtime-framework-candidate"
include(":runtime:scenarios:customer-crud:spring-boot-spring-boot:customer-contract")
include(":runtime:scenarios:customer-crud:spring-boot-starter-local:customer-app")
include(":runtime:scenarios:customer-crud:quarkus-local:customer-app")

// Keep the existing business contract and app sources; only this small
// candidate build owns their dependency admission and output directories.
val samples = rootDir.resolve("../../../..").canonicalFile
fun mapSources(project: org.gradle.api.initialization.ProjectDescriptor) {
    val parts = project.path.removePrefix(":").split(":")
    project.projectDir = samples.resolve((listOf("coakka-runtime") + parts.drop(1)).joinToString("/"))
    project.children.forEach(::mapSources)
}
rootProject.children.forEach(::mapSources)
