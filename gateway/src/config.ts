export type GatewayConfig = {
  backendUrl: string;
  ingressToken: string;
  fromNumber: string;
  port: number;
  requestTimeoutMs: number;
  maxRetries: number;
  openAiModel: string;
};

function required(env: NodeJS.ProcessEnv, name: string): string {
  const value = env[name]?.trim();
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}

function integer(env: NodeJS.ProcessEnv, name: string, fallback: number,
                 minimum: number, maximum: number): number {
  const raw = env[name];
  if (raw === undefined || raw.trim() === "") {
    return fallback;
  }
  const value = Number(raw);
  if (!Number.isInteger(value) || value < minimum || value > maximum) {
    throw new Error(`${name} must be an integer between ${minimum} and ${maximum}`);
  }
  return value;
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): GatewayConfig {
  const backendUrl = required(env, "CHEMICHECK119_BACKEND_URL").replace(/\/+$/, "");
  const parsedBackendUrl = new URL(backendUrl);
  if (parsedBackendUrl.protocol !== "https:" && parsedBackendUrl.hostname !== "localhost"
      && parsedBackendUrl.hostname !== "127.0.0.1") {
    throw new Error("CHEMICHECK119_BACKEND_URL must use HTTPS outside localhost");
  }

  required(env, "CLAWOPS_API_KEY");
  required(env, "CLAWOPS_ACCOUNT_ID");
  required(env, "OPENAI_API_KEY");

  return {
    backendUrl,
    ingressToken: required(env, "CHEMICHECK119_PHONE_INGRESS_TOKEN"),
    fromNumber: required(env, "CLAWOPS_FROM_NUMBER"),
    port: integer(env, "PORT", 8080, 1, 65535),
    requestTimeoutMs: integer(env, "CHEMICHECK119_GATEWAY_REQUEST_TIMEOUT_MS",
      5000, 250, 30000),
    maxRetries: integer(env, "CHEMICHECK119_GATEWAY_MAX_RETRIES", 3, 0, 8),
    openAiModel: env.OPENAI_REALTIME_MODEL?.trim() || "gpt-realtime-2",
  };
}
