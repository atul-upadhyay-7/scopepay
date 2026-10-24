package com.scopepay.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.scopepay.config.AppConfig;
import com.scopepay.paypal.PayPalClient;
import com.scopepay.scope.DeliveryComparator;
import com.scopepay.scope.ScopeException;
import com.scopepay.store.ClaimedOrderRepository;
import com.scopepay.store.LedgerService;
import com.scopepay.store.OutboxService;
import com.scopepay.store.RefundRequest;
import com.scopepay.store.RefundRequestRepository;
import com.scopepay.store.SandboxOrder;
import com.scopepay.store.SandboxOrderRepository;
import com.scopepay.store.ScopeDraft;
import com.scopepay.store.ScopeDraftRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Evidence-assisted make-good refunds. The AI only compares the submitted delivery account
 * against the saved scope (claim coverage, never verified performance). The human reads the
 * cited comparison, chooses the amount, and explicitly approves. The AI never sets an amount
 * and never executes anything.
 */
@RestController
public class RefundRequestController {
  private static final Pattern ORDER_PATTERN = Pattern.compile("^[A-Z0-9]{5,40}$");
  private static final Pattern AMOUNT_PATTERN = Pattern.compile("^\\d{1,6}(\\.\\d{1,2})?$");

  private final SandboxOrderRepository orders;
  private final ClaimedOrderRepository claims;
  private final ScopeDraftRepository drafts;
  private final RefundRequestRepository refundRequests;
  private final DeliveryComparator comparator;
  private final PayPalClient paypal;
  private final com.scopepay.store.AttemptMarker attemptMarker;
  private final LedgerService ledger;
  private final OutboxService outbox;

  public RefundRequestController(SandboxOrderRepository orders, ScopeDraftRepository drafts,
      RefundRequestRepository refundRequests, DeliveryComparator comparator, PayPalClient paypal,
      LedgerService ledger, OutboxService outbox, com.scopepay.store.AttemptMarker attemptMarker,
      ClaimedOrderRepository claims) {
    this.orders = orders; this.drafts = drafts; this.refundRequests = refundRequests;
    this.comparator = comparator; this.paypal = paypal; this.ledger = ledger; this.outbox = outbox;
    this.attemptMarker = attemptMarker; this.claims = claims;
  }

  private BigDecimal committed(String orderRequestId, String excludeRequestId) {
    BigDecimal sum = BigDecimal.ZERO;
    for (RefundRequest r : refundRequests.findByOrderRequestId(orderRequestId)) {
      if (r.getRefundRequestId().equals(excludeRequestId)) continue;
      // COMPLETED moved money. ANY UNCLEAR row may have moved money (a network-ambiguous
      // attempt can reach PayPal without our seeing the refund id), so every UNCLEAR row
      // reserves its amount against the cap until PayPal's own record resolves it.
      boolean counts = RefundRequest.COMPLETED.equals(r.getState())
          || RefundRequest.UNCLEAR.equals(r.getState());
      if (counts) sum = sum.add(r.getAmount());
    }
    return sum;
  }

