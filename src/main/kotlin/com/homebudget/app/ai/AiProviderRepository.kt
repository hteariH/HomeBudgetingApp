package com.homebudget.app.ai

import org.springframework.data.jpa.repository.JpaRepository

interface AiProviderRepository : JpaRepository<AiProviderEntity, Long> {
    fun findByHouseholdId(householdId: Long): AiProviderEntity?
    fun findFirstByScope(scope: AiProviderScope): AiProviderEntity?
}
