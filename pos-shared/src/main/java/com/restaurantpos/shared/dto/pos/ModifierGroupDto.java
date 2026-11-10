package com.restaurantpos.shared.dto.pos;

import java.util.List;
import java.util.UUID;

public record ModifierGroupDto(
        UUID id,
        String code,
        String name,
        int minSelections,
        int maxSelections,
        boolean required,
        boolean active,
        List<ModifierDto> modifiers,
        long version
) {
}
