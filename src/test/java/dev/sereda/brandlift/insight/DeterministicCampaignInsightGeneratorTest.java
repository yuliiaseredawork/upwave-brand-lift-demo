package dev.sereda.brandlift.insight;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sereda.brandlift.persistence.CampaignLiftSummary;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for the deterministic generator: no Spring, no database. Verifies
 * the threshold-based language for positive / neutral / negative lift, that figures
 * come straight from the summary, and that output is stable for the same input.
 */
class DeterministicCampaignInsightGeneratorTest {

    private final DeterministicCampaignInsightGenerator generator = new DeterministicCampaignInsightGenerator();

    @Test
    void describesStrongPositiveLift() {
        CampaignInsight insight = generator.generate(summary(8.5, 4.2, 6.0));

        assertThat(insight.summary()).contains("strong awareness lift");
        assertThat(insight.keyFindings()).anyMatch(f -> f.contains("+8.50"));
        assertThat(insight.keyFindings()).anyMatch(f -> f.contains("+4.20"));
        assertThat(insight.keyFindings()).anyMatch(f -> f.contains("+6.00"));
        assertThat(insight.caveats()).hasSize(2);
    }

    @Test
    void describesNeutralLift() {
        CampaignInsight insight = generator.generate(summary(0.3, 0.1, 0.2));

        assertThat(insight.summary()).contains("little change in awareness");
        assertThat(insight.summary()).contains("limited movement in purchase intent");
        // Weak lower-funnel movement should prompt the "monitor" step, not "sustain".
        assertThat(insight.recommendedNextSteps())
                .anyMatch(s -> s.contains("Monitor whether purchase-intent"));
    }

    @Test
    void describesNegativeLift() {
        CampaignInsight insight = generator.generate(summary(-3.0, -1.5, -2.0));

        assertThat(insight.summary()).contains("lower awareness than the control group");
        assertThat(insight.keyFindings()).anyMatch(f -> f.contains("-3.00"));
        assertThat(insight.recommendedNextSteps())
                .anyMatch(s -> s.contains("Investigate creative and targeting"));
    }

    @Test
    void isDeterministicForTheSameSummary() {
        CampaignLiftSummary summary = summary(8.5, 4.2, 1.1);

        assertThat(generator.generate(summary)).isEqualTo(generator.generate(summary));
    }

    private static CampaignLiftSummary summary(double awarenessLift, double considerationLift, double purchaseIntentLift) {
        return new CampaignLiftSummary(
                UUID.randomUUID(),
                100,
                100,
                60.0, 60.0 - awarenessLift, awarenessLift,
                40.0, 40.0 - considerationLift, considerationLift,
                20.0, 20.0 - purchaseIntentLift, purchaseIntentLift,
                Instant.parse("2025-01-20T00:00:00Z"));
    }
}
