plugins {
    java
    application
}

dependencies {
    implementation("io.github.phuong-tran.coakka:runtime:2.4.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("coakka.samples.runtime.jvm.javabasic.Main")
}
