package com.restaurantpos.shared.dto.security;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Size(max = 200) String fullName,
        @Email @Size(max = 200) String email
) {
}
