package com.zynergylabs.forager.app.data.routedata

import com.zynergylabs.forager.app.domain.model.GeoBoundingBox
import com.zynergylabs.forager.app.domain.routedata.RouteDataMissingReason
import java.io.File
import java.io.IOException
import java.math.RoundingMode
import java.security.MessageDigest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * The Worker's two routing routes, as PR #381's contract and the planner set them (dispatch 2026-10-23-03):
 *
 * 1. `GET /routing/area.json?south=&west=&north=&east=` answers the list of files cut for the rectangle;
 * 2. `GET` each file's `url` answers its bytes.
 *
 * Every request carries the app key in [APP_KEY_HEADER]. Blocking: the caller runs it off the main thread. Never
 * throws for an answer; a network failure is an [IOException] the caller turns into a missing reason.
 *
 * **Unverified against a live Worker:** the routes are not deployed (dispatch: "the routes aren't deployed yet"), so
 * every test here runs against a fake server. A file's bytes are checked against the list's sha256 and must come with
 * the same value in [SHA256_HEADER].
 */
internal class RouteAreaClient(
    private val http: OkHttpClient,
    private val baseUrl: HttpUrl,
    private val appKey: String,
) {

    /** The list of files for [box], or why there is none. */
    fun fetchList(box: GeoBoundingBox): RouteAnswer<RouteAreaList> {
        val url = baseUrl.newBuilder()
            .encodedPath("/routing/area.json")
            // Rounded outward to nine decimals, the most the Worker accepts (PR #381 `routing/area.ts`, DECIMAL), so
            // the area asked for still holds the whole region.
            .addQueryParameter("south", box.south.edge(RoundingMode.FLOOR))
            .addQueryParameter("west", box.west.edge(RoundingMode.FLOOR))
            .addQueryParameter("north", box.north.edge(RoundingMode.CEILING))
            .addQueryParameter("east", box.east.edge(RoundingMode.CEILING))
            .build()
        return http.newCall(request(url)).execute().use { response ->
            if (response.code != 200) return@use errorAnswer<RouteAreaList>(response)
            val text = response.body?.source()?.let { source ->
                if (source.request(RouteAreaLimits.MAX_LIST_BYTES + 1)) {
                    return@use RouteAnswer.Missing(RouteDataMissingReason.MALFORMED_RESPONSE, "the file list is over ${RouteAreaLimits.MAX_LIST_BYTES} bytes")
                }
                source.readUtf8()
            } ?: return@use RouteAnswer.Missing(RouteDataMissingReason.MALFORMED_RESPONSE, "the file list has no body")
            parseRouteAreaList(text)
        }
    }

    /**
     * Streams [file] into [destination], hashing as it goes. Answers [RouteAnswer.Ok] only when the bytes are exactly
     * [RouteAreaFile.size] long and hash to [RouteAreaFile.sha256]; otherwise [destination] is deleted. Reading stops
     * within one 64 KB buffer past the promised size, so a server that sends more never fills the phone.
     */
    fun fetchFile(file: RouteAreaFile, destination: File): RouteAnswer<Unit> {
        val url = resolveOnBase(file.url)
            ?: return RouteAnswer.Missing(RouteDataMissingReason.MALFORMED_RESPONSE, "${file.name}'s url is not on the routing host: ${file.url}")
        return http.newCall(request(url)).execute().use { response ->
            if (response.code != 200) return@use errorAnswer<Unit>(response)
            val source = response.body?.source()
                ?: return@use RouteAnswer.Missing(RouteDataMissingReason.MALFORMED_RESPONSE, "${file.name} came with no body")
            val digest = MessageDigest.getInstance("SHA-256")
            var written = 0L
            val buffer = ByteArray(64 * 1024)
            val out = try {
                destination.outputStream()
            } catch (e: IOException) {
                throw RouteStorageException("couldn't open ${destination.name} to write", e)
            }
            out.use {
                val input = source.inputStream()
                while (written <= file.size) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    try {
                        out.write(buffer, 0, n)
                    } catch (e: IOException) {
                        throw RouteStorageException("couldn't write ${destination.name}", e)
                    }
                    digest.update(buffer, 0, n)
                    written += n
                }
            }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            val headerSha = response.header(SHA256_HEADER)
            when {
                false -> {
                    destination.delete()
                    RouteAnswer.Missing(RouteDataMissingReason.MALFORMED_RESPONSE, "${file.name} came without $SHA256_HEADER")
                }
                !headerSha.equals(file.sha256, ignoreCase = true) -> {
                    destination.delete()
                    RouteAnswer.Missing(RouteDataMissingReason.CHECKSUM_MISMATCH, "${file.name}: $SHA256_HEADER $headerSha, the list gave ${file.sha256}")
                }
                written != file.size -> {
                    destination.delete()
                    RouteAnswer.Missing(RouteDataMissingReason.CHECKSUM_MISMATCH, "${file.name}: ${if (written > file.size) "more than" else written.toString() + " of"} the ${file.size} bytes the list gave")
                }
                !sha.equals(file.sha256, ignoreCase = true) -> {
                    destination.delete()
                    RouteAnswer.Missing(RouteDataMissingReason.CHECKSUM_MISMATCH, "${file.name}: sha256 $sha, the list gave ${file.sha256}")
                }
                else -> RouteAnswer.Ok(Unit)
            }
        }
    }


    private fun request(url: HttpUrl): Request = Request.Builder().url(url).header(APP_KEY_HEADER, appKey).get().build()

    /**
     * The file's url, resolved against [baseUrl] as the contract's relative urls need. Null when it lands on another
     * scheme, host or port: the app key is never sent anywhere but the routing host.
     */
    private fun resolveOnBase(url: String): HttpUrl? {
        val resolved = baseUrl.resolve(url) ?: return null
        val sameHost = resolved.scheme == baseUrl.scheme && resolved.host == baseUrl.host && resolved.port == baseUrl.port
        return resolved.takeIf { sameHost }
    }

    private fun <T> errorAnswer(response: Response): RouteAnswer<T> {
        val body = try {
            response.body?.source()?.let { source ->
                source.request(RouteAreaLimits.MAX_LIST_BYTES)
                source.buffer.readUtf8(minOf(source.buffer.size, RouteAreaLimits.MAX_LIST_BYTES))
            }
        } catch (e: IOException) {
            null // The status still decides; an unreadable body reads as a status with no error code.
        }
        return when (val meaning = classifyRouteError(response.code, body, response.header("Retry-After"))) {
            RouteErrorMeaning.OutsideCoverage -> RouteAnswer.OutsideCoverage
            is RouteErrorMeaning.Missing -> RouteAnswer.Missing(meaning.reason, meaning.detail)
        }
    }

    companion object {
        /** The header every routing request carries the app key in: the planner's contract (dispatch 2026-10-23-03). */
        const val APP_KEY_HEADER = "X-Forager-App-Key"

        /** The header a file's bytes come with, carrying their sha256 (PR #381's final shape). */
        const val SHA256_HEADER = "X-Forager-Sha256"

        /**
         * The Worker the map tiles come from (`OfflineStyle.kt`), which PR #381 adds the routing routes to. **An
         * assumption about a route not yet deployed**: #381's report places them in `server/pmtiles-worker`.
         */
        val ROUTING_BASE_URL: HttpUrl = "https://forager-pmtiles.brandonlee1-894.workers.dev/".toHttpUrl()
    }
}

