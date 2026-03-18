package com.ecommerce.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.ExcludedPath;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.ThroughputProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Arrays;

@Configuration
public class CosmosConfig {

    static {
        Security.insertProviderAt(new TrustAllSslProvider(), 1);
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, new TrustManager[]{new TrustAllX509TrustManager()}, new java.security.SecureRandom());
            SSLContext.setDefault(sc);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Failed to initialize trust-all SSL context", e);
        }
    }

    @Value("${cosmos.endpoint}")
    private String endpoint;

    @Value("${cosmos.key}")
    private String key;

    @Value("${cosmos.database}")
    private String databaseName;

    @Bean
    public CosmosClient cosmosClient() {
        return new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .gatewayMode()
                .buildClient();
    }

    private static class TrustAllX509TrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}
        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}
        @Override
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    }

    public static class TrustAllTrustManagerFactorySpi extends javax.net.ssl.TrustManagerFactorySpi {
        @Override
        protected void engineInit(java.security.KeyStore ks) {}
        @Override
        protected void engineInit(javax.net.ssl.ManagerFactoryParameters spec) {}
        @Override
        protected TrustManager[] engineGetTrustManagers() {
            return new TrustManager[]{new TrustAllX509TrustManager()};
        }
    }

    public static class TrustAllSslProvider extends Provider {
        public TrustAllSslProvider() {
            super("TrustAllCerts", "1.0", "Trust all X.509 certificates");
            put("TrustManagerFactory.PKIX", TrustAllTrustManagerFactorySpi.class.getName());
            put("TrustManagerFactory.SunX509", TrustAllTrustManagerFactorySpi.class.getName());
        }
    }

    @Bean
    public CosmosDatabase cosmosDatabase(CosmosClient cosmosClient) {
        cosmosClient.createDatabaseIfNotExists(databaseName, ThroughputProperties.createAutoscaledThroughput(1000));
        return cosmosClient.getDatabase(databaseName);
    }

    @Bean
    public CosmosContainer ordersContainer(CosmosDatabase cosmosDatabase) {
        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setIncludedPaths(Arrays.asList(
                new IncludedPath("/customerId/?"),
                new IncludedPath("/status/?"),
                new IncludedPath("/createdAt/?"),
                new IncludedPath("/total/?")
        ));
        indexingPolicy.setExcludedPaths(Arrays.asList(
                new ExcludedPath("/*")
        ));

        CosmosContainerProperties containerProperties =
                new CosmosContainerProperties("orders", "/customerId");
        containerProperties.setIndexingPolicy(indexingPolicy);

        cosmosDatabase.createContainerIfNotExists(containerProperties);
        return cosmosDatabase.getContainer("orders");
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}
