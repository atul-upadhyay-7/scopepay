package com.scopepay.api;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * One-time order-recovery codes for the local demo. A code is issued only to the server
 * console - the one channel nobody but the person running this machine can read - and the
 * browser must present it back. Codes are bound to one order and one browser session,
 * expire quickly, are single-use, and burn after repeated wrong attempts. Issued codes are
 * held in memory only; a server restart invalidates them.
 */
@Component
public class OrderRecoveryService {
  private static final int MAX_FAILURES = 5;
  private final SecureRandom random = new SecureRandom();
  private final Map<String, Pending> pending = new ConcurrentHashMap<>();
  private final Duration ttl;

  public OrderRecoveryService() { this(Duration.ofMinutes(5)); }

  OrderRecoveryService(Duration ttl) { this.ttl = ttl; }

  private static final class Pending {
    final String orderRequestId;
    final String sessionHash;
    final OffsetDateTime expiresAt;
    int failures;
    Pending(String orderRequestId, String sessionHash, OffsetDateTime expiresAt) {
      this.orderRequestId = orderRequestId; this.sessionHash = sessionHash; this.expiresAt = expiresAt;
    }
  }

  /** The returned code MUST only be printed to the server console, never sent to a browser. */
  public String issue(String orderRequestId, String sessionHash) {
    byte[] bytes = new byte[4];
    random.nextBytes(bytes);
    StringBuilder hex = new StringBuilder();
    for (byte b : bytes) hex.append(String.format("%02X", b));
    String code = hex.substring(0, 4) + "-" + hex.substring(4);
    pending.put(code, new Pending(orderRequestId, sessionHash, OffsetDateTime.now().plus(ttl)));
    return code;
  }

  public boolean redeem(String code, String orderRequestId, String sessionHash) {
    if (code == null) return false;
    String key = code.trim().toUpperCase(Locale.ROOT).replace(" ", "");
    if (key.length() == 8 && !key.contains("-")) key = key.substring(0, 4) + "-" + key.substring(4);
    Pending p = pending.get(key);
    if (p == null) return false;
    if (p.expiresAt.isBefore(OffsetDateTime.now())) { pending.remove(key); return false; }
    if (p.orderRequestId.equals(orderRequestId) && p.sessionHash.equals(sessionHash)) {
      pending.remove(key); // single use
      return true;
    }
    p.failures++;
    if (p.failures >= MAX_FAILURES) pending.remove(key); // burned after repeated wrong use
    return false;
  }
}
