package dev.sereda.brandlift.exposure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sereda.brandlift.persistence.Campaign;
import dev.sereda.brandlift.persistence.CampaignRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack ingestion tests against a real Postgres (Testcontainers). The key
 * behavior under test is idempotency: delivering the same event twice must not
 * create a second row.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ExposureEventApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private JdbcClient jdbcClient;

    private UUID campaignId;

    @BeforeEach
    void createCampaign() {
        campaignId = UUID.randomUUID();
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        campaignRepository.insert(new Campaign(
                campaignId, "Ingestion Test", "Acme", start, start.plus(14, ChronoUnit.DAYS), null, null));
    }

    @Test
    void ingestsNewEvent() throws Exception {
        String key = "idem-" + UUID.randomUUID();

        mockMvc.perform(post("/api/exposure-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(campaignId, "CTV", key)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.campaignId").value(campaignId.toString()))
                .andExpect(jsonPath("$.channel").value("CTV"))
                .andExpect(jsonPath("$.receivedAt", notNullValue()))
                .andExpect(jsonPath("$.duplicate").value(false));
    }

    @Test
    void duplicateDeliveryReturnsExistingEventAndDoesNotInsertSecondRow() throws Exception {
        String key = "idem-" + UUID.randomUUID();

        String firstResponse = mockMvc.perform(post("/api/exposure-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(campaignId, "SOCIAL", key)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duplicate").value(false))
                .andReturn().getResponse().getContentAsString();
        String firstId = objectMapper.readTree(firstResponse).get("id").asText();

        // Same idempotency key again -> 200 OK, duplicate=true, same id.
        mockMvc.perform(post("/api/exposure-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(campaignId, "SOCIAL", key)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.id").value(firstId));

        // The database holds exactly one row for that key.
        long rows = jdbcClient.sql("select count(*) from ad_exposure_events where idempotency_key = :key")
                .param("key", key)
                .query(Long.class)
                .single();
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void unknownCampaignReturns404() throws Exception {
        mockMvc.perform(post("/api/exposure-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID(), "CTV", "idem-" + UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Campaign not found")));
    }

    @Test
    void blankIdempotencyKeyReturns400() throws Exception {
        mockMvc.perform(post("/api/exposure-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(campaignId, "CTV", "  ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]", containsString("idempotencyKey")));
    }

    @Test
    void invalidChannelReturns400() throws Exception {
        mockMvc.perform(post("/api/exposure-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(campaignId, "BILLBOARD", "idem-" + UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]", containsString("channel")));
    }

    private static String body(UUID campaignId, String channel, String idempotencyKey) {
        return """
                {
                  "campaignId": "%s",
                  "userIdHash": "user-hash-1",
                  "channel": "%s",
                  "creativeId": "creative-1",
                  "placementId": "placement-1",
                  "impressionTimestamp": "2025-01-05T12:00:00Z",
                  "idempotencyKey": "%s"
                }
                """.formatted(campaignId, channel, idempotencyKey);
    }
}
