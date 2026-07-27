package com.ohdomi.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordHasherTests {
    @Test
    void hashesWithRandomSaltAndVerifiesInConstantTime() {
        String first = PasswordHasher.hash("safePassword123!");
        String second = PasswordHasher.hash("safePassword123!");

        assertThat(first).isNotEqualTo(second);
        assertThat(PasswordHasher.matches("safePassword123!", first)).isTrue();
        assertThat(PasswordHasher.matches("wrongPassword", first)).isFalse();
        assertThat(PasswordHasher.matches("safePassword123!", "invalid")).isFalse();
    }
}
