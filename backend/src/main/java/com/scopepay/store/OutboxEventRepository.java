package com.scopepay.store;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
  List<OutboxEvent> findByPublishedAtIsNullOrderByCreatedAtAsc();
  List<OutboxEvent> findByAggregateIdOrderByCreatedAtAsc(String aggregateId);
}
