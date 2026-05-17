plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation("coakka.v2:coakka-jvm-native-runtime-v2:0.1.1-ge2dc43a-9227dc0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("coakka.samples.runtime.jvm.deadletter.MainKt")
}
