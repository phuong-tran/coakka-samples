plugins {
    java
    application
}

dependencies {
    implementation("io.github.phuong-tran.coakka:runtime:2.5.2")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("coakka.samples.runtime.jvm.javabasic.Main")
}
