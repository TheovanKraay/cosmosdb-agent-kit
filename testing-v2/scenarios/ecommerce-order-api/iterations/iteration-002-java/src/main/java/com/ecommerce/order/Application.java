package com.ecommerce.order;

import com.ecommerce.order.ssl.TrustAllProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.security.Security;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        // Disable Netty's OpenSSL so the JDK SSL provider is used
        System.setProperty("io.netty.handler.ssl.noOpenSsl", "true");

        // Register trust-all provider at position 1 so emulator's self-signed cert is accepted
        Security.insertProviderAt(new TrustAllProvider(), 1);

        SpringApplication.run(Application.class, args);
    }
}
