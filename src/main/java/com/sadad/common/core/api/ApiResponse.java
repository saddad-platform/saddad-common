package com.sadad.common.core.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private T data;
    private ApiMeta meta;

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder()
                .data(data)
                .meta(ApiMeta.builder()
                        .timestamp(Instant.now())
                        .build())
                .build();
    }

    public static <T> ApiResponse<T> ok(T data, String requestId) {
        return ApiResponse.<T>builder()
                .data(data)
                .meta(ApiMeta.builder()
                        .requestId(requestId)
                        .timestamp(Instant.now())
                        .build())
                .build();
    }

    public static <T> ApiResponse<T> ok(T data, String requestId, PageMeta page) {
        return ApiResponse.<T>builder()
                .data(data)
                .meta(ApiMeta.builder()
                        .requestId(requestId)
                        .timestamp(Instant.now())
                        .page(page)
                        .build())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ApiMeta {
        private String requestId;
        private Instant timestamp;
        private PageMeta page;
    }
}
