package com.zynergylabs.forager.app.data.routedata

import java.io.IOException
import java.security.MessageDigest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * The Worker's routing routes as the contract describes them (PR #381, dispatch 2026-10-23-03), answered by the last
 * interceptor in a real OkHttp chain, so nothing reaches a network. No MockWebServer is a dependency of this project
 * (`ClientUserAgentTest` uses the same approach). Each test sets [list] and [files], or overrides an answer by path.
 */
internal class FakeRouteServer {
    val requests = mutableListOf<Request>()

    /** Answers by encoded path; a path with none answers 404 with an HTML page, as an undeployed route would. */
    val answers = mutableMapOf<String, () -> Response.Builder>()

    /** Paths that fail as a dropped connection does. */
    val dropped = mutableSetOf<String>()

    val client: OkHttpClient = OkHttpClient.Builder().addInterceptor(
        Interceptor { chain ->
            val request = chain.request()
            requests += request
            val path = request.url.encodedPath
            if (path in dropped) throw IOException("connection reset")
            (answers[path]?.invoke() ?: status(404, "<html>Not found</html>", "text/html"))
                .request(request).protocol(Protocol.HTTP_1_1).build()
        },
    ).build()

    /** Serves [files] as the area's list under [runId], each file at its own path with its true size and sha256. */
    fun serveArea(
        runId: String,
        files: Map<String, ByteArray>,
        shaOverride: Map<String, String> = emptyMap(),
        sizeOverride: Map<String, Long> = emptyMap(),
        missing: List<String> = emptyList(),
        headerShaOverride: Map<String, String?> = emptyMap(),
    ) {
        val entries = files.entries.joinToString(",") { (name, bytes) ->
            val sha = shaOverride[name] ?: sha256(bytes)
            val size = sizeOverride[name] ?: bytes.size.toLong()
            """{"name":"$name","size":$size,"sha256":"$sha","url":"/routing/$runId/$name?south=1"}"""
        }
        val missingJson = missing.joinToString(",") { "\"$it\"" }
        serveList("""{"run_id":"$runId","osm_timestamp":"2026-10-21T00:00:00Z","files":[$entries],"missing":[$missingJson]}""")
        files.forEach { (name, bytes) ->
            val headerSha = if (name in headerShaOverride) headerShaOverride[name] else sha256(bytes)
            answers["/routing/$runId/$name"] = { bytes(bytes, headerSha) }
        }
    }

    /** A whole list naming one file, for answers built by hand. */
    fun listFor(runId: String, name: String, bytes: ByteArray): String =
        """{"run_id":"$runId","osm_timestamp":"t","files":[{"name":"$name","size":${bytes.size},"sha256":"${sha256(bytes)}","url":"/routing/$runId/$name?south=1"}],"missing":[]}"""

    fun serveList(json: String) {
        answers[LIST_PATH] = { status(200, json, "application/json") }
    }

    companion object {
        const val LIST_PATH = "/routing/area.json"

        fun status(code: Int, body: String, type: String = "application/json"): Response.Builder =
            Response.Builder().code(code).message("status $code").body(body.toResponseBody(type.toMediaType()))

        fun error(code: Int, error: String, retryAfter: String? = null): Response.Builder =
            status(code, """{"error":"$error","message":"fake $error"}""").apply { if (retryAfter != null) header("Retry-After", retryAfter) }

        /** A file's bytes, with [sha] in `X-Forager-Sha256` as the Worker sends it, or no such header when null. */
        fun bytes(bytes: ByteArray, sha: String?): Response.Builder =
            Response.Builder().code(200).message("OK").body(bytes.toResponseBody("application/octet-stream".toMediaType()))
                .apply { if (sha != null) header(RouteAreaClient.SHA256_HEADER, sha) }

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
