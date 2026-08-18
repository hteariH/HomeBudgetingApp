package com.personalmoney.feature.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.data.repository.CategoryRepository
import com.personalmoney.core.database.entity.CategoryEntity
import com.personalmoney.core.domain.CategoryType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoriesUiState(
    val selectedTab: CategoryType = CategoryType.EXPENSE,
    val expense: List<CategoryEntity> = emptyList(),
    val income: List<CategoryEntity> = emptyList()
)

class CategoriesViewModel constructor(
    private val repository: CategoryRepository
) : ViewModel() {

    private val selectedTab = MutableStateFlow(CategoryType.EXPENSE)

    val uiState: StateFlow<CategoriesUiState> = combine(
        selectedTab,
        repository.observeByType(CategoryType.EXPENSE),
        repository.observeByType(CategoryType.INCOME)
    ) { tab, expense, income ->
        CategoriesUiState(tab, expense, income)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    fun setTab(tab: CategoryType) { selectedTab.value = tab }

    fun delete(id: Long) {
        viewModelScope.launch {
            val used = repository.usageCount(id)
            if (used > 0) {
                repository.setArchived(id, true)
            } else {
                repository.delete(id)
            }
        }
    }

    fun setArchived(id: Long, archived: Boolean) {
        viewModelScope.launch { repository.setArchived(id, archived) }
    }
}
