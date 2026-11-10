package com.restaurantpos.infrastructure.persistence.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantpos.domain.port.KitchenPayloadCodecPort;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KitchenPayloadCodecAdapter implements KitchenPayloadCodecPort {

    private final ObjectMapper objectMapper;

    public KitchenPayloadCodecAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String encodeOrderDispatched(DispatchedOrderSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize kitchen dispatch snapshot", ex);
        }
    }

    @Override
    public DispatchedOrderSnapshot decodeOrderDispatched(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, DispatchedOrderSnapshot.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialize kitchen dispatch snapshot", ex);
        }
    }

    @Override
    public String encodeOrderCancelled(CancelledOrderSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize kitchen cancel snapshot", ex);
        }
    }

    @Override
    public CancelledOrderSnapshot decodeOrderCancelled(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, CancelledOrderSnapshot.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialize kitchen cancel snapshot", ex);
        }
    }

    public String encodeModifiers(List<String> modifiers) {
        try {
            return objectMapper.writeValueAsString(modifiers == null ? List.of() : modifiers);
        } catch (Exception ex) {
            return "[]";
        }
    }

    public List<String> decodeModifiers(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }
}
