package com.scopepay.relay;

import com.scopepay.store.OutboxEvent;
import com.scopepay.store.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Optional relay: publishes committed outbox events to Kafka and marks them published. Postgres
 * stays the durable outbox; with this disabled the table is the whole contract. A Kafka failure
 * leaves events unpublished and the next run retries them, so nothing is ever lost.
 */
@Service
@ConditionalOnProperty(name = "scopepay.kafka-relay.enabled", havingValue = "true")
public class KafkaOutboxRelay {
  private static final Logger log = LoggerFactory.getLogger(KafkaOutboxRelay.class);
  public static final String TOPIC = "scopepay.outbox";

  private final OutboxEventRepository events;
  private final KafkaTemplate<String, String> kafka;
  private final MeterRegistry metrics;

  public KafkaOutboxRelay(OutboxEventRepository events, KafkaTemplate<String, String> kafka, MeterRegistry metrics) {
    this.events = events; this.kafka = kafka; this.metrics = metrics;
  }

  @Scheduled(fixedDelayString = "${scopepay.kafka-relay.interval-ms:5000}", initialDelayString = "5000")
  public void publish() {
    for (OutboxEvent e : events.findByPublishedAtIsNullOrderByCreatedAtAsc()) {
      try {
        kafka.send(TOPIC, e.getEventId(),
            "{\"eventId\":\"" + e.getEventId() + "\",\"aggregate\":\"" + e.getAggregate()
                + "\",\"aggregateId\":\"" + e.getAggregateId() + "\",\"type\":\"" + e.getType()
                + "\",\"payload\":" + e.getPayload() + "}").get(10, TimeUnit.SECONDS);
        e.setPublishedAt(OffsetDateTime.now());
        events.save(e);
        metrics.counter("scopepay.relay.published").increment();
      } catch (Exception ex) {
        metrics.counter("scopepay.relay.failed").increment();
        log.warn("outbox relay paused on {}: {}", e.getEventId(), ex.getMessage());
        return; // keep order: retry this event next run
      }
    }
  }
}
