package com.scopepay.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scopepay.store.SandboxOrderRepository;
import com.scopepay.store.ScopeDraftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {"PAYPAL_CLIENT_ID=test", "REQUIRE_REVIEWED_SCOPE=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestStubs.class)
class ReviewApiTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String ORIGIN = "http://localhost:5173";
  private static final String SESSION = "review-session-123456";
  private static final String KEY = "review-request-123456";
  private static final String BRIEF = "Build a website with a contact form.";

  @Autowired MockMvc mvc;
  @Autowired TestStubs.StubPayPal paypal;
  @Autowired SandboxOrderRepository orders;
  @Autowired ScopeDraftRepository drafts;

  @BeforeEach
  void reset() { orders.deleteAll(); drafts.deleteAll(); paypal.reset(); }

  private MvcResult doPost(String path, String body) throws Exception { return doPost(path, body, SESSION); }

  private MvcResult doPost(String path, String body, String session) throws Exception {
    return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).header("Origin", ORIGIN).header("x-demo-session", session)
        .header("idempotency-key", KEY).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
  }

  private String extractDraft() throws Exception {
    MvcResult r = doPost("/api/scopes/extract", "{\"brief\":\"" + BRIEF + "\"}");
    assertEquals(200, r.getResponse().getStatus());
    JsonNode d = MAPPER.readTree(r.getResponse().getContentAsString());
    assertTrue(d.has("scopeId"));
    return d.get("scopeId").asText();
  }

  @Test
  void reviewFlowDraftThenExplicitReviewThenFixedSandboxOrder() throws Exception {
    String scopeId = extractDraft();
    assertEquals(409, doPost("/api/orders", "{\"sku\":\"sandbox-demo\",\"scopeId\":\"" + scopeId + "\"}").getResponse().getStatus());
    assertEquals(0, paypal.creates.get());
    assertEquals(200, doPost("/api/scopes/" + scopeId + "/review", "{\"acknowledge\":true}").getResponse().getStatus());
    MvcResult created = doPost("/api/orders", "{\"sku\":\"sandbox-demo\",\"scopeId\":\"" + scopeId + "\"}");
    assertEquals(201, created.getResponse().getStatus());
    assertEquals(scopeId, orders.findById(KEY).orElseThrow().getScopeId());
  }

  @Test
  void reviewCannotCrossSessions() throws Exception {
    String scopeId = extractDraft();
    assertEquals(404, doPost("/api/scopes/" + scopeId + "/review", "{\"acknowledge\":true}", "different-user-12345")
        .getResponse().getStatus());
  }

  @Test
  void reviewNeedsExactExplicitAcknowledgment() throws Exception {
    String scopeId = extractDraft();
    for (String body : new String[]{"{}", "{\"acknowledge\":false}", "{\"acknowledge\":true,\"price\":1}"})
      assertEquals(400, doPost("/api/scopes/" + scopeId + "/review", body).getResponse().getStatus());
  }

  @Test
  void ordersInFullAppCannotOmitScope() throws Exception {
    assertEquals(409, doPost("/api/orders", "{\"sku\":\"sandbox-demo\"}").getResponse().getStatus());
    assertEquals(0, paypal.creates.get());
  }

  @Test
  void reviewedScopeCannotBeUsedByDifferentSession() throws Exception {
    String scopeId = extractDraft();
    doPost("/api/scopes/" + scopeId + "/review", "{\"acknowledge\":true}");
    assertEquals(409, doPost("/api/orders", "{\"sku\":\"sandbox-demo\",\"scopeId\":\"" + scopeId + "\"}", "different-user-12345")
        .getResponse().getStatus());
  }
}
