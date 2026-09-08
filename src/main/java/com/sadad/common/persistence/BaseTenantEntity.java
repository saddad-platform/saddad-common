package com.sadad.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public abstract class BaseTenantEntity extends BaseAuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;
}
