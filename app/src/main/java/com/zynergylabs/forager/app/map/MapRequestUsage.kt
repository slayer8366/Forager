package com.zynergylabs.forager.app.map

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.maplibre.android.LibraryLoaderProvider
import org.maplibre.android.ModuleProvider
import org.maplibre.android.http.HttpRequest
import org.maplibre.android.http.HttpResponder

/**
 * Whether MapLibre asked for a resource for an offline-area download (`offline`) or for the map on screen, carried on
 * the OkHttp request as a typed tag (dispatch 2026-10-23-06 Amendment 3, option A). [TileRetryInterceptor] reads it:
 * a download request is never held, the engine retries those itself ([com.zynergylabs.forager.app.domain.DownloadPause]),
 * so the map's places stay for the tiles on screen.
 *
 * **Why the app has to carry it.** MapLibre Native knows: a download marks its requests as offline usage
 * (`offline_download.cpp:444-466`, android-v13.5.0) and passes that up as a boolean (`http_file_source.cpp:123`). But
 * MapLibre's own `HttpRequestImpl` uses the flag only to add `offline=true` for Mapbox hosts and then drops it
 * (`HttpRequestUrl.java:22-35`), so nothing on the request it builds says so (`HttpRequestImpl.java:73-86`).
 */
internal data class MapRequestUsage(val offline: Boolean)

/**
 * The flag for the request being built on this thread. Set by [UsageMarkingHttpRequest] around MapLibre's own
 * `executeRequest`, read by [UsageTaggingCallFactory.newCall], which `HttpRequestImpl.executeRequest` calls on the same
 * thread before it returns (`HttpRequestImpl.java:89`, android-v13.5.0). If a later MapLibre stopped doing that, requests
 * would arrive untagged: [TileRetryInterceptor] treats those as the map's and logs it once.
 */
private val usageOnThisThread = ThreadLocal<Boolean?>()

/**
 * MapLibre's module provider with its HTTP requests wrapped in [UsageMarkingHttpRequest]. Installed with
 * `MapLibre.setModuleProvider`, which MapLibre reads for every request it makes (`NativeHttpRequest.java:13`).
 */
internal class UsageMarkingModuleProvider(private val delegate: ModuleProvider) : ModuleProvider {
    override fun createHttpRequest(): HttpRequest = UsageMarkingHttpRequest(delegate.createHttpRequest())
    override fun createLibraryLoaderProvider(): LibraryLoaderProvider = delegate.createLibraryLoaderProvider()
}

/** MapLibre's own request, with the offline-usage flag kept for this thread while it builds and enqueues its call. */
internal class UsageMarkingHttpRequest(private val delegate: HttpRequest) : HttpRequest {
    override fun executeRequest(
        httpRequest: HttpResponder,
        nativePtr: Long,
        resourceUrl: String,
        dataRange: String,
        etag: String,
        modified: String,
        offlineUsage: Boolean,
    ) {
        // FAILING-FIRST STUB: the flag is not kept
        try {
            delegate.executeRequest(httpRequest, nativePtr, resourceUrl, dataRange, etag, modified, offlineUsage)
        } finally {
            usageOnThisThread.remove()
        }
    }

    override fun cancelRequest() = delegate.cancelRequest()
}

/**
 * The map client as MapLibre's `Call.Factory` (`HttpRequestUtil.setOkHttpClient` takes any): each call gets the
 * [MapRequestUsage] tag from the request being built on this thread, then goes to [client].
 */
internal class UsageTaggingCallFactory(val client: OkHttpClient) : Call.Factory {
    override fun newCall(request: Request): Call {
        val offline = usageOnThisThread.get() ?: return client.newCall(request)
        return client.newCall(request.newBuilder().tag(MapRequestUsage::class.java, MapRequestUsage(offline)).build())
    }
}
