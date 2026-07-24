plugins {
    java
    application
}

dependencies {
    implementation("coakka.v2:coakka-jvm-native-runtime-v2:1.3.1-g0da8c2d9-8ff6f32")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("coakka.samples.runtime.jvm.javabasic.Main")
}
