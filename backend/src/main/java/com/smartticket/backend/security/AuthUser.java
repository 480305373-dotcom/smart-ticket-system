package com.smartticket.backend.security;

public record AuthUser(
        Long userId,
        String username,
        String role
) {
}