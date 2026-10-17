package com.scopepay.api;

import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Process-wide failure-injection switch for local demos (the demo is single-operator).
 * Only honored by scripted PayPal stand-ins (test/demo runners); the real PayPalClient never reads it.
 * Enabled endpoints gate on DEMO_TOOLS; production config never sets it.
 */
@Component
public class DemoControls {
  public static final Set<String> MODES = Set.of("off", "capture-pending", "capture-wrong-amount", "create-down");
  private volatile String mode = "off";

  public String mode() { return mode; }

  public void setMode(String mode) {
    if (!MODES.contains(mode)) throw new IllegalArgumentException("unknown failure mode: " + mode);
    this.mode = mode;
  }
}
