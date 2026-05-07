package coakka.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(CoAkkaRuntimeProperties.class)
public class CoAkkaRuntimeAutoConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public CoAkkaRuntime coAkkaRuntime(
        CoAkkaRuntimeProperties properties,
        ObjectMapper objectMapper,
        ListableBeanFactory beanFactory
    ) {
        return CoAkkaRuntime.start(properties, objectMapper, beanFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public CoAkkaRuntimeClient coAkkaRuntimeClient(
        CoAkkaRuntime runtime,
        ObjectMapper objectMapper
    ) {
        return new CoAkkaRuntimeClient(runtime, objectMapper);
    }
}
