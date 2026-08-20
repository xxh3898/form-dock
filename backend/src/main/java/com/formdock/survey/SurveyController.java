package com.formdock.survey;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.formdock.auth.CreatorPrincipal;

@RestController
@RequestMapping("/api/surveys")
public class SurveyController {

    private final SurveyService surveyService;
    private final SurveyRequestParser requestParser;

    public SurveyController(SurveyService surveyService, SurveyRequestParser requestParser) {
        this.surveyService = surveyService;
        this.requestParser = requestParser;
    }

    @GetMapping
    List<SurveyListItemResponse> list(@AuthenticationPrincipal CreatorPrincipal creator) {
        return surveyService.list(creator.id());
    }

    @PostMapping
    ResponseEntity<SurveyDetailResponse> create(
            @AuthenticationPrincipal CreatorPrincipal creator,
            @RequestBody Map<String, Object> body) {
        SurveyDetailResponse response = surveyService.create(
                creator.id(),
                requestParser.parseCreate(body));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{surveyId}")
    SurveyDetailResponse detail(
            @AuthenticationPrincipal CreatorPrincipal creator,
            @PathVariable Long surveyId) {
        return surveyService.detail(creator.id(), surveyId);
    }

    @PatchMapping("/{surveyId}")
    SurveyDetailResponse update(
            @AuthenticationPrincipal CreatorPrincipal creator,
            @PathVariable Long surveyId,
            @RequestBody Map<String, Object> body) {
        return surveyService.update(
                creator.id(),
                surveyId,
                requestParser.parsePatch(body));
    }

    @DeleteMapping("/{surveyId}")
    ResponseEntity<Void> delete(
            @AuthenticationPrincipal CreatorPrincipal creator,
            @PathVariable Long surveyId) {
        surveyService.delete(creator.id(), surveyId);
        return ResponseEntity.noContent().build();
    }
}
