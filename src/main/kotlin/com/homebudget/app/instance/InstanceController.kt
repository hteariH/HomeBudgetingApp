package com.homebudget.app.instance

import com.homebudget.app.bank.BankConnectionService
import com.homebudget.app.service.MailAvailability
import com.homebudget.app.service.RegistrationService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.info.BuildProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/instance")
class InstanceController(
    private val registration: RegistrationService,
    private val mail: MailAvailability,
    private val banks: BankConnectionService,
    private val build: ObjectProvider<BuildProperties>
) {
    companion object {
        const val API_VERSION = 1
    }

    @GetMapping
    fun get() = InstanceInfo(
        name = "PersonalMoney",
        version = build.ifAvailable?.version ?: "dev",
        apiVersion = API_VERSION,
        setupRequired = registration.setupRequired(),
        registrationOpen = registration.registrationOpen(),
        mailEnabled = mail.enabled,
        // Bank providers are beans that exist only when configured (Monobank corporate needs
        // a registered key); CSV files and the HomeMoney import need nothing.
        bankProviders = banks.availableProviders().map { it.id } + listOf("CSV", "HOMEMONEY")
    )
}