/** The phone, not the network, failed: a write to its own storage. Kept apart so the log and the record say which. */
internal class RouteStorageException(message: String, cause: IOException) : IOException(message, cause)

/** What one routing request came back with. */
internal sealed interface RouteAnswer<out T> {
    data class Ok<T>(val value: T) : RouteAnswer<T>

    /** 404 `outside_coverage`. */
    data object OutsideCoverage : RouteAnswer<Nothing>

    data class Missing(val reason: RouteDataMissingReason, val detail: String) : RouteAnswer<Nothing>
}

/** The Worker's file list for an area (step 1 of the contract). */
@Serializable
internal data class RouteAreaList(
    @SerialName("run_id") val runId: String,
    @SerialName("osm_timestamp") val osmTimestamp: String,
    val files: List<RouteAreaFile>,
    /** The squares the area needs that the build lacks, as `W130_N45`; always present, empty when none (PR #381). */
    val missing: List<String>,
)

@Serializable
internal data class RouteAreaFile(val name: String, val size: Long, val sha256: String, val url: String)

private val listJson = Json { ignoreUnknownKeys = true }

/**
 * Reads and checks the list: every name must be one BRouter file name (so nothing can be written outside the region's
 * folder), no name twice, sizes positive, sha256 sixty-four hex digits, and the whole inside [RouteAreaLimits].
 */
