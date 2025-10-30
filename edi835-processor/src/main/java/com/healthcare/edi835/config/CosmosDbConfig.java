package com.healthcare.edi835.config;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.spring.data.cosmos.config.AbstractCosmosConfiguration;
import com.azure.spring.data.cosmos.config.CosmosConfig;
import com.azure.spring.data.cosmos.repository.config.EnableCosmosRepositories;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Azure Cosmos DB configuration
 * Only enabled when changefeed.cosmos.enabled=true
 */
@Configuration
@ConditionalOnProperty(name = "changefeed.cosmos.enabled", havingValue = "true", matchIfMissing = false)
@EnableCosmosRepositories(basePackages = "com.healthcare.edi835.repository")
public class CosmosDbConfig extends AbstractCosmosConfiguration {

    @Value("${spring.cloud.azure.cosmos.endpoint}")
    private String endpoint;

    @Value("${spring.cloud.azure.cosmos.key}")
    private String key;

    @Value("${spring.cloud.azure.cosmos.database}")
    private String database;

    @Override
    protected String getDatabaseName() {
        return database;
    }

    @Bean
    public CosmosAsyncClient cosmosAsyncClient() {
        return new CosmosClientBuilder()
            .endpoint(endpoint)
            .key(key)
            .consistencyLevel(com.azure.cosmos.ConsistencyLevel.SESSION)
            .buildAsyncClient();
    }

    @Bean
    public CosmosConfig cosmosConfig() {
        return CosmosConfig.builder()
            .enableQueryMetrics(true)
            .responseDiagnosticsProcessor(responseDiagnostics -> {
                // Log diagnostics for monitoring
            })
            .build();
    }
}