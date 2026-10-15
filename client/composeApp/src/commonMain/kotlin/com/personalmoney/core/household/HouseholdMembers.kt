package com.personalmoney.core.household

import com.personalmoney.core.network.HomeBudgetApi
import com.personalmoney.core.network.MemberDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Who is in the household, as far as the phone last heard.
 *
 * Transactions carry only their author's user id; this is what turns it into something a
 * person can read. It is kept in memory rather than the database because it is small,
 * cheap to ask for, and wrong the moment it is stale - and when the phone is offline, the
 * last answer is as good as any.
 */
class HouseholdMembers(private val api: HomeBudgetApi) {

    private val _members = MutableStateFlow<List<MemberDto>>(emptyList())
    val members: StateFlow<List<MemberDto>> = _members

    /** Asks the server again. A failure is not worth surfacing: the previous list stays. */
    suspend fun refresh() {
        try {
            _members.value = api.household().members
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Offline, signed out, or a server without households: keep what we had.
        }
    }

    /** Forgets everyone, so a different account on this phone never glimpses the last one's household. */
    fun clear() {
        _members.value = emptyList()
    }

    /**
     * Initials for [userId], or null when there is nothing worth showing: a household of
     * one has no one to tell the author apart from, and an unknown author has no name.
     */
    fun initialsFor(userId: Long?): String? = userId?.let { initialsByUser(_members.value)[it] }

    companion object {
        /** The same rule as [initialsFor], over a snapshot - what the transaction list uses. */
        fun initialsByUser(members: List<MemberDto>): Map<Long, String> =
            if (members.size <= 1) emptyMap()
            else members.associate { it.userId to initialsOf(it) }

        private fun initialsOf(member: MemberDto): String {
            val first = member.firstName.trim().firstOrNull()
            val last = member.lastName.trim().firstOrNull()
            val fromName = listOfNotNull(first, last).joinToString("")
            return fromName.ifEmpty { member.email.trim().take(1) }.uppercase()
        }
    }
}
