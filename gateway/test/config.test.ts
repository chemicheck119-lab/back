import { describe, expect, it } from "vitest";
import { loadConfig } from "../src/config.js";

const env = {
  CHEMICHECK119_BACKEND_URL: "https://backend.example",
  CLAWOPS_API_KEY: "test-clawops-key",
  CLAWOPS_ACCOUNT_ID: "test-account",
  OPENAI_API_KEY: "test-openai-key",
  CLAWOPS_FROM_NUMBER: "07000000000",
};

describe("phone ingress token configuration", () => {
  it.each(["test-token", "test-token\n", "test-token\r\n", " \ttest-token\r\n "])(
    "normalizes surrounding secret whitespace without changing the token",
    (token) => {
      expect(loadConfig({ ...env, CHEMICHECK119_PHONE_INGRESS_TOKEN: token }).ingressToken)
        .toBe("test-token");
    },
  );

  it.each([undefined, "", " \r\n "])("rejects an empty token", (token) => {
    expect(() => loadConfig({ ...env, CHEMICHECK119_PHONE_INGRESS_TOKEN: token }))
      .toThrow("Missing required environment variable: CHEMICHECK119_PHONE_INGRESS_TOKEN");
  });
});
