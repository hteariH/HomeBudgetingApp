package com.homebudget.app

import com.homebudget.app.repository.CurrencyRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.testcontainers.junit.jupiter.Testcontainers

/** The migrations against the engine they ship to, validated by Hibernate as in production. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Import(PostgresContainerConfig::class)
@TestPropertySource(properties = ["spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"])
class PostgresSchemaTest {

    @Autowired private lateinit var currencyRepository: CurrencyRepository

    @Test
    fun `migrations build a schema the entities validate against on Postgres`() {
        assertEquals(104L, currencyRepository.count())
        assertEquals(8, currencyRepository.findByCode("BTC")?.decimals)
    }
}
