package com.zynergylabs.forager.app.routing

import android.content.Context
import java.io.File

/**
 * The BRouter profile Forager routes with, and putting it where the engine can read it (dispatch 2026-10-23-02).
 *
 * **Which profile:** BRouter's stock `hiking-mountain.brf`, unchanged, from v1.7.10. The spike measured it keeping
 * to the named trail on all three trail routes (item 5), and the route cutter's proof routes with it
 * (`server/pmtiles-worker/test/rd5/fetch-brouter.sh`). Of BRouter's stock profiles it is the walking one;
 * `shortest.brf` ignores the kind of way.
 *
 * **Pinned** by git blob hash at BRouter commit 4d2639af, the same pins as the cutter's proof. The files ship as
 * assets under [ASSET_DIR]; the tests check each against its pin.
 *
 * BRouter reads a profile by file path, and `lookups.dat` from the same folder (`ProfileCache.parseProfile`), so
 * [install] copies both out of the APK's assets into the app's own storage.
 */
object RoutingProfile {
    const val PROFILE_FILE = "hiking-mountain.brf"
    const val LOOKUPS_FILE = "lookups.dat"

    /** Git blob hashes (`git hash-object`) at BRouter v1.7.10, commit 4d2639af. */
    val PINNED_BLOBS = mapOf(
        PROFILE_FILE to "f2bed1a5c21895195d6fe47c4b1982b5f0a0d4c5",
        LOOKUPS_FILE to "60e59d083bfff198e27e14aae6a3d4ed44e1cb2b",
    )

    const val ASSET_DIR = "routing/profiles2"

    /**
     * Copies the profile and its lookups into `files/routing/profiles2` and returns that folder, for
     * [BRouterTrailRouter]'s `profileDir`. A file already there with the same bytes is left alone, so its
     * timestamp, which BRouter's profile cache keys on, does not move; one that differs, from an older app, is
     * replaced. Blocking file work: call it off the main thread.
     */
    fun install(context: Context): File {
        val dir = File(context.filesDir, ASSET_DIR).apply { mkdirs() }
        for (name in PINNED_BLOBS.keys) {
            val bytes = context.assets.open("$ASSET_DIR/$name").use { it.readBytes() }
            val target = File(dir, name)
            if (!(target.isFile && target.readBytes().contentEquals(bytes))) {
                val partial = File(dir, "$name.partial")
                partial.writeBytes(bytes)
                check(partial.renameTo(target)) { "could not move ${partial.name} into place" }
            }
        }
        return dir
    }
}
