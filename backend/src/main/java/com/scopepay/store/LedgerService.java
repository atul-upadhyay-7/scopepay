package com.scopepay.store;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Double-entry writes for sandbox money movement. Every group must net to zero per currency or the transaction rolls back. */
@Service
public class LedgerService {
  public static final String CLEARING = "paypal_sandbox_clearing";
  public static final String REVENUE = "demo_revenue";

  private final LedgerEntryRepository entries;

  public LedgerService(LedgerEntryRepository entries) { this.entries = entries; }

  @Transactional
  public String recordCapture(String requestId, String captureId, BigDecimal amount, String currency) {
    String groupId = UUID.randomUUID().toString();
    write(groupId, requestId, CLEARING, "DEBIT", amount, currency, "Sandbox capture " + captureId);
    write(groupId, requestId, REVENUE, "CREDIT", amount, currency, "Fixed sandbox demo item");
    assertBalanced(groupId);
    return groupId;
  }

  @Transactional
  public String recordRefund(String requestId, String refundId, BigDecimal amount, String currency) {
    String groupId = UUID.randomUUID().toString();
    write(groupId, requestId, REVENUE, "DEBIT", amount, currency, "Sandbox refund " + refundId);
    write(groupId, requestId, CLEARING, "CREDIT", amount, currency, "Refund reverses sandbox capture");
    assertBalanced(groupId);
    return groupId;
  }

  private void write(String groupId, String requestId, String account, String direction,
      BigDecimal amount, String currency, String memo) {
    LedgerEntry e = new LedgerEntry();
    e.setEntryId(UUID.randomUUID().toString());
    e.setGroupId(groupId);
    e.setRequestId(requestId);
    e.setAccount(account);
    e.setDirection(direction);
    e.setAmount(amount);
    e.setCurrency(currency);
    e.setMemo(memo.length() > 200 ? memo.substring(0, 200) : memo);
    entries.save(e);
  }

  public void assertBalanced(String groupId) {
    Map<String, BigDecimal> net = new HashMap<>();
    for (LedgerEntry e : entries.findByGroupId(groupId)) {
      BigDecimal signed = "DEBIT".equals(e.getDirection()) ? e.getAmount() : e.getAmount().negate();
      net.merge(e.getCurrency(), signed, BigDecimal::add);
    }
    net.forEach((currency, sum) -> {
      if (sum.compareTo(BigDecimal.ZERO) != 0)
        throw new IllegalStateException("Ledger group unbalanced for " + currency + ": " + groupId);
    });
  }

  public List<LedgerEntry> entriesFor(String requestId) {
    return entries.findByRequestIdOrderByCreatedAtAsc(requestId);
  }
}
