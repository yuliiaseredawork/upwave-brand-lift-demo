package dev.sereda.brandlift.simulation;

/**
 * Describes the shape of a synthetic dataset to generate. This is pure
 * configuration: it says how many users, how many of them are exposed, how
 * "dirty" the stream is (duplicates, late responses), and the baseline/lift for
 * each survey metric.
 *
 * <p>Conventions:
 * <ul>
 *   <li>{@code *Rate} fields are fractions in [0, 1].</li>
 *   <li>{@code baseline*Score} and {@code *Lift} fields are points on a 0..100 scale.</li>
 * </ul>
 *
 * <p>A {@link Builder} is provided because the record has many fields; constructing
 * it positionally would be easy to get wrong (adjacent doubles are hard to tell
 * apart). The builder lets tests state only the knobs they care about.
 */
public record CampaignScenario(
        String campaignName,
        String brandName,
        int numberOfUsers,
        double exposureRate,
        double duplicateExposureRate,
        double lateSurveyResponseRate,
        double baselineAwarenessScore,
        double awarenessLift,
        double baselineConsiderationScore,
        double considerationLift,
        double baselinePurchaseIntentScore,
        double purchaseIntentLift,
        long randomSeed) {

    public CampaignScenario {
        requireFraction(exposureRate, "exposureRate");
        requireFraction(duplicateExposureRate, "duplicateExposureRate");
        requireFraction(lateSurveyResponseRate, "lateSurveyResponseRate");
        if (numberOfUsers < 0) {
            throw new IllegalArgumentException("numberOfUsers must be >= 0");
        }
    }

    private static void requireFraction(double value, String name) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between 0 and 1, was " + value);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Mutable builder with sensible defaults for a realistic mid-size campaign.
     * Override only what a given test or scenario needs.
     */
    public static final class Builder {
        private String campaignName = "Demo Campaign";
        private String brandName = "Demo Brand";
        private int numberOfUsers = 1_000;
        private double exposureRate = 0.6;
        private double duplicateExposureRate = 0.05;
        private double lateSurveyResponseRate = 0.10;
        private double baselineAwarenessScore = 30.0;
        private double awarenessLift = 8.0;
        private double baselineConsiderationScore = 20.0;
        private double considerationLift = 5.0;
        private double baselinePurchaseIntentScore = 12.0;
        private double purchaseIntentLift = 3.0;
        private long randomSeed = 42L;

        public Builder campaignName(String value) {
            this.campaignName = value;
            return this;
        }

        public Builder brandName(String value) {
            this.brandName = value;
            return this;
        }

        public Builder numberOfUsers(int value) {
            this.numberOfUsers = value;
            return this;
        }

        public Builder exposureRate(double value) {
            this.exposureRate = value;
            return this;
        }

        public Builder duplicateExposureRate(double value) {
            this.duplicateExposureRate = value;
            return this;
        }

        public Builder lateSurveyResponseRate(double value) {
            this.lateSurveyResponseRate = value;
            return this;
        }

        public Builder baselineAwarenessScore(double value) {
            this.baselineAwarenessScore = value;
            return this;
        }

        public Builder awarenessLift(double value) {
            this.awarenessLift = value;
            return this;
        }

        public Builder baselineConsiderationScore(double value) {
            this.baselineConsiderationScore = value;
            return this;
        }

        public Builder considerationLift(double value) {
            this.considerationLift = value;
            return this;
        }

        public Builder baselinePurchaseIntentScore(double value) {
            this.baselinePurchaseIntentScore = value;
            return this;
        }

        public Builder purchaseIntentLift(double value) {
            this.purchaseIntentLift = value;
            return this;
        }

        public Builder randomSeed(long value) {
            this.randomSeed = value;
            return this;
        }

        public CampaignScenario build() {
            return new CampaignScenario(
                    campaignName,
                    brandName,
                    numberOfUsers,
                    exposureRate,
                    duplicateExposureRate,
                    lateSurveyResponseRate,
                    baselineAwarenessScore,
                    awarenessLift,
                    baselineConsiderationScore,
                    considerationLift,
                    baselinePurchaseIntentScore,
                    purchaseIntentLift,
                    randomSeed);
        }
    }
}
