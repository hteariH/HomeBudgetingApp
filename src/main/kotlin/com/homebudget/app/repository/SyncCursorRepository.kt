package com.homebudget.app.repository

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant

/**
 * The newest `updated_at` a user has on any syncable table.
 *
 * Exists so that [com.homebudget.app.service.SyncService] can stamp each push strictly
 * later than everything already stored. Without that guarantee two writes can land in
 * the same millisecond, and a `updated_at > cursor` pull silently skips the second one
 * for good - a change that never reaches the other client and never comes back.
 */
@Repository
class SyncCursorRepository(private val em: EntityManager) {

    fun latestUpdatedAt(userId: Long): Instant? {
        val sql = """
            SELECT MAX(m) FROM (
                SELECT MAX(updated_at) AS m FROM accounts               WHERE user_id = :uid
                UNION ALL
                SELECT MAX(updated_at) AS m FROM categories             WHERE user_id = :uid
                UNION ALL
                SELECT MAX(updated_at) AS m FROM subscriptions          WHERE user_id = :uid
                UNION ALL
                SELECT MAX(updated_at) AS m FROM transactions           WHERE user_id = :uid
                UNION ALL
                SELECT MAX(updated_at) AS m FROM user_currency_settings WHERE user_id = :uid
            ) x
        """.trimIndent()

        return when (val raw = em.createNativeQuery(sql).setParameter("uid", userId).singleResult) {
            null -> null
            is Timestamp -> raw.toInstant()
            is Instant -> raw
            is java.time.OffsetDateTime -> raw.toInstant()
            else -> null
        }
    }
}
