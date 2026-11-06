package com.zynergylabs.forager.app.importgpx

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GpxFileRead
import com.zynergylabs.forager.app.domain.GpxFileSource
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A GPX file at a content [uri] (the file picker's result, or what another app hands over with Open with
 * or Share), read for plan T16's [com.zynergylabs.forager.app.domain.ImportGpxUseCase]. Reads at most
 * `maxBytes + 1` bytes, so a huge file is refused at the limit and never held whole in memory (the
 * owner's "10 MB limit", -636); a provider's own reported size is not trusted for that, since it can be
 * missing or wrong. The display name comes from the provider's `OpenableColumns.DISPLAY_NAME`, which
 * names the track when the file does not; a provider that gives none leaves the name to the file.
 */
class ContentUriGpxFileSource(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
    private val errorLog: ErrorLog,
) : GpxFileSource {

    override suspend fun read(maxBytes: Int): GpxFileRead = withContext(Dispatchers.IO) {
        try {
            val stream = contentResolver.openInputStream(uri) ?: throw FileNotFoundException("no stream for $uri")
            val bytes = stream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > maxBytes) return@withContext GpxFileRead.TooBig
                }
                out.toByteArray()
            }
            GpxFileRead.Bytes(bytes, displayName())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GpxFileRead.Failed(e)
        }
    }

    /** `null` when the provider has no name to give (a `file:` Uri's last segment is used then). */
    private fun displayName(): String? {
        val fromProvider = try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: Exception) {
            errorLog.w(TAG, "Couldn't read the GPX file's display name; naming from the Uri instead.", e)
            null
        }
        return fromProvider ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    private companion object {
        const val TAG = "ImportGpx"
    }
}
