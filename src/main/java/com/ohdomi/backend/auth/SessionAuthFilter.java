package com.ohdomi.backend.auth;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// Enforces login on every /api/** route except /api/auth/login and /api/auth/register,
// requires ADMIN role on any path containing "/admin", and for OWNER sessions requires the
// {storeId} path segment (when present) to match the logged-in owner's own store.
//
// ponytail: storeId ownership is only checked when it appears as a path segment
// (/api/stores/{id}/..., /api/ui/stores/{id}/...). Endpoints that take storeId as a query
// param (e.g. hygiene-inspections, order-recommendations) are not yet scoped per-owner —
// add per-controller checks there if that gap matters before this is used in production.
@Component
@Order(1)
public class SessionAuthFilter extends OncePerRequestFilter {
    public static final String COOKIE_NAME = "SESSION";
    public static final String CURRENT_USER_ATTR = "currentUser";

    private static final Pattern STORE_ID_PATTERN =
            Pattern.compile("^/api/(?:ui/)?stores/(\\d+)(?:/.*)?$");

    private final SessionManager sessionManager;

    public SessionAuthFilter(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        // CSRF mitigation: a cross-site <form> POST can carry the session cookie automatically,
        // but it cannot set an arbitrary request header — only same-origin XHR/fetch can. Every
        // state-changing call from this app's own frontend already sends this header (see
        // apiUrl()/api() helpers), so this rejects the classic form-based CSRF vector for free.
        if (!isSafeMethod(request.getMethod()) && !"XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            reject(response, HttpServletResponse.SC_FORBIDDEN, "잘못된 요청입니다.");
            return;
        }

        if (isPublic(path)) {
            chain.doFilter(request, response);
            return;
        }

        CurrentUser user = sessionManager.resolve(readCookie(request));
        if (user == null) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "로그인이 필요합니다.");
            return;
        }
        if (path.contains("/admin") && !"ADMIN".equals(user.role())) {
            reject(response, HttpServletResponse.SC_FORBIDDEN, "관리자 권한이 필요합니다.");
            return;
        }
        if ("OWNER".equals(user.role())) {
            Matcher matcher = STORE_ID_PATTERN.matcher(path);
            if (matcher.matches() && !matcher.group(1).equals(String.valueOf(user.storeId()))) {
                reject(response, HttpServletResponse.SC_FORBIDDEN, "다른 매장에 접근할 수 없습니다.");
                return;
            }
        }

        request.setAttribute(CURRENT_USER_ATTR, user);
        chain.doFilter(request, response);
    }

    private boolean isSafeMethod(String method) {
        return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
    }

    private boolean isPublic(String path) {
        return path.equals("/api/auth/login") || path.equals("/api/auth/register")
                || path.equals("/api/auth/logout") || path.equals("/api/auth/captcha");
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }
}
