package com.zynergylabs.forager.app.routing

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

/** The profile the app ships, against its pins, and its install into the app's storage (dispatch 2026-10-23-02). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoutingProfileTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `each shipped profile file is BRouter v1_7_10's, by its pinned git blob hash`() {
        assertEquals(setOf("hiking-mountain.brf", "lookups.dat"), RoutingProfile.PINNED_BLOBS.keys)
        for ((name, pin) in RoutingProfile.PINNED_BLOBS) {
            val bytes = context.assets.open("${RoutingProfile.ASSET_DIR}/$name").use { it.readBytes() }
            assertEquals("$name's git blob hash", pin, gitBlobHash(bytes))
        }
    }

    @Test
    fun `install puts both files in the app's storage, byte for byte`() {
        val dir = RoutingProfile.install(context)
        assertEquals(File(context.filesDir, "routing/profiles2"), dir)
        for (name in RoutingProfile.PINNED_BLOBS.keys) {
            val shipped = context.assets.open("${RoutingProfile.ASSET_DIR}/$name").use { it.readBytes() }
            assertArrayEquals(name, shipped, File(dir, name).readBytes())
        }
        assertEquals(RoutingProfile.PINNED_BLOBS.keys, dir.list().orEmpty().toSet())
    }

    @Test
    fun `install replaces a file that differs and leaves an identical one untouched`() {
        val dir = RoutingProfile.install(context)
        val lookups = File(dir, RoutingProfile.LOOKUPS_FILE).apply { setLastModified(1_000_000_000L) }
        val profile = File(dir, RoutingProfile.PROFILE_FILE).apply { writeText("an older profile") }

        RoutingProfile.install(context)

        assertEquals("identical: not rewritten, so BRouter's profile cache stays valid", 1_000_000_000L, lookups.lastModified())
        assertEquals("replaced", RoutingProfile.PINNED_BLOBS.getValue(RoutingProfile.PROFILE_FILE), gitBlobHash(profile.readBytes()))
    }

    private fun gitBlobHash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-1").run {
        update("blob ${bytes.size}\u0000".toByteArray())
        update(bytes)
        digest().joinToString("") { "%02x".format(it) }
    }
}
