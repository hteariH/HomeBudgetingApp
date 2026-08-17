package com.homebudget.app.controller

import com.homebudget.app.dto.SyncPayload
import com.homebudget.app.service.RefreshTokenService
import com.homebudget.app.service.SyncService
import com.homebudget.app.service.UserService
import com.homebudget.app.service.toResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService,
    private val syncService: SyncService,
    private val refreshTokenService: RefreshTokenService
) {

    @GetMapping("/me")
    fun getCurrentUser() = userService.getCurrentUser().toResponse()

    @PutMapping("/me/base-currency")
    fun updateBaseCurrency(@RequestParam currencyId: Long) =
        userService.updateBaseCurrency(currencyId).toResponse()

    /**
     * Everything this account holds, in the sync wire format.
     *
     * Reuses the sync payload rather than inventing a second export shape - it already
     * covers every table a user owns and both clients already understand it.
     */
    @GetMapping("/me/export")
    fun exportMyData(): SyncPayload = syncService.pull(Instant.EPOCH, SyncService.MAX_PAGE).changes

    /**
     * Deletes the account and everything in it, irreversibly.
     *
     * A real delete rather than a tombstone: tombstones exist so other clients learn
     * about a deletion, and there is no client left to tell.
     */
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMyAccount() {
        val user = userService.getCurrentUser()
        refreshTokenService.revokeAll(user)
        userService.deleteCurrentUser()
    }
}
