package dev.sereda.brandlift.insight;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.sereda.brandlift.persistence.Campaign;
import dev.sereda.brandlift.persistence.CampaignLiftSummary;
import dev.sereda.brandlift.persistence.CampaignLiftSummaryRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack insight endpoint tests against a real Postgres (Testcontainers). The
 * summary is upserted directly so the endpoint is exercised against a known summary
 * without depending on the lift-recalculation path.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CampaignInsightApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private CampaignLiftSummaryRepository liftSummaryRepository;

    private UUID campaignId;

    @BeforeEach
    void createCampaign() {
        campaignId = UUID.randomUUID();
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        campaignRepository.insert(new Campaign(
                campaignId, "Insight Test", "Acme", start, start.plus(14, ChronoUnit.DAYS), null, null));
    }

    @Test
    void generatesInsightFromPersistedSummary() throws Exception {
        liftSummaryRepository.upsert(new CampaignLiftSummary(
                campaignId, 2, 2,
                67.5, 59.0, 8.5,
                45.0, 33.0, 12.0,
                20.0, 12.0, 8.0,
                null));

        mockMvc.perform(get("/api/campaigns/{id}/insights", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignId").value(campaignId.toString()))
                .andExpect(jsonPath("$.generatedAt", notNullValue()))
                .andExpect(jsonPath("$.summary").value(containsString("strong awareness lift")))
                .andExpect(jsonPath("$.keyFindings[1]").value(containsString("+8.50")))
                .andExpect(jsonPath("$.recommendedNextSteps", notNullValue()))
                .andExpect(jsonPath("$.caveats.length()").value(2));
    }

    @Test
    void missingCampaignReturns404() throws Exception {
        mockMvc.perform(get("/api/campaigns/{id}/insights", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Campaign not found")));
    }

    @Test
    void campaignWithoutSummaryReturnsClearEmptyState() throws Exception {
        mockMvc.perform(get("/api/campaigns/{id}/insights", campaignId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("No lift summary has been calculated")));
    }
}
