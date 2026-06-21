package dev.sereda.brandlift.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Explicit JDBC persistence for raw ad exposure events.
 *
 * <p>{@link #insert(AdExposureEvent)} is a plain insert: a second row with the same
 * idempotency_key violates the unique constraint and surfaces as a
 * {@link org.springframework.dao.DuplicateKeyException}. Dedup-on-conflict handling
 * lives in the ingestion layer (a later step); here we just expose the constraint.
 */
@Repository
public class AdExposureEventRepository {

    private final JdbcClient jdbcClient;

    public AdExposureEventRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(AdExposureEvent event) {
        jdbcClient.sql("""
                insert into ad_exposure_events
                    (id, campaign_id, user_id_hash, channel, creative_id, placement_id,
                     impression_timestamp, idempotency_key, is_duplicate)
                values
                    (:id, :campaignId, :userIdHash, :channel, :creativeId, :placementId,
                     :impressionTimestamp, :idempotencyKey, :duplicate)
                """)
                .param("id", event.id())
                .param("campaignId", event.campaignId())
                .param("userIdHash", event.userIdHash())
                .param("channel", event.channel())
                .param("creativeId", event.creativeId())
                .param("placementId", event.placementId())
                .param("impressionTimestamp", event.impressionTimestamp().atOffset(ZoneOffset.UTC))
                .param("idempotencyKey", event.idempotencyKey())
                .param("duplicate", event.duplicate())
                .update();
    }

    public Optional<AdExposureEvent> findById(UUID id) {
        return jdbcClient.sql("select * from ad_exposure_events where id = :id")
                .param("id", id)
                .query(AdExposureEventRepository::mapRow)
                .optional();
    }

    public Optional<AdExposureEvent> findByIdempotencyKey(String idempotencyKey) {
        return jdbcClient.sql("select * from ad_exposure_events where idempotency_key = :key")
                .param("key", idempotencyKey)
                .query(AdExposureEventRepository::mapRow)
                .optional();
    }

    private static AdExposureEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AdExposureEvent(
                rs.getObject("id", UUID.class),
                rs.getObject("campaign_id", UUID.class),
                rs.getString("user_id_hash"),
                rs.getString("channel"),
                rs.getString("creative_id"),
                rs.getString("placement_id"),
                rs.getObject("impression_timestamp", OffsetDateTime.class).toInstant(),
                rs.getString("idempotency_key"),
                rs.getObject("received_at", OffsetDateTime.class).toInstant(),
                rs.getBoolean("is_duplicate"));
    }
}
