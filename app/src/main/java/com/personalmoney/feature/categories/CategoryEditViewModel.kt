package com.personalmoney.feature.categories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.data.repository.CategoryRepository
import com.personalmoney.core.database.entity.CategoryEntity
import com.personalmoney.core.domain.CategoryType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryEditUiState(
    val id: Long? = null,
    val name: String = "",
    val type: CategoryType = CategoryType.EXPENSE,
    val icon: String = "more_horiz",
    val color: Long = 0xFF94A3B8L,
    val archived: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
) {
    val isEditing: Boolean get() = id != null
    val canSave: Boolean get() = name.isNotBlank() && !saving
}

@HiltViewModel
class CategoryEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CategoryRepository
) : ViewModel() {

    private val categoryId: Long? = savedStateHandle.get<String>("categoryId")?.toLongOrNull()
    private val initialType: CategoryType = runCatching {
        CategoryType.valueOf(savedStateHandle.get<String>("type") ?: "EXPENSE")
    }.getOrDefault(CategoryType.EXPENSE)

    private val _state = MutableStateFlow(CategoryEditUiState(id = categoryId, type = initialType))
    val uiState: StateFlow<CategoryEditUiState> = _state.asStateFlow()

    init {
        if (categoryId != null) {
            viewModelScope.launch {
                repository.findById(categoryId)?.let { c ->
                    _state.value = CategoryEditUiState(
                        id = c.id,
                        name = c.name,
                        type = c.type,
                        icon = c.icon,
                        color = c.color,
                        archived = c.archived
                    )
                }
            }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value) }
    fun setIcon(icon: String) = _state.update { it.copy(icon = icon) }
    fun setColor(color: Long) = _state.update { it.copy(color = color) }
    fun setType(type: CategoryType) = _state.update { it.copy(type = type) }
    fun setArchived(value: Boolean) = _state.update { it.copy(archived = value) }
    fun clearError() = _state.update { it.copy(error = null) }

    fun save() {
        val st = _state.value
        if (!st.canSave) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                if (st.id == null) {
                    repository.create(
                        CategoryEntity(
                            name = st.name.trim(),
                            type = st.type,
                            icon = st.icon,
                            color = st.color,
                            archived = st.archived
                        )
                    )
                } else {
                    val existing = repository.findById(st.id) ?: return@runCatching
                    repository.update(
                        existing.copy(
                            name = st.name.trim(),
                            type = st.type,
                            icon = st.icon,
                            color = st.color,
                            archived = st.archived
                        )
                    )
                }
            }.onSuccess { _state.update { it.copy(saving = false, saved = true) } }
                .onFailure { e -> _state.update { it.copy(saving = false, error = e.message) } }
        }
    }

    fun delete() {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            runCatching {
                val used = repository.usageCount(id)
                if (used > 0) repository.setArchived(id, true)
                else repository.delete(id)
            }
                .onSuccess { _state.update { it.copy(saved = true) } }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }
}
