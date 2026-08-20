package com.formdock.survey;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.formdock.question.QuestionRepository;
import com.formdock.question.QuestionResponse;
import com.formdock.response.SurveyResponseReadRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SurveyService {

    static final int MAX_GENERATED_SLUG_ATTEMPTS = 8;

    private final SurveyRepository surveyRepository;
    private final SurveyCreationAttempt creationAttempt;
    private final SurveySlugPolicy slugPolicy;
    private final QuestionRepository questionRepository;
    private final SurveyResponseReadRepository responseReadRepository;

    public SurveyService(
            SurveyRepository surveyRepository,
            SurveyCreationAttempt creationAttempt,
            SurveySlugPolicy slugPolicy,
            QuestionRepository questionRepository,
            SurveyResponseReadRepository responseReadRepository) {
        this.surveyRepository = surveyRepository;
        this.creationAttempt = creationAttempt;
        this.slugPolicy = slugPolicy;
        this.questionRepository = questionRepository;
        this.responseReadRepository = responseReadRepository;
    }

    @Transactional(readOnly = true)
    public List<SurveyListItemResponse> list(Long ownerId) {
        List<Survey> surveys = surveyRepository
                .findAllByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDescIdDesc(ownerId);
        Map<Long, Long> responseCounts = responseReadRepository.countBySurveyIds(
                surveys.stream().map(Survey::getId).toList());
        return surveys
                .stream()
                .map(survey -> SurveyListItemResponse.from(
                        survey,
                        responseCounts.getOrDefault(survey.getId(), 0L)))
                .toList();
    }

    public SurveyDetailResponse create(Long ownerId, SurveyCreateCommand command) {
        if (command.slug() != null) {
            try {
                return detailResponse(
                        creationAttempt.create(ownerId, command, command.slug()));
            } catch (SurveySlugCollisionException exception) {
                throw SurveyException.slugConflict();
            }
        }

        for (int attempt = 0; attempt < MAX_GENERATED_SLUG_ATTEMPTS; attempt++) {
            String candidate = slugPolicy.generatedCandidate(command.title(), attempt);
            try {
                return detailResponse(creationAttempt.create(ownerId, command, candidate));
            } catch (SurveySlugCollisionException exception) {
                // The next independent transaction retries with a fresh server-generated suffix.
            }
        }
        throw SurveyException.temporarilyUnavailable();
    }

    @Transactional(readOnly = true)
    public SurveyDetailResponse detail(Long ownerId, Long surveyId) {
        return detailResponse(requireActiveSurvey(ownerId, surveyId));
    }

    @Transactional
    public SurveyDetailResponse update(
            Long ownerId,
            Long surveyId,
            SurveyPatchCommand command) {
        int updatedRows;
        try {
            updatedRows = surveyRepository.updateActiveMetadata(
                    surveyId,
                    ownerId,
                    command.titlePresent(),
                    command.title(),
                    command.descriptionPresent(),
                    command.description(),
                    command.privacyNoticePresent(),
                    command.privacyNotice(),
                    command.slugPresent(),
                    command.slug(),
                    SurveyStatus.DRAFT,
                    Instant.now());
        } catch (DataIntegrityViolationException exception) {
            if (SurveyDatabaseConstraints.isUniqueSlugViolation(exception)) {
                throw SurveyException.slugConflict();
            }
            throw exception;
        }

        if (updatedRows == 0) {
            if (command.slugPresent()
                    && surveyRepository
                            .findByIdAndOwnerIdAndDeletedAtIsNull(surveyId, ownerId)
                            .isPresent()) {
                throw SurveyException.slugImmutable();
            }
            throw SurveyException.notFound();
        }
        return detailResponse(requireActiveSurvey(ownerId, surveyId));
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

    private SurveyDetailResponse detailResponse(Survey survey) {
        List<QuestionResponse> questions = questionRepository
                .findAllWithOptionsBySurveyIdOrderByPosition(survey.getId())
                .stream()
                .map(QuestionResponse::from)
                .toList();
        return SurveyDetailResponse.from(
                survey,
                questions,
                responseReadRepository.countBySurveyId(survey.getId()),
                responseReadRepository.existsBySurveyId(survey.getId()));
    }
}
