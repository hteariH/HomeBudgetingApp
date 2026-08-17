package com.homebudget.app.service

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * Startup hook kept as a seam for future bootstrapping.
 *
 * Currency seeding used to live here as a 12-entry hardcoded list. It now lives in
 * the V3 migration, which carries the full 104-currency catalog shared with the
 * Android client - one source of truth, applied before the context is up rather than
 * after, and versioned like the rest of the schema.
 *
 * Per-user categories are seeded on registration by [CategoryService.seedDefaultCategories];
 * they cannot be seeded here because every category belongs to a user.
 */
@Component
class DataInitializer : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        // Nothing to bootstrap globally.
    }
}
