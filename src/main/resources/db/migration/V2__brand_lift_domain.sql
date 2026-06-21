-- Brand lift domain schema.
--
-- Raw events (ad_exposure_events, survey_responses) are kept separate from the
-- computed campaign_lift_summaries: raw tables are append-only and write-heavy,
-- the summary table is small and read-heavy and is fully derivable from the raw
-- data. See docs/design-notes.md for the reasoning.

create table campaigns (
    id         uuid        primary key,
    name       text        not null,
    brand_name text        not null,
    starts_at  timestamptz not null,
    ends_at    timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint campaigns_window_valid check (ends_at > starts_at)
);

-- Raw ad exposures. High volume, append-only. idempotency_key is unique so that
-- at-least-once delivery / producer retries cannot insert the same logical event
-- twice (ingestion will dedup on it in a later step).
create table ad_exposure_events (
    id                   uuid        primary key,
    campaign_id          uuid        not null references campaigns (id),
    user_id_hash         text        not null,
    channel              text        not null,
    creative_id          text,
    placement_id         text,
    impression_timestamp timestamptz not null,
    idempotency_key      text        not null,
    received_at          timestamptz not null default now(),
    is_duplicate         boolean     not null default false,
    constraint ad_exposure_events_idempotency_key_unique unique (idempotency_key),
    constraint ad_exposure_events_channel_valid
        check (channel in ('CTV', 'SOCIAL', 'DISPLAY', 'STREAMING_AUDIO', 'RETAIL_MEDIA', 'LINEAR_TV'))
);

-- Reads are by campaign (compute lift), by user (join to survey), and by time
-- (windowing / late-data handling), so we index those access paths.
create index ad_exposure_events_campaign_id_idx          on ad_exposure_events (campaign_id);
create index ad_exposure_events_user_id_hash_idx         on ad_exposure_events (user_id_hash);
create index ad_exposure_events_impression_timestamp_idx on ad_exposure_events (impression_timestamp);

-- Raw survey responses. Scores are bounded 0..100 at the database level so bad
-- input is rejected at the storage boundary, not just in application code.
create table survey_responses (
    id                    uuid          primary key,
    campaign_id           uuid          not null references campaigns (id),
    user_id_hash          text          not null,
    exposed               boolean       not null,
    awareness_score       numeric(5, 2) not null,
    consideration_score   numeric(5, 2) not null,
    purchase_intent_score numeric(5, 2) not null,
    response_timestamp    timestamptz   not null,
    received_at           timestamptz   not null default now(),
    is_late               boolean       not null default false,
    constraint survey_responses_awareness_score_range       check (awareness_score between 0 and 100),
    constraint survey_responses_consideration_score_range   check (consideration_score between 0 and 100),
    constraint survey_responses_purchase_intent_score_range check (purchase_intent_score between 0 and 100)
);

create index survey_responses_campaign_id_idx       on survey_responses (campaign_id);
create index survey_responses_user_id_hash_idx      on survey_responses (user_id_hash);
create index survey_responses_response_timestamp_idx on survey_responses (response_timestamp);

-- Computed summary, one row per campaign (campaign_id is both PK and FK). Averages
-- and lift use a wider numeric so lift can be negative and keep three decimals.
-- This table is intentionally derivable from the raw events and can be recomputed.
create table campaign_lift_summaries (
    campaign_id                 uuid          primary key references campaigns (id),
    exposed_user_count          integer       not null,
    control_user_count          integer       not null,
    exposed_avg_awareness       numeric(6, 3),
    control_avg_awareness       numeric(6, 3),
    awareness_lift              numeric(6, 3),
    exposed_avg_consideration   numeric(6, 3),
    control_avg_consideration   numeric(6, 3),
    consideration_lift          numeric(6, 3),
    exposed_avg_purchase_intent numeric(6, 3),
    control_avg_purchase_intent numeric(6, 3),
    purchase_intent_lift        numeric(6, 3),
    calculated_at               timestamptz   not null default now()
);
