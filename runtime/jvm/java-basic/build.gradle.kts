plugins {
    java
    application
}

dependencies {
    implementation("coakka.v2:coakka-jvm-native-runtime-v2:0.1.1-ge2dc43a-9227dc0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("coakka.samples.runtime.jvm.javabasic.Main")
}
