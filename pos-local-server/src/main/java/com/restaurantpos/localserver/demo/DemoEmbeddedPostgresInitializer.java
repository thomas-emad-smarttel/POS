package com.restaurantpos.localserver.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Starts an embedded PostgreSQL instance when the {@code demo} profile is active and no external
 * {@code POS_DB_URL} is supplied, enabling zero-config interactive testing of the full POS stack.
 */
public class DemoEmbeddedPostgresInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger LOG = LoggerFactory.getLogger(DemoEmbeddedPostgresInitializer.class);

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment env = applicationContext.getEnvironment();
        if (!env.acceptsProfiles(Profiles.of("demo"))) {
            return;
        }
        String explicitUrl = env.getProperty("POS_DB_URL");
        boolean embeddedEnabled = Boolean.parseBoolean(env.getProperty("pos.demo.embedded-postgres", "true"));
        if (!embeddedEnabled || (explicitUrl != null && !explicitUrl.isBlank())) {
            return;
        }

        try {
            Class<?> epClass = Class.forName("io.zonky.test.db.postgres.embedded.EmbeddedPostgres");
            Object builder = epClass.getMethod("builder").invoke(null);
            Object embeddedPostgres = builder.getClass().getMethod("start").invoke(builder);

            Method getJdbcUrl = epClass.getMethod("getJdbcUrl", String.class, String.class);
            String jdbcUrl = (String) getJdbcUrl.invoke(embeddedPostgres, "postgres", "postgres");

            Map<String, Object> props = Map.of(
                    "spring.datasource.url", jdbcUrl,
                    "spring.datasource.username", "postgres",
                    "spring.datasource.password", "",
                    "spring.flyway.url", jdbcUrl,
                    "spring.flyway.user", "postgres",
                    "spring.flyway.password", ""
            );
            env.getPropertySources().addFirst(new MapPropertySource("demoEmbeddedPostgres", props));

            applicationContext.addApplicationListener((ApplicationListener<ContextClosedEvent>) event -> {
                if (event.getApplicationContext() == applicationContext) {
                    closeQuietly(embeddedPostgres);
                }
            });

            LOG.info("Started embedded PostgreSQL for Local Server demo profile: {}", jdbcUrl);
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException(
                    "Demo profile requires embedded-postgres on the classpath. Run with Maven profile '-Pdemo' or set POS_DB_URL.",
                    ex
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize embedded PostgreSQL for demo profile", ex);
        }
    }

    private static void closeQuietly(Object embeddedPostgres) {
        if (embeddedPostgres instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ex) {
                LOG.warn("Error shutting down demo embedded PostgreSQL", ex);
            }
        }
    }
}
