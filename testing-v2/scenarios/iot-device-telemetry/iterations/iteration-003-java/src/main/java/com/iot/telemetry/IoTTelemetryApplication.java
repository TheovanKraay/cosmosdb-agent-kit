package com.iot.telemetry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class IoTTelemetryApplication {

    public static void main(String[] args) {
        // Rule 4.8: Trust emulator self-signed certificate
        System.setProperty("COSMOS.EMULATOR_SSL_TRUST_ALL", "true");
        SpringApplication.run(IoTTelemetryApplication.class, args);
    }
}
