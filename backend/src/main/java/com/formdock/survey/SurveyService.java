package com.formdock.survey;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import com.formdock.question.Question;
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
    private final SurveyDuplicationAttempt duplicationAttempt;
    private final SurveySlugPolicy slugPolicy;
    private final QuestionRepository questionRepository;
    private final SurveyResponseReadRepository responseReadRepository;
    private final SurveyStructureLockRepository lockRepository;

    public SurveyService(
            SurveyRepository surveyRepository,
            SurveyCreationAttempt creationAttempt,
            SurveyDuplicationAttempt duplicationAttempt,
            SurveySlugPolicy slugPolicy,
            QuestionRepository questionRepository,
            SurveyResponseReadRepository responseReadRepository,
            SurveyStructureLockRepository lockRepository) {
        this.surveyRepository = surveyRepository;
        this.creationAttempt = creationAttempt;
        this.duplicationAttempt = duplicationAttempt;
        this.slugPolicy = slugPolicy;
        this.questionRepository = questionRepository;
        this.responseReadRepository = responseReadRepository;
        this.lockRepository = lockRepository;
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
        Survey survey = lockRepository.lockActiveOwnedSurvey(ownerId, surveyId);
        survey.softDelete();
    }

    @Transactional
    public SurveyDetailResponse open(Long ownerId, Long surveyId) {
        Survey survey = lockRepository.lockActiveOwnedSurvey(ownerId, surveyId);
        if (survey.getStatus() == SurveyStatus.OPEN) {
            throw SurveyException.stateConflict();
        }
        List<Question> questions = questionRepository
                .findAllWithOptionsBySurveyIdOrderByPosition(survey.getId());
        if (!hasValidOpenStructure(survey, questions)) {
            throw SurveyException.invalidStructure();
        }
        survey.open(databaseTimestamp());
        surveyRepository.flush();
        return detailResponse(survey);
    }

    @Transactional
    public SurveyDetailResponse close(Long ownerId, Long surveyId) {
        Survey survey = lockRepository.lockActiveOwnedSurvey(ownerId, surveyId);
        survey.close(databaseTimestamp());
        surveyRepository.flush();
        return detailResponse(survey);
    }

    public SurveyDetailResponse duplicate(Long ownerId, Long surveyId) {
        for (int attempt = 0; attempt < MAX_GENERATED_SLUG_ATTEMPTS; attempt++) {
            try {
                Long duplicateId = duplicationAttempt.duplicate(ownerId, surveyId, attempt);
                return detail(ownerId, duplicateId);
            } catch (SurveySlugCollisionException exception) {
                // Each retry owns a fresh transaction and source snapshot.
            }
        }
        throw SurveyException.temporarilyUnavailable();
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

    private boolean hasValidOpenStructure(
            Survey survey,
            List<Question> questions) {
        String title = survey.getTitle();
        if (title == null
                || title.isBlank()
                || !title.equals(title.strip())
                || title.codePointCount(0, title.length()) > 200
                || !slugPolicy.isCanonical(survey.getSlug())
                || questions.isEmpty()) {
            return false;
        }
        for (int position = 0; position < questions.size(); position++) {
            Question question = questions.get(position);
            if (question.getPosition() != position || !question.hasCanonicalConfiguration()) {
                return false;
            }
        }
        return true;
    }

    private Instant databaseTimestamp() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
