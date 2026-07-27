package com.ohdomi.backend.auth;

import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;

import com.ohdomi.backend.global.ConflictException;
import com.ohdomi.backend.global.UnauthorizedException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JdbcTemplate jdbc;

    public AuthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        String role = request.role().toUpperCase();
        if (!role.equals("OWNER") && !role.equals("ADMIN")) {
            throw new IllegalArgumentException("role must be OWNER or ADMIN");
        }

        List<LoginRow> users = jdbc.query("""
                SELECT u.user_id, u.login_id, u.password_hash, u.name, u.role, u.phone,
                       (SELECT MIN(s.store_id) FROM stores s WHERE s.owner_user_id = u.user_id)
                FROM app_users u
                WHERE u.login_id = ? AND u.active = TRUE
                """, (rs, row) -> new LoginRow(
                rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), (Long) rs.getObject(7)), request.loginId().trim());

        if (users.isEmpty()) throw invalidCredentials();
        LoginRow user = users.get(0);
        if (!user.role().equals(role) || !PasswordHasher.matches(request.password(), user.passwordHash())) {
            throw invalidCredentials();
        }

        return new LoginResponse(user.userId(), user.loginId(), user.name(), user.role(),
                user.phone(), user.storeId());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
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
                          (login_id, password_hash, name, role, phone, active, created_at, updated_at)
                        VALUES (?, ?, ?, 'OWNER', ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
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
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 30)
            @Pattern(regexp = "^[0-9+() -]+$", message = "전화번호 형식이 올바르지 않습니다.")
            String phone) {}

    public record RegisterResponse(long userId, String loginId, String name, String role,
                                   String phone, LocalDateTime createdAt) {}

    public record LoginRequest(
            @NotBlank @Size(max = 100) String loginId,
            @NotBlank @Size(max = 72) String password,
            @NotBlank String role) {}

    public record LoginResponse(long userId, String loginId, String name, String role,
                                String phone, Long storeId) {}

    private record LoginRow(long userId, String loginId, String passwordHash, String name,
                            String role, String phone, Long storeId) {}

    private UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("아이디, 비밀번호 또는 로그인 유형이 올바르지 않습니다.");
    }
}
