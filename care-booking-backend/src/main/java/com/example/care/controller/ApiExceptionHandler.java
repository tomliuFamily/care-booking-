package com.example.care.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter
    .HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation
    .MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log =
        LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> responseStatus(
            ResponseStatusException exception
    ) {
        String message = exception.getReason() == null
            ? "請求失敗"
            : exception.getReason();

        return ResponseEntity
            .status(exception.getStatusCode())
            .body(Map.of("message", message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validation(
            MethodArgumentNotValidException exception
    ) {
        var error = exception.getBindingResult()
            .getFieldErrors()
            .stream()
            .findFirst();

        String message = error
            .map(e -> e.getField() + "：" + e.getDefaultMessage())
            .orElse("輸入資料不正確");

        return ResponseEntity.badRequest()
            .body(Map.of("message", message));
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<Map<String, String>> badInput(
            Exception exception
    ) {
        return ResponseEntity.badRequest()
            .body(Map.of(
                "message", "資料格式不正確，請檢查輸入內容"
            ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> forbidden() {
        return ResponseEntity.status(403)
            .body(Map.of("message", "沒有操作權限"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> conflict() {
        return ResponseEntity.status(409)
            .body(Map.of(
                "message",
                "資料重複或關聯不正確，請重新整理後再試"
            ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> other(
            Exception exception
    ) {
        log.error("API 執行失敗", exception);

        return ResponseEntity.status(500)
            .body(Map.of(
                "message",
                "伺服器處理失敗，請查看後端 Console"
            ));
    }
}