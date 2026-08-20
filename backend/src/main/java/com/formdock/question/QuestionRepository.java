package com.formdock.question;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    @EntityGraph(attributePaths = "options")
    @Query("""
            SELECT question
            FROM Question question
            WHERE question.surveyId = :surveyId
            ORDER BY question.position ASC
            """)
    List<Question> findAllWithOptionsBySurveyIdOrderByPosition(
            @Param("surveyId") Long surveyId);
}
