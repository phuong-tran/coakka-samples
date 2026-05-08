package coakka.samples.runtime.scenarios.customer.quarkuslocal

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.quarkus.jackson.ObjectMapperCustomizer
import jakarta.inject.Singleton

@Singleton
class CustomerObjectMapperCustomizer : ObjectMapperCustomizer {
    override fun customize(objectMapper: ObjectMapper) {
        objectMapper.registerModule(KotlinModule.Builder().build())
    }
}
