package com.formdock.question;

import java.util.Map;

import com.formdock.auth.CreatorPrincipal;
import com.formdock.survey.SurveyDetailResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/surveys/{surveyId}/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @PostMapping
    ResponseEntity<SurveyDetailResponse> create(
            @AuthenticationPrincipal CreatorPrincipal creator,
            @PathVariable Long surveyId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(questionService.create(creator.id(), surveyId, body));
    }

    @PatchMapping("/{questionId}")
    SurveyDetailResponse update(
            @AuthenticationPrincipal CreatorPrincipal creator,
            @PathVariable Long surveyId,
            @PathVariable Long questionId,
            @RequestBody Map<String, Object> body) {
        return questionService.update(creator.id(), surveyId, questionId, body);
    }

    @DeleteMapping("/{questionId}")
    ResponseEntity<Void> delete(
            @AuthenticationPrincipal CreatorPrincipal creator,
            @PathVariable Long surveyId,
            @PathVariable Long questionId) {
        questionService.delete(creator.id(), surveyId, questionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reorder")
    SurveyDetailResponse reorder(
            @AuthenticationPrincipal CreatorPrincipal creator,
            @PathVariable Long surveyId,
            @RequestBody Map<String, Object> body) {
        return questionService.reorder(creator.id(), surveyId, body);
    }
}
