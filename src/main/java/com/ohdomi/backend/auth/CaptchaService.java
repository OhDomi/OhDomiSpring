package com.ohdomi.backend.auth;

import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

// Stateless arithmetic captcha: the answer travels inside an HMAC-signed, time-limited token
// instead of server-side session state, so it works for anonymous pre-login requests.
// ponytail: HMAC secret is regenerated on every restart (fine — tokens only live 5 minutes).
@Component
public class CaptchaService {
    private static final long TTL_MILLIS = 5 * 60 * 1000L;

    private final byte[] secret = new byte[32];
    private final SecureRandom random = new SecureRandom();

    public CaptchaService() {
        random.nextBytes(secret);
    }

    public record Challenge(String question, String token) {}

    public Challenge generate() {
        int a = random.nextInt(9) + 1;
        int b = random.nextInt(9) + 1;
        int answer = a + b;
        long expiresAt = System.currentTimeMillis() + TTL_MILLIS;
        String payload = answer + "|" + expiresAt;
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((payload + "|" + sign(payload)).getBytes());
        return new Challenge(a + " + " + b + " = ?", token);
    }

    public boolean verify(String token, String submittedAnswer) {
        if (token == null || submittedAnswer == null) return false;
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(token)).split("\\|");
            if (parts.length != 3) return false;
            String answer = parts[0];
            long expiresAt = Long.parseLong(parts[1]);
            String signature = parts[2];
            if (expiresAt < System.currentTimeMillis()) return false;
            if (!sign(answer + "|" + expiresAt).equals(signature)) return false;
            return answer.equals(submittedAnswer.trim());
        } catch (Exception exception) {
            return false;
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes()));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
