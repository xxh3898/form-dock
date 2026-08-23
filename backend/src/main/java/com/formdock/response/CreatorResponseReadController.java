package com.formdock.response;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.formdock.auth.CreatorPrincipal;

@RestController
@RequestMapping("/api/surveys/{surveyId}/responses")
public class CreatorResponseReadController {

    private final CreatorResponseReadService responseReadService;

    public CreatorResponseReadController(CreatorResponseReadService responseReadService) {
        this.responseReadService = responseReadService;
    }

    @GetMapping
    CreatorResponsePageResponse list(
            @AuthenticationPrincipal CreatorPrincipal creator,
            @PathVariable Long surveyId,
            @RequestParam(defaultValue = CreatorResponsePageRequest.DEFAULT_PAGE) String page,
            @RequestParam(defaultValue = CreatorResponsePageRequest.DEFAULT_SIZE) String size) {
        return responseReadService.list(creator.id(), surveyId, page, size);
    }

    @GetMapping("/summary")
    CreatorResponseSummaryResponse summary(
            @AuthenticationPrincipal CreatorPrincipal creator,
            @PathVariable Long surveyId) {
        return responseReadService.summary(creator.id(), surveyId);
    }

    @GetMapping("/{responseId}")
    CreatorResponseDetailResponse detail(
            @AuthenticationPrincipal CreatorPrincipal creator,
            @PathVariable Long surveyId,
            @PathVariable Long responseId) {
        return responseReadService.detail(creator.id(), surveyId, responseId);
    }
}
