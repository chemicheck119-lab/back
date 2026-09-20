import { describe, expect, it, vi } from "vitest";
import { BackendClient, BackendRequestError } from "../src/backend-client.js";
import type { GatewayConfig } from "../src/config.js";

const config: GatewayConfig = {
  backendUrl: "https://backend.example",
  ingressToken: "secret-ingress-token",
  fromNumber: "07000000000",
  port: 8080,
  requestTimeoutMs: 1000,
  maxRetries: 1,
  openAiModel: "gpt-realtime-2",
};

describe("BackendClient", () => {
  it("retries a transient failure with the same idempotency payload", async () => {
    const request = vi.fn()
      .mockResolvedValueOnce(new Response("unavailable", { status: 503 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        requestId: "REQ-1",
        incidentId: "INC-1",
        callId: "CALL-1",
        status: "IN_CALL",
        occurredAt: "2026-09-20T08:00:00Z",
        duplicate: false,
      }), { status: 200 }));
    const client = new BackendClient(config, request as typeof fetch);

    const response = await client.startCall({
      provider: "clawops",
      callId: "CALL-1",
      eventId: "EVENT-1",
      occurredAt: "2026-09-20T08:00:00Z",
    });

    expect(response.incidentId).toBe("INC-1");
    expect(request).toHaveBeenCalledTimes(2);
    expect(request.mock.calls[0]?.[1]?.body).toBe(request.mock.calls[1]?.[1]?.body);
  });

  it("does not retry a safety conflict", async () => {
    const request = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      error: { code: "PHONE_SESSION_AMBIGUOUS" },
    }), { status: 409 }));
    const client = new BackendClient(config, request as typeof fetch);

    await expect(client.startCall({
      provider: "clawops",
      callId: "CALL-1",
      eventId: "EVENT-1",
      occurredAt: "2026-09-20T08:00:00Z",
    })).rejects.toEqual(expect.objectContaining<Partial<BackendRequestError>>({
      status: 409,
      errorCode: "PHONE_SESSION_AMBIGUOUS",
    }));
    expect(request).toHaveBeenCalledTimes(1);
  });
});
