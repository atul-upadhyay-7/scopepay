package com.scopepay.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.scopepay.config.AppConfig;
import com.scopepay.paypal.PayPalClient;
import com.scopepay.scope.DeliveryComparator;
import com.scopepay.scope.ScopeException;
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
  private final ScopeDraftRepository drafts;
  private final RefundRequestRepository refundRequests;
  private final DeliveryComparator comparator;
  private final PayPalClient paypal;
  private final LedgerService ledger;
  private final OutboxService outbox;

  public RefundRequestController(SandboxOrderRepository orders, ScopeDraftRepository drafts,
      RefundRequestRepository refundRequests, DeliveryComparator comparator, PayPalClient paypal,
      LedgerService ledger, OutboxService outbox) {
    this.orders = orders; this.drafts = drafts; this.refundRequests = refundRequests;
    this.comparator = comparator; this.paypal = paypal; this.ledger = ledger; this.outbox = outbox;
  }

  private BigDecimal committed(String orderRequestId) {
    BigDecimal sum = BigDecimal.ZERO;
    for (RefundRequest r : refundRequests.findByOrderRequestId(orderRequestId)) {
      boolean counts = RefundRequest.COMPLETED.equals(r.getState())
          || (RefundRequest.UNCLEAR.equals(r.getState()) && r.getPaypalRefundId() != null);
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
        .filter(o -> owner.equals(o.getOwnerHash()))
        .orElseThrow(() -> new ScopeException("ORDER_NOT_FOUND", 404));
    if (!"COMPLETED".equals(order.getState()))
      throw new ScopeException("ORDER_NOT_COMPLETED", 409);
    if (order.getScopeId() == null || order.getCaptureId() == null)
      throw new ScopeException("SCOPE_REQUIRED", 409);
    ScopeDraft draft = drafts.findById(order.getScopeId())
        .orElseThrow(() -> new ScopeException("SCOPE_REQUIRED", 409));
    BigDecimal remaining = new BigDecimal(AppConfig.PRICE_VALUE).subtract(committed(order.getRequestId()));
    if (amount.compareTo(remaining) > 0) throw new ScopeException("REFUND_EXCEEDS_REMAINING", 409);

    String deliveryText = body.get("deliveryText").asText();
    JsonNode comparison = comparator.compare(draft.getDeliverables(), deliveryText);

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
    RefundRequest row = refundRequests.findById(id)
        .filter(r -> owner.equals(r.getOwnerHash()))
        .orElseThrow(() -> new ScopeException("REFUND_REQUEST_NOT_FOUND", 404));
    if (RefundRequest.COMPLETED.equals(row.getState())) return ResponseEntity.ok(view(row));
    if (!RefundRequest.DRAFT.equals(row.getState()))
      throw new ScopeException("REFUND_REQUEST_NOT_ACTIONABLE", 409);

    JsonNode data;
    try {
      data = paypal.refundCapture(row.getCaptureId(), row.getAmount().setScale(2).toPlainString(),
          row.getCurrency(), row.getRefundRequestId());
    } catch (Exception e) {
      // The attempt may or may not have reached PayPal. Retrying approve replays the SAME
      // PayPal-Request-Id, which PayPal answers with the original refund instead of a second one.
      throw e;
    }
    row.setApprovedAt(OffsetDateTime.now());
    String refundId = data.path("id").asText("");
    if ("COMPLETED".equals(data.path("status").asText()) && !refundId.isEmpty()) {
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
        Map.of("refundRequestId", row.getRefundRequestId(), "refundId", refundId));
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
