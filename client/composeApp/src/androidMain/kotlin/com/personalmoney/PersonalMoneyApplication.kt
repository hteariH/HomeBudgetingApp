package com.personalmoney

import android.app.Application
import com.personalmoney.core.billing.CurrentActivity
import com.personalmoney.core.billing.EntitlementRepository
import com.personalmoney.core.currency.CurrencyRegistry
import com.personalmoney.core.currency.FxScheduler
import com.personalmoney.core.data.repository.CurrencyRepository
import com.personalmoney.core.database.seed.DatabaseSeeder
import com.personalmoney.core.di.appModule
import com.personalmoney.core.sync.SyncScheduler
import com.personalmoney.feature.subscriptions.SubscriptionWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

class PersonalMoneyApplication : Application() {

    private val seeder: DatabaseSeeder by inject()
    private val fxScheduler: FxScheduler by inject()
    private val subscriptionScheduler: SubscriptionWorkScheduler by inject()
    private val currencyRepository: CurrencyRepository by inject()
    private val syncScheduler: SyncScheduler by inject()
    private val entitlements: EntitlementRepository by inject()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@PersonalMoneyApplication)
            // Supplies the WorkerFactory that builds workers with their dependencies.
            // The manifest already removes androidx.work's default initializer, so this
            // is the only thing initialising WorkManager.
            workManagerFactory()
            modules(appModule)
        }

        // Play's dialog needs an activity to open over, and the object that opens it
        // outlives them all - see CurrentActivity.
        CurrentActivity.track(this)

        appScope.launch {
            seeder.seedIfEmpty()
            fxScheduler.schedule()
            fxScheduler.refreshNow()
            subscriptionScheduler.schedule()
            // Catches changes made on the web, which have nothing on this device to
            // trigger a sync of their own.
            syncScheduler.schedule()
        }
        appScope.launch {
            // A subscription bought on a previous install is known to Play as soon as the
            // same Google account signs in, and the server can only learn about it if
            // something hands over the token. Doing it on launch is what makes a reinstall
            // and a second device simply work, with nothing for the user to press.
            entitlements.syncOwnedPurchases()
        }
        appScope.launch {
            currencyRepository.observeAll().collect { CurrencyRegistry.update(
                it.map { c -> CurrencyRegistry.Info(c.code, c.symbol, c.decimals) }
            ) }
        }
    }
}
