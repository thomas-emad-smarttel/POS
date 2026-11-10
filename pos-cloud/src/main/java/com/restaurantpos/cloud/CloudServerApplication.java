package com.restaurantpos.cloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Central Cloud Backend entry point for multi-tenant management, branch synchronization, and consolidated reporting.
 */
@SpringBootApplication(scanBasePackages = {
        "com.restaurantpos.cloud",
        "com.restaurantpos.application",
        "com.restaurantpos.infrastructure"
})
public class CloudServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudServerApplication.class, args);
    }
}
