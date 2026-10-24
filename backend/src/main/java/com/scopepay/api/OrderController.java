package com.scopepay.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.scopepay.config.AppConfig;
import com.scopepay.paypal.PayPalClient;
import com.scopepay.scope.ScopeException;
import com.scopepay.store.ClaimedOrder;
import com.scopepay.store.ClaimedOrderRepository;
import com.scopepay.store.LedgerService;
import com.scopepay.store.OrderStates;
import com.scopepay.store.OutboxService;
import com.scopepay.store.RefundRequest;
import com.scopepay.store.RefundRequestRepository;
import com.scopepay.store.SandboxOrder;
import com.scopepay.store.SandboxOrderRepository;
import com.scopepay.store.ScopeDraft;
import com.scopepay.store.ScopeDraftRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {
  private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9-]{16,80}$");
  private static final Pattern ORDER_PATTERN = Pattern.compile("^[A-Z0-9]{5,40}$");
  private static final Set<String> ALLOWED_BODY_KEYS = Set.of("sku", "scopeId");

  private final PayPalClient paypal;
  private final SandboxOrderRepository orders;
  private final ScopeDraftRepository drafts;
  private final AppConfig config;
  private final LedgerService ledger;
  private final RefundRequestRepository refundRequests;
  private final ClaimedOrderRepository claims;
  private final OrderRecoveryService recovery;
  private final OutboxService outbox;
  private final MeterRegistry metrics;

  public OrderController(PayPalClient paypal, SandboxOrderRepository orders, ScopeDraftRepository drafts,
      AppConfig config, LedgerService ledger, OutboxService outbox, MeterRegistry metrics,
      RefundRequestRepository refundRequests, ClaimedOrderRepository claims,
      OrderRecoveryService recovery) {
    this.paypal = paypal; this.orders = orders; this.drafts = drafts; this.config = config;
    this.ledger = ledger; this.outbox = outbox; this.metrics = metrics;
    this.refundRequests = refundRequests; this.claims = claims; this.recovery = recovery;
  }

  private static void transition(SandboxOrder order, String to) {
    OrderStates.assertTransition(order.getState(), to);
    order.setState(to);
  }

  @PostMapping("/api/orders")
  public ResponseEntity<Map<String, Object>> createOrder(@RequestBody(required = false) JsonNode body,
      HttpServletRequest request) throws Exception {
    if (body == null || !body.isObject()) throw new ScopeException("INVALID_PRODUCT", 400);
    body.fieldNames().forEachRemaining(k -> { if (!ALLOWED_BODY_KEYS.contains(k)) throw new ScopeException("INVALID_PRODUCT", 400); });
    if (!AppConfig.SKU.equals(body.path("sku").asText(null))) throw new ScopeException("INVALID_PRODUCT", 400);
    String owner = SessionGate.ownerHash(request);
    String scopeId = body.path("scopeId").isTextual() ? body.path("scopeId").asText() : null;
    if (config.requireReviewedScope || scopeId != null) {
      Optional<ScopeDraft> draft = scopeId == null ? Optional.empty() : drafts.findById(scopeId);
      if (draft.isEmpty() || !owner.equals(draft.get().getOwnerHash())
          || !"REVIEWED_FOR_SANDBOX".equals(draft.get().getState()))
        throw new ScopeException("REVIEWED_SCOPE_REQUIRED", 409);
    }
    String requestId = request.getHeader("idempotency-key");
    if (requestId == null || !KEY_PATTERN.matcher(requestId).matches())
      throw new ScopeException("IDEMPOTENCY_KEY_REQUIRED", 400);

    SandboxOrder reservation = new SandboxOrder();
    reservation.setRequestId(requestId);
    reservation.setOwnerHash(owner);
    reservation.setState("CREATING");
    reservation.setScopeId(scopeId);
    try {
      orders.saveAndFlush(reservation);
      reservation.markPersisted();
    } catch (DataIntegrityViolationException e) {
      SandboxOrder existing = orders.findById(requestId).orElseThrow(() -> new ScopeException("CREATE_PENDING_REVIEW", 409));
      if (!owner.equals(existing.getOwnerHash())) throw new ScopeException("REQUEST_CONFLICT", 409);
      if (existing.getOrderId() != null) {
        return ResponseEntity.ok(Map.of("orderId", existing.getOrderId()));
      }
      throw new ScopeException("CREATE_PENDING_REVIEW", 409);
    }
    JsonNode order = paypal.createOrder(requestId, AppConfig.SKU, AppConfig.PRICE_VALUE);
    String orderId = order.path("id").asText("");
    if (!ORDER_PATTERN.matcher(orderId).matches()) throw new IllegalStateException("Invalid PayPal order response");
    reservation.setOrderId(orderId);
    transition(reservation, "CREATED");
    orders.save(reservation);
    metrics.counter("scopepay.orders.placed").increment();
    outbox.record("order", requestId, "ORDER_CREATED",
        Map.of("orderId", orderId, "sku", AppConfig.SKU, "amount", AppConfig.PRICE_VALUE, "currency", AppConfig.CURRENCY));
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("orderId", orderId));
  }

  /**
   * Owner-controlled order recovery (local demo only; 404 unless ORDER_RECOVERY=true).
   * Knowing a PayPal order ID is NOT enough: step one only prints a one-time code to the
   * server's own console, which nobody but the person running this machine can read.
   */
  @PostMapping("/api/orders/{orderId}/recovery-code")
  public ResponseEntity<Map<String, Object>> issueRecoveryCode(@PathVariable String orderId, HttpServletRequest request) {
    if (!config.orderRecovery) throw new ScopeException("NOT_FOUND", 404);
    if (!ORDER_PATTERN.matcher(orderId).matches()) throw new ScopeException("INVALID_ORDER", 400);
    String owner = SessionGate.ownerHash(request);
    SandboxOrder order = orders.findByOrderId(orderId)
        .orElseThrow(() -> new ScopeException("ORDER_NOT_FOUND", 404));
    String code = recovery.issue(order.getRequestId(), owner);
    System.out.println("\n============================================================\n"
        + "ScopePay order recovery (local demo)\n"
        + "Order: " + orderId + "\n"
        + "Code:  " + code + "  (valid 5 minutes, one use)\n"
        + "Enter this code on the Receipts page to attach this order to the\n"
        + "current browser session. If you did not request it, ignore it -\n"
        + "the code expires on its own.\n"
        + "============================================================");
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("status", "RECOVERY_CODE_ON_SERVER_CONSOLE");
    out.put("expiresInSeconds", 300);
    return ResponseEntity.ok(out);
  }

  /**
   * Step two: the browser presents the console code. Ownership is never rewritten - recovery
   * is an additive, audited association (ORDER_RECOVERED on the order timeline), and a
   * session that only knows the order ID is denied.
   */
  @PostMapping("/api/orders/{orderId}/recover")
  public ResponseEntity<Map<String, Object>> recover(@PathVariable String orderId,
      @RequestBody(required = false) JsonNode body, HttpServletRequest request) {
    if (!config.orderRecovery) throw new ScopeException("NOT_FOUND", 404);
    if (!ORDER_PATTERN.matcher(orderId).matches()) throw new ScopeException("INVALID_ORDER", 400);
    if (body == null || !body.isObject() || body.size() != 1
        || !body.has("code") || !body.get("code").isTextual())
      throw new ScopeException("RECOVERY_CODE_REQUIRED", 400);
    String owner = SessionGate.ownerHash(request);
    SandboxOrder order = orders.findByOrderId(orderId)
        .orElseThrow(() -> new ScopeException("ORDER_NOT_FOUND", 404));
    if (!owner.equals(order.getOwnerHash())
        && !claims.existsByOrderRequestIdAndClaimantHash(order.getRequestId(), owner)) {
      if (!recovery.redeem(body.get("code").asText(), order.getRequestId(), owner))
        throw new ScopeException("ORDER_RECOVERY_DENIED", 403);
      try {
        claims.saveAndFlush(new ClaimedOrder(order.getRequestId(), owner));
      } catch (org.springframework.dao.DataIntegrityViolationException duplicate) {
        // A concurrent recovery already attached the order; the outcome is the same.
      }
      outbox.record("order", order.getRequestId(), "ORDER_RECOVERED", Map.of("orderId", orderId));
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("orderId", orderId);
    out.put("state", order.getState());
    out.put("recovered", true);
    return ResponseEntity.ok(out);
  }

  @PostMapping("/api/orders/{orderId}/capture")
  @Transactional
  public ResponseEntity<Map<String, Object>> capture(@PathVariable String orderId, HttpServletRequest request) throws Exception {
    if (!ORDER_PATTERN.matcher(orderId).matches()) throw new ScopeException("INVALID_ORDER", 400);
    String owner = SessionGate.ownerHash(request);
    SandboxOrder record = orders.findByOrderId(orderId)
        .filter(o -> owner.equals(o.getOwnerHash()))
        .orElseThrow(() -> new ScopeException("ORDER_NOT_FOUND", 404));
    if ("COMPLETED".equals(record.getState())) {
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("status", "COMPLETED");
      out.put("captureId", record.getCaptureId());
      return ResponseEntity.ok(out);
    }
    JsonNode data = paypal.captureOrder(orderId);
    JsonNode capture = data.path("purchase_units").path(0).path("payments").path("captures").path(0);
    if (!"COMPLETED".equals(data.path("status").asText()) || !"COMPLETED".equals(capture.path("status").asText())) {
      transition(record, "PENDING_REVIEW");
      orders.save(record);
      metrics.counter("scopepay.orders.pending_review").increment();
      outbox.record("order", record.getRequestId(), "ORDER_PENDING_REVIEW",
          Map.of("orderId", orderId, "reason", "CAPTURE_NOT_COMPLETED"));
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("error", "CAPTURE_NOT_COMPLETED");
      out.put("status", "PENDING_REVIEW");
      return ResponseEntity.status(HttpStatus.CONFLICT).body(out);
    }
    JsonNode amount = capture.path("amount");
    if (!AppConfig.PRICE_VALUE.equals(amount.path("value").asText())
        || !AppConfig.CURRENCY.equals(amount.path("currency_code").asText()))
      throw new IllegalStateException("Capture amount mismatch");
    transition(record, "COMPLETED");
    record.setCaptureId(capture.path("id").asText());
    record.setCompletedAt(OffsetDateTime.now());
    orders.save(record);
    String groupId = ledger.recordCapture(record.getRequestId(), record.getCaptureId(),
        new BigDecimal(AppConfig.PRICE_VALUE), AppConfig.CURRENCY);
    metrics.counter("scopepay.orders.completed").increment();
    outbox.record("order", record.getRequestId(), "ORDER_COMPLETED",
        Map.of("orderId", orderId, "captureId", record.getCaptureId(),
            "amount", AppConfig.PRICE_VALUE, "currency", AppConfig.CURRENCY, "ledgerGroupId", groupId));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("status", "COMPLETED");
    out.put("captureId", record.getCaptureId());
    return ResponseEntity.ok(out);
  }

  @PostMapping("/api/orders/{orderId}/refund")
  @Transactional
  public ResponseEntity<Map<String, Object>> refund(@PathVariable String orderId, HttpServletRequest request) throws Exception {
    if (!ORDER_PATTERN.matcher(orderId).matches()) throw new ScopeException("INVALID_ORDER", 400);
    String owner = SessionGate.ownerHash(request);
    // Locked for the whole check-then-refund: a make-good request created or approved
    // concurrently must not slip past the USE_MAKE_GOOD_REFUND guard below.
    SandboxOrder record = orders.findWithLockByOrderId(orderId)
        .filter(o -> owner.equals(o.getOwnerHash())
            || claims.existsByOrderRequestIdAndClaimantHash(o.getRequestId(), owner))
        .orElseThrow(() -> new ScopeException("ORDER_NOT_FOUND", 404));
    if (OrderStates.REFUNDED.equals(record.getState())) {
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("status", "REFUNDED");
      out.put("refundId", record.getRefundId());
      return ResponseEntity.ok(out);
    }
    if (!OrderStates.COMPLETED.equals(record.getState())) {
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("error", "ORDER_NOT_COMPLETED");
      out.put("status", record.getState());
      return ResponseEntity.status(HttpStatus.CONFLICT).body(out);
    }
    // A full refund with an empty body makes PayPal refund the REMAINING amount. After any
    // make-good refund exists, that would silently refund less than "full" while our ledger
    // posts a full reversal. Block the legacy path; the make-good flow owns the accounting.
    for (RefundRequest r : refundRequests.findByOrderRequestId(record.getRequestId())) {
      if (!RefundRequest.FAILED.equals(r.getState())) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("error", "USE_MAKE_GOOD_REFUND");
        out.put("status", record.getState());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(out);
      }
    }
    JsonNode data = paypal.refundCapture(record.getCaptureId());
    if (!"COMPLETED".equals(data.path("status").asText())) {
      metrics.counter("scopepay.orders.refund_failed").increment();
      outbox.record("order", record.getRequestId(), "ORDER_REFUND_FAILED",
          Map.of("orderId", orderId, "captureId", record.getCaptureId(), "reason", "REFUND_NOT_COMPLETED"));
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("error", "REFUND_NOT_COMPLETED");
      out.put("status", record.getState());
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(out);
    }
    String refundId = data.path("id").asText();
    transition(record, OrderStates.REFUNDED);
    record.setRefundId(refundId);
    orders.save(record);
    String groupId = ledger.recordRefund(record.getRequestId(), refundId,
        new BigDecimal(AppConfig.PRICE_VALUE), AppConfig.CURRENCY);
    metrics.counter("scopepay.orders.refunded").increment();
    outbox.record("order", record.getRequestId(), "ORDER_REFUNDED",
        Map.of("orderId", orderId, "captureId", record.getCaptureId(), "refundId", refundId,
            "amount", AppConfig.PRICE_VALUE, "currency", AppConfig.CURRENCY, "ledgerGroupId", groupId));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("status", "REFUNDED");
    out.put("refundId", refundId);
    return ResponseEntity.ok(out);
  }
}
