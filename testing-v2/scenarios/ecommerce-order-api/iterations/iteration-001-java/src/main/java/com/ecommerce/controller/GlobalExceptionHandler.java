package com.ecommerce.controller;

import com.azure.cosmos.CosmosException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleBadRequest(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid request body"));
    }

    @ExceptionHandler(CosmosException.class)
    public ResponseEntity<?> handleCosmosException(CosmosException e) {
        if (e.getStatusCode() == 404) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("error", e.getMessage()));
    }
}
