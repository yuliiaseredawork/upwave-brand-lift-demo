package dev.sereda.brandlift.exposure;

import dev.sereda.brandlift.persistence.AdExposureEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * API view of an ingested exposure event. {@code duplicate} reports whether this
 * particular request was a duplicate delivery (true) or created a new event (false).
 * It is a property of the call, not of the stored row.
 */
public record ExposureEventResponse(
        UUID id,
        UUID campaignId,
        String userIdHash,
        String channel,
        String creativeId,
        String placementId,
        Instant impressionTimestamp,
        String idempotencyKey,
        Instant receivedAt,
        boolean duplicate) {

    public static ExposureEventResponse from(AdExposureEvent event, boolean duplicate) {
        return new ExposureEventResponse(
                event.id(),
                event.campaignId(),
                event.userIdHash(),
                event.channel(),
                event.creativeId(),
                event.placementId(),
                event.impressionTimestamp(),
                event.idempotencyKey(),
                event.receivedAt(),
                duplicate);
    }
}
