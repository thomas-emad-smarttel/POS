package com.restaurantpos.domain.port;

/**
 * Password hashing abstraction decoupled from Spring Security implementation details.
 */
public interface PasswordHasherPort {
    String hash(String rawPassword);

    boolean matches(String rawPassword, String encodedHash);
}
