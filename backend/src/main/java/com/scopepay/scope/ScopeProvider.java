package com.scopepay.scope;

public interface ScopeProvider {
  GenerateResult generate(String instructions, String brief, String schemaJson) throws Exception;
}
