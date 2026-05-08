plugins {
    java
    application
}

dependencies {
    implementation("coakka.v2:coakka-jvm-native-runtime-v2:0.1.1-g22f571fd955c")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("coakka.samples.runtime.jvm.javabasic.Main")
}
