package com.formdock.survey;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "surveys",
        uniqueConstraints = @UniqueConstraint(name = "uk_surveys_slug", columnNames = "slug"))
public class Survey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 5000)
    private String description;

    @Column(nullable = false, length = 64, updatable = true)
    private String slug;

    @Column(name = "privacy_notice", length = 5000)
    private String privacyNotice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SurveyStatus status;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Survey() {
    }

    private Survey(
            Long ownerId,
            String title,
            String description,
            String slug,
            String privacyNotice) {
        this.ownerId = Objects.requireNonNull(ownerId, "Survey owner is required");
        this.title = Objects.requireNonNull(title, "Survey title is required");
        this.description = description;
        this.slug = Objects.requireNonNull(slug, "Survey slug is required");
        this.privacyNotice = privacyNotice;
        this.status = SurveyStatus.DRAFT;
    }

    static Survey createDraft(
            Long ownerId,
            String title,
            String description,
            String slug,
            String privacyNotice) {
        return new Survey(ownerId, title, description, slug, privacyNotice);
    }

    void softDelete() {
        if (status == SurveyStatus.OPEN) {
            throw SurveyException.deleteRequiresClosed();
        }
        deletedAt = Instant.now();
    }

    @PrePersist
    private void populateTimestamps() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getSlug() {
        return slug;
    }

    public String getPrivacyNotice() {
        return privacyNotice;
    }

    public SurveyStatus getStatus() {
        return status;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
