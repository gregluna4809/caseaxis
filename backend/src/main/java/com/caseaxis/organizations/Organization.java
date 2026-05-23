package com.caseaxis.organizations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "organizations")
@Getter
@Setter
public class Organization {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "organization_code", nullable = false)
    private String organizationCode;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;
}
