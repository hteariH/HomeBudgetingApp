package com.personalmoney.core.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkerParameters
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

/**
 * Guards the property Hilt used to give us for free.
 *
 * Hilt failed the build when a dependency could not be satisfied. Koin resolves at
 * runtime, so without this test a missing definition would first show up as a crash on
 * a user's device. `verify` walks every definition's constructor and fails here instead.
 *
 * [extraTypes] lists what comes from outside the graph: Android hands workers their
 * [Context] and [WorkerParameters], and the ViewModel machinery hands ViewModels their
 * [SavedStateHandle].
 *
 * `HttpClient` is there for a different reason. The API classes are built by a lambda that
 * configures the client inline, so it is never resolved from Koin — but `verify` inspects
 * the constructor rather than the lambda and cannot tell the difference.
 */
class KoinModulesTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `every definition can be resolved`() {
        val extra = listOf(
            Context::class,
            WorkerParameters::class,
            SavedStateHandle::class,
            HttpClientEngine::class,
            HttpClient::class,
        )
        appModule.verify(extraTypes = extra)
    }
}
