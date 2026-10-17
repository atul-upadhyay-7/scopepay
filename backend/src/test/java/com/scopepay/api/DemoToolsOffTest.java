package com.scopepay.api;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Demo tools stay off unless explicitly enabled: the simulator route does not exist in production shape. */
@SpringBootTest(properties = {"PAYPAL_CLIENT_ID=test-public-id", "REQUIRE_REVIEWED_SCOPE=false", "PAYPAL_WEBHOOK_ID=test-webhook-id"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestStubs.class)
class DemoToolsOffTest {
  @Autowired MockMvc mvc;

  @Test
  void webhookSimulatorIsNotFoundWhenDemoToolsAreOff() throws Exception {
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/demo/webhook-simulator")
            .header("Origin", "http://localhost:5173").header("x-demo-session", "demo-off-session-1")
            .contentType(MediaType.APPLICATION_JSON).content("{\"requestId\":\"anything-0123456\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void failureModeIsNotFoundWhenDemoToolsAreOff() throws Exception {
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/demo/failure-mode")
            .header("Origin", "http://localhost:5173").header("x-demo-session", "demo-off-session-1")
            .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"capture-pending\"}"))
        .andExpect(status().isNotFound());
  }
}
