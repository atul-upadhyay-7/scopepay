package com.scopepay.api;

import com.scopepay.paypal.PayPalException;
import com.scopepay.scope.ScopeException;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Set<String> KNOWN_ISSUES = Set.of("INSTRUMENT_DECLINED", "PAYER_ACTION_REQUIRED", "PAYPAL_AUTH_FAILED");

  @ExceptionHandler(ScopeException.class)
  public ResponseEntity<Map<String, String>> scope(ScopeException e) {
    return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getCode()));
  }

  @ExceptionHandler(PayPalException.class)
  public ResponseEntity<Map<String, String>> paypal(PayPalException e) {
    if (e.getIssues().stream().anyMatch(KNOWN_ISSUES::contains)) {
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
          .body(Map.of("error", e.getIssues().stream().filter(KNOWN_ISSUES::contains).findFirst().get()));
    }
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "PAYMENT_UNCONFIRMED", "message", "Payment was not confirmed. Check the sandbox activity before trying again."));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> other(Exception e) {
    // No upstream bodies, tokens or credential-bearing messages in responses/logs.
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .body(Map.of("error", "PAYMENT_UNCONFIRMED", "message", "Payment was not confirmed. Check the sandbox activity before trying again."));
  }
}
