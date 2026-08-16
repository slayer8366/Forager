package com.forager.app.domain

import com.forager.app.domain.model.ForagingArea
import com.forager.app.domain.model.ForagingAreas
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Metres in one degree of latitude — constant everywhere, so it makes fixtures readable. */
private const val METERS_PER_DEGREE_LAT = 111_194.9

private fun latPlus(lat: Double, meters: Double) = lat + meters / METERS_PER_DEGREE_LAT

private fun sightingAt(
    id: Long,
    lat: Double,
    lng: Double,
    taxonId: Long = id,
    observedOn: LocalDate? = LocalDate.of(2019, 9, 12),
) = Sighting(
    observationId = id,
    taxonId = taxonId,
    scientificName = "Species $taxonId",
    commonName = null,
    lat = lat,
    lng = lng,
    observedOn = observedOn,
    photoUrl = null,
)

/**
 * Four observations strung out over ~330 m of latitude from ([lat], [lng]) — comfortably inside
 * [ClusterForagingAreasUseCase.NEIGHBORHOOD_RADIUS_METERS] of one another, and exactly
 * [ClusterForagingAreasUseCase.MIN_OBSERVATIONS_PER_AREA] points, so the group is a cluster.
 */
private fun tightGroup(firstId: Long, lat: Double, lng: Double): List<Sighting> =
    (0 until ClusterForagingAreasUseCase.MIN_OBSERVATIONS_PER_AREA).map { step ->
        sightingAt(id = firstId + step, lat = latPlus(lat, step * 110.0), lng = lng)
    }

class ClusterForagingAreasUseCaseTest {

    private val useCase = ClusterForagingAreasUseCase()
    private val region = Region(lat = 45.0, lng = -122.0, radiusKm = 15)

    private fun found(result: ForagingAreas): ForagingAreas.Found {
        assertTrue("expected Found, got $result", result is ForagingAreas.Found)
        return result as ForagingAreas.Found
    }

    private fun ForagingArea.observationIds() = sightings.map { it.observationId }.sorted()

    @Test
    fun `two well-separated groups resolve to two areas with the expected members`() {
        // ~11 km apart: far beyond the neighbourhood radius, so they cannot merge.
        val near = tightGroup(firstId = 1, lat = 45.0, lng = -122.0)
        val far = tightGroup(firstId = 10, lat = 45.1, lng = -122.0)

        val result = found(useCase(region, near + far))

        assertEquals(2, result.areas.size)
        assertEquals(listOf(1L, 2L, 3L, 4L), result.areas[0].observationIds())
        assertEquals(listOf(10L, 11L, 12L, 13L), result.areas[1].observationIds())
        assertEquals(0, result.ungroupedObservationCount)
    }

    @Test
    fun `an isolated observation is reported as ungrouped, not absorbed into an area`() {
        val group = tightGroup(firstId = 1, lat = 45.0, lng = -122.0)
        val outlier = sightingAt(id = 99, lat = latPlus(45.0, 8_000.0), lng = -122.0)

        val result = found(useCase(region, group + outlier))

        assertEquals(1, result.areas.size)
        assertEquals(listOf(1L, 2L, 3L, 4L), result.areas.single().observationIds())
        assertEquals(1, result.ungroupedObservationCount)
        assertTrue(
            "the outlier must not appear in any area",
            result.areas.none { area -> area.sightings.any { it.observationId == 99L } },
        )
    }

    /**
     * The Euclidean-regression catcher. At 60°N a degree of longitude is ~55.6 km, so these four
     * observations span ~334 m and belong to one area. Measuring distance as
     * `sqrt(dLat² + dLng²) × 111 km` would score that span at ~668 m, put every point below the
     * neighbour threshold, and return no areas at all.
     */
    @Test
    fun `observations close in metres but spread in longitude at high latitude form one area`() {
        val highLatitudeRegion = Region(lat = 60.0, lng = 0.0, radiusKm = 15)
        val eastWestGroup = listOf(
            sightingAt(id = 1, lat = 60.0, lng = 0.000),
            sightingAt(id = 2, lat = 60.0, lng = 0.002),
            sightingAt(id = 3, lat = 60.0, lng = 0.004),
            sightingAt(id = 4, lat = 60.0, lng = 0.006),
        )

        val result = found(useCase(highLatitudeRegion, eastWestGroup))

        assertEquals(1, result.areas.size)
        assertEquals(listOf(1L, 2L, 3L, 4L), result.areas.single().observationIds())
        assertEquals(0, result.ungroupedObservationCount)
    }

