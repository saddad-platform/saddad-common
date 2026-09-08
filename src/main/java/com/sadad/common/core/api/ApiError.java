package com.sadad.common.core.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {
    private ErrorDetail error;

    public static ApiError of(String code, String message, String requestId) {
        return ApiError.builder()
                .error(ErrorDetail.builder()
                        .code(code)
                        .message(message)
                        .requestId(requestId)
                        .timestamp(Instant.now())
                        .build())
                .build();
    }

    public static ApiError of(String code, String message, String requestId, List<FieldError> details) {
        return ApiError.builder()
                .error(ErrorDetail.builder()
                        .code(code)
                        .message(message)
                        .requestId(requestId)
                        .timestamp(Instant.now())
                        .details(details)
                        .build())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorDetail {
        private String code;
        private String message;
        private String requestId;
        private Instant timestamp;
        private List<FieldError> details;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldError {
        private String field;
        private String message;
        private Object rejectedValue;
    }
}
