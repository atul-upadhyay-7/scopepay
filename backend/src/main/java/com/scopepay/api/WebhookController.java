package com.scopepay.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebhookController {
  private final WebhookService webhooks;

  public WebhookController(WebhookService webhooks) { this.webhooks = webhooks; }

  @PostMapping("/api/webhooks/paypal")
  public ResponseEntity<Map<String, Object>> receive(@RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    Map<String, String> transmission = new LinkedHashMap<>();
    request.getHeaderNames().asIterator().forEachRemaining(name -> {
      String lower = name.toLowerCase(Locale.ROOT);
      if (lower.startsWith("paypal-")) transmission.put(lower, request.getHeader(name));
    });
    WebhookService.Result result = webhooks.process(transmission, body);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("status", result.code());
    return ResponseEntity.status(result.status()).body(out);
  }
}
