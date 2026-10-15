package com.personalmoney.feature.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.auth.TokenStore
import com.personalmoney.core.household.HouseholdMembers
import com.personalmoney.core.network.HomeBudgetApi
import com.personalmoney.core.network.MemberDto
import com.personalmoney.core.network.serverMessage
import com.personalmoney.core.sync.SyncTrigger
import com.personalmoney.resources.Res
import com.personalmoney.resources.common_request_failed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

data class FamilyUiState(
    val members: List<MemberDto> = emptyList(),
    val isOwner: Boolean = false,
    /** The link to hand out, set once an invitation has been made. */
    val inviteLink: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

/**
 * The household's people, the owner's invitation link, and leaving.
 *
 * Whether the signed-in user owns the household is read from the server rather than worked
 * out from the member list: the list identifies people by user id, and the phone does not
 * otherwise keep its own.
 */
class FamilyViewModel(
    private val api: HomeBudgetApi,
    private val members: HouseholdMembers,
    private val tokens: TokenStore,
    private val sync: SyncTrigger,
) : ViewModel() {

    private data class Local(
        val isOwner: Boolean = false,
        val inviteLink: String? = null,
        val busy: Boolean = true,
        val error: String? = null,
    )

    private val local = MutableStateFlow(Local())

    val state: StateFlow<FamilyUiState> = combine(members.members, local) { list, l ->
        FamilyUiState(list, l.isOwner, l.inviteLink, l.busy, l.error)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FamilyUiState(busy = true))

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val role = try {
            api.me().householdRole
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        members.refresh()
        local.update { it.copy(isOwner = role == "OWNER", busy = false) }
    }

    /** Asks the server for a fresh single-use link. Only the owner may. */
    fun invite() {
        val current = local.value
        if (!current.isOwner || current.busy) return
        local.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val created = api.createInvite()
                val server = tokens.serverUrl?.trimEnd('/')
                    ?: error("No server address to build the link on")
                local.update { it.copy(busy = false, inviteLink = server + created.path) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /**
     * Leaves the household for one of one's own. The sync that follows is what makes the
     * phone notice: it sees the new household and replaces the local ledger.
     */
    fun leave() {
        if (local.value.busy) return
        local.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                api.leaveHousehold()
                sync.requestSync()
                load()
                local.update { it.copy(inviteLink = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun clearError() = local.update { it.copy(error = null) }

    /** The link has been handed to the share sheet; it is not shown again. */
    fun inviteShared() = local.update { it.copy(inviteLink = null) }

    private suspend fun fail(e: Exception) {
        val message = e.serverMessage() ?: getString(Res.string.common_request_failed)
        local.update { it.copy(busy = false, error = message) }
    }
}
