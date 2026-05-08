plugins {
    java
    application
}

dependencies {
    implementation("coakka.logger:coakka-jvm-native-logger:0.1.0-gba2a66d98eb5")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("coakka.samples.logger.jvm.javapressure.Main")
}
