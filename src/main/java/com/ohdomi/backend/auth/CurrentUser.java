package com.ohdomi.backend.auth;

public record CurrentUser(long userId, String loginId, String role, Long storeId) {}
