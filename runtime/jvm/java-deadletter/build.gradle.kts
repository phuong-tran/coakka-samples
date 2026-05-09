plugins {
    java
    application
}

dependencies {
    implementation("coakka.v2:coakka-jvm-native-runtime-v2:0.1.1-ga671b3a")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("coakka.samples.runtime.jvm.javadeadletter.Main")
}
