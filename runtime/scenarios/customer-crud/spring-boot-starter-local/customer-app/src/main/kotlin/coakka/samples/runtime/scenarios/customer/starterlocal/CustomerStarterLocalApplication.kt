package coakka.samples.runtime.scenarios.customer.starterlocal

import coakka.spring.CoAkkaRuntimeClient
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication
class CustomerStarterLocalApplication {
    @Bean
    fun startupLog(runtimeClient: CoAkkaRuntimeClient) = ApplicationRunner {
        val info = runtimeClient.runtimeInfo()
        val config = runtimeClient.runtimeConfig()
        LoggerFactory.getLogger("CustomerStarterLocalStartup").info(
            "customer-starter-local ready runtimeVersion={} backend={} node={} routes={}",
            info.runtimeVersion,
            info.southboundBackend,
            config.nodeId,
            config.routeCount,
        )
    }
}

fun main(args: Array<String>) {
    runApplication<CustomerStarterLocalApplication>(*args)
}
