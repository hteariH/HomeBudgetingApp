package com.personalmoney

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.personalmoney.core.currency.CurrencyRegistry
import com.personalmoney.core.currency.FxScheduler
import com.personalmoney.core.data.repository.CurrencyRepository
import com.personalmoney.core.database.seed.DatabaseSeeder
import com.personalmoney.feature.subscriptions.SubscriptionWorkScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PersonalMoneyApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var seeder: DatabaseSeeder
    @Inject lateinit var fxScheduler: FxScheduler
    @Inject lateinit var subscriptionScheduler: SubscriptionWorkScheduler
    @Inject lateinit var currencyRepository: CurrencyRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            seeder.seedIfEmpty()
            fxScheduler.schedule()
            subscriptionScheduler.schedule()
        }
        appScope.launch {
            currencyRepository.observeAll().collect { CurrencyRegistry.update(it) }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
