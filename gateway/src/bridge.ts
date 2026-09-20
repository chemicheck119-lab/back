import { createHash } from "node:crypto";
import type { PhoneBackend } from "./backend-client.js";

type AgentEvent = "call_start" | "call_end" | "call_failed" | "transcript";

export type CallLike = {
  callId: string;
  startTime: Date;
  endedStatus: string | null;
  hangup(): Promise<void>;
};

export type AgentLike = {
  on(event: AgentEvent, handler: (...args: unknown[]) => void | Promise<void>): AgentLike;
};

export type SafeLogger = {
  info(fields: Record<string, unknown>, message: string): void;
  warn(fields: Record<string, unknown>, message: string): void;
  error(fields: Record<string, unknown>, message: string): void;
};

type CallState = {
  incidentId: string;
  userUtterances: string[];
  segmentIndex: number;
};

const END_STATUSES = new Set([
  "completed", "failed", "canceled", "rejected", "busy", "no-answer",
]);

export function bindClawOpsBridge(agent: AgentLike, backend: PhoneBackend,
                                  logger: SafeLogger,
                                  now: () => Date = () => new Date()): void {
  const calls = new Map<string, CallState>();
  const queues = new Map<string, Promise<void>>();

  const enqueue = (callId: string, work: () => Promise<void>): Promise<void> => {
    const previous = queues.get(callId) ?? Promise.resolve();
    const current = previous.catch(() => undefined).then(work);
    queues.set(callId, current);
    return current.finally(() => {
      if (queues.get(callId) === current) {
        queues.delete(callId);
      }
    });
  };

  agent.on("call_start", (...args: unknown[]) => {
    const call = args[0] as CallLike;
    return enqueue(call.callId, async () => {
      try {
        const occurredAt = call.startTime.toISOString();
        const bound = await backend.startCall({
          provider: "clawops",
          callId: call.callId,
          eventId: eventId("start", call.callId, occurredAt),
          occurredAt,
        });
        calls.set(call.callId, {
          incidentId: bound.incidentId,
          userUtterances: [],
          segmentIndex: 0,
        });
        logger.info({ event: "call_bound", result: "accepted" },
          "ClawOps call bound to a waiting incident");
      } catch (error) {
        logger.error({ event: "call_bind", result: "rejected", code: errorCode(error) },
          "ClawOps call could not be safely bound");
        await call.hangup();
      }
    });
  });

  agent.on("transcript", (...args: unknown[]) => {
    const call = args[0] as CallLike;
    const role = String(args[1] ?? "");
    const text = String(args[2] ?? "").trim();
    if (role !== "user" || text === "") {
      return Promise.resolve();
    }
    return enqueue(call.callId, async () => {
      const state = calls.get(call.callId);
      if (!state) {
        logger.warn({ event: "transcript", result: "ignored", code: "CALL_NOT_BOUND" },
          "Transcript ignored because the call has no incident binding");
        return;
      }
      const segmentIndex = state.segmentIndex + 1;
      const occurredAt = now().toISOString();
      await backend.sendTranscript(state.incidentId, {
        provider: "clawops",
        callId: call.callId,
        eventId: eventId("utterance", call.callId, String(segmentIndex), text),
        occurredAt,
        text,
        language: "ko",
        isFinal: false,
        segmentIndex,
      });
      state.segmentIndex = segmentIndex;
      state.userUtterances.push(text);
      logger.info({ event: "transcript", result: "accepted", segmentIndex },
        "User utterance delivered as an interim transcript");
    }).catch((error: unknown) => {
      logger.error({ event: "transcript", result: "failed", code: errorCode(error) },
        "Interim transcript delivery failed");
    });
  });

  agent.on("call_end", (...args: unknown[]) => {
    const call = args[0] as CallLike;
    return enqueue(call.callId, async () => {
      const state = calls.get(call.callId);
      if (!state) {
        logger.warn({ event: "call_end", result: "ignored", code: "CALL_NOT_BOUND" },
          "Call end ignored because the call has no incident binding");
        return;
      }
      const endedAt = now().toISOString();
      const finalText = state.userUtterances.join(" ").trim();
      if (finalText !== "") {
        const finalIndex = state.segmentIndex + 1;
        await backend.sendTranscript(state.incidentId, {
          provider: "clawops",
          callId: call.callId,
          eventId: eventId("final", call.callId, finalText),
          occurredAt: endedAt,
          text: finalText,
          language: "ko",
          isFinal: true,
          segmentIndex: finalIndex,
        });
      }
      const status = normalizeEndStatus(call.endedStatus);
      await backend.endCall(call.callId, {
        provider: "clawops",
        eventId: eventId("end", call.callId, endedAt, status),
        occurredAt: endedAt,
        status,
      });
      calls.delete(call.callId);
      logger.info({ event: "call_end", result: "accepted", transcriptPresent: finalText !== "" },
        "ClawOps call end delivered");
    }).catch((error: unknown) => {
      logger.error({ event: "call_end", result: "failed", code: errorCode(error) },
        "Call end delivery failed");
    });
  });

  agent.on("call_failed", (...args: unknown[]) => {
    const reason = normalizeEndStatus(String(args[1] ?? "unknown"));
    logger.warn({ event: "call_failed", result: "not_connected", reason },
      "ClawOps reported a call that never connected");
  });
}

function eventId(kind: string, ...parts: string[]): string {
  const hash = createHash("sha256").update(parts.join("\u001f")).digest("hex");
  return `clawops:${kind}:${hash}`;
}

function normalizeEndStatus(value: string | null):
  "completed" | "failed" | "canceled" | "rejected" | "busy" | "no-answer" | "unknown" {
  return value !== null && END_STATUSES.has(value)
    ? value as "completed" | "failed" | "canceled" | "rejected" | "busy" | "no-answer"
    : "unknown";
}

function errorCode(error: unknown): string {
  if (typeof error === "object" && error !== null && "errorCode" in error
      && typeof (error as { errorCode?: unknown }).errorCode === "string") {
    return (error as { errorCode: string }).errorCode;
  }
  return "DELIVERY_FAILED";
}
