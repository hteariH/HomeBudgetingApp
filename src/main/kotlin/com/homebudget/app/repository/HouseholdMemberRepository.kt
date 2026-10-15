package com.homebudget.app.repository

import com.homebudget.app.domain.HouseholdMember
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface HouseholdMemberRepository : JpaRepository<HouseholdMember, Long> {
    fun findFirstByUserId(userId: Long): HouseholdMember?
    fun countByHouseholdId(householdId: Long): Long
    fun findAllByHouseholdId(householdId: Long): List<HouseholdMember>
    fun findAllByHouseholdIdOrderByJoinedAtAscIdAsc(householdId: Long): List<HouseholdMember>
}
