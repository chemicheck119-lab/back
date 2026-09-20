import type { GatewayConfig } from "./config.js";

export type StartCallInput = {
  provider: "clawops";
  callId: string;
  eventId: string;
  occurredAt: string;
};

export type TranscriptInput = {
  provider: "clawops";
  callId: string;
  eventId: string;
  occurredAt: string;
  text: string;
  language: "ko";
  isFinal: boolean;
  segmentIndex: number;
};

export type EndCallInput = {
  provider: "clawops";
  eventId: string;
  occurredAt: string;
  status: "completed" | "failed" | "canceled" | "rejected" | "busy" | "no-answer" | "unknown";
};

export type StartCallResponse = {
  requestId: string;
  incidentId: string;
  callId: string;
  status: string;
  occurredAt: string;
  duplicate: boolean;
};

export interface PhoneBackend {
  startCall(input: StartCallInput): Promise<StartCallResponse>;
  sendTranscript(incidentId: string, input: TranscriptInput): Promise<void>;
  endCall(callId: string, input: EndCallInput): Promise<void>;
}

export class BackendRequestError extends Error {
  constructor(readonly status: number | null, readonly errorCode: string) {
    super(`Backend request failed: ${errorCode}`);
    this.name = "BackendRequestError";
  }
}

type Fetch = typeof fetch;

export class BackendClient implements PhoneBackend {
  constructor(private readonly config: GatewayConfig,
              private readonly request: Fetch = fetch) {}

  async startCall(input: StartCallInput): Promise<StartCallResponse> {
    return this.post<StartCallResponse>("/api/c2guard/v1/phone-provider/calls/start", input);
  }

  async sendTranscript(incidentId: string, input: TranscriptInput): Promise<void> {
    await this.post(`/api/c2guard/v1/incidents/${encodeURIComponent(incidentId)}/phone-transcripts`, input);
  }

  async endCall(callId: string, input: EndCallInput): Promise<void> {
    await this.post(`/api/c2guard/v1/phone-provider/calls/${encodeURIComponent(callId)}/end`, input);
  }

  private async post<T = unknown>(path: string, body: object): Promise<T> {
    let lastError: unknown;
    for (let attempt = 0; attempt <= this.config.maxRetries; attempt += 1) {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), this.config.requestTimeoutMs);
      try {
        const response = await this.request(`${this.config.backendUrl}${path}`, {
          method: "POST",
          headers: {
            "content-type": "application/json",
            "x-phone-ingress-token": this.config.ingressToken,
          },
          body: JSON.stringify(body),
          signal: controller.signal,
        });
        if (response.ok) {
          const text = await response.text();
          return (text ? JSON.parse(text) : undefined) as T;
        }
        const errorCode = await safeErrorCode(response);
        const error = new BackendRequestError(response.status, errorCode);
        if (response.status < 500 && response.status !== 429) {
          throw error;
        }
        lastError = error;
      } catch (error) {
        if (error instanceof BackendRequestError && error.status !== null
            && error.status < 500 && error.status !== 429) {
          throw error;
        }
        lastError = error;
      } finally {
        clearTimeout(timeout);
      }
      if (attempt < this.config.maxRetries) {
        await new Promise((resolve) => setTimeout(resolve, 100 * 2 ** attempt));
      }
    }
    if (lastError instanceof BackendRequestError) {
      throw lastError;
    }
    throw new BackendRequestError(null, "BACKEND_UNREACHABLE");
  }
}

async function safeErrorCode(response: Response): Promise<string> {
  try {
    const body = await response.json() as { error?: { code?: unknown } };
    return typeof body.error?.code === "string" ? body.error.code : "BACKEND_REJECTED";
  } catch {
    return "BACKEND_REJECTED";
  }
}
