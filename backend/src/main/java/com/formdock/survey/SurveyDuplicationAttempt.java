package com.formdock.survey;

import java.util.List;

import com.formdock.question.Question;
import com.formdock.question.QuestionRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SurveyDuplicationAttempt {

    private final SurveyStructureLockRepository lockRepository;
    private final SurveyRepository surveyRepository;
    private final QuestionRepository questionRepository;
    private final SurveySlugPolicy slugPolicy;

    public SurveyDuplicationAttempt(
            SurveyStructureLockRepository lockRepository,
            SurveyRepository surveyRepository,
            QuestionRepository questionRepository,
            SurveySlugPolicy slugPolicy) {
        this.lockRepository = lockRepository;
        this.surveyRepository = surveyRepository;
        this.questionRepository = questionRepository;
        this.slugPolicy = slugPolicy;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long duplicate(Long ownerId, Long sourceSurveyId, int attempt) {
        Survey source = lockRepository.lockActiveOwnedSurvey(ownerId, sourceSurveyId);
        List<Question> sourceQuestions = questionRepository
                .findAllWithOptionsBySurveyIdOrderByPosition(source.getId());
        String slug = slugPolicy.generatedCandidate(source.getTitle(), attempt);

        Survey duplicate;
        try {
            duplicate = surveyRepository.saveAndFlush(Survey.createDraft(
                    ownerId,
                    source.getTitle(),
                    source.getDescription(),
                    slug,
                    source.getPrivacyNotice()));
        } catch (DataIntegrityViolationException exception) {
            if (SurveyDatabaseConstraints.isUniqueSlugViolation(exception)) {
                throw new SurveySlugCollisionException();
            }
            throw exception;
        }

        questionRepository.saveAll(sourceQuestions.stream()
                .map(question -> question.deepCopyTo(duplicate.getId()))
                .toList());
        questionRepository.flush();
        return duplicate.getId();
    }
}
