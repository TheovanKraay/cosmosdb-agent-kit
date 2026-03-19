package com.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Collection;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        setupEmulatorCertTrust();
        SpringApplication.run(Application.class, args);
    }

    /**
     * Import the Cosmos DB emulator certificate into a custom truststore
     * and set it as the JVM default. This must run BEFORE Spring context
     * initialization so that the Cosmos SDK's Netty SSL layer picks it up.
     */
    private static void setupEmulatorCertTrust() {
        String certPath = System.getenv("COSMOS_CERT_PATH");
        if (certPath == null || certPath.isEmpty() || !new File(certPath).exists()) {
            return;
        }

        try {
            // Load the default JVM truststore (cacerts)
            String cacertsPath = System.getProperty("java.home")
                    + File.separator + "lib"
                    + File.separator + "security"
                    + File.separator + "cacerts";
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            try (FileInputStream fis = new FileInputStream(cacertsPath)) {
                ks.load(fis, "changeit".toCharArray());
            }

            // Import the emulator certificate(s)
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            try (FileInputStream fis = new FileInputStream(certPath)) {
                Collection<? extends Certificate> certs = cf.generateCertificates(fis);
                int i = 0;
                for (Certificate cert : certs) {
                    ks.setCertificateEntry("cosmosdb-emulator-" + i, cert);
                    i++;
                }
            }

            // Write the updated truststore to a temp file
            File tmpStore = File.createTempFile("cosmos-truststore", ".jks");
            tmpStore.deleteOnExit();
            try (FileOutputStream fos = new FileOutputStream(tmpStore)) {
                ks.store(fos, "changeit".toCharArray());
            }

            // Set as JVM default truststore — Netty's JDK SSL provider reads this
            System.setProperty("javax.net.ssl.trustStore", tmpStore.getAbsolutePath());
            System.setProperty("javax.net.ssl.trustStorePassword", "changeit");

            System.out.println("Imported Cosmos DB emulator certificate from: " + certPath);
        } catch (Exception e) {
            System.err.println("Warning: Could not import Cosmos DB emulator certificate: " + e.getMessage());
        }
    }
}
