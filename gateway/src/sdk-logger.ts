import pino, { type DestinationStream } from "pino";

const EVENTS = new Map<string, string>([
  ["ClawOpsAgent connected on %s", "sdk_connected"],
  ["Incoming call: %s -> %s (%s)", "sdk_incoming_call"],
  ["Control WS error: %s", "sdk_control_error"],
  ["Control WS reconnect failed", "sdk_reconnect_failed"],
  ["Session start failed for %s", "sdk_session_start_failed"],
  ["OpenAI error", "sdk_voice_provider_error"],
  ["OpenAI Realtime WS error", "sdk_voice_connection_error"],
]);

// SDK printf arguments, error payloads and child bindings can contain callers,
// transcripts or credentials. Pino field redaction alone cannot sanitize those.
export function createSdkLogger(level = "info", destination?: DestinationStream) {
  const options: pino.LoggerOptions = {
    level,
    base: {},
    formatters: {
      bindings: () => ({ service: "chemicheck119-clawops-gateway", component: "clawops-sdk" }),
    },
    hooks: {
      logMethod(args, method) {
        const template = args.find((value) => typeof value === "string");
        const event = typeof template === "string" ? EVENTS.get(template) : undefined;
        method.call(this, { event: event ?? "sdk_event" }, "ClawOps SDK event");
      },
    },
  };
  const logger = destination ? pino(options, destination) : pino(options);
  // Pino children otherwise append bindings without re-running the parent's
  // bindings formatter. SDK scopes do not need arbitrary per-call bindings.
  const child = logger.child.bind(logger);
  logger.child = (_bindings, options) => child({}, options);
  return logger;
}
