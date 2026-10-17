package com.scopepay.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes outbox events inside the caller's transaction, so a state change and its event commit or roll back together. */
@Service
public class OutboxService {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final OutboxEventRepository events;

  public OutboxService(OutboxEventRepository events) { this.events = events; }

  @Transactional(propagation = Propagation.REQUIRED)
  public String record(String aggregate, String aggregateId, String type, Map<String, String> fields) {
    ObjectNode payload = MAPPER.createObjectNode();
    fields.forEach(payload::put);
    OutboxEvent e = new OutboxEvent();
    e.setEventId(UUID.randomUUID().toString());
    e.setAggregate(aggregate);
    e.setAggregateId(aggregateId);
    e.setType(type);
    e.setPayload(payload);
    events.save(e);
    return e.getEventId();
  }
}
