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
 * doc comment, the sealed-choice discriminators, and — across the entry table, the gallery's photo
 * table, and the entry-photo cross-reference table (`MIGRATION_7_8`, gallery ownership) — that
 * `save()` never touches a photo reference and `delete()` never touches a gallery photo.
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

        // save() never touches photo references (see MushroomLogRepository.save's own doc
        // comment) — a photo becomes part of what getAll() returns for this entry only once it's
        // been added to the gallery and attached, the same two steps AddPhotoToLogEntryUseCase
        // performs in production.
        repository.save(entry).getOrThrow()
        entry.photos.forEach { photo ->
            repository.addPhotoToGallery(photo).getOrThrow()
            repository.attachPhotoToEntry(entry.id, photo.id).getOrThrow()
        }
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

    /**
     * The structural fact G1 exists to fix: before `MIGRATION_7_8`, `save()` deleted and
     * reinserted an entry's *entire* photo row set on every call — this test used to be named
     * "saving replaces the photo set rather than appending to it" and proved exactly that. Under
     * gallery ownership `save()` must never touch photo references at all (autosave on every field
     * edit would otherwise churn cross-reference rows on every keystroke) — proved here by calling
     * `save()` with a stale `photos` list after attaching a real one, and confirming `getAll()`
     * still reflects only what was actually attached, not whatever `save()`'s argument happened to
     * carry in its own `photos` field.
     */
    @Test
    fun `save never touches photo references, however stale the entry argument's own photos field is`() = runTest {
        val entry = MushroomLogEntry.draft(id = "e1", location = LatLng(45.0, -122.0), date = LocalDate.of(2026, 8, 1))
        val attachedPhoto = LogPhoto(id = "p1", relativePath = "photos/p1.jpg", createdAtEpochMillis = 1_000L)
        val neverAttachedPhoto = LogPhoto(id = "p2", relativePath = "photos/p2.jpg", createdAtEpochMillis = 2_000L)

        repository.save(entry).getOrThrow()
        repository.addPhotoToGallery(attachedPhoto).getOrThrow()
        repository.attachPhotoToEntry(entry.id, attachedPhoto.id).getOrThrow()

        // A field-only save, carrying an entirely different (never-attached) photos list — save()
        // must ignore it completely, not replace the real cross-references with it.
        repository.save(entry.copy(notes = "field-only edit", photos = listOf(neverAttachedPhoto))).getOrThrow()
        val roundTripped = repository.getAll().getOrThrow().single()

        assertEquals(listOf(attachedPhoto), roundTripped.photos)
    }

    /**
     * L1's reversal, at the Room level (see [com.forager.app.domain.DeleteMushroomLogEntryUseCase]'s
     * own doc comment for the full history) — was "deleting an entry removes its photos too,
     * leaving other entries' photos intact." Under gallery ownership a photo survives the deletion
     * of any entry that referenced it, including — the many-to-many case one-to-many couldn't even
     * represent — an entry that shared it with another entry still holding a reference.
     */
    @Test
    fun `deleting an entry drops only its own photo references, including one shared with another entry`() = runTest {
        val shared = LogPhoto(id = "shared-photo", relativePath = "photos/shared.jpg", createdAtEpochMillis = 1_000L)
        val removedOnly = LogPhoto(id = "removed-only-photo", relativePath = "photos/removed-only.jpg", createdAtEpochMillis = 2_000L)
        val keep = MushroomLogEntry.draft(id = "keep", location = LatLng(45.0, -122.0), date = LocalDate.of(2026, 8, 1))
        val remove = MushroomLogEntry.draft(id = "remove", location = LatLng(46.0, -123.0), date = LocalDate.of(2026, 8, 2))
        repository.save(keep).getOrThrow()
        repository.save(remove).getOrThrow()
        repository.addPhotoToGallery(shared).getOrThrow()
        repository.addPhotoToGallery(removedOnly).getOrThrow()
        repository.attachPhotoToEntry(keep.id, shared.id).getOrThrow()
        repository.attachPhotoToEntry(remove.id, shared.id).getOrThrow()
        repository.attachPhotoToEntry(remove.id, removedOnly.id).getOrThrow()

        repository.delete("remove").getOrThrow()
        val all = repository.getAll().getOrThrow()

        assertEquals(listOf(keep.copy(photos = listOf(shared))), all)
        // The gallery rows themselves — not just what's still referenced — survive the entry
        // deletion; database is this test class's own field, the same real Room instance the
        // repository sits on.
        val remainingGalleryPhotoIds = database.mushroomLogDao().getAllPhotos().map { it.id }.toSet()
        assertEquals(setOf(shared.id, removedOnly.id), remainingGalleryPhotoIds)
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

    /**
     * Workstream G2: [MushroomLogRepository.getAllPhotos]'s own richer shape, against the real
     * Room-backed join — not a fake — including a photo shared by two entries (a gallery photo
     * with more than one referencing entry, only representable since G1's many-to-many).
     */
    @Test
    fun `getAllPhotos returns every gallery photo paired with the entries currently referencing it`() = runTest {
        val entryA = MushroomLogEntry.draft(id = "entry-a", location = LatLng(45.0, -122.0), date = LocalDate.of(2026, 8, 1))
        val entryB = MushroomLogEntry.draft(id = "entry-b", location = LatLng(45.1, -122.1), date = LocalDate.of(2026, 8, 2))
        val shared = com.forager.app.domain.model.LogPhoto(id = "shared", relativePath = "photos/shared.jpg", createdAtEpochMillis = 1_000L)
        val onlyA = com.forager.app.domain.model.LogPhoto(id = "only-a", relativePath = "photos/only-a.jpg", createdAtEpochMillis = 2_000L)
        repository.save(entryA).getOrThrow()
        repository.save(entryB).getOrThrow()
        repository.addPhotoToGallery(shared).getOrThrow()
        repository.addPhotoToGallery(onlyA).getOrThrow()
        repository.attachPhotoToEntry(entryA.id, shared.id).getOrThrow()
        repository.attachPhotoToEntry(entryB.id, shared.id).getOrThrow()
        repository.attachPhotoToEntry(entryA.id, onlyA.id).getOrThrow()

        val galleryPhotos = repository.getAllPhotos().getOrThrow().associateBy { it.photo.id }

        assertEquals(setOf(entryA.id, entryB.id), galleryPhotos.getValue("shared").referencingEntryIds.toSet())
        assertEquals(listOf(entryA.id), galleryPhotos.getValue("only-a").referencingEntryIds)
    }

    /**
     * The dispatch's own required case: `attachPhotoToEntry` failing after `addPhotoToGallery`
     * succeeds is the one reachable path to a photo with zero references (see
     * [AddPhotoToLogEntryUseCase]'s own doc comment) — `getAllPhotos` must still return it, not
     * drop it or crash reconstructing an empty referencing-entries list.
     */
    @Test
    fun `getAllPhotos includes a photo with zero referencing entries`() = runTest {
        val orphaned = com.forager.app.domain.model.LogPhoto(id = "orphaned", relativePath = "photos/orphaned.jpg", createdAtEpochMillis = 5_000L)
        repository.addPhotoToGallery(orphaned).getOrThrow()

        val galleryPhotos = repository.getAllPhotos().getOrThrow()

        assertEquals(1, galleryPhotos.size)
        assertEquals(orphaned, galleryPhotos.single().photo)
        assertTrue(galleryPhotos.single().referencingEntryIds.isEmpty())
    }

    @Test
    fun `getAllPhotos on an empty database returns an empty list, not a failure`() = runTest {
        val galleryPhotos = repository.getAllPhotos().getOrThrow()

        assertTrue(galleryPhotos.isEmpty())
    }

    /**
     * Workstream G3's own gate: "the cross-ref table's composite primary key should handle it, but
     * test it rather than assuming." Attaches the same photo to the same entry twice and asserts
     * against the real Room table, not a fake — a duplicate row here would show up as a duplicate
     * thumbnail in [LogEntryDetailScreen]'s `FlowRow`.
     */
    @Test
    fun `attaching the same photo to the same entry twice does not duplicate the reference`() = runTest {
        val entry = MushroomLogEntry.draft(id = "entry-a", location = LatLng(45.0, -122.0), date = LocalDate.of(2026, 8, 1))
        val photo = com.forager.app.domain.model.LogPhoto(id = "p1", relativePath = "photos/p1.jpg", createdAtEpochMillis = 1_000L)
        repository.save(entry).getOrThrow()
        repository.addPhotoToGallery(photo).getOrThrow()

        repository.attachPhotoToEntry(entry.id, photo.id).getOrThrow()
        repository.attachPhotoToEntry(entry.id, photo.id).getOrThrow()

        val reloaded = repository.getAll().getOrThrow().single { it.id == entry.id }
        assertEquals(listOf(photo), reloaded.photos)
    }

    /** Workstream G3: a photo already referenced by two entries, pulled into a third, ends up referenced by all three — only representable since G1's many-to-many. */
    @Test
    fun `a photo referenced by two entries, pulled into a third, is referenced by three`() = runTest {
        val entryA = MushroomLogEntry.draft(id = "entry-a", location = LatLng(45.0, -122.0), date = LocalDate.of(2026, 8, 1))
        val entryB = MushroomLogEntry.draft(id = "entry-b", location = LatLng(45.1, -122.1), date = LocalDate.of(2026, 8, 2))
        val entryC = MushroomLogEntry.draft(id = "entry-c", location = LatLng(45.2, -122.2), date = LocalDate.of(2026, 8, 3))
        val photo = com.forager.app.domain.model.LogPhoto(id = "shared", relativePath = "photos/shared.jpg", createdAtEpochMillis = 1_000L)
        repository.save(entryA).getOrThrow()
        repository.save(entryB).getOrThrow()
        repository.save(entryC).getOrThrow()
        repository.addPhotoToGallery(photo).getOrThrow()
        repository.attachPhotoToEntry(entryA.id, photo.id).getOrThrow()
        repository.attachPhotoToEntry(entryB.id, photo.id).getOrThrow()

        repository.attachPhotoToEntry(entryC.id, photo.id).getOrThrow()

        val referencingIds = repository.getAllPhotos().getOrThrow().single { it.photo.id == photo.id }.referencingEntryIds
        assertEquals(setOf(entryA.id, entryB.id, entryC.id), referencingIds.toSet())
    }

    /** Workstream G3: closes the deletion gap G1 left open. Asserted against the real Room tables, not a fake — the whole point is that no row (photo or cross-reference) survives. */
    @Test
    fun `deletePhotoFromGallery removes the photo row and every cross-reference to it`() = runTest {
        val entryA = MushroomLogEntry.draft(id = "entry-a", location = LatLng(45.0, -122.0), date = LocalDate.of(2026, 8, 1))
        val entryB = MushroomLogEntry.draft(id = "entry-b", location = LatLng(45.1, -122.1), date = LocalDate.of(2026, 8, 2))
        val photo = com.forager.app.domain.model.LogPhoto(id = "p1", relativePath = "photos/p1.jpg", createdAtEpochMillis = 1_000L)
        repository.save(entryA).getOrThrow()
        repository.save(entryB).getOrThrow()
        repository.addPhotoToGallery(photo).getOrThrow()
        repository.attachPhotoToEntry(entryA.id, photo.id).getOrThrow()
        repository.attachPhotoToEntry(entryB.id, photo.id).getOrThrow()

        repository.deletePhotoFromGallery(photo.id).getOrThrow()

        assertTrue("the gallery photo row must be gone", repository.getAllPhotos().getOrThrow().isEmpty())
        val reloaded = repository.getAll().getOrThrow().associateBy { it.id }
        assertTrue("entry-a's reference must be gone too", reloaded.getValue(entryA.id).photos.isEmpty())
        assertTrue("entry-b's reference must be gone too", reloaded.getValue(entryB.id).photos.isEmpty())
    }

    @Test
    fun `deletePhotoFromGallery on an unknown photo id is a no-op, not a failure`() = runTest {
        val result = repository.deletePhotoFromGallery("never-existed")

        assertTrue(result.isSuccess)
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
        LogPhoto(id = "photo-1", relativePath = "photos/photo-1.jpg", createdAtEpochMillis = 3_000L),
        LogPhoto(id = "photo-2", relativePath = "photos/photo-2.jpg", createdAtEpochMillis = 4_000L),
    ),
    syncState = LogSyncState.Draft,
)
