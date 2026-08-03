package com.c2guard.bff.intake;

import com.c2guard.bff.common.BffRequestIdFilter;
import com.c2guard.security.BffUserPrincipal;
import com.c2guard.station.FireStationCatalog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/c2guard/v1/intake")
public class IncidentReplayController {

    private final IncidentReplayService service;
    private final FireStationCatalog stationCatalog;

    public IncidentReplayController(IncidentReplayService service,
                                    FireStationCatalog stationCatalog) {
        this.service = service;
        this.stationCatalog = stationCatalog;
    }

    @GetMapping(value = "/replay-stream/{scenarioId}",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter replay(@PathVariable String scenarioId,
                             HttpServletRequest request,
                             HttpServletResponse response,
                             Authentication authentication) {
        SseEmitter emitter = service.open(scenarioId,
                BffRequestIdFilter.current(request), authenticatedStation(authentication));
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE
                + ";charset=" + java.nio.charset.StandardCharsets.UTF_8.name());
        return emitter;
    }

    private FireStationCatalog.Station authenticatedStation(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof BffUserPrincipal principal)) {
            return null;
        }
        return stationCatalog.find(principal.organizationId()).orElse(null);
    }
}
