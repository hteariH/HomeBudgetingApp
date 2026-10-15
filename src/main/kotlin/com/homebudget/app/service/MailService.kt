package com.homebudget.app.service

import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

/**
 * An interface, not a class, so tests can substitute a recording implementation
 * instead of mocking - which also keeps "send by SMTP" and "write to the log"
 * as two named things rather than a boolean inside one.
 */
interface MailService {
    fun sendVerification(to: String, token: String)
    fun sendPasswordReset(to: String, token: String)
}

/**
 * Sends the two transactional emails over SMTP.
 *
 * Plain SMTP through [JavaMailSender] rather than a provider's HTTP SDK: every
 * transactional service (Resend, Postmark, Brevo, Mailgun, SES) exposes SMTP, so
 * changing provider is four environment variables and no code.
 *
 * Sending is [Async] and never propagates a failure to the caller. Registration must
 * not fail because a mail server is slow, and a password-reset request must answer
 * identically whether or not anything was actually sent - see
 * [AuthFlowService.requestPasswordReset].
 */
@Service
class SmtpMailService(
    // Optional: with `log-only` there is no SMTP configured at all.
    private val mailSender: ObjectProvider<JavaMailSender>,
    @Value("\${app.mail.from:noreply@localhost}") private val from: String,
    @Value("\${app.mail.log-only:false}") private val logOnly: Boolean,
    @Value("\${app.base-url:}") private val baseUrl: String
) : MailService {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    override fun sendVerification(to: String, token: String) {
        val link = "$baseUrl/verify-email?token=$token"
        send(
            to = to,
            subject = "Confirm your PersonalMoney address",
            body = """
                <p>Confirm this address to finish setting up your PersonalMoney account.</p>
                <p><a href="$link">Confirm my address</a></p>
                <p>The link is good for 24 hours. If you did not sign up, ignore this message.</p>
            """.trimIndent()
        )
    }

    @Async
    override fun sendPasswordReset(to: String, token: String) {
        val link = "$baseUrl/reset-password?token=$token"
        send(
            to = to,
            subject = "Reset your PersonalMoney password",
            body = """
                <p>Someone asked to reset the password for this address.</p>
                <p><a href="$link">Choose a new password</a></p>
                <p>The link is good for one hour and can be used once. If this was not you,
                nothing has changed and you can ignore this message.</p>
            """.trimIndent()
        )
    }

    private fun send(to: String, subject: String, body: String) {
        val sender = mailSender.ifAvailable
        if (logOnly || sender == null) {
            // Development, and the safety net if SMTP is unconfigured in production:
            // the flow stays walkable and the link is recoverable from the log.
            log.info("[mail:log-only] to={} subject={}\n{}", to, subject, body)
            return
        }
        try {
            val message: MimeMessage = sender.createMimeMessage()
            MimeMessageHelper(message, "UTF-8").apply {
                setFrom(from)
                setTo(to)
                setSubject(subject)
                setText(body, true)
            }
            sender.send(message)
            log.info("Sent '{}' to {}", subject, to)
        } catch (e: Exception) {
            // Swallowed on purpose. A registration that fails because the mail server
            // hiccuped is worse than an account whose confirmation has to be resent.
            log.error("Could not send '{}' to {}: {}", subject, to, e.message)
        }
    }
}
