package com.projectalice.backend.memory.infrastructure;

import com.projectalice.backend.memory.application.port.TokenEstimator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Ensures the required estimator is present even before Memory Retrieval is wired to Spring. */
@Configuration(proxyBeanMethods = false)
public class MemoryTokenEstimatorStartupConfiguration {

    @Bean
    MemoryTokenEstimatorRegistrationCheck memoryTokenEstimatorRegistrationCheck(TokenEstimator tokenEstimator) {
        return new MemoryTokenEstimatorRegistrationCheck(tokenEstimator);
    }

    record MemoryTokenEstimatorRegistrationCheck(TokenEstimator tokenEstimator) {}
}
