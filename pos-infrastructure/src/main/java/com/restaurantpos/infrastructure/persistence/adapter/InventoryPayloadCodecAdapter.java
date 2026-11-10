package com.restaurantpos.infrastructure.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantpos.domain.port.InventoryPayloadCodecPort;
import org.springframework.stereotype.Component;

@Component
public class InventoryPayloadCodecAdapter implements InventoryPayloadCodecPort {

    private final ObjectMapper objectMapper;

    public InventoryPayloadCodecAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String encodeReserveOrder(ReserveOrderPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize inventory reserve payload", ex);
        }
    }

    @Override
    public ReserveOrderPayload decodeReserveOrder(String json) {
        try {
            return objectMapper.readValue(json, ReserveOrderPayload.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialize inventory reserve payload", ex);
        }
    }

    @Override
    public String encodePrepStarted(PrepStartedPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize inventory prep started payload", ex);
        }
    }

    @Override
    public PrepStartedPayload decodePrepStarted(String json) {
        try {
            return objectMapper.readValue(json, PrepStartedPayload.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialize inventory prep started payload", ex);
        }
    }

    @Override
    public String encodeTicketRefire(TicketRefirePayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize inventory refire payload", ex);
        }
    }

    @Override
    public TicketRefirePayload decodeTicketRefire(String json) {
        try {
            return objectMapper.readValue(json, TicketRefirePayload.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialize inventory refire payload", ex);
        }
    }
}
