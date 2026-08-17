package com.homebudget.app

import com.homebudget.app.repository.CurrencyRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * The guard rail for every schema change from here on.
 *
 * The test profile runs Flyway from V1 against a clean in-memory DB and then asks
 * Hibernate to `validate` the result. A migration that drifts from the JPA entities -
 * a renamed column, a forgotten table, a type that no longer lines up - fails here
 * instead of on the first request against a real deployment.
 */
@SpringBootTest
@ActiveProfiles("test")
class SchemaConsistencyTest {

    @Autowired
    private lateinit var currencyRepository: CurrencyRepository

    @Test
    fun `migrations build a schema the entities validate against`() {
        // Reaching this point means the context started, so Flyway ran and
        // ddl-auto=validate accepted the schema. Touching a repository proves
        // the mapping is usable rather than merely present.
        assertEquals(104L, currencyRepository.count(), "currency catalog is incomplete")
    }

    @Test
    fun `catalog carries the per-currency decimals the money model depends on`() {
        // Minor-unit amounts are meaningless without the right exponent, and these
        // are exactly the cases the old DECIMAL(18,2) columns could not represent.
        assertEquals(0, currencyRepository.findByCode("JPY")?.decimals, "JPY has no minor unit")
        assertEquals(3, currencyRepository.findByCode("KWD")?.decimals, "Gulf dinars are 3-decimal")
        assertEquals(8, currencyRepository.findByCode("BTC")?.decimals, "BTC is 8-decimal")
        assertEquals(2, currencyRepository.findByCode("USD")?.decimals)
    }

    @Test
    fun `four character tickers fit the code column`() {
        // Regression guard: the column was VARCHAR(3) and silently excluded these.
        assertNotNull(currencyRepository.findByCode("USDT"), "USDT missing from catalog")
        assertNotNull(currencyRepository.findByCode("USDC"), "USDC missing from catalog")
    }
}
