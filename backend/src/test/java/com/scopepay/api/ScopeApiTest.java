package com.scopepay.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

@SpringBootTest(properties = {"PAYPAL_CLIENT_ID=test"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestStubs.class)
class ScopeApiTest {
  private static final String ORIGIN = "http://localhost:5173";
  private static final String SESSION = "scope-session-0123456789";
  private static final String BRIEF = "Build a website with a contact form. No login. Require login for every visitor.";

  @Autowired MockMvc mvc;
  @Autowired SandboxOrderRepository orders;
  @Autowired ScopeDraftRepository drafts;

  @BeforeEach
  void reset() { orders.deleteAll(); drafts.deleteAll(); }

  private org.springframework.test.web.servlet.RequestBuilder extract(String body) {
    return post("/api/scopes/extract").header("Origin", ORIGIN).header("x-demo-session", SESSION)
        .contentType(MediaType.APPLICATION_JSON).content(body);
  }

  @Test
  void apiReturnsDraftOnlyWithInjectedTestProvider() throws Exception {
    mvc.perform(extract("{\"brief\":\"" + BRIEF + "\"}")).andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("DRAFT_REQUIRES_REVIEW"))
        .andExpect(jsonPath("$.scopeId").exists());
  }

  @Test
  void apiRejectsExtraBodyFields() throws Exception {
    mvc.perform(extract("{\"brief\":\"" + BRIEF + "\",\"approved\":true}")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_BRIEF_REQUEST"));
  }

  @Test
  void apiOriginGateAlsoCoversAiPath() throws Exception {
    mvc.perform(post("/api/scopes/extract").header("x-demo-session", SESSION)
        .contentType(MediaType.APPLICATION_JSON).content("{\"brief\":\"" + BRIEF + "\"}"))
        .andExpect(status().isForbidden());
  }
}
