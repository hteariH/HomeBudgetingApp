package com.homebudget.app.household

import org.springframework.web.bind.annotation.*

/** What someone holding a link may do with it. Preview is public; accepting needs a session. */
@RestController
@RequestMapping("/api/invites")
class InviteController(private val invites: InviteService) {
    @GetMapping("/{token}")
    fun preview(@PathVariable token: String) = invites.preview(token)
}
