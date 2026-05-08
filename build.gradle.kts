plugins {
    kotlin("jvm") version "2.3.21" apply false
    kotlin("plugin.allopen") version "2.3.21" apply false
    kotlin("plugin.spring") version "2.3.21" apply false
    id("org.springframework.boot") version "3.4.5" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("io.quarkus") version "3.35.2" apply false
}

group = "coakka.samples"
version = "0.1.0"

val coakkaPublishMavenUrl = providers.gradleProperty("coakkaPublishMavenUrl")
    .orElse(providers.environmentVariable("COAKKA_PUBLISH_MAVEN_URL"))
    .orElse("https://raw.githubusercontent.com/phuong-tran/coakka-publish/main/maven")
val coakkaPublishMavenLocal = providers.gradleProperty("coakkaPublishMavenLocal")
    .orElse(providers.environmentVariable("COAKKA_PUBLISH_MAVEN_LOCAL"))
    .orElse(rootProject.layout.projectDirectory.dir("../coakka-publish-public/maven").asFile.absolutePath)

allprojects {
    repositories {
        mavenCentral()
        maven {
            name = "coakka-publish-local"
            url = uri(coakkaPublishMavenLocal.get())
        }
        maven {
            name = "coakka-publish"
            url = uri(coakkaPublishMavenUrl.get())
        }
    }
}
