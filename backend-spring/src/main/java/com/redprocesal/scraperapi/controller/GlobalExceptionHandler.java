package com.redprocesal.scraperapi.controller;

import com.redprocesal.scraperapi.exception.ExternalServiceException;
import com.redprocesal.scraperapi.exception.ServiceOverloadedException;
import com.redprocesal.scraperapi.exception.UpstreamTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UpstreamTimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleUpstreamTimeout(UpstreamTimeoutException ex) {
        log.warn("Upstream timeout", ex);
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("error", "upstream_timeout");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(body);
    }

    @ExceptionHandler(ServiceOverloadedException.class)
    public ResponseEntity<Map<String, Object>> handleServiceOverloaded(ServiceOverloadedException ex) {
        log.warn("Service overloaded", ex);
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("error", "service_overloaded");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<Map<String, Object>> handleExternalService(ExternalServiceException ex) {
        log.error("External service error", ex);
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("error", "external_service_error");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        log.warn("Bad request", ex);
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("error", "bad_request");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Internal server error", ex);
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("error", "internal_server_error");
        body.put("message", "An unexpected error occurred");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}