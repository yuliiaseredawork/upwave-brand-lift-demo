package dev.sereda.brandlift.simulation;

import java.util.List;

/**
 * The output of one generation run: a campaign plus its raw exposure events and
 * survey responses.
 *
 * <p>The counts are derived from the data on demand rather than stored, so they can
 * never drift out of sync with the lists. This is cheap at demo scale; if it ever
 * mattered we would precompute them. No lift summary is computed here — that is a
 * later step.
 */
public record SyntheticBrandLiftDataset(
        GeneratedCampaign campaign,
        List<GeneratedAdExposureEvent> exposureEvents,
        List<GeneratedSurveyResponse> surveyResponses) {

    public SyntheticBrandLiftDataset {
        exposureEvents = List.copyOf(exposureEvents);
        surveyResponses = List.copyOf(surveyResponses);
    }

    /** Every user answers exactly one survey, so total users == survey responses. */
    public int totalUsers() {
        return surveyResponses.size();
    }

    public int exposedUsers() {
        return (int) surveyResponses.stream().filter(GeneratedSurveyResponse::exposed).count();
    }

    public int controlUsers() {
        return totalUsers() - exposedUsers();
    }

    /** Number of exposure events beyond the first occurrence of each idempotency key. */
    public int duplicateExposureEvents() {
        long distinctKeys = exposureEvents.stream()
                .map(GeneratedAdExposureEvent::idempotencyKey)
                .distinct()
                .count();
        return exposureEvents.size() - (int) distinctKeys;
    }

    public int lateSurveyResponses() {
        return (int) surveyResponses.stream().filter(GeneratedSurveyResponse::late).count();
    }
}
