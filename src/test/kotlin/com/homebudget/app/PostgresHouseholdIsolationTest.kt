package com.homebudget.app

import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.testcontainers.junit.jupiter.Testcontainers

/** The same isolation cases on the engine production runs. */
@Testcontainers(disabledWithoutDocker = true)
@Import(PostgresContainerConfig::class, TestIdentity::class)
@TestPropertySource(properties = ["spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"])
class PostgresHouseholdIsolationTest : HouseholdIsolationTest()
