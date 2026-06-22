package dev.sereda.brandlift.insight;

import dev.sereda.brandlift.persistence.CampaignLiftSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Offline, deterministic insight generator. Given the same summary it always produces
 * the same text, which makes it trivial to test and safe to serve as a fallback.
 *
 * <p>It classifies each metric's lift into a small set of levels and phrases the
 * output in plain, non-exaggerated language. It never invents numbers: every figure
 * comes straight from the persisted summary.
 */
@Component
public class DeterministicCampaignInsightGenerator implements CampaignInsightGenerator {

    // Lift thresholds, in score points, applied to exposed-minus-control values.
    private static final double STRONG_POSITIVE = 5.0;
    private static final double MODEST_POSITIVE = 1.0;
    private static final double NEGATIVE = -1.0;

    private enum LiftLevel {
        STRONG_POSITIVE,
        MODEST_POSITIVE,
        NEUTRAL,
        NEGATIVE
    }

    @Override
    public CampaignInsight generate(CampaignLiftSummary summary) {
        LiftLevel awareness = classify(summary.awarenessLift());
        LiftLevel purchaseIntent = classify(summary.purchaseIntentLift());

        return new CampaignInsight(
                buildSummary(awareness, purchaseIntent),
                buildKeyFindings(summary),
                buildNextSteps(summary),
                buildCaveats());
    }

    private static LiftLevel classify(double lift) {
        if (lift <= NEGATIVE) {
            return LiftLevel.NEGATIVE;
        }
        if (lift < MODEST_POSITIVE) {
            return LiftLevel.NEUTRAL;
        }
        if (lift < STRONG_POSITIVE) {
            return LiftLevel.MODEST_POSITIVE;
        }
        return LiftLevel.STRONG_POSITIVE;
    }

    private static String buildSummary(LiftLevel awareness, LiftLevel purchaseIntent) {
        String headline = switch (awareness) {
            case STRONG_POSITIVE -> "The campaign drove a strong awareness lift among exposed users";
            case MODEST_POSITIVE -> "The campaign drove a modest awareness lift among exposed users";
            case NEUTRAL -> "The campaign showed little change in awareness among exposed users";
            case NEGATIVE -> "Exposed users reported lower awareness than the control group";
        };
        String lowerFunnel = switch (purchaseIntent) {
            case STRONG_POSITIVE, MODEST_POSITIVE -> ", with positive movement in purchase intent as well.";
            case NEUTRAL -> ", with limited movement in purchase intent.";
            case NEGATIVE -> ", while purchase intent trailed the control group.";
        };
        return headline + lowerFunnel;
    }

    private static List<String> buildKeyFindings(CampaignLiftSummary summary) {
        return List.of(
                "Based on %d exposed and %d control survey responses."
                        .formatted(summary.exposedUserCount(), summary.controlUserCount()),
                "Awareness lift is %s points versus control.".formatted(formatLift(summary.awarenessLift())),
                "Consideration lift is %s points versus control.".formatted(formatLift(summary.considerationLift())),
                "Purchase intent lift is %s points versus control.".formatted(formatLift(summary.purchaseIntentLift())));
    }

    private static List<String> buildNextSteps(CampaignLiftSummary summary) {
        List<String> steps = new ArrayList<>();
        steps.add("Review channel-level performance before shifting budget.");

        LiftLevel purchaseIntent = classify(summary.purchaseIntentLift());
        if (purchaseIntent == LiftLevel.NEUTRAL || purchaseIntent == LiftLevel.NEGATIVE) {
            steps.add("Monitor whether purchase-intent movement improves with additional exposure.");
        } else {
            steps.add("Consider sustaining the current media plan while watching for diminishing returns.");
        }

        boolean anyNegative = classify(summary.awarenessLift()) == LiftLevel.NEGATIVE
                || classify(summary.considerationLift()) == LiftLevel.NEGATIVE
                || classify(summary.purchaseIntentLift()) == LiftLevel.NEGATIVE;
        if (anyNegative) {
            steps.add("Investigate creative and targeting for the metrics that trailed the control group.");
        }
        return List.copyOf(steps);
    }

    private static List<String> buildCaveats() {
        return List.of(
                "This demo uses a simple exposed-minus-control lift calculation.",
                "It does not model statistical significance, causal inference, or identity-graph attribution.");
    }

    /** Signed, two-decimal formatting, e.g. "+8.50" or "-3.00". US locale for a stable "." separator. */
    private static String formatLift(double lift) {
        return String.format(Locale.US, "%+.2f", lift);
    }
}
