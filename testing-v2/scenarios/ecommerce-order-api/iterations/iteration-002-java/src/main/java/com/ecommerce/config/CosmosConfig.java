package com.ecommerce.config;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.ExcludedPath;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingMode;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.ThroughputProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.Security;
import java.util.Arrays;
import java.util.List;
import java.security.cert.X509Certificate;

/**
 * Cosmos DB configuration.
 *
 * Uses a single CosmosClient (singleton) as recommended by the SDK best practices.
 * For the local emulator, installs a trust-all SSL provider to bypass certificate
 * validation failures.
 */
@Configuration
public class CosmosConfig {

    private static final Logger log = LoggerFactory.getLogger(CosmosConfig.class);

    @Value("${cosmos.endpoint}")
    private String endpoint;

    @Value("${cosmos.key}")
    private String key;

    @Value("${cosmos.database}")
    private String databaseName;

    @Value("${cosmos.container}")
    private String containerName;

    static {
        // Install trust-all JCA provider so Cosmos emulator self-signed cert is accepted
        Security.insertProviderAt(new TrustAllSslProvider(), 1);
    }

    @Bean(destroyMethod = "close")
    public CosmosClient cosmosClient() throws Exception {
        if (endpoint.contains("localhost") || endpoint.contains("127.0.0.1")) {
            installTrustAllSslContext();
        }

        return new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .gatewayMode()
                .buildClient();
    }

    private void installTrustAllSslContext() throws Exception {
        TrustManager[] trustAllManagers = new TrustManager[]{
            new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }
        };
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllManagers, new java.security.SecureRandom());
        SSLContext.setDefault(sslContext);
    }

    @Bean
    public CosmosDatabase cosmosDatabase(CosmosClient cosmosClient) {
        cosmosClient.createDatabaseIfNotExists(databaseName);
        return cosmosClient.getDatabase(databaseName);
    }

    @Bean
    public CosmosContainer cosmosContainer(CosmosDatabase cosmosDatabase) {
        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setIndexingMode(IndexingMode.CONSISTENT);
        indexingPolicy.setIncludedPaths(Arrays.asList(
                new IncludedPath("/customerId/?"),
                new IncludedPath("/status/?"),
                new IncludedPath("/createdAt/?")
        ));
        // Must include "/*" in excludedPaths when using custom includedPaths
        indexingPolicy.setExcludedPaths(List.of(new ExcludedPath("/*")));

        CosmosContainerProperties properties = new CosmosContainerProperties(containerName, "/customerId");
        properties.setIndexingPolicy(indexingPolicy);

        cosmosDatabase.createContainerIfNotExists(
                properties,
                ThroughputProperties.createAutoscaledThroughput(4000)
        );
        return cosmosDatabase.getContainer(containerName);
    }
}
