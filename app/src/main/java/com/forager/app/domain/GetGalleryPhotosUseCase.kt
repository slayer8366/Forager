package com.forager.app.domain

import com.forager.app.domain.model.GalleryPhoto

/**
 * Loads every photo in the gallery, each paired with the entries (if any) currently referencing
 * it — no imposed ordering beyond whatever [MushroomLogRepository.getAllPhotos] naturally gives
 * (Workstream G2's dispatch explicitly keeps sorting/filtering out of scope), unlike
 * [GetMushroomLogEntriesUseCase]'s own most-recently-found-first ordering.
 */
class GetGalleryPhotosUseCase(
    private val repository: MushroomLogRepository,
) {
    suspend operator fun invoke(): Result<List<GalleryPhoto>> = repository.getAllPhotos()
}
