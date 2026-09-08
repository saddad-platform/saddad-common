package com.sadad.common.web;

import com.sadad.common.core.api.ApiError;
import com.sadad.common.core.context.RequestContext;
import com.sadad.common.exception.PlatformException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PlatformException.class)
    public ResponseEntity<ApiError> handlePlatformException(PlatformException ex) {
        String requestId = RequestContext.currentRequestId();
        log.warn("Business or Platform exception [{}]: {} (requestId={})", ex.getCode(), ex.getMessage(), requestId);

        ApiError error = ApiError.of(ex.getCode(), ex.getMessage(), requestId);
        return ResponseEntity.status(ex.getHttpStatus()).body(error);
    }

    @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
    public ResponseEntity<ApiError> handleAccessDenied(RuntimeException ex) {
        String requestId = RequestContext.currentRequestId();
        log.warn("Access denied (requestId={}): {}", requestId, ex.getMessage());

        ApiError error = ApiError.of("ACCESS_DENIED", "You do not have permission to perform this action", requestId);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException ex) {
        String requestId = RequestContext.currentRequestId();
        List<ApiError.FieldError> details = new ArrayList<>();

        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            details.add(ApiError.FieldError.builder()
                    .field(fe.getField())
                    .message(fe.getDefaultMessage())
                    .rejectedValue(fe.getRejectedValue())
                    .build());
        }

        log.warn("Validation error on request {}: {} field errors", requestId, details.size());
        ApiError error = ApiError.of("VALIDATION_ERROR", "Validation failed for request arguments", requestId, details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        String requestId = RequestContext.currentRequestId();
        log.warn("Upload rejected - file too large (requestId={})", requestId);
        ApiError error = ApiError.of("FILE_TOO_LARGE", "This file is too large. Please upload a file under 5 MB.", requestId);
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(Exception ex) {
        String requestId = RequestContext.currentRequestId();
        log.error("Unhandled internal system error (requestId={})", requestId, ex);

        // Safe message without exposing internal stack trace or SQL
        ApiError error = ApiError.of("INTERNAL_SERVER_ERROR",
                "An unexpected error occurred. Please contact system administrator with the reference ID.",
                requestId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
