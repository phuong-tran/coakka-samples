package coakka.samples.runtime.jvm.basic

import coakka.v2.connector.CoAkka
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val target = "samples.runtime.jvm.echo"

    CoAkka.local("jvm-runtime-sample").use { runtime ->
        runtime.handler(target) { message -> "hello-$message" }

        val response = runtime.ask(target, "runtime-jvm")
        println("coakka_runtime_response payload=$response")
    }
}
