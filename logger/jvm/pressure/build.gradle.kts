plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation("coakka.logger:coakka-jvm-native-logger:0.1.0-gba2a66d98eb5")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("coakka.samples.logger.jvm.pressure.MainKt")
}
