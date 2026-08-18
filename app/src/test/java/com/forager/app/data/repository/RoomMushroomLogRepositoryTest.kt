package com.forager.app.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.forager.app.data.local.ForagerDatabase
import com.forager.app.domain.model.AnnulusType
import com.forager.app.domain.model.Association
import com.forager.app.domain.model.CapDecoration
import com.forager.app.domain.model.CapMargin
import com.forager.app.domain.model.CapSection
import com.forager.app.domain.model.CapShape
import com.forager.app.domain.model.CapSurface
import com.forager.app.domain.model.ContextFleshSection
import com.forager.app.domain.model.Feature
import com.forager.app.domain.model.FleshTexture
import com.forager.app.domain.model.ForestType
import com.forager.app.domain.model.GillAttachment
import com.forager.app.domain.model.GillEdge
import com.forager.app.domain.model.GillSpacing
import com.forager.app.domain.model.HostHealth
import com.forager.app.domain.model.HostSubstrateSection
import com.forager.app.domain.model.HymenophoreDetails
import com.forager.app.domain.model.HymenophoreSection
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.LogSyncState
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.Observed
import com.forager.app.domain.model.SporePrint
import com.forager.app.domain.model.SporePrintColor
import com.forager.app.domain.model.SporePrintSection
import com.forager.app.domain.model.StipeBase
import com.forager.app.domain.model.StipeDetails
import com.forager.app.domain.model.StipeInterior
import com.forager.app.domain.model.StipePosition
import com.forager.app.domain.model.StipeSection
import com.forager.app.domain.model.VeilSection
import com.forager.app.domain.model.VolvaType
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [RoomMushroomLogRepository] against a real, in-memory Room database — not a fake — the same
 * reasoning as [RoomPlannedTripRepositoryTest]: the thing worth verifying is the mapping Room
 * itself is responsible for, here considerably larger than [com.forager.app.data.local.PlannedTripEntity]'s:
 * every [Observed]/[Feature] column encoding described in [com.forager.app.data.local.MushroomLogEntryEntity]'s
 * doc comment, the sealed-choice discriminators, and the entry/photos two-table write.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomMushroomLogRepositoryTest {

    private lateinit var database: ForagerDatabase
    private lateinit var repository: RoomMushroomLogRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            ForagerDatabase::class.java,
        ).build()
        repository = RoomMushroomLogRepository(database.mushroomLogDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a barely-populated entry round-trips exactly, with every unrecorded field surviving as NotObserved`() = runTest {
        val entry = MushroomLogEntry.draft(id = "draft-1", location = LatLng(45.4, -122.7), date = LocalDate.of(2026, 8, 1))

        repository.save(entry).getOrThrow()
        val all = repository.getAll().getOrThrow()

        assertEquals(listOf(entry), all)
    }

    @Test
    fun `a fully-populated entry round-trips every field exactly, including nested sealed choices and photos`() = runTest {
        val entry = fullyPopulatedEntry()

        repository.save(entry).getOrThrow()
        val all = repository.getAll().getOrThrow()

        assertEquals(listOf(entry), all)
    }

    @Test
    fun `an entry recorded with Pores hymenophore carries no gill sub-fields after round-tripping`() = runTest {
        val entry = fullyPopulatedEntry().copy(
            hymenophore = HymenophoreSection(details = Observed.Recorded(HymenophoreDetails.Pores), notes = "pored"),
        )

        repository.save(entry).getOrThrow()
        val roundTripped = repository.getAll().getOrThrow().single()

        assertEquals(HymenophoreDetails.Pores, (roundTripped.hymenophore.details as Observed.Recorded).value)
    }

    @Test
    fun `saving a second entry with the same id replaces it rather than duplicating it`() = runTest {
        val original = MushroomLogEntry.draft(id = "e1", location = LatLng(45.0, -122.0), date = LocalDate.of(2026, 8, 1))
        val updated = original.copy(notes = "updated in the field", ownIdentification = "maybe a bolete")

        repository.save(original).getOrThrow()
        repository.save(updated).getOrThrow()
        val all = repository.getAll().getOrThrow()

        assertEquals(listOf(updated), all)
    }

    @Test
    fun `saving replaces the photo set rather than appending to it`() = runTest {
        val withOnePhoto = MushroomLogEntry.draft(id = "e1", location = LatLng(45.0, -122.0), date = LocalDate.of(2026, 8, 1))
            .copy(photos = listOf(LogPhoto(id = "p1", relativePath = "photos/p1.jpg")))
        val withDifferentPhoto = withOnePhoto.copy(photos = listOf(LogPhoto(id = "p2", relativePath = "photos/p2.jpg")))

        repository.save(withOnePhoto).getOrThrow()
        repository.save(withDifferentPhoto).getOrThrow()
        val roundTripped = repository.getAll().getOrThrow().single()

        assertEquals(listOf(LogPhoto(id = "p2", relativePath = "photos/p2.jpg")), roundTripped.photos)
    }

    @Test
    fun `deleting an entry removes its photos too, leaving other entries' photos intact`() = runTest {
        val keep = MushroomLogEntry.draft(id = "keep", location = LatLng(45.0, -122.0), date = LocalDate.of(2026, 8, 1))
            .copy(photos = listOf(LogPhoto(id = "keep-photo", relativePath = "photos/keep.jpg")))
        val remove = MushroomLogEntry.draft(id = "remove", location = LatLng(46.0, -123.0), date = LocalDate.of(2026, 8, 2))
            .copy(photos = listOf(LogPhoto(id = "remove-photo", relativePath = "photos/remove.jpg")))
        repository.save(keep).getOrThrow()
        repository.save(remove).getOrThrow()

        repository.delete("remove").getOrThrow()
        val all = repository.getAll().getOrThrow()

        assertEquals(listOf(keep), all)
    }

    @Test
    fun `getAll on an empty database returns an empty list, not a failure`() = runTest {
        val all = repository.getAll().getOrThrow()

        assertTrue(all.isEmpty())
    }

    @Test
    fun `deleting an id that isn't stored is a no-op, not a failure`() = runTest {
        val result = repository.delete("never-saved")

        assertTrue(result.isSuccess)
    }

    @Test
    fun `Uploaded and Failed sync states round-trip exactly, even though Phase 1 never constructs them`() = runTest {
        val uploaded = MushroomLogEntry.draft(id = "e1", location = LatLng(45.0, -122.0), date = LocalDate.of(2026, 8, 1))
            .copy(syncState = LogSyncState.Uploaded(remoteObservationId = "12345", uploadedAt = Instant.ofEpochMilli(1_000_000)))
        val failed = MushroomLogEntry.draft(id = "e2", location = LatLng(45.0, -122.0), date = LocalDate.of(2026, 8, 1))
            .copy(syncState = LogSyncState.Failed(reason = "network error", remoteObservationId = "12345"))

        repository.save(uploaded).getOrThrow()
        repository.save(failed).getOrThrow()
        val all = repository.getAll().getOrThrow().associateBy { it.id }

        assertEquals(uploaded.syncState, all.getValue("e1").syncState)
        assertEquals(failed.syncState, all.getValue("e2").syncState)
    }
}

