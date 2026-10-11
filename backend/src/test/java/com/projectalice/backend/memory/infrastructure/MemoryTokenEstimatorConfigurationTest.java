package com.projectalice.backend.memory.infrastructure;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.projectalice.backend.memory.application.port.TokenEstimator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MemoryTokenEstimatorConfigurationTest {

    @Test
    void registersJTokkitEstimatorAsApplicationPort() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        MemoryTokenEstimatorConfiguration.class,
                        MemoryTokenEstimatorStartupConfiguration.class)
                .run(context -> assertInstanceOf(JTokkitTokenEstimator.class, context.getBean(TokenEstimator.class)));
    }

    @Test
    void requiredEstimatorDependencyFailsContextStartupWhenBeanIsMissing() {
        new ApplicationContextRunner()
                .withUserConfiguration(MemoryTokenEstimatorStartupConfiguration.class)
                .run(context -> {
                    assertNotNull(context.getStartupFailure());
                });
    }
}
