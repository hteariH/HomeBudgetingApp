package com.homebudget.app.household

import com.homebudget.app.PostgresContainerConfig
import com.homebudget.app.TestIdentity
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.testcontainers.junit.jupiter.Testcontainers

/** The same join cases on the engine production runs: the merge is native SQL. */
@Testcontainers(disabledWithoutDocker = true)
@Import(PostgresContainerConfig::class, TestIdentity::class)
@TestPropertySource(properties = ["spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"])
class PostgresJoinHouseholdTest : JoinHouseholdTest()
