package com.homebudget.app.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "instance_settings")
data class InstanceSetting(
    // `key` and `value` are reserved words in H2, hence the prefixed column names.
    @Id
    @Column(name = "setting_key", length = 100)
    val key: String,

    @Column(name = "setting_value", nullable = false, length = 500)
    var value: String
)
