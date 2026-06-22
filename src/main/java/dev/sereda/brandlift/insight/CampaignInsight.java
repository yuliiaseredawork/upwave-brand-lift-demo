package dev.sereda.brandlift.insight;

import java.util.List;

/**
 * A generated, customer-facing insight about a campaign's lift. This is the domain
 * output of a {@link CampaignInsightGenerator}; it deliberately carries no campaign id
 * or timestamp so the text depends only on the underlying numbers (and is therefore
 * stable and testable). The service adds those fields when building the API response.
 */
public record CampaignInsight(
        String summary,
        List<String> keyFindings,
        List<String> recommendedNextSteps,
        List<String> caveats) {
}
