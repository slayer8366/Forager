package com.zynergylabs.forager.app.domain

/**
 * Whether the app captures the device's current position and records it alongside a photo and its
 * find — the Settings checkbox "Automatically Save Location to Photos" (owner request,
 * 2026-09-14). Backed by DataStore rather than Room, and its own repository with its own file
 * rather than more keys on [MapPreferencesRepository], for the reasons
 * [SundownPreferencesRepository]'s own doc comment states: a flat scalar preference, one
 * repository per concern.
 *
 * ## What the one flag gates, which is wider than its label (owner ruling, 2026-09-14)
 *
 * The label names photos because that is the case the owner was reasoning about, but the ruling
 * was explicit that turning this off stops **every automatic location capture — photos and finds
 * alike**. So this gates, and only gates, the three paths where the app reaches for the device's
 * position on its own:
 *
 * 1. the fire-and-forget fix a camera capture patches onto its photo row,
 * 2. that same fix being promoted to the find's own `foundAt` when the find has none,
 * 3. the held fix a find started from the Journal takes at creation.
 *
 * It deliberately does **not** gate a location the user supplied themselves — the map's tapped or
 * centred point, or the Add/Change Location picker — because those are not automatic, and
 * switching this off must not break a control the user is actively operating. Nor does it touch an
 * imported photo's own EXIF coordinate: that comes from the file the user chose to bring in, not
 * from this device, and the owner's instruction was that imported photos stay untouched.
 *
 * **Defaults to `true`**, so an existing install behaves exactly as it did before the setting
 * existed and nobody silently loses the location features. That default is for a preference that
 * was never set; a *failed read* is a different case and fails closed — see `MainActivity`'s own
 * wiring, which logs and treats an unreadable preference as "do not capture", since the one thing
 * worse than not recording a position is recording one the user switched off.
 */
interface PhotoLocationPreferenceRepository {

    /** `true` until the user has ever unchecked it — see this interface's own doc comment on why the default is on. */
    suspend fun getAutoSaveLocationToPhotos(): Result<Boolean>

    suspend fun setAutoSaveLocationToPhotos(enabled: Boolean): Result<Unit>
}
