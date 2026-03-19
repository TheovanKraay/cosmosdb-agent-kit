package com.example.multitenentsaas.controller;

import com.azure.cosmos.CosmosException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler for Cosmos DB and application errors.
 * Provides meaningful error responses including RU consumption
 * and Cosmos DB status codes for debugging (Rule 4.5: diagnostics).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CosmosException.class)
    public ResponseEntity<Map<String, Object>> handleCosmosException(CosmosException ex) {
        int statusCode = ex.getStatusCode();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", statusCode);
        body.put("cosmosStatusCode", statusCode);
        body.put("cosmosSubStatusCode", ex.getSubStatusCode());
        body.put("message", ex.getMessage());
        body.put("activityId", ex.getActivityId());
        body.put("requestCharge", ex.getRequestCharge());

        // Rule 4.3: log diagnostics for 429 and 5xx errors
        if (statusCode == 429 || statusCode >= 500) {
            logger.error("Cosmos DB error {}/{}: {} (RU: {}, ActivityId: {})",
                    statusCode, ex.getSubStatusCode(), ex.getMessage(),
                    ex.getRequestCharge(), ex.getActivityId());
            logger.error("Diagnostics: {}", ex.getDiagnostics());
        }

        HttpStatus httpStatus;
        switch (statusCode) {
            case 404: httpStatus = HttpStatus.NOT_FOUND; break;
            case 409: httpStatus = HttpStatus.CONFLICT; break;
            case 412: httpStatus = HttpStatus.PRECONDITION_FAILED; break; // ETag conflict
            case 429: httpStatus = HttpStatus.TOO_MANY_REQUESTS; break;
            default: httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        return ResponseEntity.status(httpStatus).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        logger.error("Unhandled exception", ex);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", 500);
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
