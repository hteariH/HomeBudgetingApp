package com.homebudget.app.admin

import com.homebudget.app.repository.UserRepository
import com.homebudget.app.service.AuthFlowService
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.stereotype.Component
import kotlin.system.exitProcess

/**
 * `personalmoney reset-password <email>` inside the container: the way back in for an
 * administrator who has lost their own password on an instance with no mail.
 *
 * Runs as a second, non-web start of the same jar against the same database, prints the
 * link and exits. Does nothing on a normal start.
 */
@Component
class ResetPasswordCommand(
    private val users: UserRepository,
    private val authFlow: AuthFlowService,
    private val context: ConfigurableApplicationContext,
    @Value("\${app.base-url:}") private val baseUrl: String
) : ApplicationRunner {

    fun linkFor(email: String): String? {
        val user = users.findByEmail(email.trim()).orElse(null) ?: return null
        val link = authFlow.issueAdminResetLink(user)
        return if (baseUrl.isBlank()) link.path else baseUrl.trimEnd('/') + link.path
    }

    override fun run(args: ApplicationArguments) {
        val email = args.getOptionValues("reset-password")?.firstOrNull() ?: return
        val link = linkFor(email)
        val code = if (link == null) {
            System.err.println("No account uses the address $email")
            1
        } else {
            println("Password reset link for $email, valid for 24 hours and usable once:")
            println(link)
            if (baseUrl.isBlank()) println("(Prefix it with the address you open PersonalMoney at.)")
            0
        }
        exitProcess(SpringApplication.exit(context, { code }))
    }
}
