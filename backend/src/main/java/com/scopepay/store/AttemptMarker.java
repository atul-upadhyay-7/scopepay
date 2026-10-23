package com.scopepay.store;

import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Commits the first-attempt marker in its own transaction BEFORE the remote refund call.
    A process crash after this point leaves the request UNCLEAR with its amount reserved -
    never a retryable DRAFT - so no second remote refund can ever be sent for the attempt. */
@Component
public class AttemptMarker {
  private final RefundRequestRepository requests;
  public AttemptMarker(RefundRequestRepository requests) { this.requests = requests; }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean markStarted(String refundRequestId, OffsetDateTime at) {
    return requests.markAttemptStarted(refundRequestId, at) == 1;
  }
}
