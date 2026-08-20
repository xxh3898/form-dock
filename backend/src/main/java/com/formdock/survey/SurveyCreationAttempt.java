package com.formdock.survey;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SurveyCreationAttempt {

    private final SurveyRepository surveyRepository;

    public SurveyCreationAttempt(SurveyRepository surveyRepository) {
        this.surveyRepository = surveyRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Survey create(Long ownerId, SurveyCreateCommand command, String slug) {
        try {
            return surveyRepository.saveAndFlush(Survey.createDraft(
                    ownerId,
                    command.title(),
                    command.description(),
                    slug,
                    command.privacyNotice()));
        } catch (DataIntegrityViolationException exception) {
            if (SurveyDatabaseConstraints.isUniqueSlugViolation(exception)) {
                throw new SurveySlugCollisionException();
            }
            throw exception;
        }
    }
}

final class SurveySlugCollisionException extends RuntimeException {
}
