package com.zynergylabs.forager.app.routing

import com.zynergylabs.forager.app.domain.model.LatLng
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The Harriman fixture and the pinned profile, for the routing tests added with the way home (dispatch 2026-10-23-09
 * Amendment 1). The same files `BRouterTrailRouterTest` uses, copied into a test's own folder; see
 * `src/test/resources/routing/README.md` for where they come from and their licence.
 */
internal object RoutingTestData {
    private val ASSETS = File("src/main/assets/${RoutingProfile.ASSET_DIR}")
    const val SQUARE = "W75_N40.rd5"

    fun profileDir(tmp: TemporaryFolder): File = tmp.newFolder("profiles2").also { dir ->
        for (name in RoutingProfile.PINNED_BLOBS.keys) File(ASSETS, name).copyTo(File(dir, name))
    }

    fun segments(tmp: TemporaryFolder): File = tmp.newFolder("segments").also { dir -> resource("segments/W75_N40.rd5").copyTo(File(dir, SQUARE)) }

    /** The points of a route BRouter's own command-line router returned on the fixture (`brouter-cli/<name>.geojson`). */
    fun commandLineRoute(name: String): List<LatLng> =
        Json.parseToJsonElement(resource("brouter-cli/$name.geojson").readText()).jsonObject["features"]!!.jsonArray[0]
            .jsonObject["geometry"]!!.jsonObject["coordinates"]!!.jsonArray
            .map { point -> point.jsonArray.let { LatLng(lat = it[1].jsonPrimitive.double, lng = it[0].jsonPrimitive.double) } }

    private fun resource(path: String): File =
        File(checkNotNull(javaClass.classLoader?.getResource("routing/$path")) { "routing/$path missing" }.toURI())

    // Route ends from server/pmtiles-worker/test/fixtures/rd5/routes.json: OpenStreetMap trail nodes.
    val ACROSS_FROM = LatLng(41.2742564, -73.9978932)
    val ACROSS_TO = LatLng(41.3036912, -74.0159457)

    // Inside the fixture's square, north of every way it holds: no way within BRouter's 250 m catching range.
    val NO_TRAIL_NEAR = LatLng(41.40, -74.05)

    /**
     * About 55 m north-east of [ACROSS_FROM], off the trail. Its nearest way, as BRouter v1.7.10 matches it on the
     * fixture, is 34.3 m away (seen with the release jar before these tests were written).
     */
    val OFF_TRAIL = LatLng(ACROSS_FROM.lat + 0.0004, ACROSS_FROM.lng + 0.0004)
}
