package com.iot.telemetry.controller;

import com.azure.cosmos.CosmosException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CosmosException.class)
    public ResponseEntity<Map<String, String>> handleCosmosException(CosmosException e) {
        logger.error("Cosmos DB error: status={}, message={}", e.getStatusCode(), e.getMessage());

        if (e.getStatusCode() == 404) {
            return ResponseEntity.notFound().build();
        }
        if (e.getStatusCode() == 409) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Resource already exists"));
        }
        if (e.getStatusCode() == 429) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too many requests, please retry"));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception e) {
        logger.error("Unexpected error: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
    }
}
