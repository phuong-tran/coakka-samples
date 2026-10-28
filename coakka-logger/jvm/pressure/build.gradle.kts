plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation("io.github.phuong-tran.coakka:logger:1.2.2")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("coakka.samples.logger.jvm.pressure.MainKt")
}
