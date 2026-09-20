package com.c2guard.bff.phone;

import com.c2guard.bff.common.BffRequestIdFilter;
import com.c2guard.security.BffUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/c2guard/v1/incidents/{incidentId}/phone-transcripts")
public class PhoneTranscriptReviewController {

    private final PhoneTranscriptReviewService service;

    public PhoneTranscriptReviewController(PhoneTranscriptReviewService service) {
        this.service = service;
    }

    @PutMapping(value = "/{transcriptId}/review",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public PhoneTranscriptEvent review(
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_.:-]{1,128}$") String incidentId,
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_.:-]{1,128}$") String transcriptId,
            @Valid @RequestBody PhoneTranscriptReviewRequest request,
            @AuthenticationPrincipal BffUserPrincipal principal,
            HttpServletRequest httpRequest) {
        return service.review(incidentId, transcriptId, request, principal,
                BffRequestIdFilter.current(httpRequest));
    }
}
