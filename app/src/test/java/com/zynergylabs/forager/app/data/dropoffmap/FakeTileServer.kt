package com.zynergylabs.forager.app.data.dropoffmap

import com.zynergylabs.forager.app.domain.mapdata.TileId
import java.io.IOException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * The basemap's tile route as the Worker answers it (`server/pmtiles-worker/src/index.ts`: a 200 with the tile, a 204 for
 * an empty one), answered by the last interceptor in a real OkHttp chain, so nothing reaches a network: the approach of
 * `data/routedata/FakeRouteServer`. Every tile answers [body] with a 200 unless [answers] says otherwise.
 */
internal class FakeTileServer(private val body: ByteArray) {
    /** Every tile asked for, in the order asked. */
    val asked: MutableList<TileId> = Collections.synchronizedList(mutableListOf())

    val answers: MutableMap<TileId, () -> Response.Builder> = ConcurrentHashMap()

    /** Tiles whose request fails at once, as a dropped connection does. */
    val dropped: MutableSet<TileId> = ConcurrentHashMap.newKeySet()

    /** How long each answer is held before it is given, in real milliseconds, so requests overlap. */
    @Volatile
    var holdMillis = 0L

    private val inFlight = AtomicInteger()
    val maxInFlight = AtomicInteger()

    val client: OkHttpClient = OkHttpClient.Builder().addInterceptor(
        Interceptor { chain ->
            val request = chain.request()
            val tile = tileOf(request.url.encodedPath) ?: throw IOException("not a tile path: ${request.url.encodedPath}")
            asked += tile
            val now = inFlight.incrementAndGet()
            maxInFlight.accumulateAndGet(now) { a, b -> maxOf(a, b) }
            try {
                // A dropped connection fails at once; every other answer is held, so the failure lands while they wait.
                if (tile in dropped) throw IOException("connection reset")
                if (holdMillis > 0) Thread.sleep(holdMillis)
                (answers[tile]?.invoke() ?: tileAnswer(body)).request(request).protocol(Protocol.HTTP_1_1).build()
            } finally {
                inFlight.decrementAndGet()
            }
        },
    ).build()

    companion object {
        const val TEMPLATE = "https://tiles.example.invalid/us/{z}/{x}/{y}.mvt"

        /** The stored TileJSON of the basemap, as MapLibre's store holds it: its layers include buildings, and its first tile address is [TEMPLATE]. */
        const val TILE_JSON = """{"tiles":["$TEMPLATE"],"vector_layers":[{"id":"barriers"},{"id":"buildings"},{"id":"roads"}]}"""

        fun tileAnswer(bytes: ByteArray): Response.Builder =
            Response.Builder().code(200).message("OK").body(bytes.toResponseBody("application/vnd.mapbox-vector-tile".toMediaType()))

        fun empty(): Response.Builder = Response.Builder().code(204).message("No Content").body(ByteArray(0).toResponseBody(null))

        fun status(code: Int): Response.Builder = Response.Builder().code(code).message("status $code").body("error $code".toResponseBody("text/plain".toMediaType()))

        private val PATH = Regex("^/us/(\\d+)/(\\d+)/(\\d+)\\.mvt$")

        fun tileOf(path: String): TileId? = PATH.matchEntire(path)?.destructured?.let { (z, x, y) -> TileId(z.toInt(), x.toInt(), y.toInt()) }
    }
}
