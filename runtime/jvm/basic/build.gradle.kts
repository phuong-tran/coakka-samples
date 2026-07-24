plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation("coakka.v2:coakka-jvm-native-runtime-v2:1.3.1-g0da8c2d9-8ff6f32")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("coakka.samples.runtime.jvm.basic.MainKt")
}
