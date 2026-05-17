plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation("coakka.v2:coakka-jvm-native-runtime-v2:0.2.0-g94a5729-6b7a3bf")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("coakka.samples.runtime.jvm.deadletter.MainKt")
}