internal fun parseRouteAreaList(text: String): RouteAnswer<RouteAreaList> {
    val list = try {
        listJson.decodeFromString(RouteAreaList.serializer(), text)
    } catch (e: IllegalArgumentException) {
        return RouteAnswer.Missing(RouteDataMissingReason.MALFORMED_RESPONSE, "the file list is not the agreed JSON (${e.message?.take(200)})")
    }
    fun malformed(why: String) = RouteAnswer.Missing(RouteDataMissingReason.MALFORMED_RESPONSE, why)
    if (list.runId.isBlank()) return malformed("the file list has no run_id")
    if (list.files.isEmpty()) return malformed("the file list names no files")
    list.files.firstOrNull { !BROUTER_FILE_NAME.matches(it.name) }?.let { return malformed("not a BRouter file name: '${it.name.take(80)}'") }
    if (list.files.map { it.name }.toSet().size != list.files.size) return malformed("a file name appears twice")
    list.missing.firstOrNull { !SQUARE_NAME.matches(it) }?.let { return malformed("not a square name in missing: '${it.take(80)}'") }
    list.files.firstOrNull { it.size <= 0 }?.let { return malformed("${it.name} has size ${it.size}") }
    list.files.firstOrNull { !SHA256_HEX.matches(it.sha256) }?.let { return malformed("${it.name}'s sha256 is not 64 hex digits") }
    if (list.files.size > RouteAreaLimits.MAX_FILES) {
        return RouteAnswer.Missing(RouteDataMissingReason.OVER_PHONE_LIMIT, "${list.files.size} files, over ${RouteAreaLimits.MAX_FILES}")
    }
    val total = list.files.sumOf { it.size }
    if (total > RouteAreaLimits.MAX_AREA_BYTES) {
        return RouteAnswer.Missing(RouteDataMissingReason.OVER_PHONE_LIMIT, "$total bytes, over ${RouteAreaLimits.MAX_AREA_BYTES}")
    }
    return RouteAnswer.Ok(list)
}

/** BRouter's own segment file names, `W125_N45.rd5`: a five-degree square's south-west corner. */
internal val BROUTER_FILE_NAME = Regex("^[EW][0-9]{1,3}_[NS][0-9]{1,2}\\.rd5$")

/** A five-degree square's name without `.rd5`, as the Worker's `missing` list gives it. */
internal val SQUARE_NAME = Regex("^[EW][0-9]{1,3}_[NS][0-9]{1,2}$")

/** [this] degrees as plain decimal with at most nine places, rounded by [mode]; never exponent form (`1.0E-4`). */
internal fun Double.edge(mode: RoundingMode): String = toBigDecimal().setScale(9, mode).stripTrailingZeros().toPlainString()

private val SHA256_HEX = Regex("^[0-9a-fA-F]{64}$")
