package com.zynergylabs.forager.app.data.referencetrails

import com.zynergylabs.forager.app.domain.referencetrails.ReferenceTrailsManifestSource
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * [ReferenceTrailsManifestSource] over the Worker's `/reference-trails-manifest.json` (dispatch 2026-10-26-22 M1;
 * served since RECORD -1706). The route takes no app key: the Worker serves it as an asset (`index.ts`,
 * `REFERENCE_MANIFEST_PATH`). The served file is 2,814 bytes (-1718); [MAX_MANIFEST_BYTES] is an operating limit far
 * above it, so a growing file still fits and a wrong answer cannot fill memory (CLAUDE.md, Architecture).
 */
internal class WorkerReferenceTrailsManifestSource(
    private val http: OkHttpClient,
    private val url: HttpUrl = MANIFEST_URL,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ReferenceTrailsManifestSource {

    override suspend fun fetch(): Result<String> = withContext(io) {
        runCatching {
            http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                val source = response.body?.source() ?: throw IOException("the manifest came with no body")
                if (source.request(MAX_MANIFEST_BYTES + 1)) throw IOException("the manifest is over $MAX_MANIFEST_BYTES bytes")
                source.readUtf8()
            }
        }
    }

    companion object {
        val MANIFEST_URL: HttpUrl = "https://forager-pmtiles.brandonlee1-894.workers.dev/reference-trails-manifest.json".toHttpUrl()

        const val MAX_MANIFEST_BYTES = 256L * 1024
    }
}
