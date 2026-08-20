package com.ohdomi.backend.global;

import java.time.Instant;
import java.util.Map;

import com.ohdomi.backend.hygiene.HygieneAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(ResourceNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<Map<String, Object>> conflict(ConflictException exception) {
        return response(HttpStatus.CONFLICT, "CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<Map<String, Object>> unauthorized(UnauthorizedException exception) {
        return response(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", exception.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<Map<String, Object>> badRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, "BAD_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(HygieneAiException.class)
    ResponseEntity<Map<String, Object>> hygieneAiUnavailable(HygieneAiException exception) {
        return response(HttpStatus.BAD_GATEWAY, "HYGIENE_AI_UNAVAILABLE", exception.getMessage());
    }

    // 위 목록에 없는 예외(DB 오류, NPE 등 코드로 미리 예상 못 한 것)까지 전부 커버 — 여기가
    // 없으면 Spring 기본 whitelabel 에러로 빠져서 error_code 없이 프론트에 도달함(2026-08-20,
    // "에러코드를 모든 기능에 반영해달라"는 요청으로 추가).
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> internalError(Exception exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                exception.getClass().getSimpleName() + ": " + exception.getMessage());
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String errorCode, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "error_code", errorCode,
                "message", message == null ? "" : message));
    }
}
