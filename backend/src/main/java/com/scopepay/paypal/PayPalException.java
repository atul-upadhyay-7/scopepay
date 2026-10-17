package com.scopepay.paypal;

import java.util.List;

public class PayPalException extends RuntimeException {
  private final int status;
  private final String issue;
  private final List<String> issues;
  public PayPalException(int status, String issue, List<String> issues) {
    super(issue); this.status = status; this.issue = issue; this.issues = issues;
  }
  public int getStatus() { return status; }
  public String getIssue() { return issue; }
  public List<String> getIssues() { return issues; }
}
