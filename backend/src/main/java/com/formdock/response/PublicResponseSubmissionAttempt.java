package com.formdock.response;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.formdock.question.Question;
import com.formdock.question.QuestionRepository;
import com.formdock.survey.Survey;
import com.formdock.survey.SurveyException;
import com.formdock.survey.SurveyStatus;
import com.formdock.survey.SurveyStructureLockRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicResponseSubmissionAttempt {

    private final SurveyStructureLockRepository lockRepository;
    private final QuestionRepository questionRepository;
    private final SurveyResponseRepository responseRepository;
    private final AnswerRepository answerRepository;
    private final PublicResponseValidator validator;
    private final ResponsePayloadCanonicalizer canonicalizer;

    public PublicResponseSubmissionAttempt(
            SurveyStructureLockRepository lockRepository,
            QuestionRepository questionRepository,
            SurveyResponseRepository responseRepository,
            AnswerRepository answerRepository,
            PublicResponseValidator validator,
            ResponsePayloadCanonicalizer canonicalizer) {
        this.lockRepository = lockRepository;
        this.questionRepository = questionRepository;
        this.responseRepository = responseRepository;
        this.answerRepository = answerRepository;
        this.validator = validator;
        this.canonicalizer = canonicalizer;
    }

    @Transactional
    public PublicResponseSubmissionResponse submit(
            Long surveyId,
            PublicResponseSubmissionCommand command) {
        Survey survey = lockSurvey(surveyId);
        if (survey.getDeletedAt() != null || survey.getStatus() == SurveyStatus.DRAFT) {
            throw PublicResponseException.notFound();
        }

        SurveyResponse existing = responseRepository
                .findBySurveyIdAndClientSubmissionId(
                        survey.getId(),
                        command.clientSubmissionId())
                .orElse(null);
        if (existing != null) {
            ValidatedSubmission validated = validateAndCanonicalize(survey, command);
            return replay(existing, validated.payload().sha256());
        }

        if (survey.getStatus() != SurveyStatus.OPEN) {
            throw PublicResponseException.notOpen();
        }
        ValidatedSubmission validated = validateAndCanonicalize(survey, command);
        validator.requireComplete(validated.questions(), validated.payload().answers());

        Instant submittedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        SurveyResponseResolution resolution = responseRepository.createOrResolve(
                survey.getId(),
                command.clientSubmissionId(),
                validated.payload().sha256(),
                submittedAt);
        return switch (resolution.outcome()) {
            case CREATED -> {
                answerRepository.insertAll(
                        resolution.response().id(),
                        validated.payload().answers(),
                        resolution.response().submittedAt());
                yield PublicResponseSubmissionResponse.created(resolution.response());
            }
            case EXISTING_SAME_PAYLOAD ->
                    PublicResponseSubmissionResponse.replayed(resolution.response());
            case EXISTING_DIFFERENT_PAYLOAD -> throw PublicResponseException.duplicateConflict();
        };
    }

    private ValidatedSubmission validateAndCanonicalize(
            Survey survey,
            PublicResponseSubmissionCommand command) {
        List<Question> questions = questionRepository
                .findAllWithOptionsBySurveyIdOrderByPosition(survey.getId());
        List<CanonicalAnswer> semanticAnswers = validator.validateAnswers(
                questions,
                command.answers());
        return new ValidatedSubmission(
                questions,
                canonicalizer.canonicalize(semanticAnswers));
    }

    private Survey lockSurvey(Long surveyId) {
        try {
            return lockRepository.lockSurveyForPublicSubmission(surveyId);
        } catch (SurveyException exception) {
            if (exception.kind() == SurveyException.Kind.NOT_FOUND) {
                throw PublicResponseException.notFound();
            }
            if (exception.kind() == SurveyException.Kind.TEMPORARILY_UNAVAILABLE) {
                throw PublicResponseException.temporarilyUnavailable();
            }
            throw exception;
        }
    }

    private PublicResponseSubmissionResponse replay(
            SurveyResponse existing,
            String payloadHash) {
        if (!existing.payloadHash().equals(payloadHash)) {
            throw PublicResponseException.duplicateConflict();
        }
        return PublicResponseSubmissionResponse.replayed(existing);
    }

    private record ValidatedSubmission(
            List<Question> questions,
            CanonicalResponsePayload payload) {
    }
}
