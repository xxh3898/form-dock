package com.formdock.survey;

import com.formdock.response.SurveyResponseReadRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SurveyStructureGuard {

    private final SurveyStructureLockRepository lockRepository;
    private final SurveyResponseReadRepository responseReadRepository;

    public SurveyStructureGuard(
            SurveyStructureLockRepository lockRepository,
            SurveyResponseReadRepository responseReadRepository) {
        this.lockRepository = lockRepository;
        this.responseReadRepository = responseReadRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Survey requireMutable(Long ownerId, Long surveyId) {
        Survey survey = lockRepository.lockActiveOwnedSurvey(ownerId, surveyId);
        if (responseReadRepository.existsBySurveyId(survey.getId())) {
            throw SurveyException.structureLocked();
        }
        return survey;
    }
}
