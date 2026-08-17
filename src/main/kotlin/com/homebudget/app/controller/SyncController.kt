package com.homebudget.app.controller

import com.homebudget.app.dto.SyncPullResponse
import com.homebudget.app.dto.SyncPushRequest
import com.homebudget.app.dto.SyncPushResponse
import com.homebudget.app.service.SyncService
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/api/sync")
class SyncController(private val service: SyncService) {

    /**
     * Everything that changed after [since], tombstones included.
     *
     * Pass the `serverTime` from the previous response as the next `since`; the very
     * first sync passes the epoch. When `hasMore` is true the page was capped, so pull
     * again straight away with the returned cursor.
     */
    @GetMapping("/pull")
    fun pull(
        @RequestParam(required = false) since: Instant?,
        @RequestParam(defaultValue = "500") limit: Int
    ): SyncPullResponse = service.pull(since ?: Instant.EPOCH, limit)

    @PostMapping("/push")
    fun push(@RequestBody request: SyncPushRequest): SyncPushResponse = service.push(request)
}
