package dev.sereda.brandlift.simulation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates a deterministic synthetic dataset from a {@link CampaignScenario}.
 *
 * <p>Determinism: all randomness comes from a single {@link Random} seeded with
 * {@code scenario.randomSeed()}, and ids are derived by hashing stable inputs. The
 * same scenario therefore always produces an equal dataset, which is what makes the
 * data usable in tests and reproducible bug reports.
 *
 * <p>The model is deliberately simple: each user is independently exposed or not,
 * exposed users get a few impressions (occasionally redelivered as duplicates), and
 * everyone answers one survey whose scores are baseline (+ lift if exposed) plus a
 * little noise. It is meant to exercise backend concerns, not to be statistically rigorous.
 */
public class SyntheticBrandLiftGenerator {

    // A fixed window keeps generated timestamps stable across runs.
    private static final Instant CAMPAIGN_START = Instant.parse("2025-01-01T00:00:00Z");
    private static final Duration CAMPAIGN_LENGTH = Duration.ofDays(14);
    private static final Duration LATE_RESPONSE_GRACE = Duration.ofDays(7);

    private static final int MAX_EXPOSURES_PER_USER = 3;
    private static final double SCORE_NOISE = 5.0; // +/- points of per-respondent variation

    public SyntheticBrandLiftDataset generate(CampaignScenario scenario) {
        Random random = new Random(scenario.randomSeed());
        GeneratedCampaign campaign = buildCampaign(scenario);

        List<GeneratedAdExposureEvent> exposureEvents = new ArrayList<>();
        List<GeneratedSurveyResponse> surveyResponses = new ArrayList<>();

        long windowSeconds = CAMPAIGN_LENGTH.getSeconds();

        for (int i = 0; i < scenario.numberOfUsers(); i++) {
            String userId = hashedUserId(scenario.randomSeed(), i);
            boolean exposed = random.nextDouble() < scenario.exposureRate();

            if (exposed) {
                int impressions = 1 + random.nextInt(MAX_EXPOSURES_PER_USER);
                for (int impression = 0; impression < impressions; impression++) {
                    GeneratedAdExposureEvent event =
                            buildExposureEvent(campaign, userId, impression, random, windowSeconds);
                    exposureEvents.add(event);

                    // At-least-once delivery: the same event is sometimes redelivered.
                    if (random.nextDouble() < scenario.duplicateExposureRate()) {
                        exposureEvents.add(event); // identical, same idempotencyKey
                    }
                }
            }

            surveyResponses.add(
                    buildSurveyResponse(scenario, campaign, userId, exposed, random, windowSeconds));
        }

        return new SyntheticBrandLiftDataset(campaign, exposureEvents, surveyResponses);
    }

    private GeneratedCampaign buildCampaign(CampaignScenario scenario) {
        String campaignId = "camp-" + hash(scenario.campaignName() + ":" + scenario.randomSeed()).substring(0, 12);
        return new GeneratedCampaign(
                campaignId,
                scenario.campaignName(),
                scenario.brandName(),
                CAMPAIGN_START,
                CAMPAIGN_START.plus(CAMPAIGN_LENGTH));
    }

    private GeneratedAdExposureEvent buildExposureEvent(
            GeneratedCampaign campaign, String userId, int impression, Random random, long windowSeconds) {
        Channel channel = Channel.values()[random.nextInt(Channel.values().length)];
        Instant exposedAt = campaign.startedAt().plusSeconds(randomOffsetSeconds(random, windowSeconds));
        String idempotencyKey = hash(campaign.campaignId() + ":" + userId + ":" + impression);
        return new GeneratedAdExposureEvent(idempotencyKey, campaign.campaignId(), userId, channel, exposedAt);
    }

    private GeneratedSurveyResponse buildSurveyResponse(
            CampaignScenario scenario,
            GeneratedCampaign campaign,
            String userId,
            boolean exposed,
            Random random,
            long windowSeconds) {

        double awareness = score(scenario.baselineAwarenessScore(), exposed ? scenario.awarenessLift() : 0.0, random);
        double consideration = score(scenario.baselineConsiderationScore(), exposed ? scenario.considerationLift() : 0.0, random);
        double purchaseIntent = score(scenario.baselinePurchaseIntentScore(), exposed ? scenario.purchaseIntentLift() : 0.0, random);

        boolean late = random.nextDouble() < scenario.lateSurveyResponseRate();
        Instant respondedAt = late
                ? campaign.endedAt().plusSeconds(randomOffsetSeconds(random, LATE_RESPONSE_GRACE.getSeconds()))
                : campaign.startedAt().plusSeconds(randomOffsetSeconds(random, windowSeconds));

        return new GeneratedSurveyResponse(
                campaign.campaignId(), userId, exposed, awareness, consideration, purchaseIntent, respondedAt, late);
    }

    /** Baseline plus lift plus small symmetric noise, clamped to a valid 0..100 score. */
    private double score(double baseline, double lift, Random random) {
        double noise = (random.nextDouble() * 2.0 - 1.0) * SCORE_NOISE;
        return clamp(baseline + lift + noise);
    }

    private static long randomOffsetSeconds(Random random, long windowSeconds) {
        return (long) (random.nextDouble() * windowSeconds);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    private static String hashedUserId(long seed, int index) {
        return "user-" + hash(seed + ":" + index).substring(0, 16);
    }

    /** Hex SHA-256 of the input. Used to derive stable, opaque-looking ids and keys. */
    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but not available", ex);
        }
    }
}
