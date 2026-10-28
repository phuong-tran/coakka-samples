plugins {
    java
    application
}

dependencies {
    implementation("io.github.phuong-tran.coakka:logger:1.2.2")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("coakka.samples.logger.jvm.javabasic.Main")
}
