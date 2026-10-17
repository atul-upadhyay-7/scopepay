package com.scopepay.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Immutable double-entry ledger row. No setters are called after persist; updates and deletes are never issued by the app. */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {
  @Id
  @Column(name = "entry_id", length = 64)
  private String entryId;
  @Column(name = "group_id", length = 64, nullable = false)
  private String groupId;
  @Column(name = "request_id", length = 80, nullable = false)
  private String requestId;
  @Column(name = "account", length = 40, nullable = false)
  private String account;
  @Column(name = "direction", length = 6, nullable = false)
  private String direction;
  @Column(name = "amount", nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;
  @Column(name = "currency", length = 3, nullable = false)
  private String currency;
  @Column(name = "memo", length = 200, nullable = false)
  private String memo;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now();

  public String getEntryId() { return entryId; }
  public void setEntryId(String v) { entryId = v; }
  public String getGroupId() { return groupId; }
  public void setGroupId(String v) { groupId = v; }
  public String getRequestId() { return requestId; }
  public void setRequestId(String v) { requestId = v; }
  public String getAccount() { return account; }
  public void setAccount(String v) { account = v; }
  public String getDirection() { return direction; }
  public void setDirection(String v) { direction = v; }
  public BigDecimal getAmount() { return amount; }
  public void setAmount(BigDecimal v) { amount = v; }
  public String getCurrency() { return currency; }
  public void setCurrency(String v) { currency = v; }
  public String getMemo() { return memo; }
  public void setMemo(String v) { memo = v; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
}
