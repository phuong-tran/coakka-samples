package benchmark.frameworks

import java.util.concurrent.CountDownLatch

/** Shared immutable payload; response writers and mutable buffers stay local. */
val body: ByteArray = "0123456789abcdef0123456789abcdef".toByteArray(Charsets.US_ASCII)

/** The runner supplies taskset and ActiveProcessorCount as a matched CPU budget. */
fun cpuBudget(): Int = Runtime.getRuntime().availableProcessors().also { require(it in 1..2) }

/** Report successful close only after the framework completes its own drain. */
fun awaitStop(lane: String, topology: String, close: () -> Unit) {
    Runtime.getRuntime().addShutdownHook(Thread({
        close()
        println("benchmark-shutdown=pass")
    }, "benchmark-shutdown"))
    println("framework-ready=$lane;cpus=${cpuBudget()};$topology")
    CountDownLatch(1).await()
}
