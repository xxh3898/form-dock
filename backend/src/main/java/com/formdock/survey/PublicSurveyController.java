package com.formdock.survey;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/surveys")
public class PublicSurveyController {

    private final PublicSurveyQueryService publicSurveyQueryService;

    public PublicSurveyController(PublicSurveyQueryService publicSurveyQueryService) {
        this.publicSurveyQueryService = publicSurveyQueryService;
    }

    @GetMapping("/{slug}")
    PublicSurveyResponse getPublicSurvey(@PathVariable String slug) {
        return publicSurveyQueryService.getBySlug(slug);
    }
}
