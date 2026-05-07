plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation("coakka.v2:coakka-jvm-native-runtime-v2:0.1.1-g22f571fd955c")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("coakka.samples.runtime.jvm.deadletter.MainKt")
}
