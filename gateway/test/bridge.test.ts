import { describe, expect, it } from "vitest";
import type {
  EndCallInput,
  PhoneBackend,
  StartCallInput,
  StartCallResponse,
  TranscriptInput,
} from "../src/backend-client.js";
import { bindClawOpsBridge, type AgentLike, type CallLike, type SafeLogger } from "../src/bridge.js";

class FakeAgent implements AgentLike {
  private readonly handlers = new Map<string, (...args: unknown[]) => void | Promise<void>>();

  on(event: string, handler: (...args: unknown[]) => void | Promise<void>): AgentLike {
    this.handlers.set(event, handler);
    return this;
  }

  async emit(event: string, ...args: unknown[]): Promise<void> {
    await this.handlers.get(event)?.(...args);
  }
}

class FakeBackend implements PhoneBackend {
  starts: StartCallInput[] = [];
  transcripts: Array<{ incidentId: string; input: TranscriptInput }> = [];
  ends: Array<{ callId: string; input: EndCallInput }> = [];
  failStart = false;

  async startCall(input: StartCallInput): Promise<StartCallResponse> {
    if (this.failStart) {
      throw new Error("contains-sensitive-provider-payload");
    }
    this.starts.push(input);
    return {
      requestId: "REQ-1",
      incidentId: "INC-PHONE-1",
      callId: input.callId,
      status: "IN_CALL",
      occurredAt: input.occurredAt,
      duplicate: false,
    };
  }

  async sendTranscript(incidentId: string, input: TranscriptInput): Promise<void> {
    this.transcripts.push({ incidentId, input });
  }

  async endCall(callId: string, input: EndCallInput): Promise<void> {
    this.ends.push({ callId, input });
  }
}

class CapturingLogger implements SafeLogger {
  entries: string[] = [];
  info(fields: Record<string, unknown>, message: string): void {
    this.entries.push(JSON.stringify({ fields, message }));
  }
  warn(fields: Record<string, unknown>, message: string): void {
    this.entries.push(JSON.stringify({ fields, message }));
  }
  error(fields: Record<string, unknown>, message: string): void {
    this.entries.push(JSON.stringify({ fields, message }));
  }
}

function call(): CallLike & { hangups: number } {
  return {
    callId: "CALL-1",
    startTime: new Date("2026-09-20T08:00:00Z"),
    endedStatus: "completed",
    hangups: 0,
    async hangup() {
      this.hangups += 1;
    },
  };
}

describe("ClawOps bridge", () => {
  it("delivers interim user utterances and one review-pending final transcript", async () => {
    const agent = new FakeAgent();
    const backend = new FakeBackend();
    const logger = new CapturingLogger();
    const activeCall = call();
    bindClawOpsBridge(agent, backend, logger,
      () => new Date("2026-09-20T08:01:00Z"));

    await agent.emit("call_start", activeCall);
    await agent.emit("transcript", activeCall, "assistant", "어떤 사고인가요?");
    await agent.emit("transcript", activeCall, "user", "염소 탱크에서 냄새가 납니다.");
    await agent.emit("transcript", activeCall, "user", "작업자 한 명이 어지럽습니다.");
    await agent.emit("call_end", activeCall);

    expect(backend.starts).toHaveLength(1);
    expect(backend.transcripts).toHaveLength(3);
    expect(backend.transcripts[0]?.input.isFinal).toBe(false);
    expect(backend.transcripts[1]?.input.isFinal).toBe(false);
    expect(backend.transcripts[2]?.input).toMatchObject({
      isFinal: true,
      text: "염소 탱크에서 냄새가 납니다. 작업자 한 명이 어지럽습니다.",
      segmentIndex: 3,
    });
    expect(backend.ends).toHaveLength(1);
    expect(backend.ends[0]?.input.status).toBe("completed");
  });

  it("fails closed and hangs up when no incident can be safely claimed", async () => {
    const agent = new FakeAgent();
    const backend = new FakeBackend();
    backend.failStart = true;
    const logger = new CapturingLogger();
    const activeCall = call();
    bindClawOpsBridge(agent, backend, logger);

    await agent.emit("call_start", activeCall);
    await agent.emit("transcript", activeCall, "user", "010-1234-5678 secret transcript");

    expect(activeCall.hangups).toBe(1);
    expect(backend.transcripts).toHaveLength(0);
    expect(logger.entries.join(" ")).not.toContain("010-1234-5678");
    expect(logger.entries.join(" ")).not.toContain("secret transcript");
    expect(logger.entries.join(" ")).not.toContain("contains-sensitive-provider-payload");
  });
});
