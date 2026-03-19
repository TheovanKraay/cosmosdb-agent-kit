package com.ecommerce.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.ThroughputProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.Security;
import java.security.cert.X509Certificate;

@Configuration
public class CosmosConfig {

    @Value("${cosmos.endpoint}")
    private String endpoint;

    @Value("${cosmos.key}")
    private String key;

    @Value("${cosmos.database}")
    private String databaseName;

    @PostConstruct
    public void init() {
        if (endpoint != null && (endpoint.contains("localhost") || endpoint.contains("127.0.0.1"))) {
            try {
                Security.insertProviderAt(TrustAllTmfSpi.PROVIDER, 1);

                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, new TrustManager[]{
                        new X509TrustManager() {
                            @Override
                            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                            }

                            @Override
                            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                            }

                            @Override
                            public X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[0];
                            }
                        }
                }, new java.security.SecureRandom());
                SSLContext.setDefault(sc);
            } catch (Exception e) {
                throw new RuntimeException("Failed to configure SSL trust for emulator", e);
            }
        }
    }

    @Bean
    public CosmosClient cosmosClient() {
        return new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .gatewayMode()
                .buildClient();
    }

    @Bean
    public CosmosDatabase cosmosDatabase(CosmosClient cosmosClient) {
        cosmosClient.createDatabaseIfNotExists(databaseName,
                ThroughputProperties.createManualThroughput(400));
        return cosmosClient.getDatabase(databaseName);
    }

    @Bean
    public CosmosContainer ordersContainer(CosmosDatabase cosmosDatabase) {
        CosmosContainerProperties props = new CosmosContainerProperties("orders", "/customerId");
        cosmosDatabase.createContainerIfNotExists(props);
        return cosmosDatabase.getContainer("orders");
    }
}
