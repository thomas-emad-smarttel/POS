package com.restaurantpos.shared.dto.kitchen;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SaveKitchenStationRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 32) String stationType,
        UUID printerDeviceId,
        int displayOrder,
        @Min(1) @Max(240) int expectedPrepMinutes,
        boolean expoEnabled,
        @Min(32) @Max(64) Integer paperWidth,
        @Pattern(regexp = "^(EN|AR|BILINGUAL)$") String receiptLanguage,
        @Pattern(regexp = "^(UTF-8|CP864|WPC1256)$") String receiptEncoding,
        boolean defaultStation,
        boolean active
) {
}
