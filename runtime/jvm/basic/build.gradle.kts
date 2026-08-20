plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation("io.github.phuong-tran.coakka:runtime:2.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("coakka.samples.runtime.jvm.basic.MainKt")
}
