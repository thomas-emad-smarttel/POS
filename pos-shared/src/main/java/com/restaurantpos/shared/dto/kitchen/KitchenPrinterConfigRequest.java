package com.restaurantpos.shared.dto.kitchen;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record KitchenPrinterConfigRequest(
        UUID printerDeviceId,
        @Min(32) @Max(64) int paperWidth,
        @Pattern(regexp = "^(EN|AR|BILINGUAL)$") String receiptLanguage,
        @Pattern(regexp = "^(UTF-8|CP864|WPC1256)$") String receiptEncoding
) {
}
