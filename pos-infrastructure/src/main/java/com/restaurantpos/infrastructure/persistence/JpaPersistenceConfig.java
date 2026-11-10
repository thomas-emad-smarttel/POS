package com.restaurantpos.infrastructure.persistence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Enables JPA entity scanning and Spring Data repositories when database persistence is active.
 */
@Configuration
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
@EntityScan(basePackages = "com.restaurantpos.infrastructure.persistence.entity")
@EnableJpaRepositories(basePackages = "com.restaurantpos.infrastructure.persistence.repository")
public class JpaPersistenceConfig {
}
