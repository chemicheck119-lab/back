package com.c2guard.bff.intake;

import com.c2guard.bff.common.BffContractException;
import com.c2guard.station.FireStationCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class IncidentReplayService {

    private static final Logger log = LoggerFactory.getLogger(IncidentReplayService.class);
    private final IncidentReplayProperties properties;
    private final IncidentReplayCatalog catalog;
    private final SyntheticIncidentReplayRegistry registry;
    private final ScheduledExecutorService executor;

    public IncidentReplayService(IncidentReplayProperties properties,
                                 IncidentReplayCatalog catalog,
                                 SyntheticIncidentReplayRegistry registry,
                                 ScheduledExecutorService incidentReplayExecutor) {
        this.properties = properties;
        this.catalog = catalog;
        this.registry = registry;
        this.executor = incidentReplayExecutor;
    }

    public SseEmitter open(String scenarioId, String requestId) {
        return open(scenarioId, requestId, null);
    }

    public SseEmitter open(String scenarioId, String requestId,
                           FireStationCatalog.Station station) {
        if (!properties.isEnabled()) {
            throw new BffContractException(404, "INCIDENT_REPLAY_DISABLED",
                    "공개 합성 지령 replay가 비활성화되어 있습니다.", false);
        }
        IncidentEnvelope envelope = catalog.create(scenarioId, requestId, station);
        SseEmitter emitter = new SseEmitter(properties.getTimeout().toMillis());
        AtomicReference<ScheduledFuture<?>> future = new AtomicReference<>();
        Runnable cancel = () -> {
            ScheduledFuture<?> scheduled = future.get();
            if (scheduled != null) {
                scheduled.cancel(false);
            }
        };
        emitter.onCompletion(cancel);
        emitter.onTimeout(() -> {
            cancel.run();
            emitter.complete();
        });
        emitter.onError(error -> cancel.run());

        future.set(executor.schedule(() -> send(emitter, envelope),
                properties.getDelay().toMillis(), TimeUnit.MILLISECONDS));
        return emitter;
    }

    private void send(SseEmitter emitter, IncidentEnvelope envelope) {
        try {
            emitter.send(SseEmitter.event()
                    .id(envelope.sourceEventId())
                    .name("incident.accepted")
                    .reconnectTime(5000L)
                    .data(envelope));
            registry.register(envelope);
            log.info("incident_replay_event requestId={} sourceEventId={} classification={} pii={}",
                    envelope.requestId(), envelope.sourceEventId(),
                    envelope.dataClassification(), envelope.containsPersonalInformation());
            emitter.complete();
        } catch (IOException error) {
            log.warn("incident_replay_event requestId={} status=delivery_failed type={}",
                    envelope.requestId(), error.getClass().getSimpleName());
            emitter.completeWithError(error);
        }
    }
}
