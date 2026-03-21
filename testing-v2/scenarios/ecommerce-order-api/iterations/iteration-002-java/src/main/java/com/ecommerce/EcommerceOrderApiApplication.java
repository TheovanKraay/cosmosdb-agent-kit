package com.ecommerce;

import com.ecommerce.config.TrustAllProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EcommerceOrderApiApplication {

    public static void main(String[] args) {
        TrustAllProvider.install();
        SpringApplication.run(EcommerceOrderApiApplication.class, args);
    }
}
