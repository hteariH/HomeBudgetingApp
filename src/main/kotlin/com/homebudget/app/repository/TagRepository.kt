package com.homebudget.app.repository

import com.homebudget.app.domain.Tag
import com.homebudget.app.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface TagRepository : JpaRepository<Tag, Long> {
    fun findAllByUser(user: User): List<Tag>
    fun findByIdAndUser(id: Long, user: User): Tag?
    fun findByUuidAndUser(uuid: String, user: User): Tag?
    fun findByUserAndNameIgnoreCase(user: User, name: String): Tag?

    @Query(
        value = "SELECT * FROM tags WHERE user_id = :userId AND updated_at > :since ORDER BY updated_at, id LIMIT :limit",
        nativeQuery = true
    )
    fun findChangedSince(userId: Long, since: Instant, limit: Int): List<Tag>

    @Query(value = "SELECT * FROM tags WHERE uuid = :uuid AND user_id = :userId", nativeQuery = true)
    fun findByUuidIncludingDeleted(uuid: String, userId: Long): Tag?
}
