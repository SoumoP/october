package com.october.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "jobs", indexes = {
        @Index(name = "idx_jobs_company_id", columnList = "company_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "external_id")
    private String externalId;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(length = 256)
    private String location;

    @Column(length = 256)
    private String department;

    @Column(length = 1024)
    private String url;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String description;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;
}
