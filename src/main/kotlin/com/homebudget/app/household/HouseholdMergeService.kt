package com.homebudget.app.household

import com.homebudget.app.domain.Household
import com.homebudget.app.domain.HouseholdMember
import com.homebudget.app.domain.HouseholdRole
import com.homebudget.app.domain.User
import com.homebudget.app.repository.HouseholdMemberRepository
import com.homebudget.app.repository.HouseholdRepository
import com.homebudget.app.service.SyncClock
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Moves a user into the household an invitation names. One transaction; both households'
 * sync locks are taken lowest id first, the same lock a sync push takes, so no push and
 * no second join can interleave.
 */
@Service
class HouseholdMergeService(
    private val households: HouseholdRepository,
    private val members: HouseholdMemberRepository,
    private val invites: InviteService,
    private val syncClock: SyncClock,
    private val em: EntityManager
) {
    /**
     * Makes [user] a `MEMBER` of [invite]'s household.
     *
     * - A sole member brings their books: every row their household holds moves into the
     *   family, and the emptied household is deleted.
     * - A `MEMBER` of a household shared with others moves alone: the books stay with that
     *   family, as they would if the user had left it.
     * - The `OWNER` of a household shared with others is refused (400): ownership has to be
     *   handed over or the members removed first.
     */
    @Transactional
    fun join(user: User, invite: HouseholdInvite): Household {
        val fromId = members.findFirstByUserId(user.id)?.household?.id ?: throw IllegalStateException("No household")
        val toId = invite.household.id
        require(fromId != toId) { "You are already in this household" }

        listOf(fromId, toId).sorted().forEach { households.lockById(it) }
        // Re-read under the lock: a second click, another join or an ownership change may
        // have landed meanwhile. Native, because the persistence context still holds the
        // membership as it was read before the lock.
        val (lockedFromId, role) = membershipOf(user.id)
        require(lockedFromId == fromId) { "Your household changed while joining. Try again." }
        val sharedWithOthers = members.countByHouseholdId(fromId) > 1
        require(role != HouseholdRole.OWNER || !sharedWithOthers) {
            "You own a household that others share. Hand ownership over or remove its members first."
        }
        val live = invites.refreshLive(invite.id)
        em.flush()

        if (!sharedWithOthers) moveBooks(fromId, toId)
        exec("DELETE FROM household_members WHERE user_id = :user", "user" to user.id)
        if (!sharedWithOthers) exec("DELETE FROM households WHERE id = :from", "from" to fromId)
        em.clear()

        members.save(HouseholdMember(household = households.findById(toId).orElseThrow(), user = user, role = HouseholdRole.MEMBER))
        invites.markUsed(live, user)
        return households.findById(toId).orElseThrow()
    }

    private fun membershipOf(userId: Long): Pair<Long, HouseholdRole> {
        val row = em.createNativeQuery("SELECT household_id, role FROM household_members WHERE user_id = :user")
            .setParameter("user", userId).resultList.firstOrNull() as Array<*>?
            ?: throw IllegalStateException("No household")
        return (row[0] as Number).toLong() to HouseholdRole.valueOf(row[1] as String)
    }

    /**
     * Every row [from] holds becomes [to]'s, stamped later than anything either has, so
     * the family's next pull from any cursor picks it up.
     */
    private fun moveBooks(from: Long, to: Long) {
        // Bound as an Instant, like the sync repositories' native queries do: Hibernate then
        // writes it as UTC (hibernate.jdbc.time_zone). A java.sql.Timestamp is written as
        // the JVM zone's wall-clock instead, and lands hours off on a non-UTC host.
        val stamp = syncClock.next(from, to)
        mergeCategories(from, to, stamp)
        listOf("accounts", "subscriptions", "transactions").forEach { table ->
            exec("UPDATE $table SET household_id = :to, updated_at = :stamp WHERE household_id = :from",
                "to" to to, "stamp" to stamp, "from" to from)
        }
        exec("UPDATE bank_connections SET household_id = :to WHERE household_id = :from", "to" to to, "from" to from)
        exec(
            """UPDATE household_currency_settings SET household_id = :to, updated_at = :stamp
               WHERE household_id = :from AND currency_id NOT IN
                 (SELECT currency_id FROM household_currency_settings WHERE household_id = :to)""",
            "to" to to, "stamp" to stamp, "from" to from
        )
        exec("DELETE FROM household_currency_settings WHERE household_id = :from", "from" to from)
        exec(
            """UPDATE exchange_rates r SET household_id = :to
               WHERE r.household_id = :from AND NOT EXISTS
                 (SELECT 1 FROM exchange_rates t WHERE t.household_id = :to
                   AND t.base_code = r.base_code AND t.quote_code = r.quote_code)""",
            "to" to to, "from" to from
        )
        exec("DELETE FROM exchange_rates WHERE household_id = :from", "from" to from)
    }

    /**
     * A joiner's category with the same name (case-insensitive), type and parent as one
     * the family has is the same category: its rows are repointed and it is tombstoned.
     * Parents first, so a child compares against its already-resolved parent.
     */
    private fun mergeCategories(from: Long, to: Long, stamp: Instant) {
        @Suppress("UNCHECKED_CAST")
        val rows = em.createNativeQuery(
            "SELECT id, name, type, parent_id FROM categories WHERE household_id = :from AND deleted = false ORDER BY parent_id NULLS FIRST, id"
        ).setParameter("from", from).resultList as List<Array<Any?>>
        val resolved = mutableMapOf<Long, Long>()
        for (row in rows) {
            val id = (row[0] as Number).toLong()
            val name = row[1] as String
            val type = row[2] as String
            val parent = (row[3] as Number?)?.toLong()?.let { resolved[it] ?: it }
            val match = findFamilyCategory(to, name, type, parent) ?: continue
            resolved[id] = match
            exec("UPDATE transactions SET category_id = :t WHERE category_id = :s", "t" to match, "s" to id)
            exec("UPDATE subscriptions SET category_id = :t WHERE category_id = :s", "t" to match, "s" to id)
            exec("UPDATE categories SET parent_id = :t WHERE parent_id = :s", "t" to match, "s" to id)
            exec(
                """INSERT INTO category_mcc_codes (category_id, mcc_code)
                   SELECT :t, mcc_code FROM category_mcc_codes WHERE category_id = :s
                   AND mcc_code NOT IN (SELECT mcc_code FROM category_mcc_codes WHERE category_id = :t)""",
                "t" to match, "s" to id
            )
            exec("UPDATE categories SET deleted = true WHERE id = :s", "s" to id)
        }
        exec("UPDATE categories SET household_id = :to, updated_at = :stamp WHERE household_id = :from",
            "to" to to, "stamp" to stamp, "from" to from)
    }

    /**
     * Two statements rather than `(:parent IS NULL AND …) OR parent_id = :parent`: Postgres
     * cannot type a parameter that is bound as null and only ever compared with IS NULL.
     */
    private fun findFamilyCategory(household: Long, name: String, type: String, parent: Long?): Long? {
        val sameParent = if (parent == null) "parent_id IS NULL" else "parent_id = :parent"
        val query = em.createNativeQuery(
            """SELECT id FROM categories WHERE household_id = :household AND deleted = false
               AND LOWER(name) = LOWER(:name) AND type = :type AND $sameParent ORDER BY id"""
        ).setParameter("household", household).setParameter("name", name).setParameter("type", type)
        if (parent != null) query.setParameter("parent", parent)
        return (query.resultList.firstOrNull() as Number?)?.toLong()
    }

    private fun exec(sql: String, vararg params: Pair<String, Any?>) {
        val q = em.createNativeQuery(sql)
        params.forEach { (k, v) -> q.setParameter(k, v) }
        q.executeUpdate()
    }
}
