package com.scopepay.relay;

import static org.junit.jupiter.api.Assertions.*;

import com.scopepay.api.TestStubs;
import com.scopepay.store.OutboxEventRepository;
import com.scopepay.store.OutboxService;
import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {"PAYPAL_CLIENT_ID=test-public-id", "scopepay.kafka-relay.enabled=true",
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"})
@ActiveProfiles("test")
@Import(TestStubs.class)
@EmbeddedKafka(topics = {"scopepay.outbox"}, partitions = 1)
class KafkaOutboxRelayTest {

  @Autowired EmbeddedKafkaBroker broker;
  @Autowired KafkaOutboxRelay relay;
  @Autowired OutboxService outbox;
  @Autowired OutboxEventRepository events;

  @BeforeEach
  void reset() { events.deleteAll(); }

  @Test
  void publishesCommittedEventsAndMarksThem() {
    String eventId = outbox.record("order", "relay-req-0000000001", "ORDER_CREATED", Map.of("orderId", "ORDERXYZ"));
    relay.publish();

    assertNotNull(events.findById(eventId).orElseThrow().getPublishedAt());

    Map<String, Object> props = KafkaTestUtils.consumerProps("relay-test-group", "true", broker);
    try (KafkaConsumer<String, String> consumer =
        new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
      broker.consumeFromAnEmbeddedTopic(consumer, "scopepay.outbox");
      ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
      assertFalse(records.isEmpty());
      var record = records.iterator().next();
      assertEquals(eventId, record.key());
      assertTrue(record.value().contains("ORDER_CREATED"));
      assertTrue(record.value().contains("ORDERXYZ"));
    }
  }
}
