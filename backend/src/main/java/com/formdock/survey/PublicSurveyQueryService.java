package com.formdock.survey;

import java.util.List;

import com.formdock.question.Question;
import com.formdock.question.QuestionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicSurveyQueryService {

    private final SurveyRepository surveyRepository;
    private final QuestionRepository questionRepository;

    public PublicSurveyQueryService(
            SurveyRepository surveyRepository,
            QuestionRepository questionRepository) {
        this.surveyRepository = surveyRepository;
        this.questionRepository = questionRepository;
    }

    @Transactional(readOnly = true)
    public PublicSurveyResponse getBySlug(String slug) {
        Survey survey = surveyRepository
                .findBySlugAndStatusAndDeletedAtIsNull(slug, SurveyStatus.OPEN)
                .orElseThrow(SurveyException::notFound);
        List<Question> questions = questionRepository
                .findAllWithOptionsBySurveyIdOrderByPosition(survey.getId());
        return PublicSurveyResponse.from(survey, questions);
    }
}
