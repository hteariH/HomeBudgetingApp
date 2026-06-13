package com.personalmoney.feature.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.data.repository.AccountRepository
import com.personalmoney.core.database.entity.AccountEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AccountPickerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    accountRepository: AccountRepository
) : ViewModel() {

    /** Id of the account currently selected by the caller, for highlighting. */
    val selectedId: Long? = savedStateHandle.get<String>("selectedId")?.toLongOrNull()

    val accounts: StateFlow<List<AccountEntity>> =
        accountRepository.observeActive()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
