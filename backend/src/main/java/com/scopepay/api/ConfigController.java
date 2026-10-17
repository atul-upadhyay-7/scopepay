package com.scopepay.api;

import com.scopepay.config.AppConfig;
import com.scopepay.store.SandboxOrder;
import com.scopepay.store.SandboxOrderRepository;
import com.scopepay.store.ScopeDraft;
import com.scopepay.scope.ScopeProvider;
import com.scopepay.store.LedgerEntry;
import com.scopepay.store.LedgerEntryRepository;
import com.scopepay.store.OutboxEvent;
import com.scopepay.store.OutboxEventRepository;
import com.scopepay.store.ScopeDraftRepository;
import java.util.Optional;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConfigController {
  private final AppConfig config;
  private final Optional<ScopeProvider> scopeProvider;
  private final ScopeDraftRepository drafts;
  private final SandboxOrderRepository orders;
  private final OutboxEventRepository outboxEvents;
  private final LedgerEntryRepository ledgerEntries;

  public ConfigController(AppConfig config, Optional<ScopeProvider> scopeProvider, ScopeDraftRepository drafts,
      SandboxOrderRepository orders, OutboxEventRepository outboxEvents, LedgerEntryRepository ledgerEntries) {
    this.config = config; this.scopeProvider = scopeProvider; this.drafts = drafts; this.orders = orders;
    this.outboxEvents = outboxEvents; this.ledgerEntries = ledgerEntries;
  }

  @GetMapping("/api/health")
  public Map<String, Object> health() {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("status", "ok");
    out.put("environment", "sandbox");
    out.put("database", "connected");
    return out;
  }

  @GetMapping("/api/config")
  public Map<String, Object> config() {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("clientId", config.paypalClientId);
    out.put("currency", AppConfig.CURRENCY);
    out.put("price", AppConfig.PRICE_VALUE);
    out.put("sku", AppConfig.SKU);
    out.put("environment", "sandbox");
    out.put("aiConfigured", scopeProvider.isPresent());
    out.put("syntheticOnly", true);
    out.put("demoTools", config.demoTools);
    return out;
  }

  @GetMapping("/api/receipts")
  public Map<String, Object> receipts(HttpServletRequest request) {
    String owner = SessionGate.ownerHash(request);
    List<Map<String, Object>> draftList = drafts.findTop20ByOwnerHashOrderByCreatedAtDesc(owner).stream()
        .map(d -> {
          Map<String, Object> m = new LinkedHashMap<String, Object>();
          m.put("scopeId", d.getScopeId());
          m.put("state", d.getState());
          m.put("summary", d.getSummary());
          m.put("deliverables", d.getDeliverables());
          m.put("questions", d.getQuestions());
          m.put("conflicts", d.getConflicts());
          m.put("createdAt", d.getCreatedAt());
          m.put("reviewedAt", d.getReviewedAt());
          return m;
        }).toList();
    List<Map<String, Object>> orderList = orders.findTop20ByOwnerHashOrderByCreatedAtDesc(owner).stream()
        .map(o -> {
          Map<String, Object> m = new LinkedHashMap<String, Object>();
          m.put("requestId", o.getRequestId());
          m.put("orderId", o.getOrderId());
          m.put("state", o.getState());
          m.put("scopeId", o.getScopeId());
          m.put("captureId", o.getCaptureId());
          m.put("createdAt", o.getCreatedAt());
          m.put("completedAt", o.getCompletedAt());
          m.put("timeline", outboxEvents.findByAggregateIdOrderByCreatedAtAsc(o.getRequestId()).stream()
              .filter(e -> "order".equals(e.getAggregate()))
              .map(e -> {
                Map<String, Object> ev = new LinkedHashMap<String, Object>();
                ev.put("type", e.getType());
                ev.put("at", e.getCreatedAt());
                ev.put("payload", e.getPayload());
                return ev;
              }).toList());
          m.put("ledger", ledgerEntries.findByRequestIdOrderByCreatedAtAsc(o.getRequestId()).stream()
              .map(e -> {
                Map<String, Object> le = new LinkedHashMap<String, Object>();
                le.put("account", e.getAccount());
                le.put("direction", e.getDirection());
                le.put("amount", e.getAmount());
                le.put("currency", e.getCurrency());
                le.put("memo", e.getMemo());
                return le;
              }).toList());
          return m;
        }).toList();
    return Map.of("drafts", draftList, "orders", orderList);
  }
}
