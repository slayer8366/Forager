package com.forager.app.domain

import com.forager.app.domain.model.CartographyEntry
import java.time.LocalDate
import java.util.UUID

/**
 * Starts a new, unwritten Cartography entry for [date] and persists it immediately as a draft — the
 * same "persist from the moment it's started" shape [CreateMushroomLogEntryUseCase] uses for a find,
 * so an entry survives a crash or an incidental exit from the trip-report/entry-writing flow the same
 * way an open find edit does.
 *
 * [now]/[idGenerator] are injected for the same reason [CreateMushroomLogEntryUseCase]'s are: a test
 * fixes both instead of racing the clock or asserting against a random id.
 */
class CreateCartographyEntryUseCase(
    private val repository: CartographyEntryRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    suspend operator fun invoke(date: LocalDate): Result<CartographyEntry> {
        val entry = CartographyEntry.draft(id = idGenerator(), date = date, updatedAtEpochMillis = now())
        return repository.save(entry).map { entry }
    }
}
