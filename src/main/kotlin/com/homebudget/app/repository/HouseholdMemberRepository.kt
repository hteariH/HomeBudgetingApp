package com.homebudget.app.repository

import com.homebudget.app.domain.HouseholdMember
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface HouseholdMemberRepository : JpaRepository<HouseholdMember, Long> {
    fun findFirstByUserId(userId: Long): HouseholdMember?
    fun countByHouseholdId(householdId: Long): Long
    fun findAllByHouseholdId(householdId: Long): List<HouseholdMember>
    fun findAllByHouseholdIdOrderByJoinedAtAscIdAsc(householdId: Long): List<HouseholdMember>

    /**
     * The household [userId] belongs to, read from the database rather than the
     * persistence context: called after a lock, to see a move that committed meanwhile.
     */
    @Query(value = "SELECT household_id FROM household_members WHERE user_id = :userId", nativeQuery = true)
    fun currentHouseholdIdOf(userId: Long): List<Long>
}
