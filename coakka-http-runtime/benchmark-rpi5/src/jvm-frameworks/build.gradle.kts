plugins { kotlin("jvm") version "2.3.21" }
repositories { mavenCentral() }
layout.buildDirectory.set(file(providers.gradleProperty("sampleBuildDir").get()))

// Separate runtime dependency graphs: WebFlux and Vert.x do not share a
// classpath, and no comparison process loads a CoAkka JAR or native library.
val undertow by configurations.creating
val vertx by configurations.creating
val webflux by configurations.creating
val mvc by configurations.creating
val stacks = listOf(undertow, vertx, webflux, mvc)
dependencies {
    undertow("io.undertow:undertow-core:2.3.26.Final")
    vertx("io.vertx:vertx-web:4.5.34")
    vertx("io.netty:netty-transport-native-epoll:4.1.138.Final:linux-aarch_64")
    webflux("org.springframework:spring-webflux:6.2.19")
    webflux("org.springframework:spring-context:6.2.19")
    webflux("io.projectreactor.netty:reactor-netty-http:1.2.18")
    webflux("io.netty:netty-transport-native-epoll:4.1.135.Final:linux-aarch_64")
    mvc("org.springframework:spring-webmvc:6.2.19")
    mvc("org.apache.tomcat.embed:tomcat-embed-core:10.1.50")
    mvc("jakarta.annotation:jakarta.annotation-api:2.1.1")
    stacks.forEach {
        add(it.name, "org.jetbrains.kotlin:kotlin-stdlib:2.3.21")
        add(it.name, "org.slf4j:slf4j-nop:2.0.17")
    }
}
configurations.compileOnly { extendsFrom(*stacks.toTypedArray()) }
kotlin {
    jvmToolchain(21)
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    compilerOptions.allWarningsAsErrors.set(true)
}
tasks.register("stage") {
    dependsOn("compileKotlin")
    doLast {
        stacks.forEach { stack ->
            sync { from(stack); into(layout.buildDirectory.dir("deps/" + stack.name)) }
            layout.buildDirectory.file(stack.name + "-dependencies.txt").get().asFile.writeText(
                stack.resolvedConfiguration.resolvedArtifacts.map {
                    it.moduleVersion.id.toString() + " " + it.file.name
                }.sorted().joinToString("\n") + "\n")
        }
    }
}
