package coakka.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Creates the default CoAkka runtime and request/reply client beans. */
@AutoConfiguration
@EnableConfigurationProperties(CoAkkaRuntimeProperties.class)
public class CoAkkaRuntimeAutoConfiguration {
    /**
     * Starts the context-owned runtime unless the application provides one.
     *
     * @param properties bound CoAkka properties
     * @param objectMapper application JSON mapper
     * @param beanFactory handler discovery source
     * @return started context-owned runtime
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public CoAkkaRuntime coAkkaRuntime(
        CoAkkaRuntimeProperties properties,
        ObjectMapper objectMapper,
        ListableBeanFactory beanFactory
    ) {
        return CoAkkaRuntime.start(properties, objectMapper, beanFactory);
    }

    /**
     * Creates the blocking request/reply facade unless the application provides one.
     *
     * @param runtime context-owned runtime
     * @param objectMapper application JSON mapper
     * @return request/reply client
     */
    @Bean
    @ConditionalOnMissingBean
    public CoAkkaRuntimeClient coAkkaRuntimeClient(
        CoAkkaRuntime runtime,
        ObjectMapper objectMapper
    ) {
        return new CoAkkaRuntimeClient(runtime, objectMapper);
    }
}
