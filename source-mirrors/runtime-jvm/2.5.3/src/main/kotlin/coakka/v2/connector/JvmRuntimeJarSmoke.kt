package coakka.v2.connector

object JvmRuntimeJarSmoke {
    @JvmStatic
    fun main(args: Array<String>) {
        val info = RuntimeHandle.readRuntimeInfo()
        val capabilities = RuntimeHandle.readRuntimeCapabilities()
        println(
            "runtime_info abi=${info.abiVersion} version=${info.runtimeVersion} " +
                "git=${info.gitCommit} backend=${info.southboundBackend}",
        )

        val orchestrator = ConnectorOrchestrator.start(
            startSpec = RuntimeStartSpec(
                systemName = args.firstOrNull().takeUnless(String?::isNullOrBlank) ?: "jar-smoke",
                nodeId = "node-jar-smoke",
                routes = RuntimeClient.localRoutes(listOf("svc.echo")),
                connectionStrategy = RuntimeTcpConnectionStrategySpec(
                    RuntimeTcpConnectionMode.PER_EXCHANGE,
                ),
                security = RuntimeTcpSecuritySpec(RuntimeTcpSecurityMode.PLAINTEXT),
            ),
        )

        kotlinx.coroutines.runBlocking {
            try {
                check(orchestrator.runtimeCapabilities() == capabilities)
                check(orchestrator.startupConnectionResult()?.applied() == true)
                check(orchestrator.startupSecurityResult()?.applied() == true)
                check(
                    orchestrator.tcpConnectionConfig().mode ==
                        RuntimeTcpConnectionMode.PER_EXCHANGE,
                )
                check(orchestrator.tcpSecurityInfo().mode == RuntimeTcpSecurityMode.PLAINTEXT)

                orchestrator.registerHandler("svc.echo") { request ->
                    RuntimeClient.replyTo(
                        request = request,
                        source = "svc.echo",
                        payloadUtf8 = "echo:${request.payloadUtf8()}",
                    )
                }
                val response = orchestrator.kotlin.ask(
                    source = "smoke-client",
                    target = "svc.echo",
                    payloadUtf8 = "embedded-native-ok",
                )

                check(response.payloadUtf8() == "echo:embedded-native-ok") {
                    "unexpected response payload=${response.payloadUtf8()}"
                }

                println(
                    "runtime_smoke ok payload=${response.payloadUtf8()} " +
                        "version=${info.runtimeVersion} backend=${info.southboundBackend} " +
                        "capabilities=${capabilities.effectiveCapabilities}",
                )
            } finally {
                orchestrator.kotlin.shutdown()
            }
        }
    }
}
