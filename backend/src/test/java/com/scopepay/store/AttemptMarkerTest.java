package com.scopepay.store;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scopepay.api.TestStubs;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"PAYPAL_CLIENT_ID=test-public-id"})
@ActiveProfiles("test")
@Import(TestStubs.class)
class AttemptMarkerTest {
  @Autowired AttemptMarker marker;
  @Autowired RefundRequestRepository requests;
  @Autowired org.springframework.transaction.PlatformTransactionManager txManager;

  private RefundRequest draftRow() {
    RefundRequest r = new RefundRequest();
    r.setRefundRequestId("rr-marker-test-1");
    r.setOwnerHash("owner-hash");
    r.setOrderRequestId("req-marker-test");
    r.setCaptureId("CAPTURE123");
    r.setAmount(new BigDecimal("3.50"));
    r.setCurrency("USD");
    r.setReason("r");
    r.setDeliveryText("some delivery account text");
    r.setComparison(new ObjectMapper().createObjectNode());
    r.setState(RefundRequest.DRAFT);
    requests.saveAndFlush(r);
    r.markPersisted();
    return r;
  }

  @Test
  void markerCommitsIndependentlyAndSurvivesOuterRollback() {
    RefundRequest r = draftRow();
    java.time.OffsetDateTime at = java.time.OffsetDateTime.now();
    TransactionTemplate outer = new TransactionTemplate(txManager);
    outer.execute(status -> {
      assertTrue(marker.markStarted(r.getRefundRequestId(), at));
      // Read from a SEPARATE transaction while the outer one is still open: the marker must
      // already be visible - a bare flush inside the outer transaction could not do this.
      TransactionTemplate independent = new TransactionTemplate(txManager);
      independent.setPropagationBehavior(
          org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
      String seen = independent.execute(s2 ->
          requests.findById(r.getRefundRequestId()).orElseThrow().getState());
      assertEquals(RefundRequest.UNCLEAR, seen, "marker visible outside the open transaction");
      status.setRollbackOnly(); // simulate the outer work failing after the marker
      return null;
    });
    RefundRequest after = requests.findById(r.getRefundRequestId()).orElseThrow();
    assertEquals(RefundRequest.UNCLEAR, after.getState(), "marker survives outer rollback");
    assertNotNull(after.getApprovedAt(), "first-attempt time preserved");
    assertNull(after.getPaypalRefundId());
    // And a second marker call refuses: the row is no longer DRAFT.
    assertFalse(marker.markStarted(r.getRefundRequestId(), at));
    requests.delete(after);
  }
}