    @Test
    fun `an area reports observation count, distinct species count, and most recent year`() {
        val group = listOf(
            sightingAt(id = 1, lat = 45.0, lng = -122.0, taxonId = 7, observedOn = LocalDate.of(2011, 10, 1)),
            sightingAt(id = 2, lat = latPlus(45.0, 100.0), lng = -122.0, taxonId = 7, observedOn = LocalDate.of(2018, 9, 3)),
            sightingAt(id = 3, lat = latPlus(45.0, 200.0), lng = -122.0, taxonId = 8, observedOn = LocalDate.of(2023, 10, 20)),
            sightingAt(id = 4, lat = latPlus(45.0, 300.0), lng = -122.0, taxonId = 9, observedOn = LocalDate.of(2015, 9, 9)),
        )

        val area = found(useCase(region, group)).areas.single()

        assertEquals(4, area.observationCount)
        assertEquals(3, area.distinctSpeciesCount)
        assertEquals(2023, area.mostRecentYear)
        assertEquals(0, area.undatedObservationCount)
    }

    @Test
    fun `undated observations still count toward the totals but never supply a year`() {
        val group = listOf(
            sightingAt(id = 1, lat = 45.0, lng = -122.0, taxonId = 7, observedOn = null),
            sightingAt(id = 2, lat = latPlus(45.0, 100.0), lng = -122.0, taxonId = 8, observedOn = null),
            sightingAt(id = 3, lat = latPlus(45.0, 200.0), lng = -122.0, taxonId = 9, observedOn = LocalDate.of(2016, 9, 1)),
            sightingAt(id = 4, lat = latPlus(45.0, 300.0), lng = -122.0, taxonId = 9, observedOn = LocalDate.of(2014, 9, 1)),
        )

        val area = found(useCase(region, group)).areas.single()

        assertEquals(4, area.observationCount)
        assertEquals(3, area.distinctSpeciesCount)
        assertEquals(2016, area.mostRecentYear)
        assertEquals(2, area.undatedObservationCount)
    }

    @Test
    fun `an area whose observations are all undated reports no most-recent year`() {
        val group = (0 until 4).map { step ->
            sightingAt(id = 1L + step, lat = latPlus(45.0, step * 110.0), lng = -122.0, observedOn = null)
        }

        val area = found(useCase(region, group)).areas.single()

        assertNull(area.mostRecentYear)
        assertEquals(4, area.undatedObservationCount)
        assertEquals(4, area.observationCount)
    }

    /**
     * Greedy nearest-neighbour, not "sorted by distance from the search centre". The middle
     * group here is the *furthest* of the three from the centre yet is visited second, because
     * it is the nearest thing to the first area — which is exactly what greedy chaining does and
     * a distance-from-centre sort would not.
     */
    @Test
    fun `areas are ordered by greedy nearest-neighbour from the search centre`() {
        val oneKmNorth = tightGroup(firstId = 1, lat = latPlus(45.0, 1_000.0), lng = -122.0)
        val threeKmNorth = tightGroup(firstId = 10, lat = latPlus(45.0, 3_000.0), lng = -122.0)
        val twoAndAHalfKmSouth = tightGroup(firstId = 20, lat = latPlus(45.0, -2_500.0), lng = -122.0)

        // Deliberately shuffled on input, so the ordering can't be an accident of input order.
        val result = found(useCase(region, twoAndAHalfKmSouth + threeKmNorth + oneKmNorth))

        assertEquals(
            listOf(listOf(1L, 2L, 3L, 4L), listOf(10L, 11L, 12L, 13L), listOf(20L, 21L, 22L, 23L)),
            result.areas.map { it.observationIds() },
        )
        assertEquals(listOf(1, 2, 3), result.areas.map { it.visitOrder })
    }

    @Test
    fun `an area centre is the mean position of its observations`() {
        val group = tightGroup(firstId = 1, lat = 45.0, lng = -122.0)

        val center = found(useCase(region, group)).areas.single().center

        // Four points at 0/110/220/330 m north of 45.0: the mean sits 165 m north.
        assertEquals(latPlus(45.0, 165.0), center.lat, 1e-9)
        assertEquals(-122.0, center.lng, 1e-9)
    }

    @Test
    fun `no sightings at all is reported as no observations`() {
        val result = useCase(region, emptyList())

        assertEquals(ForagingAreas.None(ForagingAreas.Reason.NO_OBSERVATIONS, observationsConsidered = 0), result)
    }

    @Test
    fun `fewer observations than it takes to form an area is reported as too few`() {
        val barelyAnything = listOf(
            sightingAt(id = 1, lat = 45.0, lng = -122.0),
            sightingAt(id = 2, lat = latPlus(45.0, 50.0), lng = -122.0),
        )

        val result = useCase(region, barelyAnything)

        assertEquals(
            ForagingAreas.None(ForagingAreas.Reason.TOO_FEW_OBSERVATIONS, observationsConsidered = 2),
            result,
        )
    }

    /** The threshold is never relaxed to manufacture an area out of scattered singletons. */
    @Test
    fun `observations that are all scattered are reported as no group meeting the threshold`() {
        val scattered = (0 until 6).map { step ->
            sightingAt(id = 1L + step, lat = latPlus(45.0, step * 3_000.0), lng = -122.0)
        }

        val result = useCase(region, scattered)

        assertEquals(
            ForagingAreas.None(ForagingAreas.Reason.NO_GROUP_MET_THRESHOLD, observationsConsidered = 6),
            result,
        )
    }
}
