package com.scopepay.scope;

public class ScopeException extends RuntimeException {
  private final String code;
  private final int status;
  public ScopeException(String code) { this(code, 422); }
  public ScopeException(String code, int status) { super(code); this.code = code; this.status = status; }
  public String getCode() { return code; }
  public int getStatus() { return status; }
}
