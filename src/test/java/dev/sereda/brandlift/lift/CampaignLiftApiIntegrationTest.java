package dev.sereda.brandlift.lift;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.sereda.brandlift.persistence.Campaign;
import dev.sereda.brandlift.persistence.CampaignRepository;
import dev.sereda.brandlift.persistence.SurveyResponse;
import dev.sereda.brandlift.persistence.SurveyResponseRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack lift calculation tests against a real Postgres (Testcontainers). Uses
 * known survey scores so the computed averages and lift values can be asserted exactly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CampaignLiftApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private SurveyResponseRepository surveyResponseRepository;

    private UUID campaignId;

    @BeforeEach
    void createCampaign() {
        campaignId = UUID.randomUUID();
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        campaignRepository.insert(new Campaign(
                campaignId, "Lift Test", "Acme", start, start.plus(14, ChronoUnit.DAYS), null, null));
    }

    @Test
    void recalculateComputesLiftCorrectly() throws Exception {
        seedBalancedResponses();

        mockMvc.perform(post("/api/campaigns/{id}/lift-summary/recalculate", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignId").value(campaignId.toString()))
                .andExpect(jsonPath("$.exposedUserCount").value(2))
                .andExpect(jsonPath("$.controlUserCount").value(2))
                // exposed awareness avg (70,65)=67.5; control (60,58)=59.0; lift 8.5
                .andExpect(jsonPath("$.exposedAvgAwareness").value(67.5))
                .andExpect(jsonPath("$.controlAvgAwareness").value(59.0))
                .andExpect(jsonPath("$.awarenessLift").value(8.5))
                // consideration exposed (40,50)=45.0; control (30,36)=33.0; lift 12.0
                .andExpect(jsonPath("$.considerationLift").value(12.0))
                // purchase intent exposed (25,15)=20.0; control (10,14)=12.0; lift 8.0
                .andExpect(jsonPath("$.purchaseIntentLift").value(8.0))
                .andExpect(jsonPath("$.calculatedAt", notNullValue()));
    }

    @Test
    void summaryCanBeRetrievedAfterRecalculation() throws Exception {
        seedBalancedResponses();

        mockMvc.perform(post("/api/campaigns/{id}/lift-summary/recalculate", campaignId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/campaigns/{id}/lift-summary", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.awarenessLift").value(8.5))
                .andExpect(jsonPath("$.considerationLift").value(12.0))
                .andExpect(jsonPath("$.purchaseIntentLift").value(8.0));
    }

    @Test
    void recalculateMissingCampaignReturns404() throws Exception {
        mockMvc.perform(post("/api/campaigns/{id}/lift-summary/recalculate", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Campaign not found")));
    }

    @Test
    void getMissingCampaignReturns404() throws Exception {
        mockMvc.perform(get("/api/campaigns/{id}/lift-summary", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Campaign not found")));
    }

    @Test
    void onlyExposedResponsesReturnsInsufficientData() throws Exception {
        surveyResponseRepository.insert(response(true, 70, 40, 25));

        mockMvc.perform(post("/api/campaigns/{id}/lift-summary/recalculate", campaignId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("exposed and control")));
    }

    @Test
    void onlyControlResponsesReturnsInsufficientData() throws Exception {
        surveyResponseRepository.insert(response(false, 60, 30, 10));

        mockMvc.perform(post("/api/campaigns/{id}/lift-summary/recalculate", campaignId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("exposed and control")));
    }

    @Test
    void getSummaryBeforeRecalculationReturnsClearEmptyState() throws Exception {
        mockMvc.perform(get("/api/campaigns/{id}/lift-summary", campaignId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("No lift summary has been calculated")));
    }

    private void seedBalancedResponses() {
        surveyResponseRepository.insert(response(true, 70, 40, 25));
        surveyResponseRepository.insert(response(true, 65, 50, 15));
        surveyResponseRepository.insert(response(false, 60, 30, 10));
        surveyResponseRepository.insert(response(false, 58, 36, 14));
    }

    private SurveyResponse response(boolean exposed, double awareness, double consideration, double purchaseIntent) {
        return new SurveyResponse(
                UUID.randomUUID(),
                campaignId,
                "user-" + UUID.randomUUID(),
                exposed,
                awareness,
                consideration,
                purchaseIntent,
                Instant.parse("2025-01-05T12:00:00Z"),
                null,
                false);
    }
}
