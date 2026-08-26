package com.formdock.export;

import java.io.OutputStream;

import com.formdock.auth.CreatorPrincipal;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/surveys/{surveyId}/responses")
public class CreatorResponseCsvExportController {

    private static final String CSV_CONTENT_TYPE = "text/csv; charset=UTF-8";

    private final CreatorResponseCsvExportService exportService;

    public CreatorResponseCsvExportController(CreatorResponseCsvExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/export.csv")
    void export(
            @AuthenticationPrincipal CreatorPrincipal creator,
            @PathVariable Long surveyId,
            HttpServletResponse response) {
        exportService.export(creator.id(), surveyId, () -> openCsvResponse(response, surveyId));
    }

    private OutputStream openCsvResponse(HttpServletResponse response, Long surveyId)
            throws java.io.IOException {
        response.setHeader(HttpHeaders.CONTENT_TYPE, CSV_CONTENT_TYPE);
        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"formdock-survey-"
                        + surveyId
                        + "-responses.csv\"");
        return response.getOutputStream();
    }
}
