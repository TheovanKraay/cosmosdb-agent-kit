package com.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        setupEmulatorTrustStore();
        SpringApplication.run(Application.class, args);
    }

    /**
     * If COSMOS_CERT_PATH is set (CI environment), imports the emulator's
     * self-signed certificate into a copy of the JDK's cacerts truststore
     * and sets javax.net.ssl.trustStore before Spring/Netty initializes.
     *
     * The Java Cosmos SDK uses Netty which bypasses SSLContext.setDefault(),
     * so we must configure the JVM truststore directly.
     */
    private static void setupEmulatorTrustStore() {
        String certPath = System.getenv("COSMOS_CERT_PATH");
        if (certPath == null || certPath.isEmpty()) {
            return;
        }

        try {
            Path certFile = Paths.get(certPath);
            if (!Files.exists(certFile)) {
                System.out.println("COSMOS_CERT_PATH set but file not found: " + certPath);
                return;
            }

            // Read the PEM certificate
            String pemContent = Files.readString(certFile);
            String base64 = pemContent
                    .replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(base64);

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(decoded));

            // Copy JDK's cacerts to a temp file
            String javaHome = System.getProperty("java.home");
            Path cacertsPath = Paths.get(javaHome, "lib", "security", "cacerts");
            Path customTrustStore = Files.createTempFile("custom-cacerts", ".jks");
            Files.copy(cacertsPath, customTrustStore, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // Load the custom truststore and add the emulator cert
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            try (InputStream is = new FileInputStream(customTrustStore.toFile())) {
                ks.load(is, "changeit".toCharArray());
            }
            ks.setCertificateEntry("cosmosemulator", cert);

            try (FileOutputStream fos = new FileOutputStream(customTrustStore.toFile())) {
                ks.store(fos, "changeit".toCharArray());
            }

            // Set the JVM truststore properties BEFORE Netty/Spring initializes
            System.setProperty("javax.net.ssl.trustStore", customTrustStore.toAbsolutePath().toString());
            System.setProperty("javax.net.ssl.trustStorePassword", "changeit");

            System.out.println("Imported emulator certificate into custom truststore: " + customTrustStore);
        } catch (Exception e) {
            System.err.println("Failed to setup emulator trust store: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
