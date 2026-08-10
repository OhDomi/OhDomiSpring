package com.ohdomi.backend.auth;

import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;

import com.ohdomi.backend.global.ConflictException;
import com.ohdomi.backend.global.UnauthorizedException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final int SESSION_COOKIE_MAX_AGE_SECONDS = 12 * 60 * 60;
    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    private final JdbcTemplate jdbc;
    private final SessionManager sessionManager;
    private final CaptchaService captchaService;

    public AuthController(JdbcTemplate jdbc, SessionManager sessionManager, CaptchaService captchaService) {
        this.jdbc = jdbc;
        this.sessionManager = sessionManager;
        this.captchaService = captchaService;
    }

    @GetMapping("/captcha")
    public CaptchaService.Challenge captcha() {
        return captchaService.generate();
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        String role = request.role().toUpperCase();
        if (!role.equals("OWNER") && !role.equals("ADMIN")) {
            throw new IllegalArgumentException("role must be OWNER or ADMIN");
        }

        List<LoginRow> users = jdbc.query("""
                SELECT u.user_id, u.login_id, u.password_hash, u.name, u.role, u.phone,
                       u.failed_login_count, u.locked_until,
                       (SELECT MIN(s.store_id) FROM stores s WHERE s.owner_user_id = u.user_id)
                FROM app_users u
                WHERE u.login_id = ? AND u.active = TRUE
                """, (rs, row) -> new LoginRow(
                rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getInt(7),
                rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toLocalDateTime(),
                (Long) rs.getObject(9)), request.loginId().trim());

        if (users.isEmpty()) throw invalidCredentials();
        LoginRow user = users.get(0);

        if (user.lockedUntil() != null && user.lockedUntil().isAfter(LocalDateTime.now())) {
            throw new UnauthorizedException(
                    "로그인 실패 횟수가 초과되어 계정이 잠겼습니다. 잠시 후 다시 시도해 주세요.");
        }

        if (!user.role().equals(role) || !PasswordHasher.matches(request.password(), user.passwordHash())) {
            registerFailedAttempt(user);
            throw invalidCredentials();
        }
        jdbc.update("UPDATE app_users SET failed_login_count = 0, locked_until = NULL WHERE user_id = ?",
                user.userId());

        String token = sessionManager.create(
                new CurrentUser(user.userId(), user.loginId(), user.role(), user.storeId()));
        Cookie cookie = new Cookie(SessionAuthFilter.COOKIE_NAME, token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(SESSION_COOKIE_MAX_AGE_SECONDS);
        response.addCookie(cookie);

        return new LoginResponse(user.userId(), user.loginId(), user.name(), user.role(),
                user.phone(), user.storeId());
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (SessionAuthFilter.COOKIE_NAME.equals(cookie.getName())) {
                    sessionManager.invalidate(cookie.getValue());
                }
            }
        }
        Cookie expired = new Cookie(SessionAuthFilter.COOKIE_NAME, "");
        expired.setHttpOnly(true);
        expired.setPath("/");
        expired.setMaxAge(0);
        response.addCookie(expired);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        if (!captchaService.verify(request.captchaToken(), request.captchaAnswer())) {
            throw new IllegalArgumentException("캡챠 정답이 올바르지 않습니다.");
        }
        String loginId = request.loginId().trim();
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_users WHERE login_id = ?", Integer.class, loginId);
        if (existing != null && existing > 0) {
            throw new ConflictException("이미 사용 중인 아이디입니다.");
        }

        KeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO app_users
                          (login_id, password_hash, name, role, phone, active,
                           privacy_consent_at, created_at, updated_at)
                        VALUES (?, ?, ?, 'OWNER', ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """, new String[]{"user_id"});
                statement.setString(1, loginId);
                statement.setString(2, PasswordHasher.hash(request.password()));
                statement.setString(3, request.name().trim());
                statement.setString(4, request.phone().trim());
                return statement;
            }, keys);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("이미 사용 중인 아이디입니다.");
        }

        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("Database did not return a user id");
        LocalDateTime createdAt = jdbc.queryForObject(
                "SELECT created_at FROM app_users WHERE user_id = ?",
                LocalDateTime.class, key.longValue());
        return new RegisterResponse(key.longValue(), loginId, request.name().trim(),
                "OWNER", request.phone().trim(), createdAt);
    }

    public record RegisterRequest(
            @NotBlank
            @Size(min = 4, max = 100)
            @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "아이디는 영문, 숫자, 점, 밑줄, 하이픈만 사용할 수 있습니다.")
            String loginId,
            @NotBlank @Size(min = 8, max = 72)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                    message = "비밀번호는 영문, 숫자, 특수문자를 모두 포함해야 합니다.")
            String password,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 30)
            @Pattern(regexp = "^[0-9+() -]+$", message = "전화번호 형식이 올바르지 않습니다.")
            String phone,
            @AssertTrue(message = "개인정보 수집·이용에 동의해야 가입할 수 있습니다.")
            boolean privacyConsent,
            @NotBlank String captchaToken,
            @NotBlank String captchaAnswer) {}

    public record RegisterResponse(long userId, String loginId, String name, String role,
                                   String phone, LocalDateTime createdAt) {}

    public record LoginRequest(
            @NotBlank @Size(max = 100) String loginId,
            @NotBlank @Size(max = 72) String password,
            @NotBlank String role) {}

    public record LoginResponse(long userId, String loginId, String name, String role,
                                String phone, Long storeId) {}

    private record LoginRow(long userId, String loginId, String passwordHash, String name,
                            String role, String phone, int failedLoginCount,
                            LocalDateTime lockedUntil, Long storeId) {}

    private void registerFailedAttempt(LoginRow user) {
        int attempts = user.failedLoginCount() + 1;
        if (attempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
            jdbc.update("UPDATE app_users SET failed_login_count = ?, locked_until = ? WHERE user_id = ?",
                    attempts, LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES), user.userId());
        } else {
            jdbc.update("UPDATE app_users SET failed_login_count = ? WHERE user_id = ?",
                    attempts, user.userId());
        }
    }

    private UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("아이디, 비밀번호 또는 로그인 유형이 올바르지 않습니다.");
    }
}
