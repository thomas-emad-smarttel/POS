package com.restaurantpos.api.config;

import com.restaurantpos.application.kitchen.KitchenOutboxConsumerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically drains reliable kitchen outbox events (`ORDER_SENT_TO_KITCHEN`, `ORDER_CANCELLED_IN_KITCHEN`)
 * into KDS tickets and station print jobs.
 */
@Component
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class KitchenOutboxScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(KitchenOutboxScheduler.class);

    private final KitchenOutboxConsumerService consumerService;

    public KitchenOutboxScheduler(KitchenOutboxConsumerService consumerService) {
        this.consumerService = consumerService;
    }

    @Scheduled(fixedDelayString = "${pos.kitchen.outbox-poll-ms:500}")
    public void pollKitchenOutbox() {
        int count = consumerService.processPendingEvents(25);
        if (count > 0) {
            LOG.debug("Processed {} kitchen outbox event(s)", count);
        }
    }
}
