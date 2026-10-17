package com.c2guard.bff.phone;

import com.c2guard.security.BffRole;
import com.c2guard.security.SignedSessionTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"chemicheck119.phone-stream.max-connections=1", "chemicheck119.phone-stream.max-per-incident=1", "chemicheck119.phone-ingress.enabled=true", "chemicheck119.phone-ingress.token=local-synthetic-only"})
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PhoneStreamHttpTest {
    @LocalServerPort int port;
    @Autowired PhoneTranscriptEventBroker broker;
    @Autowired SignedSessionTokenService tokens;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test void realHttpSyntheticCallToSseReviewAndDatabaseHistory() throws Exception {
        var client = HttpClient.newHttpClient();
        String initial = "CHEMICHECK119_SESSION=" + tokens.issue("flow-user", "flow-station",
                Set.of(BffRole.RESPONDER), Set.of("*"));
        var created = send(client, "/api/c2guard/v1/phone-sessions", "POST", "{}", "Cookie", initial);
        assertThat(created.statusCode()).isEqualTo(200);
        String incident = mapper.readTree(created.body()).path("incidentId").asText();
        assertThat(incident).startsWith("INC-PHONE-");
        String cookie = "CHEMICHECK119_SESSION=" + tokens.issue("flow-user", "flow-station",
                Set.of(BffRole.RESPONDER), Set.of(incident));
        String timestamp = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString();
        String start = "{\"provider\":\"clawops\",\"callId\":\"CALL-HTTP-FLOW\",\"eventId\":\"START-HTTP-FLOW\",\"occurredAt\":\"" + timestamp + "\"}";
        assertThat(send(client, "/api/c2guard/v1/phone-provider/calls/start", "POST", start,
                "X-Phone-Ingress-Token", "local-synthetic-only").statusCode()).isEqualTo(200);
        String body = "{\"provider\":\"clawops\",\"callId\":\"CALL-HTTP-FLOW\",\"eventId\":\"FINAL-HTTP-FLOW\",\"occurredAt\":\"" + timestamp + "\",\"text\":\"합성 신고 원문\",\"language\":\"ko\",\"isFinal\":true,\"segmentIndex\":1}";
        String path = "/api/c2guard/v1/incidents/" + incident + "/phone-transcripts";
        var ingested = send(client, path, "POST", body, "X-Phone-Ingress-Token", "local-synthetic-only");
        assertThat(ingested.statusCode()).isEqualTo(200);
        String transcript = mapper.readTree(ingested.body()).path("transcriptId").asText();
        assertThat(send(client, path, "POST", body, "X-Phone-Ingress-Token", "local-synthetic-only").statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM incident_phone_transcripts WHERE incident_id=?", Integer.class, incident)).isEqualTo(1);
        var review = send(client, path + "/" + transcript + "/review", "PUT",
                "{\"text\":\"담당자가 수정한 합성 신고\",\"expectedRevision\":0}", "Cookie", cookie);
        assertThat(review.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(review.body()).path("revision").asInt()).isEqualTo(1);
        var replay = client.send(HttpRequest.newBuilder(uri(path + "/stream")).header("Cookie", cookie).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        assertThat(replay.statusCode()).isEqualTo(200);
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(replay.body(), java.nio.charset.StandardCharsets.UTF_8))) {
            boolean found = false;
            for (int line = 0; line < 20; line++) {
                String value = reader.readLine();
                if (value == null) break;
                if (value.contains("담당자가 수정한 합성 신고")) { found = true; break; }
            }
            assertThat(found).isTrue();
        }
        assertThat(send(client, path + "/" + transcript + "/review", "PUT",
                "{\"text\":\"오래된 수정\",\"expectedRevision\":0}", "Cookie", cookie).statusCode()).isEqualTo(409);
    }

    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
    private HttpResponse<String> send(HttpClient client, String path, String method, String body,
                                      String header, String value) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(8))
            .header(header, value).header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void realHttpCapacityStopsReconnectionWithoutBypassingAuthorization() throws Exception {
        var held = broker.open("INC-OWN", null);
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        try {
            var path = URI.create("http://127.0.0.1:" + port + "/api/c2guard/v1/incidents/INC-OWN/phone-transcripts/stream");
            var cookie = "CHEMICHECK119_SESSION=" + tokens.issue("synthetic-user", "synthetic-station",
                    Set.of(BffRole.RESPONDER), Set.of("INC-OWN"));
            var limited = client.send(HttpRequest.newBuilder(path).header("Cookie", cookie).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(limited.statusCode()).isEqualTo(204);
            assertThat(limited.body()).isEmpty();
            assertThat(limited.headers().firstValue("Cache-Control")).contains("no-store");
            var anonymous = client.send(HttpRequest.newBuilder(path).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(anonymous.statusCode()).isEqualTo(401);
            var foreign = URI.create(path.toString().replace("INC-OWN", "INC-OTHER"));
            var forbidden = client.send(HttpRequest.newBuilder(foreign).header("Cookie", cookie).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(forbidden.statusCode()).isEqualTo(403);
            assertThat(broker.activeConnectionCount()).isEqualTo(1);
        } finally { held.complete(); }
    }
}
