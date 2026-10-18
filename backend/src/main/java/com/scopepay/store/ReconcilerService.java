package com.scopepay.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.scopepay.config.AppConfig;
import com.scopepay.paypal.PayPalClient;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Scheduled reconciliation: re-reads every PENDING_REVIEW order from PayPal and recovers it only
 * when PayPal's own record proves a completed capture of the exact expected amount. Disagreement
 * fails closed (stays PENDING_REVIEW). PayPal errors never change state.
 */
@Service
@ConditionalOnProperty(name = "scopepay.reconciler.enabled", havingValue = "true", matchIfMissing = true)
public class ReconcilerService {
  private static final Logger log = LoggerFactory.getLogger(ReconcilerService.class);

  private final PayPalClient paypal;
  private final SandboxOrderRepository orders;
  private final LedgerService ledger;
  private final OutboxService outbox;
  private final MeterRegistry metrics;
  private final TransactionTemplate tx;

  public ReconcilerService(PayPalClient paypal, SandboxOrderRepository orders, LedgerService ledger,
      OutboxService outbox, MeterRegistry metrics,
      org.springframework.transaction.PlatformTransactionManager txManager) {
    this.paypal = paypal; this.orders = orders; this.ledger = ledger; this.outbox = outbox;
    this.metrics = metrics; this.tx = new TransactionTemplate(txManager);
  }

  @Scheduled(fixedDelayString = "${scopepay.reconciler.interval-ms:60000}", initialDelayString = "15000")
  public void reconcile() {
    metrics.counter("scopepay.reconciler.runs").increment();
    for (SandboxOrder order : orders.findByStateAndOrderIdIsNotNull("PENDING_REVIEW")) {
      try {
        tx.executeWithoutResult(s -> {
          try {
            reconcileOne(order.getRequestId());
          } catch (RuntimeException e) {
            throw e;
          } catch (Exception e) {
            throw new IllegalStateException(e);
          }
        });
      } catch (Exception e) {
        metrics.counter("scopepay.reconciler.errors").increment();
        log.warn("reconcile failed for {}: {}", order.getRequestId(), e.getMessage());
      }
    }
  }

  private void reconcileOne(String requestId) throws Exception {
    SandboxOrder order = orders.findById(requestId).orElse(null);
    if (order == null || !"PENDING_REVIEW".equals(order.getState()) || order.getOrderId() == null) return;
    JsonNode data = paypal.getOrder(order.getOrderId());
    JsonNode capture = data.path("purchase_units").path(0).path("payments").path("captures").path(0);
    boolean completed = "COMPLETED".equals(data.path("status").asText())
        && "COMPLETED".equals(capture.path("status").asText());
    if (!completed) return; // PayPal shows no completed capture yet; leave for a later run
    JsonNode amount = capture.path("amount");
    if (!AppConfig.PRICE_VALUE.equals(amount.path("value").asText())
        || !AppConfig.CURRENCY.equals(amount.path("currency_code").asText())) {
      outbox.record("order", requestId, "ORDER_PENDING_REVIEW",
          Map.of("orderId", order.getOrderId(), "reason", "RECONCILE_AMOUNT_MISMATCH"));
      metrics.counter("scopepay.reconciler.held").increment();
      return; // disagreement: fail closed
    }
    OrderStates.assertTransition(order.getState(), "COMPLETED");
    order.setState("COMPLETED");
    order.setCaptureId(capture.path("id").asText());
    order.setCompletedAt(OffsetDateTime.now());
    orders.save(order);
    String groupId = ledger.recordCapture(requestId, order.getCaptureId(),
        new BigDecimal(AppConfig.PRICE_VALUE), AppConfig.CURRENCY);
    outbox.record("order", requestId, "ORDER_COMPLETED",
        Map.of("orderId", order.getOrderId(), "captureId", order.getCaptureId(),
            "amount", AppConfig.PRICE_VALUE, "currency", AppConfig.CURRENCY,
            "ledgerGroupId", groupId, "recoveredBy", "reconciler"));
    metrics.counter("scopepay.reconciler.recovered").increment();
    log.info("reconciler recovered order {} (paypal order {})", requestId, order.getOrderId());
  }
}
