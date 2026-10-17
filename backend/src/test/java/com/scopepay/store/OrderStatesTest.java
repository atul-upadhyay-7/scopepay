package com.scopepay.store;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OrderStatesTest {

  @Test
  void happyPathTransitionsAreLegal() {
    assertDoesNotThrow(() -> OrderStates.assertTransition("CREATING", "CREATED"));
    assertDoesNotThrow(() -> OrderStates.assertTransition("CREATED", "COMPLETED"));
    assertDoesNotThrow(() -> OrderStates.assertTransition("CREATED", "PENDING_REVIEW"));
    assertDoesNotThrow(() -> OrderStates.assertTransition("PENDING_REVIEW", "COMPLETED"));
  }

  @Test
  void skippingStatesIsIllegal() {
    assertThrows(IllegalStateException.class, () -> OrderStates.assertTransition("CREATING", "COMPLETED"));
    assertThrows(IllegalStateException.class, () -> OrderStates.assertTransition("CREATING", "PENDING_REVIEW"));
  }

  @Test
  void terminalStatesAcceptNoFurtherTransition() {
    assertThrows(IllegalStateException.class, () -> OrderStates.assertTransition("COMPLETED", "PENDING_REVIEW"));
    assertThrows(IllegalStateException.class, () -> OrderStates.assertTransition("COMPLETED", "CREATED"));
    assertThrows(IllegalStateException.class, () -> OrderStates.assertTransition("CREATE_FAILED", "CREATING"));
  }

  @Test
  void unknownStatesFailClosed() {
    assertThrows(IllegalStateException.class, () -> OrderStates.assertTransition("PAID", "COMPLETED"));
    assertThrows(IllegalStateException.class, () -> OrderStates.assertTransition("CREATED", "PAID"));
  }

  @Test
  void noOpWritesStayLegal() {
    assertDoesNotThrow(() -> OrderStates.assertTransition(null, "CREATING"));
    assertDoesNotThrow(() -> OrderStates.assertTransition("CREATED", "CREATED"));
  }
}
