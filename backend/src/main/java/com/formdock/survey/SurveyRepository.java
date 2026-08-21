package com.formdock.survey;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SurveyRepository extends JpaRepository<Survey, Long> {

    List<Survey> findAllByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDescIdDesc(Long ownerId);

    Optional<Survey> findByIdAndOwnerIdAndDeletedAtIsNull(Long id, Long ownerId);

    Optional<Survey> findBySlugAndStatusAndDeletedAtIsNull(
            String slug,
            SurveyStatus status);

    @Query("""
            SELECT survey.id
            FROM Survey survey
            WHERE survey.slug = :slug
              AND survey.deletedAt IS NULL
            """)
    Optional<Long> findActiveIdBySlug(@Param("slug") String slug);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Survey survey
            SET survey.title = CASE WHEN :titlePresent = true THEN :title ELSE survey.title END,
                survey.description = CASE
                    WHEN :descriptionPresent = true THEN :description
                    ELSE survey.description
                END,
                survey.privacyNotice = CASE
                    WHEN :privacyNoticePresent = true THEN :privacyNotice
                    ELSE survey.privacyNotice
                END,
                survey.slug = CASE WHEN :slugPresent = true THEN :slug ELSE survey.slug END,
                survey.updatedAt = :updatedAt
            WHERE survey.id = :id
              AND survey.ownerId = :ownerId
              AND survey.deletedAt IS NULL
              AND (
                  :slugPresent = false
                  OR (survey.status = :draftStatus AND survey.openedAt IS NULL)
              )
            """)
    int updateActiveMetadata(
            @Param("id") Long id,
            @Param("ownerId") Long ownerId,
            @Param("titlePresent") boolean titlePresent,
            @Param("title") String title,
            @Param("descriptionPresent") boolean descriptionPresent,
            @Param("description") String description,
            @Param("privacyNoticePresent") boolean privacyNoticePresent,
            @Param("privacyNotice") String privacyNotice,
            @Param("slugPresent") boolean slugPresent,
            @Param("slug") String slug,
            @Param("draftStatus") SurveyStatus draftStatus,
            @Param("updatedAt") Instant updatedAt);
}
