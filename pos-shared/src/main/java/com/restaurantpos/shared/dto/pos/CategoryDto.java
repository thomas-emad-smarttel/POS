package com.restaurantpos.shared.dto.pos;

import java.util.UUID;

public record CategoryDto(
        UUID id,
        UUID parentId,
        String code,
        String name,
        int displayOrder,
        String colorHex,
        boolean active,
        boolean branchVisible,
        long version
) {
}
