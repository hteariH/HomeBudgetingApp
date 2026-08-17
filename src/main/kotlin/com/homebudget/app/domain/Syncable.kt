package com.homebudget.app.domain

import java.time.Instant
import java.util.UUID

/**
 * A row that takes part in the two-way sync protocol.
 *
 * [uuid] is the identity clients use. It matters because a phone creates rows while
 * offline: a transaction can reference an account that has no server id yet, and both
 * arrive in the same push. Server ids stay as the primary key for foreign keys and
 * JPA, but never appear as a reference in a sync payload.
 *
 * [deleted] is a tombstone rather than a hard delete. A row that simply vanishes is
 * invisible to a client that was offline when it happened, which would resurrect it on
 * the next push.
 */
interface Syncable {
    val uuid: String
    var updatedAt: Instant
    var deleted: Boolean

    companion object {
        fun newUuid(): String = UUID.randomUUID().toString()
    }
}
