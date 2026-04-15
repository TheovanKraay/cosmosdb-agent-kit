package com.ecommerce.order;

import com.ecommerce.order.ssl.TrustAllProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.security.Security;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        // Disable Netty's OpenSSL so it falls back to JDK SSL
        System.setProperty("io.netty.handler.ssl.noOpenSsl", "true");

        // Install trust-all provider at highest priority for emulator SSL
        Security.insertProviderAt(new TrustAllProvider(), 1);

        SpringApplication.run(Application.class, args);
    }
}
