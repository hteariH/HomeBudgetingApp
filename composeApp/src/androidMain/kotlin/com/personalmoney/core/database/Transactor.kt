package com.personalmoney.core.database

/**
 * Runs a block inside a single database transaction. Abstracted behind an interface so repositories
 * stay unit-testable (a fake can run the block directly without a real Room database), while the
 * production binding delegates to Room's `withTransaction`.
 */
interface Transactor {
    suspend fun <R> transaction(block: suspend () -> R): R
}
