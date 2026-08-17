package com.homebudget.app.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import java.time.Clock

@Configuration
@EnableScheduling
// Mail goes out on a separate thread: a slow SMTP server must not hold up a
// registration or make a password-reset request take visibly longer for a
// registered address than an unregistered one.
@EnableAsync
class AppConfig {

    /**
     * Injected rather than called statically so that date-sensitive logic - subscription
     * catch-up in particular - can be tested at a fixed instant instead of "whatever
     * today happens to be when CI runs".
     *
     * UTC on purpose: the server has no business inheriting the host's timezone, and
     * every stored instant is UTC.
     */
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
