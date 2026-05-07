plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":runtime:scenarios:customer-crud:spring-boot-spring-boot:customer-contract"))
    implementation("coakka.v2:coakka-jvm-native-runtime-v2:0.1.1-g22f571fd955c")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("coakka.samples.runtime.scenarios.customer.desktop.CustomerDesktopLocalApplicationKt")
}
