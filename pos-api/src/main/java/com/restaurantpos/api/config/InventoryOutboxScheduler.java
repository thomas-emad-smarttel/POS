package com.restaurantpos.api.config;

import com.restaurantpos.application.inventory.InventoryOutboxConsumerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically drains inventory outbox events (`INVENTORY_RESERVE_ORDER`, `INVENTORY_CONSUME_PREP`,
 * `INVENTORY_RELEASE_ORDER`, `INVENTORY_REFIRE_TICKET`) with idempotency and backoff retries.
 */
@Component
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class InventoryOutboxScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(InventoryOutboxScheduler.class);

    private final InventoryOutboxConsumerService consumerService;

    public InventoryOutboxScheduler(InventoryOutboxConsumerService consumerService) {
        this.consumerService = consumerService;
    }

    @Scheduled(fixedDelayString = "${pos.inventory.outbox-poll-ms:600}")
    public void pollInventoryOutbox() {
        int count = consumerService.processPendingEvents(25);
        if (count > 0) {
            LOG.debug("Processed {} inventory outbox event(s)", count);
        }
    }
}
