package com.hieu.order_service.interfaces.rest;

import com.hieu.order_service.application.common.ApplicationException;
import com.hieu.order_service.application.common.ValidationException;
import com.hieu.order_service.domain.exception.*;
import com.hieu.order_service.domain.shared.DomainException;
import com.hieu.common.error.ErrorCode;
import com.hieu.common.error.ErrorResponse;
import com.hieu.common.error.ErrorResponse.FieldError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Maps domain/application exceptions to stable JSON error responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({OrderNotFoundException.class, ReturnRequestNotFoundException.class})
    public ResponseEntity<ErrorResponse> notFound(DomainException ex, HttpServletRequest req) {
        return body(HttpStatus.NOT_FOUND, ex.code(), ex.getMessage(), req, null);
    }

    @ExceptionHandler({DuplicateOrderException.class})
    public ResponseEntity<ErrorResponse> conflict(DomainException ex, HttpServletRequest req) {
        return body(HttpStatus.CONFLICT, ex.code(), ex.getMessage(), req, null);
    }

    @ExceptionHandler({InvalidOrderStateException.class})
    public ResponseEntity<ErrorResponse> invalidState(DomainException ex, HttpServletRequest req) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, ex.code(), ex.getMessage(), req, null);
    }

    @ExceptionHandler({InsufficientStockException.class, EmptyCartException.class})
    public ResponseEntity<ErrorResponse> badRequest(DomainException ex, HttpServletRequest req) {
        return body(HttpStatus.BAD_REQUEST, ex.code(), ex.getMessage(), req, null);
    }

    @ExceptionHandler(CancelNotAllowedException.class)
    public ResponseEntity<ErrorResponse> cancelNotAllowed(CancelNotAllowedException ex, HttpServletRequest req) {
        return body(HttpStatus.CONFLICT, "ORDER-CANCEL-NOT-ALLOWED", ex.getMessage(), req, null);
    }

    @ExceptionHandler(CancelRateLimitedException.class)
    public ResponseEntity<ErrorResponse> cancelRateLimited(CancelRateLimitedException ex, HttpServletRequest req) {
        return body(HttpStatus.TOO_MANY_REQUESTS, "ORDER-CANCEL-RATE-LIMIT", ex.getMessage(), req, null);
    }

    @ExceptionHandler({ServiceUnavailableException.class})
    public ResponseEntity<ErrorResponse> serviceUnavailable(DomainException ex, HttpServletRequest req) {
        return body(HttpStatus.SERVICE_UNAVAILABLE, ex.code(), ex.getMessage(), req, null);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> validation(ValidationException ex, HttpServletRequest req) {
        var fields = ex.fieldErrors().entrySet().stream()
                .map(e -> new FieldError(e.getKey(), e.getValue(), null)).toList();
        return body(HttpStatus.BAD_REQUEST, ex.code(), ex.getMessage(), req, fields);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> beanValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        var fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage(), fe.getRejectedValue())).toList();
        return body(HttpStatus.BAD_REQUEST, ErrorCode.APP_BAD_REQUEST.code(), "Validation failed", req, fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> constraintViolation(ConstraintViolationException ex, HttpServletRequest req) {
        return body(HttpStatus.BAD_REQUEST, ErrorCode.APP_BAD_REQUEST.code(), ex.getMessage(), req, null);
    }

    /** Malformed JSON body, missing required fields, primitive coercion failures → 400 not 500. */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> notReadable(org.springframework.http.converter.HttpMessageNotReadableException ex, HttpServletRequest req) {
        var cause = ex.getMostSpecificCause();
        var msg = cause != null ? cause.getMessage() : ex.getMessage();
        return body(HttpStatus.BAD_REQUEST, ErrorCode.APP_BAD_REQUEST.code(), "Malformed JSON: " + msg, req, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> dataIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return body(HttpStatus.CONFLICT, ErrorCode.APP_CONFLICT.code(), "Constraint violation", req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> accessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return body(HttpStatus.FORBIDDEN, ErrorCode.APP_FORBIDDEN.code(), "Access denied", req, null);
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<ErrorResponse> unauthenticated(AuthenticationCredentialsNotFoundException ex, HttpServletRequest req) {
        return body(HttpStatus.UNAUTHORIZED, ErrorCode.APP_UNAUTHORIZED.code(), "Authentication required", req, null);
    }

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ErrorResponse> application(ApplicationException ex, HttpServletRequest req) {
        return body(HttpStatus.BAD_REQUEST, ex.code(), ex.getMessage(), req, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> illegalArg(IllegalArgumentException ex, HttpServletRequest req) {
        return body(HttpStatus.BAD_REQUEST, ErrorCode.APP_BAD_REQUEST.code(), ex.getMessage(), req, null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> illegalState(IllegalStateException ex, HttpServletRequest req) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.APP_UNPROCESSABLE.code(), ex.getMessage(), req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unknown(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.APP_INTERNAL.code(), "Internal server error", req, null);
    }

    private static ResponseEntity<ErrorResponse> body(HttpStatus status, String code, String message,
                                                       HttpServletRequest req, List<FieldError> fields) {
        return ResponseEntity.status(status).body(
                new ErrorResponse(code, message, req.getRequestURI(), Instant.now(), fields, null, Map.of()));
    }
}
