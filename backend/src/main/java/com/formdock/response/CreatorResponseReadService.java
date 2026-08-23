package com.formdock.response;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.formdock.question.Question;
import com.formdock.question.QuestionOption;
import com.formdock.question.QuestionRepository;
import com.formdock.survey.Survey;
import com.formdock.survey.SurveyRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreatorResponseReadService {

    private final SurveyRepository surveyRepository;
    private final SurveyResponseReadRepository responseReadRepository;
    private final CreatorResponseSummaryRepository summaryRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;

    public CreatorResponseReadService(
            SurveyRepository surveyRepository,
            SurveyResponseReadRepository responseReadRepository,
            CreatorResponseSummaryRepository summaryRepository,
            QuestionRepository questionRepository,
            AnswerRepository answerRepository) {
        this.surveyRepository = surveyRepository;
        this.responseReadRepository = responseReadRepository;
        this.summaryRepository = summaryRepository;
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

    @Transactional(readOnly = true)
    public CreatorResponseSummaryResponse summary(Long ownerId, Long surveyId) {
        Survey survey = requireOwnedActiveSurvey(ownerId, surveyId);
        List<Question> questions = questionRepository
                .findAllWithOptionsBySurveyIdOrderByPosition(surveyId);
        CreatorResponseSummaryRepository.Overview overview = summaryRepository
                .findOverviewBySurveyId(surveyId);
        Map<Long, Long> answeredCounts = summaryRepository
                .findAnsweredCountsBySurveyId(surveyId);
        Map<CreatorResponseSummaryRepository.ChoiceCountKey, Long> choiceCounts =
                summaryRepository.findChoiceCountsBySurveyId(surveyId);
        Map<Long, BigDecimal> scaleAverages = summaryRepository
                .findScaleAveragesBySurveyId(surveyId);
        Map<CreatorResponseSummaryRepository.ScaleBucketKey, Long> scaleDistribution =
                summaryRepository.findScaleDistributionBySurveyId(surveyId);

        List<CreatorResponseSummaryResponse.QuestionSummary> questionSummaries = questions
                .stream()
                .map(question -> summaryQuestion(
                        question,
                        answeredCounts.getOrDefault(question.getId(), 0L),
                        choiceCounts,
                        scaleAverages,
                        scaleDistribution))
                .toList();
        return new CreatorResponseSummaryResponse(
                survey.getId(),
                survey.getStatus(),
                overview.totalResponses(),
                overview.lastSubmittedAt(),
                questionSummaries.size(),
                questionSummaries);
    }

    private Survey requireOwnedActiveSurvey(Long ownerId, Long surveyId) {
        return surveyRepository
                .findByIdAndOwnerIdAndDeletedAtIsNull(surveyId, ownerId)
                .orElseThrow(CreatorResponseReadException::surveyNotFound);
    }

    private CreatorResponseSummaryResponse.QuestionSummary summaryQuestion(
            Question question,
            long answeredCount,
            Map<CreatorResponseSummaryRepository.ChoiceCountKey, Long> choiceCounts,
            Map<Long, BigDecimal> scaleAverages,
            Map<CreatorResponseSummaryRepository.ScaleBucketKey, Long> scaleDistribution) {
        return switch (question.getType()) {
            case SHORT_TEXT, LONG_TEXT, NUMBER ->
                new CreatorResponseSummaryResponse.CountQuestion(
                        question.getId(),
                        question.getType(),
                        question.getTitle(),
                        question.getPosition(),
                        answeredCount);
            case SINGLE_CHOICE, MULTIPLE_CHOICE ->
                new CreatorResponseSummaryResponse.ChoiceQuestion(
                        question.getId(),
                        question.getType(),
                        question.getTitle(),
                        question.getPosition(),
                        answeredCount,
                        question.getOptions().stream()
                                .sorted(Comparator.comparingInt(QuestionOption::getPosition))
                                .map(option -> choiceOption(
                                        question.getId(),
                                        option,
                                        answeredCount,
                                        choiceCounts))
                                .toList());
            case SCALE -> new CreatorResponseSummaryResponse.ScaleQuestion(
                    question.getId(),
                    question.getType(),
                    question.getTitle(),
                    question.getPosition(),
                    answeredCount,
                    roundedAverage(scaleAverages.get(question.getId())),
                    scaleBuckets(question, answeredCount, scaleDistribution));
        };
    }

    private CreatorResponseSummaryResponse.Option choiceOption(
            Long questionId,
            QuestionOption option,
            long answeredCount,
            Map<CreatorResponseSummaryRepository.ChoiceCountKey, Long> choiceCounts) {
        long count = choiceCounts.getOrDefault(
                new CreatorResponseSummaryRepository.ChoiceCountKey(
                        questionId,
                        option.getId()),
                0L);
        return new CreatorResponseSummaryResponse.Option(
                option.getId(),
                option.getLabel(),
                option.getPosition(),
                count,
                percentage(count, answeredCount));
    }

    private List<CreatorResponseSummaryResponse.ScaleBucket> scaleBuckets(
            Question question,
            long answeredCount,
            Map<CreatorResponseSummaryRepository.ScaleBucketKey, Long> distribution) {
        Integer minimum = question.getScaleMin();
        Integer maximum = question.getScaleMax();
        if (minimum == null || maximum == null) {
            throw new IllegalStateException("Persisted Scale Question has no configured range");
        }
        return java.util.stream.IntStream.rangeClosed(minimum, maximum)
                .mapToObj(value -> {
                    long count = distribution.getOrDefault(
                            new CreatorResponseSummaryRepository.ScaleBucketKey(
                                    question.getId(),
                                    value),
                            0L);
                    return new CreatorResponseSummaryResponse.ScaleBucket(
                            value,
                            count,
                            percentage(count, answeredCount));
                })
                .toList();
    }

    private String roundedAverage(BigDecimal average) {
        return average == null
                ? null
                : average.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String percentage(long count, long answeredCount) {
        if (answeredCount == 0) {
            return "0.00";
        }
        return BigDecimal.valueOf(count)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(answeredCount), 2, RoundingMode.HALF_UP)
                .toPlainString();
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
