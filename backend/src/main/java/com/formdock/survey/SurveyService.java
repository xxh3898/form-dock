package com.formdock.survey;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SurveyService {

    static final int MAX_GENERATED_SLUG_ATTEMPTS = 8;

    private final SurveyRepository surveyRepository;
    private final SurveyCreationAttempt creationAttempt;
    private final SurveySlugPolicy slugPolicy;

    public SurveyService(
            SurveyRepository surveyRepository,
            SurveyCreationAttempt creationAttempt,
            SurveySlugPolicy slugPolicy) {
        this.surveyRepository = surveyRepository;
        this.creationAttempt = creationAttempt;
        this.slugPolicy = slugPolicy;
    }

    @Transactional(readOnly = true)
    public List<SurveyListItemResponse> list(Long ownerId) {
        return surveyRepository
                .findAllByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDescIdDesc(ownerId)
                .stream()
                .map(SurveyListItemResponse::from)
                .toList();
    }

    public SurveyDetailResponse create(Long ownerId, SurveyCreateCommand command) {
        if (command.slug() != null) {
            try {
                return SurveyDetailResponse.from(
                        creationAttempt.create(ownerId, command, command.slug()));
            } catch (SurveySlugCollisionException exception) {
                throw SurveyException.slugConflict();
            }
        }

        for (int attempt = 0; attempt < MAX_GENERATED_SLUG_ATTEMPTS; attempt++) {
            String candidate = slugPolicy.generatedCandidate(command.title(), attempt);
            try {
                return SurveyDetailResponse.from(
                        creationAttempt.create(ownerId, command, candidate));
            } catch (SurveySlugCollisionException exception) {
                // The next independent transaction retries with a fresh server-generated suffix.
            }
        }
        throw SurveyException.temporarilyUnavailable();
    }

    @Transactional(readOnly = true)
    public SurveyDetailResponse detail(Long ownerId, Long surveyId) {
        return SurveyDetailResponse.from(requireActiveSurvey(ownerId, surveyId));
    }

    @Transactional
    public SurveyDetailResponse update(
            Long ownerId,
            Long surveyId,
            SurveyPatchCommand command) {
        Survey survey = requireActiveSurvey(ownerId, surveyId);

        if (command.titlePresent()) {
            survey.updateTitle(command.title());
        }
        if (command.descriptionPresent()) {
            survey.updateDescription(command.description());
        }
        if (command.privacyNoticePresent()) {
            survey.updatePrivacyNotice(command.privacyNotice());
        }
        if (command.slugPresent()) {
            survey.updateSlug(command.slug());
        }

        try {
            surveyRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            if (SurveyDatabaseConstraints.isUniqueSlugViolation(exception)) {
                throw SurveyException.slugConflict();
            }
            throw exception;
        }
        return SurveyDetailResponse.from(survey);
    }

    @Transactional
    public void delete(Long ownerId, Long surveyId) {
        Survey survey = requireActiveSurvey(ownerId, surveyId);
        survey.softDelete();
    }

    private Survey requireActiveSurvey(Long ownerId, Long surveyId) {
        return surveyRepository
                .findByIdAndOwnerIdAndDeletedAtIsNull(surveyId, ownerId)
                .orElseThrow(SurveyException::notFound);
    }
}
