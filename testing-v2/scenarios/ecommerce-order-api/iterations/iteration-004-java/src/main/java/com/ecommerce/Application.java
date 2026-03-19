package com.ecommerce;

import com.ecommerce.config.TrustAllProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        TrustAllProvider.install();
        SpringApplication.run(Application.class, args);
    }
}
