package com.scopepay.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"PAYPAL_CLIENT_ID=test", "GROQ_API_KEY=", "GROQ_FREE_PLAN_CONFIRMED=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScopeApiNoProviderTest {
  private static final String BRIEF = "Build a website with a contact form. No login. Require login for every visitor.";

  @Autowired MockMvc mvc;

  @Test
  void apiDisabledByDefaultWithClear503() throws Exception {
    mvc.perform(post("/api/scopes/extract").header("Origin", "http://localhost:5173")
        .header("x-demo-session", "scope-session-0123456789")
        .contentType(MediaType.APPLICATION_JSON).content("{\"brief\":\"" + BRIEF + "\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("AI_NOT_CONFIGURED"));
  }
}
