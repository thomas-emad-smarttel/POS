package com.restaurantpos.tests.security;

import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.restaurantpos.api",
        "com.restaurantpos.application",
        "com.restaurantpos.infrastructure"
})
public class SecurityApiTestApplication {
}
