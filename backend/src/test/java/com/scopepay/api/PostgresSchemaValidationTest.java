package com.scopepay.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.ResultSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Boots the application against a REAL PostgreSQL (embedded, free, no Docker) with
 * Flyway enabled and Hibernate set to validate, exactly like production config.
 * The H2 suite uses create-drop without Flyway, so migration/entity drift - like the
 * CHAR(3) vs VARCHAR(3) currency bug found on a real install - is invisible to it.
 * This test fails at context startup on any such mismatch.
 */
@SpringBootTest(properties = {"PAYPAL_CLIENT_ID=test", "GROQ_API_KEY=", "GROQ_FREE_PLAN_CONFIRMED=false"})
@ActiveProfiles("test")
class PostgresSchemaValidationTest {

  private static EmbeddedPostgres postgres;

  @BeforeAll
  static void startPostgres() throws Exception {
    postgres = EmbeddedPostgres.start();
  }

  @AfterAll
  static void stopPostgres() throws Exception {
    if (postgres != null) postgres.close();
  }

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> postgres.getJdbcUrl("postgres", "postgres"));
    registry.add("spring.datasource.username", () -> "postgres");
    registry.add("spring.datasource.password", () -> "postgres");
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
  }

  @Test
  void flywayMigrationsMatchJpaMappingsOnRealPostgres() throws Exception {
    // Context startup already ran Flyway + Hibernate validation; reaching here is the proof.
    try (Connection c = postgres.getPostgresDatabase().getConnection();
         ResultSet rs = c.createStatement().executeQuery(
             "SELECT data_type FROM information_schema.columns WHERE table_name='ledger_entries' AND column_name='currency'")) {
      rs.next();
      assertEquals("character varying", rs.getString(1));
    }
  }
}
