package com.ohdomi.backend.auth;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

// ponytail: in-memory session store, single-instance ceiling — move to a DB/Redis-backed
// store if the app ever runs behind more than one Spring instance.
@Component
public class SessionManager {
    private static final long TTL_MILLIS = 12 * 60 * 60 * 1000L;

    private record Entry(CurrentUser user, long expiresAt) {}

    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();

    public String create(CurrentUser user) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, new Entry(user, System.currentTimeMillis() + TTL_MILLIS));
        return token;
    }

    public CurrentUser resolve(String token) {
        if (token == null) return null;
        Entry entry = sessions.get(token);
        if (entry == null) return null;
        if (entry.expiresAt() < System.currentTimeMillis()) {
            sessions.remove(token);
            return null;
        }
        return entry.user();
    }

    public void invalidate(String token) {
        if (token != null) sessions.remove(token);
    }
}
