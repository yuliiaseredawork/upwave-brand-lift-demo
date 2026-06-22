package dev.sereda.brandlift.insight;

import dev.sereda.brandlift.persistence.CampaignLiftSummary;

/**
 * Boundary for turning a computed {@link CampaignLiftSummary} into a customer-facing
 * {@link CampaignInsight}. This interface is the seam where AI plugs in.
 *
 * <p>The current implementation is deterministic and offline
 * ({@link DeterministicCampaignInsightGenerator}). A future LLM-backed implementation
 * would, behind this same method:
 * <ul>
 *   <li><b>Input:</b> shape the summary's structured numbers (counts and the three
 *       lift values) into a prompt — not free text from end users.</li>
 *   <li><b>Output:</b> expect a structured response matching {@link CampaignInsight}
 *       (a short summary plus bounded lists), which we would parse rather than render
 *       raw model text.</li>
 *   <li><b>Fallback:</b> on timeout, error, or unusable output, fall back to the
 *       deterministic generator so the endpoint always returns a sane, grounded
 *       answer. Keeping the contract a plain interface makes that swap/fallback a
 *       wiring decision, not a redesign.</li>
 * </ul>
 */
public interface CampaignInsightGenerator {

    CampaignInsight generate(CampaignLiftSummary summary);
}