  @PostMapping("/api/orders/{orderId}/refund-requests")
  public ResponseEntity<Map<String, Object>> create(@PathVariable String orderId,
      @RequestBody(required = false) JsonNode body, HttpServletRequest request) {
    if (!ORDER_PATTERN.matcher(orderId).matches()) throw new ScopeException("INVALID_ORDER", 400);
    if (body == null || !body.isObject() || body.size() != 3
        || !body.has("amount") || !body.has("reason") || !body.has("deliveryText")
        || !body.get("amount").isTextual() || !body.get("reason").isTextual()
        || !body.get("deliveryText").isTextual())
      throw new ScopeException("INVALID_REFUND_REQUEST", 400);
    String amountText = body.get("amount").asText();
    if (!AMOUNT_PATTERN.matcher(amountText).matches()) throw new ScopeException("INVALID_AMOUNT", 400);
    BigDecimal amount = new BigDecimal(amountText);
    if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new ScopeException("INVALID_AMOUNT", 400);
    String reason = body.get("reason").asText();
    if (reason.trim().isEmpty() || reason.length() > 500) throw new ScopeException("INVALID_REFUND_REQUEST", 400);
    String owner = SessionGate.ownerHash(request);
    SandboxOrder order = orders.findByOrderId(orderId)
        .filter(o -> owner.equals(o.getOwnerHash())
            || claims.existsByOrderRequestIdAndClaimantHash(o.getRequestId(), owner))
        .orElseThrow(() -> new ScopeException("ORDER_NOT_FOUND", 404));
    if (!"COMPLETED".equals(order.getState()))
      throw new ScopeException("ORDER_NOT_COMPLETED", 409);
    if (order.getScopeId() == null || order.getCaptureId() == null)
      throw new ScopeException("SCOPE_REQUIRED", 409);
    ScopeDraft draft = drafts.findById(order.getScopeId())
        .orElseThrow(() -> new ScopeException("SCOPE_REQUIRED", 409));
    BigDecimal remaining = new BigDecimal(AppConfig.PRICE_VALUE).subtract(committed(order.getRequestId(), null));
    if (amount.compareTo(remaining) > 0) throw new ScopeException("REFUND_EXCEEDS_REMAINING", 409);

    String deliveryText = body.get("deliveryText").asText();
    // The complaint (reason) is a cited input: the comparator quotes it verbatim, never trusts it.
    JsonNode comparison = comparator.compare(draft.getDeliverables(), deliveryText, reason);

    RefundRequest row = new RefundRequest();
    row.setRefundRequestId("rr-" + UUID.randomUUID());
    row.setOwnerHash(owner);
    row.setOrderRequestId(order.getRequestId());
    row.setCaptureId(order.getCaptureId());
    row.setAmount(amount.setScale(2));
    row.setCurrency(AppConfig.CURRENCY);
    row.setReason(reason);
    row.setDeliveryText(deliveryText);
    row.setComparison(comparison);
    row.setState(RefundRequest.DRAFT);
    refundRequests.saveAndFlush(row);
    row.markPersisted();
    outbox.record("order", order.getRequestId(), "REFUND_REQUEST_CREATED",
        Map.of("refundRequestId", row.getRefundRequestId(), "amount", row.getAmount().toPlainString(),
            "currency", row.getCurrency()));
    return ResponseEntity.status(HttpStatus.CREATED).body(view(row));
  }

