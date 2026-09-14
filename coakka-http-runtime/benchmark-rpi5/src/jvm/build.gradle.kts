plugins {
    application
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(files("../../build/jvm-application/coakka-http-jvm-application.jar"))
    implementation(files("../../build/http-connector-source/connectors/jvm/runtime/build/libs/coakka-http-jvm-1.0.0.jar"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.10")
    implementation("io.netty:netty-codec-http:4.1.137.Final")
    implementation("org.apache.tomcat.embed:tomcat-embed-core:11.0.25")
    implementation("org.eclipse.jetty:jetty-server:12.1.13")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("benchmark.jvm.FixedServer")
    applicationDefaultJvmArgs = listOf("--add-modules=jdk.httpserver")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
