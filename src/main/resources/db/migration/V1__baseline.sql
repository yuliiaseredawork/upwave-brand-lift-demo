-- Baseline migration.
--
-- Intentionally empty of domain tables for now. This placeholder exists so that:
--   1. Flyway has a valid migration history from commit one, and
--   2. later schema changes (campaigns, exposures, survey_responses,
--      campaign_lift_summaries) are added as additive, reviewable V2.., V3.. files
--      rather than retrofitted into a single mega-migration.
--
-- The real domain schema lands in a later step, where raw events and computed
-- summaries are modeled as separate tables (see docs/design-notes.md).

SELECT 1;
