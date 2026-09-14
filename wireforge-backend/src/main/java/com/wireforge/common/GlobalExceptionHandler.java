package com.wireforge.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalState(IllegalStateException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.fail(e.getMessage());
    }

    /** 保留 404/400 等具体状态码（如素材不存在），避免被兜底成 500 */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<Void>> handleResponseStatus(ResponseStatusException e) {
        Result<Void> body = Result.fail(e.getReason() != null ? e.getReason() : "请求错误");
        return ResponseEntity.status(e.getStatusCode()).body(body);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail("系统异常: " + e.getMessage());
    }
}