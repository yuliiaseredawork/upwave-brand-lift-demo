package dev.sereda.brandlift;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Smoke test: boots the full Spring context against a real Postgres (via Testcontainers)
 * and lets Flyway run the migrations. If this passes, wiring + datasource + migrations
 * are all consistent. Business behavior is covered in later steps.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class BrandLiftApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void contextLoads() {
        // Intentionally empty: success means the context started and migrations applied.
    }
}
