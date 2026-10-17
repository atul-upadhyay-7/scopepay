package com.scopepay;

import com.scopepay.paypal.PayPalClient;
import java.util.Map;
import com.scopepay.scope.GenerateResult;
import com.scopepay.scope.ScopeProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.support.GenericApplicationContext;

/** Local demo runner (test sources only): H2 database + scripted AI provider. Never in production code. */
public class DemoMain {
  public static void main(String[] args) {
    ObjectMapper mapper = new ObjectMapper();
    ScopeProvider demo = (instructions, brief, schema) -> {
      var d = mapper.createObjectNode();
      d.put("summary", "Three-page portfolio website with a contact form; the deadline is still open.");
      var deliverables = d.putArray("deliverables");
      deliverables.addObject().put("description", "Home, Projects and Contact pages").put("evidence", "Home, Projects and Contact");
      deliverables.addObject().put("description", "Contact form").put("evidence", "Include a contact form");
      deliverables.addObject().put("description", "Responsive layouts").put("evidence", "Deliver responsive layouts");
      d.putArray("questions").add("What is the agreed deadline?");
      var conflicts = d.putArray("conflicts");
      var c = conflicts.addObject().put("description", "The brief excludes visitor login but the form may need abuse protection.");
      c.putArray("evidence").add("Do not include visitor login").add("Include a contact form");
      return GenerateResult.ofData(d);
    };
    SpringApplication app = new SpringApplication(ScopepayApplication.class);
    app.setAdditionalProfiles("test");
    app.setDefaultProperties(Map.of("DEMO_TOOLS", "true", "PAYPAL_WEBHOOK_ID", "demo-webhook-id"));
    app.addInitializers(ctx -> ((GenericApplicationContext) ctx).registerBean("demoScopeProvider", ScopeProvider.class, () -> demo));
    // Screenshot/demo mode only, never default: DEMO_STUB_PAYPAL=true replaces PayPal with a scripted sandbox
    // stand-in so the full order timeline can be exercised without real sandbox credentials.
    if ("true".equals(System.getenv("DEMO_STUB_PAYPAL"))) {
      final com.scopepay.api.DemoControls[] controlsRef = new com.scopepay.api.DemoControls[1];
      PayPalClient stub = new PayPalClient("demo-stub", "demo-stub") {
        private String mode() { return controlsRef[0] == null ? "off" : controlsRef[0].mode(); }
        @Override
        public String verifyWebhookSignature(java.util.Map<String, String> transmission, com.fasterxml.jackson.databind.JsonNode webhookEvent, String webhookId) {
          return "SUCCESS";
        }
        @Override
        public com.fasterxml.jackson.databind.JsonNode createOrder(String requestId, String sku, String value) {
          if ("create-down".equals(mode())) throw new RuntimeException("simulated paypal outage");
          String id = "DEMO" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
          return mapper.createObjectNode().put("id", id).put("status", "CREATED");
        }
        @Override
        public com.fasterxml.jackson.databind.JsonNode captureOrder(String orderId) {
          if ("capture-pending".equals(mode())) return mapper.createObjectNode().put("status", "APPROVED");
          var root = mapper.createObjectNode();
          root.put("status", "COMPLETED");
          var capture = root.putArray("purchase_units").addObject().putObject("payments").putArray("captures").addObject();
          capture.put("id", "CAP-" + orderId).put("status", "COMPLETED");
          capture.putObject("amount").put("value", "capture-wrong-amount".equals(mode()) ? "0.01" : "10.00").put("currency_code", "USD");
          return root;
        }
      };
      app.addInitializers(ctx -> ((GenericApplicationContext) ctx)
          .registerBean("demoStubPayPalClient", PayPalClient.class, () -> {
            controlsRef[0] = ctx.getBean(com.scopepay.api.DemoControls.class);
            return stub;
          }, bd -> bd.setPrimary(true)));
    }
    app.run(args);
  }
}
