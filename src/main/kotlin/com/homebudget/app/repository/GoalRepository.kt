package com.homebudget.app.repository

import com.homebudget.app.domain.SavingsGoal
import com.homebudget.app.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface GoalRepository : JpaRepository<SavingsGoal, Long> {
    fun findAllByUser(user: User): List<SavingsGoal>
    fun findByIdAndUser(id: Long, user: User): SavingsGoal?
    fun findByUuidAndUser(uuid: String, user: User): SavingsGoal?

    @Query(
        value = "SELECT * FROM savings_goals WHERE user_id = :userId AND updated_at > :since ORDER BY updated_at, id LIMIT :limit",
        nativeQuery = true
    )
    fun findChangedSince(userId: Long, since: Instant, limit: Int): List<SavingsGoal>

    @Query(value = "SELECT * FROM savings_goals WHERE uuid = :uuid AND user_id = :userId", nativeQuery = true)
    fun findByUuidIncludingDeleted(uuid: String, userId: Long): SavingsGoal?
}
