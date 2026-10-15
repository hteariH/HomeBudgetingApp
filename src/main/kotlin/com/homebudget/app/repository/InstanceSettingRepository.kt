package com.homebudget.app.repository

import com.homebudget.app.domain.InstanceSetting
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface InstanceSettingRepository : JpaRepository<InstanceSetting, String>
