package com.scopepay;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

@SpringBootApplication
public class ScopepayApplication {
  /** Loads a local .env (never committed) as defaults; real environment variables win. */
  public static void main(String[] args) {
    for (String name : new String[]{"PAYPAL_CLIENT_ID", "PAYPAL_CLIENT_SECRET", "DATABASE_URL"}) {
      if (readEnv(name) == null || readEnv(name).isBlank()) {
        throw new IllegalStateException("Missing " + name + ". Copy .env.example to .env and configure sandbox values locally.");
      }
    }
    String paypalEnv = readEnv("PAYPAL_ENV");
    if (paypalEnv != null && !paypalEnv.isBlank() && !"sandbox".equals(paypalEnv)) {
      throw new IllegalStateException("Only sandbox is supported.");
    }
    SpringApplication app = new SpringApplication(ScopepayApplication.class);
    Map<String, Object> dotenv = loadDotenv();
    if (!dotenv.isEmpty()) {
      app.addInitializers(ctx -> ctx.getEnvironment().getPropertySources()
          .addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
              new MapPropertySource("dotenv", dotenv)));
    }
    app.run(args);
  }

  private static String readEnv(String name) {
    String value = System.getenv(name);
    if (value != null) return value;
    return loadDotenv().getOrDefault(name, "").toString();
  }

  private static Map<String, Object> loadDotenv() {
    Map<String, Object> values = new HashMap<>();
    Path path = Path.of(".env");
    if (!Files.exists(path)) return values;
    try {
      for (String line : Files.readAllLines(path)) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
        int eq = trimmed.indexOf('=');
        if (eq > 0) values.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
      }
    } catch (IOException e) {
      throw new IllegalStateException("Could not read .env", e);
    }
    return values;
  }
}
