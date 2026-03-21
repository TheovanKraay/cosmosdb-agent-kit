package com.ecommerce;

import com.ecommerce.config.TrustAllProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.security.Security;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        // Install trust-all provider before Spring starts to handle emulator self-signed certs
        Security.insertProviderAt(new TrustAllProvider(), 1);
        SpringApplication.run(Application.class, args);
    }
}
