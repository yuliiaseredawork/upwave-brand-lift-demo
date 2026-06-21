package dev.sereda.brandlift.simulation;

import java.time.Instant;

/**
 * A user's survey answer for a campaign. {@code exposed} records which group the
 * respondent is in (exposed vs. control) — the comparison that lift is built on.
 *
 * <p>Each metric is a 0..100 score. For exposed users it reflects baseline + lift
 * (plus small per-respondent noise); for control users it reflects baseline only.
 * {@code late} marks responses that arrived after the campaign window closed.
 */
public record GeneratedSurveyResponse(
        String campaignId,
        String userId,
        boolean exposed,
        double awarenessScore,
        double considerationScore,
        double purchaseIntentScore,
        Instant respondedAt,
        boolean late) {
}
