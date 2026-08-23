package com.formdock.response;

import com.formdock.survey.SurveyRepository;

import org.springframework.stereotype.Service;

@Service
public class PublicResponseSubmissionService {

    private final SurveyRepository surveyRepository;
    private final PublicResponseSubmissionAttempt submissionAttempt;

    public PublicResponseSubmissionService(
            SurveyRepository surveyRepository,
            PublicResponseSubmissionAttempt submissionAttempt) {
        this.surveyRepository = surveyRepository;
        this.submissionAttempt = submissionAttempt;
    }

    PublicResponseSubmissionResponse submit(
            String slug,
            PublicResponseSubmissionCommand command) {
        Long surveyId = surveyRepository.findActiveIdBySlug(slug)
                .orElseThrow(PublicResponseException::notFound);
        return submissionAttempt.submit(surveyId, command);
    }
}
