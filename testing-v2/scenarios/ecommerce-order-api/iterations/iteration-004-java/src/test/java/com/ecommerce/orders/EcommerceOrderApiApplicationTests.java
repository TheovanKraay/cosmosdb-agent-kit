package com.ecommerce.orders;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "cosmos.endpoint=https://localhost:8081",
    "cosmos.key=test-key"
})
class EcommerceOrderApiApplicationTests {

    @Test
    void contextLoads() {
    }
}
