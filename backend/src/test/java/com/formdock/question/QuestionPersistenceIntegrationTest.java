package com.formdock.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import com.formdock.PostgreSQLTestConfiguration;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(PostgreSQLTestConfiguration.class)
@SpringBootTest
@Transactional
class QuestionPersistenceIntegrationTest {

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void should_persistAllCanonicalTypesAndOrderedOptions_when_questionsAreSaved() {
        Long surveyId = createSurvey();
        List<Question> questions = List.of(
                question(surveyId, QuestionType.NUMBER, 5, null, null, null, null,
                        new BigDecimal("-1.2500"), new BigDecimal("1.00"), List.of()),
                question(surveyId, QuestionType.SCALE, 4, 1, 10, "Low", "High",
                        null, null, List.of()),
                question(surveyId, QuestionType.MULTIPLE_CHOICE, 3, null, null, null, null,
                        null, null, List.of(" Third ", "First", "Second")),
                question(surveyId, QuestionType.SINGLE_CHOICE, 2, null, null, null, null,
                        null, null, List.of("Yes", "No")),
                question(surveyId, QuestionType.LONG_TEXT, 1, null, null, null, null,
                        null, null, List.of()),
                question(surveyId, QuestionType.SHORT_TEXT, 0, null, null, null, null,
                        null, null, List.of()));
        questionRepository.saveAllAndFlush(questions);
        entityManager.clear();

        List<Question> persisted = questionRepository
                .findAllWithOptionsBySurveyIdOrderByPosition(surveyId);

        assertThat(persisted)
                .extracting(Question::getType)
                .containsExactly(
                        QuestionType.SHORT_TEXT,
                        QuestionType.LONG_TEXT,
                        QuestionType.SINGLE_CHOICE,
                        QuestionType.MULTIPLE_CHOICE,
                        QuestionType.SCALE,
                        QuestionType.NUMBER);
        assertThat(persisted.get(2).getOptions())
                .extracting(QuestionOption::getLabel, QuestionOption::getPosition)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Yes", 0),
                        org.assertj.core.groups.Tuple.tuple("No", 1));
        assertThat(persisted.get(3).getOptions())
                .extracting(QuestionOption::getLabel)
                .containsExactly("Third", "First", "Second");
        assertThat(persisted.get(4).getScaleMin()).isEqualTo(1);
        assertThat(persisted.get(4).getScaleMax()).isEqualTo(10);
        assertThat(persisted.get(5).getNumberMin()).isEqualByComparingTo("-1.2500");
        assertThat(persisted)
                .allSatisfy(question -> {
                    assertThat(question.getCreatedAt()).isNotNull();
                    assertThat(question.getUpdatedAt()).isNotNull();
                });

        QuestionResponse numberResponse = QuestionResponse.from(persisted.get(5));
        assertThat(numberResponse.numberMin()).isEqualTo("-1.25");
        assertThat(numberResponse.numberMax()).isEqualTo("1");
        assertThat(numberResponse.options()).isEmpty();
    }

    @Test
    void should_rejectInvalidTypeConfiguration_when_questionIsConstructed() {
        Long surveyId = createSurvey();

        assertThatThrownBy(() -> question(
                surveyId,
                QuestionType.SINGLE_CHOICE,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of("Only one")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> question(
                surveyId,
                QuestionType.SHORT_TEXT,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of("Not allowed", "Still not allowed")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> question(
                surveyId,
                QuestionType.SCALE,
                0,
                10,
                10,
                null,
                null,
                null,
                null,
                List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> question(
                surveyId,
                QuestionType.NUMBER,
                0,
                null,
                null,
                null,
                null,
                new BigDecimal("1.00001"),
                null,
                List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Question question(
            Long surveyId,
            QuestionType type,
            int position,
            Integer scaleMin,
            Integer scaleMax,
            String scaleMinLabel,
            String scaleMaxLabel,
            BigDecimal numberMin,
            BigDecimal numberMax,
            List<String> options) {
        return Question.create(
                surveyId,
                type,
                "  " + type + " title  ",
                "Description",
                true,
                position,
                scaleMin,
                scaleMax,
                scaleMinLabel,
                scaleMaxLabel,
                numberMin,
                numberMax,
                options);
    }

    private Long createSurvey() {
        Instant now = Instant.now();
        Long ownerId = jdbcTemplate.queryForObject("""
                INSERT INTO users (
                    email, password_hash, display_name, role, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                "question-owner@example.test",
                "{bcrypt}test-only-hash",
                "Question Owner",
                "ADMIN",
                Timestamp.from(now),
                Timestamp.from(now));
        return jdbcTemplate.queryForObject("""
                INSERT INTO surveys (
                    owner_id, title, slug, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                ownerId,
                "Question Survey",
                "question-survey",
                "DRAFT",
                Timestamp.from(now),
                Timestamp.from(now));
    }
}
