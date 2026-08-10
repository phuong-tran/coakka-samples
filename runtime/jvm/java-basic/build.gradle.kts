plugins {
    java
    application
}

dependencies {
    implementation("coakka.v2:coakka-jvm-native-runtime-v2:2.1.0-g60ddf70d-4782dcd")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("coakka.samples.runtime.jvm.javabasic.Main")
}
