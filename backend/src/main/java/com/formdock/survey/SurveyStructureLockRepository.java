package com.formdock.survey;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
public class SurveyStructureLockRepository {

    private static final String LOCK_NOT_AVAILABLE = "55P03";
    private static final String DEADLOCK_DETECTED = "40P01";

    private final EntityManager entityManager;
    private final String lockTimeout;

    public SurveyStructureLockRepository(
            EntityManager entityManager,
            @Value("${formdock.survey.structure-lock-timeout:1s}") Duration lockTimeout) {
        long timeoutMillis = lockTimeout.toMillis();
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException(
                    "Survey structure lock timeout must be at least one millisecond");
        }
        this.entityManager = entityManager;
        this.lockTimeout = timeoutMillis + "ms";
    }

    public Survey lockActiveOwnedSurvey(Long ownerId, Long surveyId) {
        entityManager.createNativeQuery(
                        "SELECT set_config('lock_timeout', ?1, true)",
                        String.class)
                .setParameter(1, lockTimeout)
                .getSingleResult();

        try {
            List<Survey> matches = entityManager.createQuery("""
                            SELECT survey
                            FROM Survey survey
                            WHERE survey.id = :surveyId
                              AND survey.ownerId = :ownerId
                              AND survey.deletedAt IS NULL
                            """, Survey.class)
                    .setParameter("surveyId", surveyId)
                    .setParameter("ownerId", ownerId)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .getResultList();
            if (matches.isEmpty()) {
                throw SurveyException.notFound();
            }
            return matches.getFirst();
        } catch (RuntimeException exception) {
            if (isBoundedLockFailure(exception)) {
                throw SurveyException.temporarilyUnavailable();
            }
            throw exception;
        }
    }

    static boolean isBoundedLockFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                SQLException candidate = sqlException;
                while (candidate != null) {
                    String sqlState = candidate.getSQLState();
                    if (LOCK_NOT_AVAILABLE.equals(sqlState)
                            || DEADLOCK_DETECTED.equals(sqlState)) {
                        return true;
                    }
                    candidate = candidate.getNextException();
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
