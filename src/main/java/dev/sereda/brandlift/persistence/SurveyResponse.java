package dev.sereda.brandlift.persistence;

import java.time.Instant;
import java.util.UUID;

/**
 * A raw survey response row. Scores are 0..100 (enforced by check constraints in
 * the schema). {@code receivedAt} is assigned by the database on insert.
 */
public record SurveyResponse(
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
}
