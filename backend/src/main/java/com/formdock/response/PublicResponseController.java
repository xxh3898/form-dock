package com.formdock.response;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/surveys")
public class PublicResponseController {

    private final PublicResponseTransportGuard transportGuard;
    private final PublicResponseRequestParser requestParser;
    private final PublicResponseSubmissionService submissionService;

    public PublicResponseController(
            PublicResponseTransportGuard transportGuard,
            PublicResponseRequestParser requestParser,
            PublicResponseSubmissionService submissionService) {
        this.transportGuard = transportGuard;
        this.requestParser = requestParser;
        this.submissionService = submissionService;
    }

    @PostMapping("/{slug}/responses")
    ResponseEntity<PublicResponseSubmissionResponse> submit(
            @PathVariable String slug,
            HttpServletRequest request) {
        byte[] body = transportGuard.admit(request);
        PublicResponseSubmissionResponse response = submissionService.submit(
                slug,
                requestParser.parse(body));
        return ResponseEntity.status(response.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(response);
    }
}
