package com.projectalice.backend.memory.infrastructure;

import com.projectalice.backend.memory.application.port.TokenEstimator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MemoryTokenEstimatorConfiguration {

    @Bean
    TokenEstimator memoryTokenEstimator() {
        return new JTokkitTokenEstimator();
    }
}