private fun fullyPopulatedEntry(): MushroomLogEntry = MushroomLogEntry(
    id = "full-1",
    foundAt = LatLng(lat = 45.512, lng = -122.658),
    foundOn = LocalDate.of(2026, 8, 15),
    cap = CapSection(
        shape = Observed.Recorded(CapShape.CONVEX),
        surface = Observed.Recorded(CapSurface.VISCID),
        decorations = Feature.Present(setOf(CapDecoration.WARTS, CapDecoration.PATCHES)),
        margin = Observed.Recorded(CapMargin.STRIATE),
        notes = "between convex and flat at the very center",
    ),
    hymenophore = HymenophoreSection(
        details = Observed.Recorded(
            HymenophoreDetails.Gills(
                attachment = Observed.Recorded(GillAttachment.ADNEXED),
                spacing = Observed.Recorded(GillSpacing.CROWDED),
                edge = Observed.Recorded(GillEdge.SERRATED),
            ),
        ),
        notes = "gills bruise slightly on handling",
    ),
    stipe = StipeSection(
        details = Observed.Recorded(
            StipeDetails.Present(
                position = Observed.Recorded(StipePosition.CENTRAL),
                interior = Observed.Recorded(StipeInterior.HOLLOW),
                base = Observed.Recorded(StipeBase.BULBOUS),
            ),
        ),
        notes = "stipe tapers slightly toward the cap",
    ),
    veil = VeilSection(
        annulus = Feature.Present(AnnulusType.SKIRT),
        volva = Feature.Present(VolvaType.SACK),
        notes = "volva mostly buried, had to dig",
    ),
    contextFlesh = ContextFleshSection(
        texture = Observed.Recorded(FleshTexture.BRITTLE),
        colorChangeOnCutting = Feature.Present("pale yellow to orange"),
        exudate = Feature.Present("white latex, unchanging"),
        notes = "flesh thick near the cap center",
    ),
    sporePrint = SporePrintSection(
        details = Observed.Recorded(
            SporePrint(color = SporePrintColor.Other("pale olive-buff"), readOn = LocalDate.of(2026, 8, 16)),
        ),
        notes = "read after 18 hours under a glass",
    ),
    hostSubstrate = HostSubstrateSection(
        association = Observed.Recorded(Association.Mycorrhizal(hostSpecies = "Quercus garryana")),
        forestType = Observed.Recorded(ForestType.DECIDUOUS),
        hostHealth = Observed.Recorded(HostHealth.HEALTHY),
        notes = "growing at the drip line",
    ),
    notes = "found after the first real rain of the season",
    ownIdentification = "possibly Amanita",
    photos = listOf(
        LogPhoto(id = "photo-1", relativePath = "photos/photo-1.jpg"),
        LogPhoto(id = "photo-2", relativePath = "photos/photo-2.jpg"),
    ),
    syncState = LogSyncState.Draft,
)
