import { BuiltinTool, ClawOpsAgent, OpenAIRealtime } from "@teamlearners/clawops/agent";
import pino from "pino";
import { BackendClient } from "./backend-client.js";
import { bindClawOpsBridge } from "./bridge.js";
import { loadConfig } from "./config.js";

const config = loadConfig();
const logger = pino({
  level: process.env.LOG_LEVEL || "info",
  redact: {
    paths: [
      "apiKey", "token", "authorization", "req.headers.authorization",
      "fromNumber", "toNumber", "text", "transcript",
    ],
    censor: "[REDACTED]",
  },
  base: { service: "chemicheck119-clawops-gateway" },
});

const session = new OpenAIRealtime({
  model: config.openAiModel,
  voice: "marin",
  language: "ko",
  greeting: true,
  transcriptionPrompt: "화학물질, CAS 번호, 저장탱크, 누출, 화재, 폭발, 자극성 냄새, 연기, 작업자 노출",
  systemPrompt: [
    "당신은 화학사고 신고 접수 보조 음성 에이전트입니다.",
    "신고자의 안전을 우선하고 위치, 사고 유형, 시설, 물질명, 라벨 또는 CAS, 누출·화재 여부, 인명 증상을 짧게 확인하세요.",
    "확인되지 않은 물질이나 위험도를 단정하지 말고, 현장 대응 지시나 화학 반응 판정을 하지 마세요.",
    "즉시 생명 위험이 있으면 안전한 거리로 이동하고 119 지시에 따르도록 안내하세요.",
    "개인식별정보는 불필요하게 묻지 마세요.",
  ].join(" "),
});

const agent = new ClawOpsAgent({
  from: config.fromNumber,
  session,
  recording: false,
  builtinTools: [BuiltinTool.HANG_UP],
  logger,
});

bindClawOpsBridge(agent, new BackendClient(config), logger);
await agent.serve({
  healthPort: config.port,
  handoverWaitMs: 20_000,
  shutdownDeadlineMs: 280_000,
});
