package com.sadad.common.messaging;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Standard Domain Event envelope matching SADAD Platform Architecture Specification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DomainEvent<T> {
    @Builder.Default
    private UUID eventId = UUID.randomUUID();
    private String eventType;
    @Builder.Default
    private int eventVersion = 1;
    @Builder.Default
    private Instant occurredAt = Instant.now();
    private String correlationId;
    private String causationId;
    private String tenantId;
    private String producer;
    private T payload;
}
