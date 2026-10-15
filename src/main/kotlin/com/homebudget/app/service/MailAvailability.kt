package com.homebudget.app.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Whether this instance can send mail at all.
 *
 * A self-hosted instance often has no SMTP server. Without one, nobody is asked to verify
 * an address they cannot receive mail at, and the clients hide "forgot password" in
 * favour of an administrator-issued reset link. A link in a mail is useless without an
 * absolute address, so APP_BASE_URL is part of "configured".
 */
@Component
class MailAvailability(
    @Value("\${spring.mail.host:}") host: String,
    @Value("\${app.mail.log-only:false}") logOnly: Boolean,
    @Value("\${app.base-url:}") baseUrl: String
) {
    val enabled: Boolean = host.isNotBlank() && baseUrl.isNotBlank() && !logOnly
}
