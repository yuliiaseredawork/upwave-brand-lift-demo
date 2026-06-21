package dev.sereda.brandlift.persistence;

import java.time.Instant;
import java.util.UUID;

/**
 * A campaign row. {@code createdAt} and {@code updatedAt} are assigned by the
 * database (column defaults), so they are {@code null} on a value being inserted
 * and populated on a value read back from the database.
 */
public record Campaign(
        UUID id,
        String name,
        String brandName,
        Instant startsAt,
        Instant endsAt,
        Instant createdAt,
        Instant updatedAt) {
}
