package com.forager.app.domain

/** Removes a Cartography entry by [id] and its own kept-item ref rows. A no-op, not a failure, if no such entry is stored. */
class DeleteCartographyEntryUseCase(
    private val repository: CartographyEntryRepository,
) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.delete(id)
}
