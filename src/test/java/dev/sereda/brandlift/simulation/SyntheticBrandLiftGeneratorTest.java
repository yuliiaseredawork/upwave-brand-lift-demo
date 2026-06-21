package dev.sereda.brandlift.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SyntheticBrandLiftGeneratorTest {

    private final SyntheticBrandLiftGenerator generator = new SyntheticBrandLiftGenerator();

    @Test
    void sameScenarioAndSeedProducesIdenticalDataset() {
        CampaignScenario scenario = CampaignScenario.builder().randomSeed(123L).build();

        SyntheticBrandLiftDataset first = generator.generate(scenario);
        SyntheticBrandLiftDataset second = generator.generate(scenario);

        // Records give us deep value equality across the campaign and both lists.
        assertThat(first).isEqualTo(second);
    }

    @Test
    void allSurveyScoresStayWithinZeroToHundred() {
        // High baseline + lift would exceed 100 before clamping, so this exercises the bound.
        CampaignScenario scenario = CampaignScenario.builder()
                .numberOfUsers(500)
                .baselineAwarenessScore(99.0)
                .awarenessLift(5.0)
                .baselinePurchaseIntentScore(0.0)
                .purchaseIntentLift(0.0)
                .build();

        SyntheticBrandLiftDataset dataset = generator.generate(scenario);

        assertThat(dataset.surveyResponses()).isNotEmpty();
        for (GeneratedSurveyResponse response : dataset.surveyResponses()) {
            assertThat(response.awarenessScore()).isBetween(0.0, 100.0);
            assertThat(response.considerationScore()).isBetween(0.0, 100.0);
            assertThat(response.purchaseIntentScore()).isBetween(0.0, 100.0);
        }
    }

    @Test
    void datasetContainsBothExposedAndControlUsers() {
        CampaignScenario scenario = CampaignScenario.builder()
                .numberOfUsers(1_000)
                .exposureRate(0.6)
                .build();

        SyntheticBrandLiftDataset dataset = generator.generate(scenario);

        assertThat(dataset.totalUsers()).isEqualTo(1_000);
        assertThat(dataset.exposedUsers()).isPositive();
        assertThat(dataset.controlUsers()).isPositive();
        assertThat(dataset.exposedUsers() + dataset.controlUsers()).isEqualTo(dataset.totalUsers());
    }

    @Test
    void duplicateExposureEventsAppearWhenConfigured() {
        CampaignScenario scenario = CampaignScenario.builder()
                .numberOfUsers(1_000)
                .exposureRate(1.0)
                .duplicateExposureRate(0.5)
                .build();

        SyntheticBrandLiftDataset dataset = generator.generate(scenario);

        assertThat(dataset.duplicateExposureEvents()).isPositive();
    }

    @Test
    void noDuplicateExposureEventsWhenRateIsZero() {
        CampaignScenario scenario = CampaignScenario.builder()
                .numberOfUsers(1_000)
                .exposureRate(1.0)
                .duplicateExposureRate(0.0)
                .build();

        SyntheticBrandLiftDataset dataset = generator.generate(scenario);

        assertThat(dataset.exposureEvents()).isNotEmpty();
        assertThat(dataset.duplicateExposureEvents()).isZero();
    }

    @Test
    void lateSurveyResponsesAppearWhenConfigured() {
        CampaignScenario scenario = CampaignScenario.builder()
                .numberOfUsers(1_000)
                .lateSurveyResponseRate(0.3)
                .build();

        SyntheticBrandLiftDataset dataset = generator.generate(scenario);

        assertThat(dataset.lateSurveyResponses()).isPositive();
        // Late responses must fall after the campaign window closes.
        dataset.surveyResponses().stream()
                .filter(GeneratedSurveyResponse::late)
                .forEach(response -> assertThat(response.respondedAt()).isAfter(dataset.campaign().endedAt()));
    }

    @Test
    void noLateSurveyResponsesWhenRateIsZero() {
        CampaignScenario scenario = CampaignScenario.builder()
                .numberOfUsers(1_000)
                .lateSurveyResponseRate(0.0)
                .build();

        SyntheticBrandLiftDataset dataset = generator.generate(scenario);

        assertThat(dataset.lateSurveyResponses()).isZero();
    }
}
