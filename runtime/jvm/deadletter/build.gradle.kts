plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation("coakka.v2:coakka-jvm-native-runtime-v2:2.1.0-g60ddf70d-4782dcd")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("coakka.samples.runtime.jvm.deadletter.MainKt")
}
