package com.forager.app.domain

import com.forager.app.domain.model.CartographyEntry

/** Reloads one Cartography entry by id, straight from persistence — used to discard in-progress edits back to what's actually stored, never an in-memory revert (device-check patch, Item 1). `null` if no such entry exists. */
class GetCartographyEntryUseCase(
    private val repository: CartographyEntryRepository,
) {
    suspend operator fun invoke(id: String): Result<CartographyEntry?> = repository.getById(id)
}
