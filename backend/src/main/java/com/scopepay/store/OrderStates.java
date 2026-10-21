package com.scopepay.store;

import java.util.Map;
import java.util.Set;

/** Explicit sandbox-order state machine. Anything not listed is illegal and fails closed. */
public final class OrderStates {
  public static final String CREATING = "CREATING";
  public static final String CREATED = "CREATED";
  public static final String COMPLETED = "COMPLETED";
  public static final String PENDING_REVIEW = "PENDING_REVIEW";
  public static final String REFUNDED = "REFUNDED";
  public static final String CREATE_FAILED = "CREATE_FAILED";

  private static final Map<String, Set<String>> ALLOWED = Map.of(
      CREATING, Set.of(CREATED, CREATE_FAILED),
      CREATED, Set.of(COMPLETED, PENDING_REVIEW),
      PENDING_REVIEW, Set.of(COMPLETED),
      REFUNDED, Set.of(),
      COMPLETED, Set.of(REFUNDED),
      CREATE_FAILED, Set.of());

  private OrderStates() {}

  public static void assertTransition(String from, String to) {
    if (from == null || from.equals(to)) return; // no-op writes stay legal
    if (!ALLOWED.getOrDefault(from, Set.of()).contains(to))
      throw new IllegalStateException("Illegal order state transition: " + from + " -> " + to);
  }
}
