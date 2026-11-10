package com.restaurantpos.localserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Branch-level Local Server entry point. Owns branch transactions, RBAC validation, and hardware gateway APIs.
 */
@SpringBootApplication(scanBasePackages = "com.restaurantpos")
@EnableScheduling
public class LocalServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocalServerApplication.class, args);
    }
}
