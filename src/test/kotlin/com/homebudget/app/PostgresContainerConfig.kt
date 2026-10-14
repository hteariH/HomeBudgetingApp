package com.homebudget.app

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.PostgreSQLContainer

/**
 * A real Postgres for the tests that must prove the dialect, not just the model.
 *
 * H2 in PostgreSQL mode runs the bulk of the suite because it needs no Docker; it is close
 * to Postgres but not the same engine, and the migrations ship to Postgres alone.
 */
@TestConfiguration(proxyBeanMethods = false)
class PostgresContainerConfig {
    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17")
}
