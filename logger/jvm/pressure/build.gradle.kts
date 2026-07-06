plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation("coakka.logger:coakka-jvm-native-logger:1.2.1-gf50756ebff0d")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("coakka.samples.logger.jvm.pressure.MainKt")
}
