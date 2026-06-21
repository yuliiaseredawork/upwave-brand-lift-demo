package dev.sereda.brandlift.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Persistence integration tests against a real Postgres (Testcontainers), with
 * Flyway applying the schema on startup. Covers the round-trip plus the two
 * constraints that protect data quality: the unique idempotency key and the
 * survey score range.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class BrandLiftPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private CampaignRepository campaigns;

    @Autowired
    private AdExposureEventRepository exposureEvents;

    @Autowired
    private SurveyResponseRepository surveyResponses;

    @Test
    void campaignCanBeInsertedAndRetrieved() {
        Campaign campaign = newCampaign();

        campaigns.insert(campaign);
        Campaign loaded = campaigns.findById(campaign.id()).orElseThrow();

        assertThat(loaded.id()).isEqualTo(campaign.id());
        assertThat(loaded.name()).isEqualTo(campaign.name());
        assertThat(loaded.brandName()).isEqualTo(campaign.brandName());
        assertThat(loaded.startsAt()).isEqualTo(campaign.startsAt());
        assertThat(loaded.endsAt()).isEqualTo(campaign.endsAt());
        // Audit timestamps are assigned by the database.
        assertThat(loaded.createdAt()).isNotNull();
        assertThat(loaded.updatedAt()).isNotNull();
    }

    @Test
    void exposureEventCannotBeInsertedTwiceWithSameIdempotencyKey() {
        Campaign campaign = newCampaign();
        campaigns.insert(campaign);

        String sharedKey = "idem-" + UUID.randomUUID();
        exposureEvents.insert(newExposureEvent(campaign.id(), sharedKey));

        // A redelivery of the same logical event must be rejected by the unique constraint.
        assertThatThrownBy(() -> exposureEvents.insert(newExposureEvent(campaign.id(), sharedKey)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void surveyResponseWithOutOfRangeScoreIsRejected() {
        Campaign campaign = newCampaign();
        campaigns.insert(campaign);

        SurveyResponse invalid = new SurveyResponse(
                UUID.randomUUID(),
                campaign.id(),
                "user-hash-1",
                true,
                150.0, // outside 0..100
                40.0,
                20.0,
                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                null,
                false);

        assertThatThrownBy(() -> surveyResponses.insert(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void validSurveyResponseRoundTrips() {
        Campaign campaign = newCampaign();
        campaigns.insert(campaign);

        SurveyResponse response = new SurveyResponse(
                UUID.randomUUID(),
                campaign.id(),
                "user-hash-2",
                false,
                33.5,
                21.0,
                10.25,
                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                null,
                true);

        surveyResponses.insert(response);
        SurveyResponse loaded = surveyResponses.findById(response.id()).orElseThrow();

        assertThat(loaded.awarenessScore()).isEqualTo(33.5);
        assertThat(loaded.exposed()).isFalse();
        assertThat(loaded.late()).isTrue();
        assertThat(loaded.receivedAt()).isNotNull();
    }

    private static Campaign newCampaign() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        return new Campaign(
                UUID.randomUUID(),
                "Spring Awareness Push",
                "Acme",
                start,
                start.plus(14, ChronoUnit.DAYS),
                null,
                null);
    }

    private static AdExposureEvent newExposureEvent(UUID campaignId, String idempotencyKey) {
        return new AdExposureEvent(
                UUID.randomUUID(),
                campaignId,
                "user-hash-1",
                "CTV",
                "creative-1",
                "placement-1",
                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                idempotencyKey,
                null,
                false);
    }
}
