package com.forager.app.domain

/**
 * Patches a gallery photo's location — photo-geodata dispatch. The one write
 * [com.forager.app.ui.log.MushroomLogViewModel] issues, fire-and-forget, after a camera capture's
 * live GPS fix resolves; see that class's own doc comment for why this is always a follow-up write,
 * never bundled into the photo's initial persist. A thin wrapper over
 * [MushroomLogRepository.updatePhotoLocation] — kept as its own use case, not called on the
 * repository directly from the ViewModel, matching every other repository write in this app's
 * domain layer.
 */
class UpdatePhotoLocationUseCase(
    private val repository: MushroomLogRepository,
) {
    suspend operator fun invoke(photoId: String, latitude: Double, longitude: Double): Result<Unit> =
        repository.updatePhotoLocation(photoId, latitude, longitude)
}
