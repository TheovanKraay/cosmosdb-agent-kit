package com.ecommerce;

import com.ecommerce.config.TrustAllSslProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        TrustAllSslProvider.install();
        SpringApplication.run(Application.class, args);
    }
}
