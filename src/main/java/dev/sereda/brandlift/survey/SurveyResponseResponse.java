package dev.sereda.brandlift.survey;

import dev.sereda.brandlift.persistence.SurveyResponse;
import java.time.Instant;
import java.util.UUID;

/**
 * API view of a stored survey response. Kept separate from the persistence
 * {@link SurveyResponse} record so the wire and storage shapes can diverge.
 */
public record SurveyResponseResponse(
        UUID id,
        UUID campaignId,
        String userIdHash,
        boolean exposed,
        double awarenessScore,
        double considerationScore,
        double purchaseIntentScore,
        Instant responseTimestamp,
        Instant receivedAt,
        boolean late) {

    public static SurveyResponseResponse from(SurveyResponse response) {
        return new SurveyResponseResponse(
                response.id(),
                response.campaignId(),
                response.userIdHash(),
                response.exposed(),
                response.awarenessScore(),
                response.considerationScore(),
                response.purchaseIntentScore(),
                response.responseTimestamp(),
                response.receivedAt(),
                response.late());
    }
}
