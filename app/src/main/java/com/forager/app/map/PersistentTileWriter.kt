package com.forager.app.map

import android.graphics.drawable.Drawable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import org.osmdroid.tileprovider.modules.IFilesystemCache
import org.osmdroid.tileprovider.tilesource.ITileSource

/**
 * A minimal [IFilesystemCache] that writes tiles under a directory this class is handed directly,
 * rather than osmdroid's own [org.osmdroid.tileprovider.modules.TileWriter] or
 * [org.osmdroid.tileprovider.modules.SqlTileWriter] — both of which resolve their storage location
 * from `Configuration.getInstance().getOsmdroidTileCache()`, the same process-wide singleton path
 * [com.forager.app.ui.map.SightingsMap] points at `context.cacheDir/osmdroid/tiles` for ordinary
 * browsing (confirmed by reading both classes' pinned-artifact sources: `TileWriter.getFile` reads
 * that global on every call, and `SqlTileWriter` opens one `cache.db` there the first time any
 * instance is constructed and never again — mutating the global path after that point would not
 * even redirect it). Using either would make the offline store either share the browsing cache's
 * directory outright or race it through a global mutable singleton. This class touches neither: it
 * is hand-rolled but small, modeled directly on `TileWriter`'s own `saveFile`/`getFile`/`exists`.
 *
 * Deliberately not the same directory [SightingsMap] uses for browsing, and not under
 * `context.cacheDir` at all — see `OsmdroidOfflineMapRepository`'s doc comment for why the caller
 * points [tilesDir] at `context.filesDir` instead: `cacheDir` is fair game for the OS to clear under
 * storage pressure, which would silently delete a region the user explicitly downloaded to have
 * available offline.
 */
internal class PersistentTileWriter(private val tilesDir: File) : IFilesystemCache {

    override fun saveFile(
        pTileSource: ITileSource,
        pMapTileIndex: Long,
        pStream: InputStream,
        pExpirationTime: Long?,
    ): Boolean {
        val file = fileFor(pTileSource, pMapTileIndex)
        val parent = file.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs() && !parent.exists()) return false

        return try {
            FileOutputStream(file).use { output -> pStream.copyTo(output) }
            true
        } catch (e: IOException) {
            false
        }
    }

    override fun exists(pTileSource: ITileSource, pMapTileIndex: Long): Boolean =
        fileFor(pTileSource, pMapTileIndex).exists()

    override fun remove(pTileSource: ITileSource, pMapTileIndex: Long): Boolean {
        val file = fileFor(pTileSource, pMapTileIndex)
        return !file.exists() || file.delete()
    }

    /** No expiry tracking: a downloaded region is replaced wholesale by the next download, not refreshed tile-by-tile. */
    override fun getExpirationTimestamp(pTileSource: ITileSource, pMapTileIndex: Long): Long? = null

    override fun loadTile(pTileSource: ITileSource, pMapTileIndex: Long): Drawable? {
        val file = fileFor(pTileSource, pMapTileIndex)
        if (!file.exists()) return null
        return pTileSource.getDrawable(file.path)
    }

    override fun onDetach() = Unit

    private fun fileFor(tileSource: ITileSource, mapTileIndex: Long): File =
        File(tilesDir, tileSource.getTileRelativeFilenameString(mapTileIndex) + TILE_FILE_EXTENSION)

    private companion object {
        const val TILE_FILE_EXTENSION = ".tile"
    }
}
