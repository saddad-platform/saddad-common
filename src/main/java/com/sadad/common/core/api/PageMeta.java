package com.sadad.common.core.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageMeta {
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;
}