  @PostMapping("/api/refund-requests/{id}/approve")
  @Transactional
  public ResponseEntity<Map<String, Object>> approve(@PathVariable String id,
      @RequestBody(required = false) JsonNode body, HttpServletRequest request) throws Exception {
    if (body == null || !body.isObject() || body.size() != 1
        || !body.has("acknowledge") || !body.get("acknowledge").asBoolean(false))
      throw new ScopeException("REVIEW_ACKNOWLEDGMENT_REQUIRED", 400);
    String owner = SessionGate.ownerHash(request);
    RefundRequest probe = refundRequests.findById(id)
        .filter(r -> owner.equals(r.getOwnerHash()))
        .orElseThrow(() -> new ScopeException("REFUND_REQUEST_NOT_FOUND", 404));
    // Lock the parent order row FIRST: every approve on this capture (and the legacy full
    // refund) serializes here, so the cap re-check below can never be passed twice at once.
    SandboxOrder order = orders.findWithLockByRequestId(probe.getOrderRequestId())
        .orElseThrow(() -> new ScopeException("ORDER_NOT_FOUND", 404));
    // Re-read under the order lock: the state may have moved between the probe and the lock.
    RefundRequest row = refundRequests.findById(id).orElseThrow();
    if (RefundRequest.COMPLETED.equals(row.getState())) return ResponseEntity.ok(view(row));
    // DRAFT rows, and UNCLEAR rows with no PayPal refund id (nothing reached PayPal as far as we
    // can prove), may (re-)send: the same PayPal-Request-Id replays one attempt, never two.
    // Only a DRAFT row may send an attempt. DRAFT means "no attempt was ever recorded": the
    // first-attempt marker is committed BEFORE the remote call, so a network-ambiguous failure
    // lands in UNCLEAR, never back in DRAFT. An UNCLEAR row without a PayPal refund id has an
    // unknown outcome; PayPal documents no guaranteed retention for replaying its
    // PayPal-Request-Id, so NO retry is permitted at any age - a second POST could double-refund.
    // The amount stays reserved; the human resolves the original attempt at PayPal.
    if (RefundRequest.UNCLEAR.equals(row.getState()) && row.getPaypalRefundId() == null) {
      outbox.record("order", row.getOrderRequestId(), "REFUND_RETRY_BLOCKED",
          Map.of("refundRequestId", row.getRefundRequestId(), "reason", "REFUND_OUTCOME_UNKNOWN"));
      throw new ScopeException("REFUND_OUTCOME_UNKNOWN", 409);
    }
    if (!RefundRequest.DRAFT.equals(row.getState()))
      throw new ScopeException("REFUND_REQUEST_NOT_ACTIONABLE", 409);
    if (!"COMPLETED".equals(order.getState())) throw new ScopeException("ORDER_NOT_COMPLETED", 409);
    // The cap is enforced again at approve time: other requests may have committed since create.
    BigDecimal committed = committed(row.getOrderRequestId(), row.getRefundRequestId());
    BigDecimal remaining = new BigDecimal(AppConfig.PRICE_VALUE).subtract(committed);
    if (row.getAmount().compareTo(remaining) > 0) throw new ScopeException("REFUND_EXCEEDS_REMAINING", 409);

    // Commit the first-attempt marker in its OWN transaction BEFORE the remote call. From
    // this instant the amount is reserved and any second approve is blocked, and a crash after
    // this point can never leave a retryable DRAFT: the attempt stays UNCLEAR and reserved
    // until a human resolves it at PayPal. (A bare flush is not a commit - that was the hole.)
    OffsetDateTime attemptAt = OffsetDateTime.now();
    if (!attemptMarker.markStarted(row.getRefundRequestId(), attemptAt))
      throw new ScopeException("REFUND_REQUEST_NOT_ACTIONABLE", 409);
    // Keep the in-memory entity in lockstep so the post-call save cannot clobber the marker.
    row.setApprovedAt(attemptAt);
    row.setState(RefundRequest.UNCLEAR);
    JsonNode data;
    try {
      data = paypal.refundCapture(row.getCaptureId(), row.getAmount().setScale(2).toPlainString(),
          row.getCurrency(), row.getRefundRequestId());
    } catch (Exception e) {
      // The attempt may or may not have reached PayPal. The marker is already committed, so
      // the outcome-unknown row stays UNCLEAR and reserved; only the outbox rides this tx.
      outbox.record("order", row.getOrderRequestId(), "REFUND_REQUEST_UNCLEAR",
          Map.of("refundRequestId", row.getRefundRequestId(), "refundId", "",
              "reason", "ATTEMPT_OUTCOME_UNKNOWN"));
      Map<String, Object> out = new LinkedHashMap<>(view(row));
      out.put("error", "REFUND_OUTCOME_UNKNOWN");
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(out);
    }
    String refundId = data.path("id").asText("");
    // Fail closed unless PayPal's own response confirms the exact amount and currency we sent.
    JsonNode remoteAmount = data.path("amount");
    boolean amountMatches = row.getAmount().setScale(2).toPlainString().equals(remoteAmount.path("value").asText())
        && row.getCurrency().equals(remoteAmount.path("currency_code").asText());
    if ("COMPLETED".equals(data.path("status").asText()) && !refundId.isEmpty() && amountMatches) {
      row.setState(RefundRequest.COMPLETED);
      row.setPaypalRefundId(refundId);
      row.setCompletedAt(OffsetDateTime.now());
      refundRequests.save(row);
      String groupId = ledger.recordRefund(row.getOrderRequestId(), refundId, row.getAmount(), row.getCurrency());
      outbox.record("order", row.getOrderRequestId(), "REFUND_REQUEST_COMPLETED",
          Map.of("refundRequestId", row.getRefundRequestId(), "refundId", refundId,
              "amount", row.getAmount().toPlainString(), "currency", row.getCurrency(),
              "ledgerGroupId", groupId));
      return ResponseEntity.ok(view(row));
    }
    row.setState(RefundRequest.UNCLEAR);
    if (!refundId.isEmpty()) row.setPaypalRefundId(refundId);
    refundRequests.save(row);
    outbox.record("order", row.getOrderRequestId(), "REFUND_REQUEST_UNCLEAR",
        Map.of("refundRequestId", row.getRefundRequestId(), "refundId", refundId,
            "reason", "COMPLETED".equals(data.path("status").asText()) ? "REFUND_AMOUNT_MISMATCH" : "REFUND_NOT_COMPLETED"));
    Map<String, Object> out = new LinkedHashMap<>(view(row));
    out.put("error", "REFUND_NOT_COMPLETED");
    return ResponseEntity.status(HttpStatus.CONFLICT).body(out);
  }

  private Map<String, Object> view(RefundRequest r) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("refundRequestId", r.getRefundRequestId());
    m.put("orderRequestId", r.getOrderRequestId());
    m.put("state", r.getState());
    m.put("amount", r.getAmount().toPlainString());
    m.put("currency", r.getCurrency());
    m.put("reason", r.getReason());
    m.put("comparison", r.getComparison());
    m.put("paypalRefundId", r.getPaypalRefundId());
    m.put("createdAt", r.getCreatedAt());
    m.put("completedAt", r.getCompletedAt());
    return m;
  }
}
