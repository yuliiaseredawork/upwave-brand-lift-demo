package dev.sereda.brandlift.exposure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.sereda.brandlift.persistence.AdExposureEvent;
import dev.sereda.brandlift.persistence.AdExposureEventRepository;
import dev.sereda.brandlift.persistence.CampaignRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

/**
 * Unit test for the race-recovery branch: the pre-check misses, the insert loses the
 * race and the unique constraint throws, and the service recovers by returning the
 * row that won. This is the path that proves the database constraint — not the
 * pre-check — is the real correctness guard.
 */
@ExtendWith(MockitoExtension.class)
class ExposureEventServiceTest {

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private AdExposureEventRepository exposureEventRepository;

    @Test
    void recoversWhenInsertLosesTheUniquenessRace() {
        UUID campaignId = UUID.randomUUID();
        String key = "idem-123";
        IngestExposureEventRequest request = new IngestExposureEventRequest(
                campaignId, "user-hash-1", "CTV", "creative-1", "placement-1",
                Instant.parse("2025-01-05T12:00:00Z"), key);

        AdExposureEvent winner = new AdExposureEvent(
                UUID.randomUUID(), campaignId, "user-hash-1", "CTV", "creative-1", "placement-1",
                Instant.parse("2025-01-05T12:00:00Z"), key, Instant.now(), false);

        when(campaignRepository.existsById(campaignId)).thenReturn(true);
        // First look-up misses (pre-check), then after the failed insert it returns the winner.
        when(exposureEventRepository.findByIdempotencyKey(key))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        doThrow(new DuplicateKeyException("unique violation"))
                .when(exposureEventRepository).insert(any(AdExposureEvent.class));

        ExposureEventService service = new ExposureEventService(campaignRepository, exposureEventRepository);
        ExposureEventService.IngestResult result = service.ingest(request);

        assertThat(result.duplicate()).isTrue();
        assertThat(result.event()).isEqualTo(winner);
        // We must not try to read back by the generated id after losing the race.
        verify(exposureEventRepository, never()).findById(any(UUID.class));
    }
}
