package com.scopepay.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = {"PAYPAL_CLIENT_ID=test", "REQUIRE_REVIEWED_SCOPE=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestStubs.class)
class ReceiptsApiTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String ORIGIN = "http://localhost:5173";
  private static final String S = "receipt-session-12345";
  private static final String BRIEF = "Build a website with a contact form.";

  @Autowired MockMvc mvc;
  @Autowired SandboxOrderRepository orders;
  @Autowired ScopeDraftRepository drafts;

  @BeforeEach
  void reset() { orders.deleteAll(); drafts.deleteAll(); }

  private MockHttpServletRequestBuilder receipts(String session, String origin) {
    MockHttpServletRequestBuilder b = get("/api/receipts");
    if (session != null) b = b.header("x-demo-session", session);
    if (origin != null) b = b.header("Origin", origin);
    return b;
  }

  private MvcResult doPost(String path, String body) throws Exception {
    return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).header("Origin", ORIGIN).header("x-demo-session", S)
        .header("idempotency-key", "receipt-request-1234").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
  }

  @Test
  void receiptsRequireAValidSession() throws Exception {
    mvc.perform(receipts("bad", ORIGIN)).andExpect(status().isBadRequest());
    mvc.perform(receipts(null, ORIGIN)).andExpect(status().isBadRequest());
  }

  @Test
  void receiptsRejectAForeignOriginWhenOneIsSent() throws Exception {
    mvc.perform(receipts(S, "https://evil.example")).andExpect(status().isForbidden());
    mvc.perform(receipts(S, null)).andExpect(status().isOk());
  }

  @Test
  void receiptsStartEmptyThenListSessionDraftOrderAndCapture() throws Exception {
    mvc.perform(receipts(S, ORIGIN)).andExpect(status().isOk())
        .andExpect(jsonPath("$.drafts").isEmpty()).andExpect(jsonPath("$.orders").isEmpty());
    MvcResult d = doPost("/api/scopes/extract", "{\"brief\":\"" + BRIEF + "\"}");
    String scopeId = MAPPER.readTree(d.getResponse().getContentAsString()).get("scopeId").asText();
    doPost("/api/scopes/" + scopeId + "/review", "{\"acknowledge\":true}");
    MvcResult o = doPost("/api/orders", "{\"sku\":\"sandbox-demo\",\"scopeId\":\"" + scopeId + "\"}");
    String orderId = MAPPER.readTree(o.getResponse().getContentAsString()).get("orderId").asText();
    doPost("/api/orders/" + orderId + "/capture", "{}");
    MvcResult r = mvc.perform(receipts(S, ORIGIN)).andExpect(status().isOk()).andReturn();
    JsonNode body = MAPPER.readTree(r.getResponse().getContentAsString());
    assertEquals(1, body.get("drafts").size());
    assertEquals(scopeId, body.get("drafts").get(0).get("scopeId").asText());
    assertEquals("REVIEWED_FOR_SANDBOX", body.get("drafts").get(0).get("state").asText());
    assertEquals(1, body.get("orders").size());
    assertEquals(orderId, body.get("orders").get(0).get("orderId").asText());
    assertEquals("COMPLETED", body.get("orders").get(0).get("state").asText());
    assertEquals("CAPTURE123", body.get("orders").get(0).get("captureId").asText());
    assertFalse(body.get("drafts").get(0).has("owner"));
    assertFalse(body.get("orders").get(0).has("owner"));
    assertFalse(body.get("drafts").get(0).has("ownerHash"));
    assertFalse(body.get("orders").get(0).has("ownerHash"));
  }

  @Test
  void receiptsNeverCrossSessions() throws Exception {
    post("/api/scopes/extract", "{\"brief\":\"" + BRIEF + "\"}");
    mvc.perform(receipts("different-session-999", ORIGIN)).andExpect(status().isOk())
        .andExpect(jsonPath("$.drafts").isEmpty()).andExpect(jsonPath("$.orders").isEmpty());
  }
}
