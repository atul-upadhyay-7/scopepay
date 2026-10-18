package com.scopepay.api;

import static org.junit.jupiter.api.Assertions.*;

import com.scopepay.store.OutboxEventRepository;
import com.scopepay.store.ReconcilerService;
import com.scopepay.store.SandboxOrder;
import com.scopepay.store.SandboxOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {"PAYPAL_CLIENT_ID=test-public-id", "scopepay.reconciler.enabled=true"})
@ActiveProfiles("test")
@Import(TestStubs.class)
class ReconcilerServiceTest {

  @Autowired ReconcilerService reconciler;
  @Autowired SandboxOrderRepository orders;
  @Autowired OutboxEventRepository events;
  @Autowired TestStubs.StubPayPal paypal;

  @BeforeEach
  void reset() { events.deleteAll(); orders.deleteAll(); paypal.reset(); }

  private SandboxOrder pendingReview(String requestId, String orderId) {
    SandboxOrder o = new SandboxOrder();
    o.setRequestId(requestId);
    o.setOwnerHash("owner-hash");
    o.setState("PENDING_REVIEW");
    o.setOrderId(orderId);
    return orders.save(o);
  }

  @Test
  void recoversVerifiedCompletedCapture() {
    pendingReview("reconcile-req-0000001", "ORDERABC123");
    paypal.getBehavior = TestStubs.StubPayPal::completedCapture;
    reconciler.reconcile();
    SandboxOrder after = orders.findById("reconcile-req-0000001").orElseThrow();
    assertEquals("COMPLETED", after.getState());
    assertEquals("CAPTURE123", after.getCaptureId());
    assertTrue(events.findByAggregateIdOrderByCreatedAtAsc("reconcile-req-0000001").stream()
        .anyMatch(e -> "ORDER_COMPLETED".equals(e.getType())));
  }

  @Test
  void amountMismatchStaysPendingReview() {
    pendingReview("reconcile-req-0000002", "ORDERABC124");
    paypal.getBehavior = () -> {
      var root = TestStubs.MAPPER.createObjectNode();
      root.put("status", "COMPLETED");
      var capture = root.putArray("purchase_units").addObject()
          .putObject("payments").putArray("captures").addObject();
      capture.put("id", "CAPTURE999").put("status", "COMPLETED");
      capture.putObject("amount").put("value", "0.01").put("currency_code", "USD");
      return root;
    };
    reconciler.reconcile();
    SandboxOrder after = orders.findById("reconcile-req-0000002").orElseThrow();
    assertEquals("PENDING_REVIEW", after.getState());
    assertTrue(events.findByAggregateIdOrderByCreatedAtAsc("reconcile-req-0000002").stream()
        .anyMatch(e -> "ORDER_PENDING_REVIEW".equals(e.getType())));
  }

  @Test
  void paypalFailureLeavesStateUntouched() {
    pendingReview("reconcile-req-0000003", "ORDERABC125");
    paypal.getBehavior = () -> { throw new RuntimeException("simulated outage"); };
    assertDoesNotThrow(() -> reconciler.reconcile());
    assertEquals("PENDING_REVIEW", orders.findById("reconcile-req-0000003").orElseThrow().getState());
  }

  @Test
  void notCompletedAtPayPalStaysPending() {
    pendingReview("reconcile-req-0000004", "ORDERABC126");
    // default getBehavior returns APPROVED with no captures
    reconciler.reconcile();
    assertEquals("PENDING_REVIEW", orders.findById("reconcile-req-0000004").orElseThrow().getState());
  }
}
