package com.zynergylabs.forager.app.ui.track

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.NoSeaLevelConverter
import com.zynergylabs.forager.app.domain.SeaLevelConverter
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackPointRecord
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.photo.FileProviderCacheReset
import com.zynergylabs.forager.app.ui.availability.FixedOffsetSeaLevelConverter
import com.zynergylabs.forager.app.ui.format.LocalSeaLevelConverter
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.w3c.dom.Element

/**
 * Dispatch 2026-10-28-13, item 5 and Amendment 1 (RECORD -1793): a shared GPX file's standard `<ele>` is above sea
 * level; on a phone that cannot convert it is left out ("Leave heights out (Recommended)"); the file's own full record
 * keeps the raw stored height ("Keep it raw (Recommended)"); an imported track's heights go out as the file gave them
 * ("Show as the file gave them (Recommended)"). Through the track row's own Share button, a real touch, reading the
 * file the share wrote, parsed as XML. Made-up walk: stored heights 50 m (points) and 60 m (the waypoint).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackExportSeaLevelTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(FileProviderCacheReset()).around(composeRule)

    private val points = listOf(
        TrackPoint(45.52, -122.68, 50.0, 5f, 1_790_000_000_000L),
        TrackPoint(45.521, -122.68, 50.0, 5f, 1_790_000_010_000L),
    )

    private fun track(importedAt: Long? = null) = Track(
        id = "T1", name = null, startedAtEpochMillis = 1_790_000_000_000L, endedAtEpochMillis = 1_790_000_600_000L,
        points = points, importedAtEpochMillis = importedAt,
    )

    private val waypoint = Waypoint(id = "W1", lat = 45.52, lng = -122.68, altitude = 60.0, name = "Start", note = "", createdAtEpochMillis = 1_790_000_000_000L, trackId = "T1")

    private class Exported(val trackPointEle: List<String?>, val waypointEle: List<String?>, val fullRecordEle: List<String?>)

    private fun share(track: Track, converter: SeaLevelConverter): Exported {
        val record = points.map { TrackPointRecord(it, kept = true) }
        composeRule.setContent {
            CompositionLocalProvider(LocalSeaLevelConverter provides converter) {
                ForagerTheme { TrackExportRow(track = track, waypoints = listOf(waypoint), getFullRecord = { Result.success(record) }) }
            }
        }
        composeRule.onNodeWithTag("share-track-T1").performTouchInput { click(center) }
        // The file is written on Dispatchers.IO before the chooser starts; wait for the chooser, as TrackExportPartialTest does.
        composeRule.waitUntil(timeoutMillis = 5_000) { Shadows.shadowOf(composeRule.activity).nextStartedActivity != null }
        val dir = File(ApplicationProvider.getApplicationContext<Application>().cacheDir, "tracks")
        val file = dir.listFiles().orEmpty().single { it.extension == "gpx" }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        fun eleOf(tag: String): List<String?> {
            val nodes = doc.getElementsByTagName(tag)
            return (0 until nodes.length).map { i ->
                val e = nodes.item(i) as Element
                e.getElementsByTagName("ele").item(0)?.textContent
            }
        }
        val full = doc.getElementsByTagName("forager:point")
        return Exported(
            trackPointEle = eleOf("trkpt"),
            waypointEle = eleOf("wpt"),
            fullRecordEle = (0 until full.length).map { (full.item(it) as Element).getAttribute("ele").ifEmpty { null } },
        )
    }

    @Test
    fun `a recorded track's ele is above sea level, and its full record keeps the stored raw height`() {
        val out = share(track(), FixedOffsetSeaLevelConverter(21.5))

        assertEquals(listOf("71.5", "71.5"), out.trackPointEle)
        assertEquals(listOf("81.5"), out.waypointEle)
        assertEquals("the full record is the stored data, raw", listOf("50.0", "50.0"), out.fullRecordEle)
    }

    @Test
    fun `on a phone that cannot convert the standard ele is left out, never the raw height`() {
        val out = share(track(), NoSeaLevelConverter)

        assertEquals(listOf(null, null), out.trackPointEle)
        assertEquals(listOf<String?>(null), out.waypointEle)
        assertEquals("the full record still carries the raw height", listOf("50.0", "50.0"), out.fullRecordEle)
    }

    @Test
    fun `an imported track's heights go out as the file gave them`() {
        val out = share(track(importedAt = 1_790_001_000_000L), FixedOffsetSeaLevelConverter(21.5))

        assertEquals(listOf("50.0", "50.0"), out.trackPointEle)
        assertEquals(listOf("60.0"), out.waypointEle)
    }
}
