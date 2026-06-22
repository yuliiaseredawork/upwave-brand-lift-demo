package dev.sereda.brandlift.insight;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * API view of a generated insight. {@code generatedAt} is stamped by the service at
 * request time; the rest comes from the {@link CampaignInsight}.
 */
public record CampaignInsightResponse(
        UUID campaignId,
        Instant generatedAt,
        String summary,
        List<String> keyFindings,
        List<String> recommendedNextSteps,
        List<String> caveats) {

    public static CampaignInsightResponse from(UUID campaignId, Instant generatedAt, CampaignInsight insight) {
        return new CampaignInsightResponse(
                campaignId,
                generatedAt,
                insight.summary(),
                insight.keyFindings(),
                insight.recommendedNextSteps(),
                insight.caveats());
    }
}
