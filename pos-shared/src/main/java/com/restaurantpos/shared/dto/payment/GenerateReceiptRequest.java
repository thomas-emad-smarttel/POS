package com.restaurantpos.shared.dto.payment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record GenerateReceiptRequest(
        @Pattern(regexp = "^(EN|AR|BILINGUAL)$") String language,
        @Min(32) @Max(64) Integer paperWidth,
        @Pattern(regexp = "^(UTF-8|CP864|WPC1256)$") String encoding,
        boolean printImmediately
) {
}
