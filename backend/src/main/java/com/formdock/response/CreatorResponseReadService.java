package com.formdock.response;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.formdock.question.Question;
import com.formdock.question.QuestionOption;
import com.formdock.question.QuestionRepository;
import com.formdock.survey.SurveyRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreatorResponseReadService {

    private final SurveyRepository surveyRepository;
    private final SurveyResponseReadRepository responseReadRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;

    public CreatorResponseReadService(
            SurveyRepository surveyRepository,
            SurveyResponseReadRepository responseReadRepository,
            QuestionRepository questionRepository,
            AnswerRepository answerRepository) {
        this.surveyRepository = surveyRepository;
        this.responseReadRepository = responseReadRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
    }

    @Transactional(readOnly = true)
    public CreatorResponsePageResponse list(
            Long ownerId,
            Long surveyId,
            String rawPage,
            String rawSize) {
        requireOwnedActiveSurvey(ownerId, surveyId);
        CreatorResponsePageRequest request = CreatorResponsePageRequest.parse(rawPage, rawSize);
        long totalElements = responseReadRepository.countBySurveyId(surveyId);
        long totalPages = totalElements == 0
                ? 0
                : ((totalElements - 1) / request.size()) + 1;
        List<CreatorResponsePageResponse.Item> items = responseReadRepository
                .findPageBySurveyId(surveyId, request.size(), request.offset())
                .stream()
                .map(row -> new CreatorResponsePageResponse.Item(
                        row.responseId(),
                        row.submittedAt()))
                .toList();
        return new CreatorResponsePageResponse(
                items,
                request.page(),
                request.size(),
                totalElements,
                totalPages);
    }

    @Transactional(readOnly = true)
    public CreatorResponseDetailResponse detail(
            Long ownerId,
            Long surveyId,
            Long responseId) {
        requireOwnedActiveSurvey(ownerId, surveyId);
        SurveyResponseReadRepository.ResponseReadRow response = responseReadRepository
                .findByIdAndSurveyId(responseId, surveyId)
                .orElseThrow(CreatorResponseReadException::responseNotFound);

        List<Question> questions = questionRepository
                .findAllWithOptionsBySurveyIdOrderByPosition(surveyId);
        Map<Long, PersistedAnswer> answersByQuestion = answerRepository
                .findAllByResponseId(responseId)
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        PersistedAnswer::questionId,
                        Function.identity()));
        List<CreatorResponseDetailResponse.Question> questionResponses = questions.stream()
                .map(question -> questionResponse(
                        question,
                        answersByQuestion.get(question.getId())))
                .toList();
        return new CreatorResponseDetailResponse(
                response.responseId(),
                response.submittedAt(),
                questionResponses);
    }

    private void requireOwnedActiveSurvey(Long ownerId, Long surveyId) {
        surveyRepository
                .findByIdAndOwnerIdAndDeletedAtIsNull(surveyId, ownerId)
                .orElseThrow(CreatorResponseReadException::surveyNotFound);
    }

    private CreatorResponseDetailResponse.Question questionResponse(
            Question question,
            PersistedAnswer persistedAnswer) {
        return new CreatorResponseDetailResponse.Question(
                question.getId(),
                question.getType(),
                question.getTitle(),
                question.getDescription(),
                question.isRequired(),
                question.getPosition(),
                answerResponse(question, persistedAnswer));
    }

    private CreatorResponseDetailResponse.Answer answerResponse(
            Question question,
            PersistedAnswer persistedAnswer) {
        if (persistedAnswer == null) {
            return null;
        }
        return switch (question.getType()) {
            case SHORT_TEXT, LONG_TEXT -> new CreatorResponseDetailResponse.Answer(
                    persistedAnswer.textValue(),
                    null,
                    List.of());
            case SCALE, NUMBER -> new CreatorResponseDetailResponse.Answer(
                    null,
                    canonicalDecimal(persistedAnswer.numericValue()),
                    List.of());
            case SINGLE_CHOICE, MULTIPLE_CHOICE -> new CreatorResponseDetailResponse.Answer(
                    null,
                    null,
                    selectedOptions(question, persistedAnswer.optionIds()));
        };
    }

    private List<CreatorResponseDetailResponse.Option> selectedOptions(
            Question question,
            List<Long> selectedOptionIds) {
        Set<Long> selectedIds = Set.copyOf(selectedOptionIds);
        List<QuestionOption> selected = question.getOptions().stream()
                .filter(option -> selectedIds.contains(option.getId()))
                .toList();
        if (selected.size() != selectedIds.size()) {
            throw new IllegalStateException(
                    "Persisted Answer contains an Option outside its Question");
        }
        return selected.stream()
                .sorted(Comparator.comparingInt(QuestionOption::getPosition))
                .map(option -> new CreatorResponseDetailResponse.Option(
                        option.getId(),
                        option.getLabel(),
                        option.getPosition()))
                .toList();
    }

    private String canonicalDecimal(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.signum() == 0 ? "0" : normalized.toPlainString();
    }
}
