package com.scopepay.api;

import com.scopepay.config.AppConfig;
import com.scopepay.scope.ScopeException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
public class SessionGate extends OncePerRequestFilter {
  private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9-]{16,80}$");
  private final AppConfig config;

  public SessionGate(AppConfig config) { this.config = config; }

  public static String ownerHash(HttpServletRequest request) {
    return ownerHash(request.getHeader("x-demo-session"));
  }

  public static String ownerHash(String session) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(session.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : hash) hex.append(String.format("%02x", b));
      return hex.toString();
    } catch (Exception e) { throw new IllegalStateException(e); }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    if (!path.startsWith("/api/")) return true;
    if ("GET".equals(request.getMethod()) && (path.equals("/api/health") || path.equals("/api/config"))) return true;
    if ("POST".equals(request.getMethod()) && path.equals("/api/webhooks/paypal")) return true;
    return false;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws java.io.IOException, jakarta.servlet.ServletException {
    String path = request.getRequestURI();
    boolean isReceipts = "GET".equals(request.getMethod()) && path.equals("/api/receipts");
    String origin = request.getHeader("Origin");
    if (isReceipts) {
      if (origin != null && !origin.equals(config.appOrigin)) { send(response, 403, "ORIGIN_NOT_ALLOWED"); return; }
    } else if (!config.appOrigin.equals(origin)) { send(response, 403, "ORIGIN_NOT_ALLOWED"); return; }
    String session = request.getHeader("x-demo-session");
    if (session == null || !KEY_PATTERN.matcher(session).matches()) { send(response, 400, "SESSION_REQUIRED"); return; }
    chain.doFilter(request, response);
  }

  private void send(HttpServletResponse response, int status, String code) throws java.io.IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.getWriter().write("{\"error\":\"" + code + "\"}");
  }
}
