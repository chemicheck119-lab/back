package com.c2guard.bff.phone;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.c2guard.security.BffUserPrincipal;
import com.c2guard.security.IncidentAccessPolicy;

@RestController
@RequestMapping("/api/c2guard/v1/incidents/{incidentId}/phone-transcripts")
public class PhoneTranscriptStreamController {

    private final PhoneTranscriptEventBroker broker;
    private final IncidentAccessPolicy incidentAccessPolicy;

    public PhoneTranscriptStreamController(PhoneTranscriptEventBroker broker,
                                           IncidentAccessPolicy incidentAccessPolicy) {
        this.broker = broker;
        this.incidentAccessPolicy = incidentAccessPolicy;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_.:-]{1,128}$") String incidentId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
            @AuthenticationPrincipal BffUserPrincipal principal,
            HttpServletRequest request,
            HttpServletResponse response) {
        incidentAccessPolicy.requireAccess(principal, incidentId);
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8");
        return broker.open(incidentId, lastEventId);
    }
}
