package com.personalmoney.widget

import com.personalmoney.core.data.repository.AccountRepository
import com.personalmoney.core.data.repository.CategoryRepository
import com.personalmoney.core.data.repository.TransactionRepository
import org.koin.core.context.GlobalContext

/**
 * How the widget reaches the object graph.
 *
 * A Glance widget is built by the system, not by us, so there is no constructor to inject
 * into and no composition-local Koin scope to read. Pulling from the global Koin context
 * is the supported way in; keeping it behind this one type means the service-locator call
 * appears exactly once instead of at every widget call site.
 */
object WidgetDependencies {
    private val koin get() = GlobalContext.get()

    val transactionRepository: TransactionRepository get() = koin.get()
    val accountRepository: AccountRepository get() = koin.get()
    val categoryRepository: CategoryRepository get() = koin.get()
}
