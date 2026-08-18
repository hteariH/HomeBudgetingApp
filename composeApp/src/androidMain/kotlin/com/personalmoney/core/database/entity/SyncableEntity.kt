package com.personalmoney.core.database.entity

import java.util.UUID

/**
 * A row that takes part in sync with the server.
 *
 * The four fields exist for reasons that only show up offline:
 *
 * - [uuid] is the identity both sides agree on. Server ids cannot be, because a
 *   transaction entered in a shop with no signal has to reference an account before
 *   either row has ever reached the server.
 * - [updatedAt] is the *server's* timestamp for the version this row was last known to
 *   match, not a local clock reading. It goes back as `baseUpdatedAt`, and the server
 *   rejects the write if its own copy has moved on. 0 means never synced.
 * - [deleted] is a tombstone. Removing the row outright would leave the server holding
 *   a copy that comes back on the next pull.
 * - [pendingSync] marks a local edit the server has not accepted yet.
 */
interface SyncableEntity {
    val uuid: String
    val updatedAt: Long
    val deleted: Boolean
    val pendingSync: Boolean
}

fun newUuid(): String = UUID.randomUUID().toString()
