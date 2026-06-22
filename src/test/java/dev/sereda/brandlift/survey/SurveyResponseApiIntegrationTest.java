package dev.sereda.brandlift.survey;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack survey ingestion tests against a real Postgres (Testcontainers).
 * Covers exposed/control storage, the campaign-existence 404, score-range and
 * required-field validation, and that {@code late} round-trips.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class SurveyResponseApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CampaignRepository campaignRepository;

    private UUID campaignId;

    @BeforeEach
    void createCampaign() {
        campaignId = UUID.randomUUID();
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        campaignRepository.insert(new Campaign(
                campaignId, "Survey Test", "Acme", start, start.plus(14, ChronoUnit.DAYS), null, null));
    }

    @Test
    void ingestsExposedResponse() throws Exception {
        mockMvc.perform(post("/api/survey-responses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(campaignId, "user-hash-1", true, 42.5, false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.campaignId").value(campaignId.toString()))
                .andExpect(jsonPath("$.exposed").value(true))
                .andExpect(jsonPath("$.awarenessScore").value(42.5))
                .andExpect(jsonPath("$.receivedAt", notNullValue()))
                .andExpect(jsonPath("$.late").value(false));
    }

    @Test
    void ingestsControlResponse() throws Exception {
        mockMvc.perform(post("/api/survey-responses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(campaignId, "user-hash-2", false, 18.0, false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exposed").value(false));
    }

    @Test
    void persistsAndReturnsLateFlag() throws Exception {
        mockMvc.perform(post("/api/survey-responses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(campaignId, "user-hash-3", true, 30.0, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.late").value(true));
    }

    @Test
    void unknownCampaignReturns404() throws Exception {
        mockMvc.perform(post("/api/survey-responses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID(), "user-hash-1", true, 42.5, false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Campaign not found")));
    }

    @Test
    void blankUserIdHashReturns400() throws Exception {
        mockMvc.perform(post("/api/survey-responses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(campaignId, "   ", true, 42.5, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]", containsString("userIdHash")));
    }

    @Test
    void scoreBelowZeroReturns400() throws Exception {
        mockMvc.perform(post("/api/survey-responses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(campaignId, "user-hash-1", true, -1.0, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]", containsString("awarenessScore")));
    }

    @Test
    void scoreAboveHundredReturns400() throws Exception {
        mockMvc.perform(post("/api/survey-responses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(campaignId, "user-hash-1", true, 150.0, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]", containsString("awarenessScore")));
    }

    private static String body(
            UUID campaignId, String userIdHash, boolean exposed, double awarenessScore, boolean late) {
        return """
                {
                  "campaignId": "%s",
                  "userIdHash": "%s",
                  "exposed": %b,
                  "awarenessScore": %s,
                  "considerationScore": 20,
                  "purchaseIntentScore": 10,
                  "responseTimestamp": "2025-01-05T12:00:00Z",
                  "late": %b
                }
                """.formatted(campaignId, userIdHash, exposed, awarenessScore, late);
    }
}
