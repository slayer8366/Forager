package com.forager.app.domain

import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.PhotoSource

/**
 * Owned abstraction over photo capture/storage for log entries — the same "wrap the external
 * integration" pattern as [LocationProvider] and [OfflineMapRepository]. Domain and ViewModel code
 * depend on this interface and on [PhotoSource], never on `ActivityResultContracts`, a `Uri`, or
 * `FileProvider` directly.
 *
 * The real implementation stores files under app-private storage (`context.filesDir`, never
 * `cacheDir` — see [LogPhoto]'s doc comment on why: photos are user-created data that must survive,
 * the same reasoning already applied to downloaded offline-map tiles) and returns paths relative to
 * it, since an absolute path breaks across app reinstall/restore.
 */
interface PhotoStore {
    /** Copies [source]'s bytes into app-private storage under a new id, returning the resulting [LogPhoto]. */
    suspend fun persist(source: PhotoSource): Result<LogPhoto>

    /** Removes [photo]'s file from app-private storage. A no-op, not a failure, if it's already gone. */
    suspend fun delete(photo: LogPhoto): Result<Unit>
}
