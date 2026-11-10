package com.restaurantpos.shared.dto.security;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 100) String username,
        @NotBlank @Size(max = 200) String fullName,
        @Email @Size(max = 200) String email,
        @NotBlank @Size(min = 8, max = 128) String password,
        List<UUID> branchIds
) {
}
