package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * A real walk, through the height chart's own rule ([heightProfileOf]). RECORD -889: the S22 test phone's entry chart
 * ended in a false drop of about 35 ft on a flat walk with the phone in a pocket (the owner: "I dropped into the earth at
 * the end"). The S22's pressure swings by up to about 1.4 hPa (about 12 m) between points 5 to 8 s apart while walking;
 * the S26 in the other pocket stayed within ±0.2 hPa over the same stretch (RECORD -887: a 5.3 m barometric range and
 * -0.8 m end minus start).
 *
 * The fixture is that walk as stored (track 596f5e3d, 69 points), with locations and absolute times removed: times are
 * relative to the first point, and only GPS altitude and pressure remain. The points are placed at one made-up spot,
 * since the chart's heights do not depend on position.
 *
 * Bounds, stated before the test was first run: on ground the owner walked as flat, the drawn heights' end minus start
 * is within 3 m (about 10 ft), and their spread (highest minus lowest) is at most 6 m (about 20 ft), near the S26's
 * 5.3 m on the same walk. Before the fix, by a port of the code: end minus start -9.44 m, spread 12.53 m.
 */
class S22WalkHeightTest {

    private fun walk(): Track {
        val text = checkNotNull(javaClass.classLoader?.getResourceAsStream("walks/s22-walk-596f5e3d-no-location.json")) { "fixture missing" }
            .bufferedReader().use { it.readText() }
        val points = Json.parseToJsonElement(text).jsonObject.getValue("points").jsonArray.map { element ->
            val p = element.jsonObject
            TrackPoint(
                lat = 0.0,
                lng = 0.0,
                altitude = p.getValue("gpsAltitudeM").jsonPrimitive.double,
                accuracyMeters = null,
                timestampEpochMillis = p.getValue("tMs").jsonPrimitive.long,
                pressureHpa = p.getValue("pressureHpa").jsonPrimitive.float,
            )
        }
        return Track(id = "s22-walk", name = null, startedAtEpochMillis = 0L, endedAtEpochMillis = points.last().timestampEpochMillis, points = points)
    }

    @Test
    fun `the S22's flat pocket walk draws flat, with no drop at its end`() {
        val track = walk()
        assertTrue("the fixture is the 69-point walk", track.points.size == 69)
        val profile = heightProfileOf(listOf(track)) as HeightProfile.Drawn
        val heights = profile.segments.single().map { it.altitudeMeters }
        val endMinusStart = heights.last() - heights.first()
        val spread = profile.highestMeters - profile.lowestMeters
        // Recorded in the JUnit XML's system-out, so the run states what the walk drew, pass or fail.
        println("S22 walk 596f5e3d: end minus start ${"%.2f".format(endMinusStart)} m, spread ${"%.2f".format(spread)} m")

        assertTrue("end minus start ${"%.2f".format(endMinusStart)} m, bound ±3 m", abs(endMinusStart) <= 3.0)
        assertTrue("spread ${"%.2f".format(spread)} m, bound 6 m", spread <= 6.0)
    }
}
