package com.projectalice.backend.memory.infrastructure;

import com.projectalice.backend.memory.application.GetMemoryPreferencesService;
import com.projectalice.backend.memory.application.GetMemoryPreferencesUseCase;
import com.projectalice.backend.memory.application.UpdateMemoryPreferencesService;
import com.projectalice.backend.memory.application.UpdateMemoryPreferencesUseCase;
import com.projectalice.backend.memory.application.port.out.MemoryPreferencesRepository;
import com.projectalice.backend.memory.infrastructure.persistence.dynamodb.DynamoDbMemoryPreferencesRepository;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Spring wiring for global Memory preferences.
 *
 * <p>Uses the existing DynamoDbClient and Clock beans.</p>
 */
@Configuration(proxyBeanMethods = false)
public class MemoryPreferencesConfiguration {

    @Bean
    MemoryPreferencesRepository memoryPreferencesRepository(
            DynamoDbClient client,
            @Value("${ALICE_DYNAMODB_TABLE_NAME:project-alice-conversation-local}")
                    String table) {
        return new DynamoDbMemoryPreferencesRepository(client, table);
    }

    @Bean
    GetMemoryPreferencesUseCase getMemoryPreferencesUseCase(
            MemoryPreferencesRepository repository,
            Clock clock) {
        return new GetMemoryPreferencesService(repository, clock);
    }

    @Bean
    UpdateMemoryPreferencesUseCase updateMemoryPreferencesUseCase(
            MemoryPreferencesRepository repository,
            Clock clock) {
        return new UpdateMemoryPreferencesService(repository, clock);
    }
}
