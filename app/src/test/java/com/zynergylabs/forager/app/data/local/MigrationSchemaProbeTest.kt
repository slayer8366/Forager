package com.zynergylabs.forager.app.data.local

// PROBE — wire-migration-tests dispatch §2, 2026-09-13, rerun on PR #102's branch. Three separate
// questions, three tests, so each answer is read on its own: (1) does Robolectric serve app assets
// here and is the Instrumentation context's AssetManager the application's; (2) does the helper,
// as shipped, find app/schemas/ (expected: no — AGP merges no unit-test assets); (3) can app/schemas/
// be added to that AssetManager at test time through addAssetPath. Every test throws with its
// numbers so the JUnit XML carries them. To be DELETED or rewritten once the gate is recorded.

import android.content.Context
import android.content.res.AssetManager
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MigrationSchemaProbeTest {
    private val schemaAsset = "com.zynergylabs.forager.app.data.local.ForagerDatabase/4.json"

    @Test
    fun `PROBE 1 - asset pipeline - app asset opens, instrumentation context shares the AssetManager`() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val n1 = app.assets.open("databases/fungi_index.db").use { it.read(ByteArray(16)) }
        val instr = InstrumentationRegistry.getInstrumentation().context
        val n2 = instr.assets.open("databases/fungi_index.db").use { it.read(ByteArray(16)) }
        throw AssertionError("PROBE1 RESULT: appCtx read=$n1; instrCtx read=$n2; sameAssetManager=${app.assets === instr.assets}")
    }

    @Test
    fun `PROBE 2 - helper as shipped - createDatabase v4 from committed 4_json`() {
        val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ForagerDatabase::class.java)
        val outcome = runCatching { helper.createDatabase("probe2.db", 4).close() }
        throw AssertionError("PROBE2 RESULT: ${outcome.fold({ "created v4 from 4.json OK" }, { "${it::class.java.simpleName}: ${it.message?.take(160)}" })}")
    }

    @Test
    fun `PROBE 3 - addAssetPath route - schemas dir added to the shared AssetManager, then 4_json read`() {
        val assets = ApplicationProvider.getApplicationContext<Context>().assets
        val dir = File("schemas").absolutePath
        val cookie = runCatching { AssetManager::class.java.getMethod("addAssetPath", String::class.java).invoke(assets, dir) as Int }
        val read = cookie.getOrNull()?.takeIf { it != 0 }?.let { runCatching { assets.open(schemaAsset).use { s -> s.read(ByteArray(8)) } } }
        throw AssertionError("PROBE3 RESULT: dir=$dir exists=${File(dir).isDirectory} cookie=${cookie.fold({ it.toString() }, { it::class.java.simpleName })} read4json=${read?.fold({ "$it bytes" }, { it::class.java.simpleName }) ?: "not attempted"}")
    }

    /** Zips app/schemas/ (paths relative to it) into a temp file, the form Robolectric's asset loader takes. */
    private fun zipSchemas(): File {
        val root = File("schemas"); val zip = File.createTempFile("schemas-", ".zip")
        ZipOutputStream(FileOutputStream(zip)).use { out ->
            root.walkTopDown().filter { it.isFile }.forEach { f ->
                out.putNextEntry(ZipEntry(f.relativeTo(root).path.replace(File.separatorChar, '/'))); f.inputStream().use { it.copyTo(out) }; out.closeEntry()
            }
        }
        return zip
    }

    @Test
    fun `PROBE 4 - addAssetPath with a ZIP of schemas, then 4_json read`() {
        val assets = ApplicationProvider.getApplicationContext<Context>().assets
        val zip = zipSchemas()
        val cookie = runCatching { AssetManager::class.java.getMethod("addAssetPath", String::class.java).invoke(assets, zip.absolutePath) as Int }
        val read = cookie.getOrNull()?.takeIf { it != 0 }?.let { runCatching { assets.open(schemaAsset).use { s -> s.read(ByteArray(8)) } } }
        throw AssertionError("PROBE4 RESULT: zip=${zip.length()} bytes cookie=${cookie.fold({ it.toString() }, { it::class.java.simpleName })} read4json=${read?.fold({ "$it bytes" }, { "${it::class.java.simpleName}: ${it.message?.take(80)}" }) ?: "not attempted"}")
    }

    @Test
    fun `PROBE 5 - with the ZIP added, helper creates v4 from 4_json, migrates 4 to 5, validates 5_json`() {
        val assets = ApplicationProvider.getApplicationContext<Context>().assets
        val cookie = AssetManager::class.java.getMethod("addAssetPath", String::class.java).invoke(assets, zipSchemas().absolutePath) as Int
        if (cookie == 0) throw AssertionError("PROBE5 RESULT: not attempted (zip cookie=0)")
        val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ForagerDatabase::class.java)
        val outcome = runCatching {
            helper.createDatabase("probe5.db", 4).use { db -> db.execSQL("INSERT INTO mushroom_log_entries (id, lat, lng, foundOnEpochDay) VALUES ('p', 45.5, -122.6, 20000)") }
            val migrated = helper.runMigrationsAndValidate("probe5.db", 5, true, MIGRATION_4_5)
            val n = migrated.query("SELECT COUNT(*) FROM mushroom_log_entries").use { c -> c.moveToFirst(); c.getInt(0) }
            migrated.close(); n
        }
        throw AssertionError("PROBE5 RESULT: ${outcome.fold({ "v4 from 4.json, 4->5 migrated and VALIDATED against 5.json, rows=$it" }, { "${it::class.java.simpleName}: ${it.message?.take(200)}" })}")
    }
}
