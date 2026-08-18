package com.forager.app.domain.model

import java.lang.reflect.Modifier
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Headless proof of the plan's central modeling rule (see `docs/plans/mushroom-log.md`): three
 * states, not two, and inapplicable fields have no slot to be wrongly filled — enforced by the type
 * system, not by a validation check that runs after the fact.
 *
 * The field-shape tests below inspect the compiled class's own declared fields via plain
 * reflection, rather than only relying on "the file compiles" — a [HymenophoreDetails.Pores] entry
 * genuinely has zero fields at runtime, not merely a convention nobody violated yet.
 */
class ThreeStateModelTest {

    private fun instanceFieldNames(clazz: Class<*>): Set<String> =
        clazz.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.map { it.name }.toSet()

    @Test
    fun `Observed has exactly two states, and they are never equal to each other`() {
        val recorded: Observed<CapShape> = Observed.Recorded(CapShape.CONVEX)
        val notObserved: Observed<CapShape> = Observed.NotObserved

        assertNotEquals(recorded, notObserved)
        assertEquals(CapShape.CONVEX, recorded.valueOrNull())
        assertEquals(null, notObserved.valueOrNull())
    }

    @Test
    fun `Feature has exactly three states, pairwise distinct`() {
        val present: Feature<AnnulusType> = Feature.Present(AnnulusType.RING)
        val absent: Feature<AnnulusType> = Feature.Absent
        val notObserved: Feature<AnnulusType> = Feature.NotObserved

        assertNotEquals(present, absent)
        assertNotEquals(present, notObserved)
        // The distinction the whole type exists for: absence and "never checked" are not the same fact.
        assertNotEquals(absent, notObserved)

        assertEquals(AnnulusType.RING, present.valueOrNull())
        assertEquals(null, absent.valueOrNull())
        assertEquals(null, notObserved.valueOrNull())
    }

    @Test
    fun `Pores, Teeth, and SmoothOrWrinkled carry no fields — there is nowhere to put a gill attachment`() {
        assertEquals(emptySet<String>(), instanceFieldNames(HymenophoreDetails.Pores.javaClass))
        assertEquals(emptySet<String>(), instanceFieldNames(HymenophoreDetails.Teeth.javaClass))
        assertEquals(emptySet<String>(), instanceFieldNames(HymenophoreDetails.SmoothOrWrinkled.javaClass))
    }

    @Test
    fun `Gills carries exactly attachment, spacing, and edge — the only place those fields exist`() {
        assertEquals(
            setOf("attachment", "spacing", "edge"),
            instanceFieldNames(HymenophoreDetails.Gills::class.java),
        )
    }

    @Test
    fun `an entry recorded as Pores cannot be asked for a gill attachment`() {
        // Not a runtime assertion — HymenophoreDetails.Pores has no `attachment` property to read
        // at all, so this is checked by the fact that the line below would not compile if it did:
        // `(HymenophoreDetails.Pores as HymenophoreDetails).attachment` is not valid Kotlin.
        // What *is* checked here is that recovering the recorded kind still works correctly.
        val details: HymenophoreDetails = HymenophoreDetails.Pores
        assertEquals(HymenophoreDetails.Pores, details)
    }

    @Test
    fun `StipeDetails Absent carries no fields — an absent stipe has no interior or base to record`() {
        assertEquals(emptySet<String>(), instanceFieldNames(StipeDetails.Absent.javaClass))
    }

    @Test
    fun `StipeDetails Present carries exactly position, interior, and base`() {
        assertEquals(
            setOf("position", "interior", "base"),
            instanceFieldNames(StipeDetails.Present::class.java),
        )
    }

    @Test
    fun `a freshly-started log entry has every section unrecorded, not defaulted to a value`() {
        val entry = MushroomLogEntry.draft(id = "e1", location = LatLng(45.0, -122.0), date = LocalDate.of(2026, 8, 1))

        assertEquals(Observed.NotObserved, entry.cap.shape)
        assertEquals(Observed.NotObserved, entry.cap.surface)
        assertEquals(Feature.NotObserved, entry.cap.decorations)
        assertEquals(Observed.NotObserved, entry.cap.margin)
        assertEquals(Observed.NotObserved, entry.hymenophore.details)
        assertEquals(Observed.NotObserved, entry.stipe.details)
        assertEquals(Feature.NotObserved, entry.veil.annulus)
        assertEquals(Feature.NotObserved, entry.veil.volva)
        assertEquals(Observed.NotObserved, entry.contextFlesh.texture)
        assertEquals(Feature.NotObserved, entry.contextFlesh.colorChangeOnCutting)
        assertEquals(Feature.NotObserved, entry.contextFlesh.exudate)
        assertEquals(Observed.NotObserved, entry.sporePrint.details)
        assertEquals(Observed.NotObserved, entry.hostSubstrate.association)
        assertEquals(Observed.NotObserved, entry.hostSubstrate.forestType)
        assertEquals(Observed.NotObserved, entry.hostSubstrate.hostHealth)
        assertEquals(LogSyncState.Draft, entry.syncState)
    }
}
