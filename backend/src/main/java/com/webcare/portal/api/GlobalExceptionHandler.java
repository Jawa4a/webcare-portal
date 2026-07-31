package com.webcare.portal.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.webcare.portal.website.UrlNotAllowedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException exception) {
        List<ApiErrorResponse.FieldErrorResponse> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiErrorResponse.FieldErrorResponse(
                        error.getField(), error.getCode() == null ? "INVALID" : error.getCode()))
                .toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Проверьте заполнение полей.", errors);
    }

    @ExceptionHandler(UrlNotAllowedException.class)
    ResponseEntity<ApiErrorResponse> urlNotAllowed(UrlNotAllowedException exception) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, exception.getCode(), exception.getMessage(),
                List.of(new ApiErrorResponse.FieldErrorResponse("url", exception.getCode())));
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiErrorResponse> notFound(NotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage(), List.of());
    }

    @ExceptionHandler({ConflictException.class, DataIntegrityViolationException.class})
    ResponseEntity<ApiErrorResponse> conflict(Exception exception) {
        String message = exception instanceof ConflictException
                ? exception.getMessage()
                : "Запись с такими данными уже существует.";
        return response(HttpStatus.CONFLICT, "DATA_CONFLICT", message, List.of());
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ApiErrorResponse> badCredentials() {
        return response(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "Неверный email или пароль.", List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> typeMismatch(MethodArgumentTypeMismatchException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Некорректное значение параметра " + exception.getName() + ".", List.of());
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String code,
            String message,
            List<ApiErrorResponse.FieldErrorResponse> fieldErrors) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                code,
                message,
                fieldErrors,
                UUID.randomUUID().toString(),
                Instant.now()));
    }
}
