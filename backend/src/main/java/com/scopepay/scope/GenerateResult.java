package com.scopepay.scope;

import com.fasterxml.jackson.databind.JsonNode;

public record GenerateResult(JsonNode data, boolean refusal) {
  public static GenerateResult ofData(JsonNode data) { return new GenerateResult(data, false); }
  public static GenerateResult ofRefusal() { return new GenerateResult(null, true); }
}
