package com.homebudget.app.config

import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.math.BigDecimal

@Configuration
class JacksonConfig {

    /**
     * Every [BigDecimal] goes over the wire as a string.
     *
     * As a JSON number, `1000.00` is parsed by most clients into a double on the way in
     * - which is the one thing not to do with money - and it also loses trailing zeros,
     * so a client cannot tell 2-decimal from 0-decimal currencies from the value alone.
     * The Android client parses these back into integer minor units and would rather be
     * handed the exact digits the server meant.
     *
     * Scoped to BigDecimal on purpose: `write-numbers-as-strings` would stringify ids
     * and counts too, which no client expects.
     */
    @Bean
    fun bigDecimalAsStringModule(): SimpleModule =
        SimpleModule().addSerializer(BigDecimal::class.java, ToStringSerializer.instance)
}
