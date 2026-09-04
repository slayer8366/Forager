package com.forager.app.tools

import android.app.Application
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.forager.app.data.local.fungiindex.FungiIndexDatabase
import com.forager.app.data.local.fungiindex.FungiTaxonEntity
import com.forager.app.data.local.fungiindex.FungiTaxonNameEntity
import com.forager.app.domain.normalizeSearchName
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regenerates `app/src/main/assets/databases/fungi_index.db`
 * ([com.forager.app.data.local.fungiindex.FungiIndexDatabase]'s prepackaged asset) from
 * `data/species-index/fungi-us-species-index.json`.
 *
 * Not a Python script over raw SQLite, deliberately: [Room.databaseBuilder.createFromAsset]
 * validates a copied-in database against an identity hash Room's own annotation processor bakes
 * into [FungiIndexDatabase]'s generated code at compile time — that hash is not something to
 * reproduce independently, so the asset has to come out of a real, running [FungiIndexDatabase]
 * (see `RoomOpenHelper.checkIdentity`, upstream). Robolectric is what makes that possible headless
 * on the JVM (`android.database.sqlite` needs a real or shadowed Android runtime, which is exactly
 * what every other Room test in this codebase already uses Robolectric for), and this reuses that
 * same mechanism rather than adding a new one.
 *
 * Skipped by an ordinary `./gradlew test`/`testDebugUnitTest` run — see the `assumeTrue` guard
 * below and `app/build.gradle.kts`'s doc comment on the `testDebugUnitTest` block that wires its
 * opt-in flag through. This is a generator, not a test: it has no assertions, and running it is a
 * manual step taken after the index JSON changes, not on every build. Run it via
 * `scripts/generate_fungi_index_db.sh`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GenerateFungiIndexDbAsset {

    @Test
    fun generate() = runBlocking {
        assumeTrue(
            "Skipped: pass -Pforager.generateFungiIndexDbAsset=true (see scripts/generate_fungi_index_db.sh). " +
                "This test rewrites a committed asset and is not meant to run as part of the ordinary suite.",
            System.getProperty("forager.generateFungiIndexDbAsset") == "true",
        )

        val repoRoot = findRepoRoot()
        val sourceJson = File(repoRoot, "data/species-index/fungi-us-species-index.json")
        check(sourceJson.isFile) { "Expected the index JSON at $sourceJson" }

        val records = Json.decodeFromString<List<FungiIndexJsonRecord>>(sourceJson.readText())
        check(records.isNotEmpty()) { "Parsed zero records out of $sourceJson" }

        val outputFile = File(repoRoot, "app/src/main/assets/databases/fungi_index.db")
        outputFile.parentFile.mkdirs()
        listOf(outputFile, File(outputFile.path + "-wal"), File(outputFile.path + "-shm"), File(outputFile.path + "-journal"))
            .forEach { if (it.exists()) it.delete() }

        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            FungiIndexDatabase::class.java,
            outputFile.absolutePath,
        )
            // A single self-contained file when generation finishes, not main+WAL+shm — this file
            // is what gets copied byte-for-byte into every install via createFromAsset.
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()

        val dao = db.fungiIndexDao()
        for (record in records) {
            dao.insertTaxon(
                FungiTaxonEntity(
                    taxonId = record.taxonId,
                    scientificName = record.scientificName,
                    observationCount = record.observationCount,
                ),
            )
            val names = buildList {
                add(
                    FungiTaxonNameEntity(
                        taxonId = record.taxonId,
                        name = record.scientificName,
                        isScientific = true,
                        nameNormalized = normalizeSearchName(record.scientificName),
                    ),
                )
                record.commonNames.forEach { common ->
                    add(
                        FungiTaxonNameEntity(
                            taxonId = record.taxonId,
                            name = common,
                            isScientific = false,
                            nameNormalized = normalizeSearchName(common),
                        ),
                    )
                }
            }
            dao.insertNames(names)
        }
        db.close()

        println(
            "Generated $outputFile (${outputFile.length()} bytes) from ${records.size} taxa, " +
                "${records.sumOf { it.commonNames.size + 1 }} name rows.",
        )
    }
}

@Serializable
private data class FungiIndexJsonRecord(
    @SerialName("taxon_id") val taxonId: Long,
    @SerialName("scientific_name") val scientificName: String,
    @SerialName("common_names") val commonNames: List<String> = emptyList(),
    @SerialName("observation_count") val observationCount: Int,
)

/** Walks up from the working directory to the checkout root, identified by `settings.gradle.kts`. */
private fun findRepoRoot(): File {
    var dir: File? = File(".").absoluteFile
    while (dir != null) {
        if (File(dir, "settings.gradle.kts").isFile) return dir
        dir = dir.parentFile
    }
    error("Could not find repo root (no settings.gradle.kts in any parent of ${File(".").absolutePath})")
}
