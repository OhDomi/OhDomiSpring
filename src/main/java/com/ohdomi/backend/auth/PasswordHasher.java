package com.ohdomi.backend.auth;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class PasswordHasher {
    private static final String PREFIX = "pbkdf2_sha256";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordHasher() {}

    static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        byte[] derivedKey = derive(password.toCharArray(), salt, ITERATIONS);
        Base64.Encoder encoder = Base64.getEncoder().withoutPadding();
        return PREFIX + "$" + ITERATIONS + "$" + encoder.encodeToString(salt)
                + "$" + encoder.encodeToString(derivedKey);
    }

    static boolean matches(String password, String encodedPassword) {
        String[] parts = encodedPassword.split("\\$", -1);
        if (parts.length != 4 || !PREFIX.equals(parts[0])) return false;

        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(password.toCharArray(), salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec specification = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(specification).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("PBKDF2-SHA256 is unavailable", exception);
        } finally {
            specification.clearPassword();
        }
    }
}
