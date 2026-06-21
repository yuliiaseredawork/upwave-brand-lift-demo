package dev.sereda.brandlift.exposure;

import dev.sereda.brandlift.campaign.CampaignNotFoundException;
import dev.sereda.brandlift.persistence.AdExposureEvent;
import dev.sereda.brandlift.persistence.AdExposureEventRepository;
import dev.sereda.brandlift.persistence.CampaignRepository;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Idempotent ingestion of ad exposure events.
 *
 * <p>The same logical event can be delivered more than once (at-least-once delivery
 * from upstream systems), so this never stores a second raw row for an idempotency
 * key it has already seen. The flow is:
 * <ol>
 *   <li>reject unknown campaigns with 404;</li>
 *   <li>fast path: if the key already exists, return it as a duplicate;</li>
 *   <li>otherwise insert; if a concurrent request won the race, the unique
 *       constraint throws and we fall back to returning the now-existing row.</li>
 * </ol>
 * The database unique constraint on {@code idempotency_key} is the final guard:
 * the pre-check is only an optimization, not the correctness boundary.
 */
@Service
public class ExposureEventService {

    private final CampaignRepository campaignRepository;
    private final AdExposureEventRepository exposureEventRepository;

    public ExposureEventService(
            CampaignRepository campaignRepository,
            AdExposureEventRepository exposureEventRepository) {
        this.campaignRepository = campaignRepository;
        this.exposureEventRepository = exposureEventRepository;
    }

    public IngestResult ingest(IngestExposureEventRequest request) {
        if (!campaignRepository.existsById(request.campaignId())) {
            throw new CampaignNotFoundException(request.campaignId());
        }

        // Fast path: already ingested, return the stored event as a duplicate.
        var existing = exposureEventRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return new IngestResult(existing.get(), true);
        }

        UUID id = UUID.randomUUID();
        AdExposureEvent event = new AdExposureEvent(
                id,
                request.campaignId(),
                request.userIdHash(),
                request.channel(),
                request.creativeId(),
                request.placementId(),
                request.impressionTimestamp(),
                request.idempotencyKey(),
                null,   // received_at assigned by the database
                false); // not a stored duplicate; we never insert duplicates

        try {
            exposureEventRepository.insert(event);
            AdExposureEvent saved = exposureEventRepository.findById(id).orElseThrow();
            return new IngestResult(saved, false);
        } catch (DuplicateKeyException race) {
            // A concurrent request inserted the same key between our check and insert.
            // The constraint did its job; return the event that won.
            AdExposureEvent saved = exposureEventRepository
                    .findByIdempotencyKey(request.idempotencyKey())
                    .orElseThrow(() -> race);
            return new IngestResult(saved, true);
        }
    }

    /** Outcome of an ingest call: the stored event and whether this call was a duplicate. */
    public record IngestResult(AdExposureEvent event, boolean duplicate) {
    }
}
