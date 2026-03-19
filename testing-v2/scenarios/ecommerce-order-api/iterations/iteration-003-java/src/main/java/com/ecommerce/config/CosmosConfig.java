package com.ecommerce.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.DirectConnectionConfig;
import com.azure.cosmos.GatewayConnectionConfig;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosContainerResponse;
import com.azure.cosmos.models.CosmosDatabaseResponse;
import com.azure.cosmos.models.PartitionKeyDefinition;
import com.azure.cosmos.models.ThroughputProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.util.Collections;

@Configuration
public class CosmosConfig {

    @Value("${cosmos.endpoint}")
    private String endpoint;

    @Value("${cosmos.key}")
    private String key;

    @Value("${cosmos.database}")
    private String databaseName;

    @Value("${cosmos.container}")
    private String containerName;

    @PostConstruct
    public void init() {
        trustAllCertificates();
    }

    private void trustAllCertificates() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            SSLContext.setDefault(sc);
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set up SSL trust", e);
        }
    }

    @Bean
    public CosmosClient cosmosClient() {
        return new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .gatewayMode(new GatewayConnectionConfig())
                .buildClient();
    }

    @Bean
    public CosmosDatabase cosmosDatabase(CosmosClient cosmosClient) {
        CosmosDatabaseResponse response = cosmosClient.createDatabaseIfNotExists(
                databaseName, ThroughputProperties.createAutoscaledThroughput(4000));
        return cosmosClient.getDatabase(databaseName);
    }

    @Bean
    public CosmosContainer cosmosContainer(CosmosDatabase cosmosDatabase) {
        PartitionKeyDefinition partitionKeyDef = new PartitionKeyDefinition();
        partitionKeyDef.setPaths(Collections.singletonList("/customerId"));

        CosmosContainerProperties containerProps = new CosmosContainerProperties(containerName, partitionKeyDef);

        cosmosDatabase.createContainerIfNotExists(containerProps);
        return cosmosDatabase.getContainer(containerName);
    }
}
