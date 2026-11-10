package com.restaurantpos.domain.menu;

import com.restaurantpos.shared.model.TenantId;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ModifierGroup(
        UUID id,
        TenantId tenantId,
        String code,
        String name,
        int minSelections,
        int maxSelections,
        boolean required,
        boolean active,
        List<Modifier> modifiers,
        long version
) {
    public ModifierGroup {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(code);
        Objects.requireNonNull(name);
        modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
    }
}
