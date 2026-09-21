import { describe, expect, it } from "vitest";
import { createSdkLogger } from "../src/sdk-logger.js";

describe("SDK log privacy", () => {
  it("retains a fixed event but drops interpolated phone numbers and call IDs", () => {
    const lines: string[] = [];
    const logger = createSdkLogger("info", { write: (line) => { lines.push(line); } });
    logger.info("Incoming call: %s -> %s (%s)", "01000000000", "07000000000", "private-call-id");
    const entry = JSON.parse(lines.join(""));
    expect(entry.event).toBe("sdk_incoming_call");
    expect(lines.join("")).not.toMatch(/01000000000|07000000000|private-call-id/);
  });

  it("drops error payloads, unknown messages and nested child bindings", () => {
    const lines: string[] = [];
    const logger = createSdkLogger("debug", { write: (line) => { lines.push(line); } });
    const child = logger.child({ callId: "private-call-id", token: "private-token" })
      .child({ transcript: "private-transcript" });
    child.error({ err: new Error("private-key"), apiError: { text: "private-text" } }, "OpenAI error");
    child.warn("private-unknown-message");
    expect(lines.join("")).not.toContain("private-");
    expect(JSON.parse(lines[0]!).event).toBe("sdk_voice_provider_error");
    expect(JSON.parse(lines[1]!).event).toBe("sdk_event");
  });
});
