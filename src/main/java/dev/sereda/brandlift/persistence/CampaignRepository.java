package dev.sereda.brandlift.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Explicit JDBC persistence for campaigns. We hand-write the SQL and row mapping
 * on purpose: it keeps the persistence boundary obvious and easy to read, with no
 * mapping magic to reason about. created_at / updated_at are left to the database.
 */
@Repository
public class CampaignRepository {

    private final JdbcClient jdbcClient;

    public CampaignRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(Campaign campaign) {
        jdbcClient.sql("""
                insert into campaigns (id, name, brand_name, starts_at, ends_at)
                values (:id, :name, :brandName, :startsAt, :endsAt)
                """)
                .param("id", campaign.id())
                .param("name", campaign.name())
                .param("brandName", campaign.brandName())
                .param("startsAt", campaign.startsAt().atOffset(ZoneOffset.UTC))
                .param("endsAt", campaign.endsAt().atOffset(ZoneOffset.UTC))
                .update();
    }

    public Optional<Campaign> findById(UUID id) {
        return jdbcClient.sql("select * from campaigns where id = :id")
                .param("id", id)
                .query(CampaignRepository::mapRow)
                .optional();
    }

    /** Newest campaigns first. No pagination yet; the dataset is small at this stage. */
    public List<Campaign> findAll() {
        return jdbcClient.sql("select * from campaigns order by created_at desc")
                .query(CampaignRepository::mapRow)
                .list();
    }

    private static Campaign mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Campaign(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("brand_name"),
                rs.getObject("starts_at", OffsetDateTime.class).toInstant(),
                rs.getObject("ends_at", OffsetDateTime.class).toInstant(),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
