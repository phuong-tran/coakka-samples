plugins {
    `java-library`
    `maven-publish`
    id("io.spring.dependency-management")
}

group = "coakka.spring"
version = "0.1.0-ge2b402e"

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.5")
    }
}

dependencies {
    api("coakka.v2:coakka-jvm-native-runtime-v2:0.1.0-g22f571fd955c")
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("org.springframework.boot:spring-boot-starter")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}

java {
    withSourcesJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("CoAkka Spring Boot Starter")
                description.set("Spring Boot adapter for local CoAkka runtime capability handlers.")
            }
        }
    }
    repositories {
        maven {
            name = "coakkaPublishStaging"
            url = uri(
                providers.gradleProperty("coakkaSpringStarterPublishDir")
                    .orElse(providers.environmentVariable("COAKKA_SPRING_STARTER_PUBLISH_DIR"))
                    .orElse(layout.buildDirectory.dir("maven-staging").map { it.asFile.absolutePath })
                    .get()
            )
        }
    }
}
