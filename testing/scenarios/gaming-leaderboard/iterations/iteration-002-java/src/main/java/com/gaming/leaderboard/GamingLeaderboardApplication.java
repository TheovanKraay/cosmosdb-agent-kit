package com.gaming.leaderboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GamingLeaderboardApplication {

    public static void main(String[] args) {
        // Rule 4.6: Trust emulator's self-signed SSL cert for local dev
        // In production, remove this and use proper certificate management
        System.setProperty("COSMOS.EMULATOR_SSL_TRUST_ALL", "true");
        SpringApplication.run(GamingLeaderboardApplication.class, args);
    }
}
