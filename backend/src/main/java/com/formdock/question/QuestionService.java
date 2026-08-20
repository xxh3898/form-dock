package com.formdock.question;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.formdock.survey.SurveyDetailResponse;
import com.formdock.survey.SurveyService;
import com.formdock.survey.SurveyStructureGuard;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionService {

    private final SurveyStructureGuard structureGuard;
    private final QuestionRepository questionRepository;
    private final QuestionRequestParser requestParser;
    private final SurveyService surveyService;

    public QuestionService(
            SurveyStructureGuard structureGuard,
            QuestionRepository questionRepository,
            QuestionRequestParser requestParser,
            SurveyService surveyService) {
        this.structureGuard = structureGuard;
        this.questionRepository = questionRepository;
        this.requestParser = requestParser;
        this.surveyService = surveyService;
    }

    @Transactional
    public SurveyDetailResponse create(
            Long ownerId,
            Long surveyId,
            Map<String, Object> body) {
        structureGuard.requireMutable(ownerId, surveyId);
        QuestionCommand command = requestParser.parseCreate(body);
        List<Question> questions = questions(surveyId);
        questionRepository.saveAndFlush(Question.create(
                surveyId,
                questions.size(),
                command));
        return surveyService.detail(ownerId, surveyId);
    }

    @Transactional
    public SurveyDetailResponse update(
            Long ownerId,
            Long surveyId,
            Long questionId,
            Map<String, Object> body) {
        structureGuard.requireMutable(ownerId, surveyId);
        QuestionCommand command = requestParser.parseUpdate(body);
        Question question = requireQuestion(questions(surveyId), questionId);
        validateOptionIdentities(question, command);

        if (!question.getOptions().isEmpty()) {
            int temporaryOffset = Math.max(
                    question.getOptions().size(),
                    command.options().size()) + 1;
            question.temporarilyOffsetOptionPositions(temporaryOffset);
            questionRepository.flush();
        }
        question.replaceSemanticState(command);
        questionRepository.flush();
        return surveyService.detail(ownerId, surveyId);
    }

    @Transactional
    public void delete(Long ownerId, Long surveyId, Long questionId) {
        structureGuard.requireMutable(ownerId, surveyId);
        List<Question> questions = questions(surveyId);
        Question target = requireQuestion(questions, questionId);

        moveToTemporaryPositions(questions);
        questionRepository.delete(target);
        questionRepository.flush();

        int finalPosition = 0;
        for (Question question : questions) {
            if (question != target) {
                question.moveToPosition(finalPosition++);
            }
        }
        questionRepository.flush();
    }

    @Transactional
    public SurveyDetailResponse reorder(
            Long ownerId,
            Long surveyId,
            Map<String, Object> body) {
        structureGuard.requireMutable(ownerId, surveyId);
        QuestionReorderCommand command = requestParser.parseReorder(body);
        List<Question> questions = questions(surveyId);
        Map<Long, Question> questionsById = new HashMap<>();
        questions.forEach(question -> questionsById.put(question.getId(), question));

        if (command.questionIds().size() != questions.size()
                || !questionsById.keySet().equals(new HashSet<>(command.questionIds()))) {
            throw QuestionException.validation(List.of(new QuestionException.Violation(
                    "questionIds",
                    "INVALID_SET",
                    "questionIds must contain the complete current Question set once.")));
        }

        moveToTemporaryPositions(questions);
        for (int position = 0; position < command.questionIds().size(); position++) {
            questionsById.get(command.questionIds().get(position)).moveToPosition(position);
        }
        questionRepository.flush();
        return surveyService.detail(ownerId, surveyId);
    }

    private List<Question> questions(Long surveyId) {
        return questionRepository.findAllWithOptionsBySurveyIdOrderByPosition(surveyId);
    }

    private Question requireQuestion(List<Question> questions, Long questionId) {
        return questions.stream()
                .filter(question -> question.getId().equals(questionId))
                .findFirst()
                .orElseThrow(QuestionException::notFound);
    }

    private void validateOptionIdentities(Question question, QuestionCommand command) {
        Set<Long> existingIds = question.getOptions().stream()
                .map(QuestionOption::getId)
                .collect(java.util.stream.Collectors.toSet());
        List<QuestionException.Violation> violations = new java.util.ArrayList<>();
        for (int index = 0; index < command.options().size(); index++) {
            Long submittedId = command.options().get(index).id();
            if (submittedId != null && !existingIds.contains(submittedId)) {
                violations.add(new QuestionException.Violation(
                        "options[" + index + "].id",
                        "INVALID_IDENTITY",
                        "Option does not belong to the target Question."));
            }
        }
        if (!violations.isEmpty()) {
            throw QuestionException.validation(violations);
        }
    }

    private void moveToTemporaryPositions(List<Question> questions) {
        if (questions.isEmpty()) {
            return;
        }
        int temporaryOffset = questions.size() + 1;
        questions.forEach(question -> question.moveToPosition(
                question.getPosition() + temporaryOffset));
        questionRepository.flush();
    }
}
