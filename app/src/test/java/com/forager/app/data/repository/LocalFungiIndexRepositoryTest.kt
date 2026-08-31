package com.forager.app.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.forager.app.data.local.fungiindex.FungiIndexDatabase
import com.forager.app.data.local.fungiindex.FungiTaxonEntity
import com.forager.app.data.local.fungiindex.FungiTaxonNameEntity
import com.forager.app.domain.normalizeSearchName
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [LocalFungiIndexRepository] against a real, in-memory Room database, populated exactly the way
 * `GenerateFungiIndexDbAsset` populates the shipped asset — same [FungiIndexDao.insertTaxon]/
 * [FungiIndexDao.insertNames] calls, same [normalizeSearchName] normalization — so this proves the
 * tiered SQL query and the dedup-by-taxon logic against real Room behavior rather than a fake that
 * would just echo back whatever this test assumed about both (CLAUDE.md: assert on actual output,
 * not a proxy for it). Fixture data mirrors the real index's shape for the dispatch's own
 * motivating cases — see `data/species-index/fungi-us-species-index.json` for the source records
 * these numbers come from.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalFungiIndexRepositoryTest {

    private lateinit var database: FungiIndexDatabase
    private lateinit var repository: LocalFungiIndexRepository

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            FungiIndexDatabase::class.java,
        ).build()
        repository = LocalFungiIndexRepository(database.fungiIndexDao())

        seed(taxonId = 49158, scientificName = "Hericium erinaceus", observationCount = 10028, commonNames = listOf("lion's-mane mushroom", "lion's mane mushroom"))
        seed(taxonId = 55276, scientificName = "Lactarius rubidus", observationCount = 2500, commonNames = listOf("Candy Cap"))
        seed(taxonId = 52135, scientificName = "Amanita phalloides", observationCount = 6170, commonNames = listOf("Death Cap", "Euro-Asian Death Cap"))
        seed(taxonId = 48607, scientificName = "Craterellus cornucopioides", observationCount = 10, commonNames = listOf("Black Trumpet"))
        seed(taxonId = 58682, scientificName = "Morchella esculenta", observationCount = 2, commonNames = listOf("Yellow Morel", "Common Morel"))
        // A decoy with "mane" in its common name but far lower observation count than Hericium's
        // exact-tier match, so the tiering test also proves count only breaks ties within a tier.
        seed(taxonId = 999999, scientificName = "Decoyus manensis", observationCount = 999999999, commonNames = listOf("fake mane mushroom"))
    }

    private suspend fun seed(taxonId: Long, scientificName: String, observationCount: Int, commonNames: List<String>) {
        val dao = database.fungiIndexDao()
        dao.insertTaxon(FungiTaxonEntity(taxonId, scientificName, observationCount))
        val names = buildList {
            add(FungiTaxonNameEntity(taxonId = taxonId, name = scientificName, isScientific = true, nameNormalized = normalizeSearchName(scientificName)))
            commonNames.forEach { add(FungiTaxonNameEntity(taxonId = taxonId, name = it, isScientific = false, nameNormalized = normalizeSearchName(it))) }
        }
        dao.insertNames(names)
    }

    @After
    fun tearDown() {
        if (database.isOpen) database.close()
    }

    @Test
    fun `lions mane finds Hericium erinaceus ahead of a lower-tier decoy also containing mane`() = runTest {
        val results = repository.searchTaxa("lions mane").getOrThrow()

        assertTrue(results.isNotEmpty())
        assertEquals(49158L, results.first().taxonId)
        assertEquals("Hericium erinaceus", results.first().scientificName)
        assertEquals("lion's mane mushroom", results.first().commonName)
    }

    @Test
    fun `apostrophe and casing variants of the same query all match the same taxon`() = runTest {
        val withApostrophe = repository.searchTaxa("lion's mane").getOrThrow()
        val mixedCase = repository.searchTaxa("Lions Mane").getOrThrow()

        assertEquals(49158L, withApostrophe.first().taxonId)
        assertEquals(49158L, mixedCase.first().taxonId)
    }

    @Test
    fun `a taxon matching on two common names appears exactly once, not twice`() = runTest {
        val results = repository.searchTaxa("lions mane").getOrThrow()

        assertEquals(1, results.count { it.taxonId == 49158L })
    }

    @Test
    fun `candy cap resolves to Lactarius rubidus`() = runTest {
        val results = repository.searchTaxa("candy cap").getOrThrow()

        assertEquals(55276L, results.first().taxonId)
    }

    @Test
    fun `death cap resolves to Amanita phalloides and is not pushed down by ranking`() = runTest {
        val results = repository.searchTaxa("death cap").getOrThrow()

        assertEquals(52135L, results.first().taxonId)
    }

    @Test
    fun `black trumpet resolves to a Craterellus`() = runTest {
        val results = repository.searchTaxa("black trumpet").getOrThrow()

        assertTrue(results.first().scientificName.startsWith("Craterellus"))
    }

    @Test
    fun `morel resolves to a Morchella`() = runTest {
        val results = repository.searchTaxa("morel").getOrThrow()

        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.scientificName.startsWith("Morchella") })
    }

    @Test
    fun `scientific-name search returns a null common name`() = runTest {
        val results = repository.searchTaxa("hericium").getOrThrow()

        assertEquals(49158L, results.first().taxonId)
        assertEquals("Hericium erinaceus", results.first().scientificName)
        assertNull(results.first().commonName)
    }

    @Test
    fun `a query matching nothing returns an empty list, not a failure`() = runTest {
        val outcome = repository.searchTaxa("zzzzz")

        assertTrue(outcome.isSuccess)
        assertTrue(outcome.getOrThrow().isEmpty())
    }

    @Test
    fun `iconicTaxonName is always Fungi, since every taxon in this index is one`() = runTest {
        val results = repository.searchTaxa("morel").getOrThrow()

        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.iconicTaxonName == "Fungi" })
    }

    @Test
    fun `a query that normalizes to nothing returns empty rather than matching every row`() = runTest {
        val outcome = repository.searchTaxa("--").getOrThrow()

        assertTrue(outcome.isEmpty())
    }
}
