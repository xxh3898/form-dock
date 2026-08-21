package com.formdock.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.sql.SQLException;
import java.time.Duration;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;

class SurveyStructureLockRepositoryTest {

    @Test
    void should_rejectSubMillisecondTimeout_when_configurationWouldDisablePostgresqlTimeout() {
        assertThatThrownBy(() -> new SurveyStructureLockRepository(
                mock(EntityManager.class),
                Duration.ofNanos(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_classifyLockTimeout_when_postgresqlReportsLockUnavailable() {
        RuntimeException failure = new RuntimeException(new SQLException(
                "test-only lock timeout",
                "55P03"));

        assertThat(SurveyStructureLockRepository.isBoundedLockFailure(failure)).isTrue();
    }

    @Test
    void should_classifyDeadlock_when_postgresqlReportsDeadlockDetected() {
        RuntimeException failure = new RuntimeException(new SQLException(
                "test-only deadlock",
                "40P01"));

        assertThat(SurveyStructureLockRepository.isBoundedLockFailure(failure)).isTrue();
    }

    @Test
    void should_notClassifyUnrelatedDatabaseFailure_when_sqlStateIsDifferent() {
        RuntimeException failure = new RuntimeException(new SQLException(
                "test-only unique violation",
                "23505"));

        assertThat(SurveyStructureLockRepository.isBoundedLockFailure(failure)).isFalse();
    }
}
