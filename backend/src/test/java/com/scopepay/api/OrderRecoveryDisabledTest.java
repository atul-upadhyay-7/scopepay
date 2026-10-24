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

/**
 * Order recovery fails closed: without ORDER_RECOVERY=true (the default, and every
 * production-style config) both recovery endpoints are 404 and /api/config reports it off.
 */
@SpringBootTest(properties = {"PAYPAL_CLIENT_ID=test", "REQUIRE_REVIEWED_SCOPE=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestStubs.class)
class OrderRecoveryDisabledTest {
  private static final String ORIGIN = "http://localhost:5173";
  private static final String SESSION = "recovery-disabled-sess";

  @Autowired MockMvc mvc;

  @Test
  void recoveryEndpointsAreNotFoundWhenDisabled() throws Exception {
    var code = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .post("/api/orders/ORDER12345/recovery-code")
        .header("Origin", ORIGIN).header("x-demo-session", SESSION)
        .contentType(MediaType.APPLICATION_JSON).content("{}");
    mvc.perform(code).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NOT_FOUND"));
    var recover = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .post("/api/orders/ORDER12345/recover")
        .header("Origin", ORIGIN).header("x-demo-session", SESSION)
        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"AAAA-BBBB\"}");
    mvc.perform(recover).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NOT_FOUND"));
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/config"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.orderRecovery").value(false));
  }
}
