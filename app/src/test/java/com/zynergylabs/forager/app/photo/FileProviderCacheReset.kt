package com.zynergylabs.forager.app.photo

import androidx.core.content.FileProvider
import org.junit.rules.ExternalResource

/**
 * Clears `FileProvider`'s process-wide cache before and after each test. Any test class that asks
 * for a capture URI needs this rule, or its second method fails.
 *
 * **`FileProvider` caches one `PathStrategy` per authority in a static map, and Robolectric gives
 * every `@Test` method a fresh data directory.** So the strategy built during the first method
 * that asks for a capture URI points at that method's temp `filesDir` for the rest of the JVM's
 * life, and every later method fails with `Failed to find configured root that contains …`.
 * Nothing about the message says "stale cache", and the trap only springs in a class run: each
 * such test passes on its own, which was how it was diagnosed — running one alone and watching it
 * go green is what separated "my file paths are wrong" from "state survives between methods".
 *
 * The same shape as the `by preferencesDataStore` singleton this project already avoids for
 * exactly this reason (CLAUDE.md, the Room/DataStore pitfall): a per-process cache is invisible
 * under Robolectric until a second test method meets it.
 *
 * Cleared on the way out as well as in, as hygiene: a rule that writes process-wide state puts it
 * back. That is **not** claimed to fix any cross-class failure — see the correction note on
 * [com.zynergylabs.forager.app.ui.log.InAppCameraDialogTest], where an earlier version of this
 * code did claim exactly that and was withdrawn.
 *
 * `sCache` was confirmed by reflecting over `FileProvider`'s declared fields, not assumed from
 * memory, and the reset fails loudly rather than silently doing nothing if androidx ever renames
 * it — a cleanup that quietly stops cleaning is how this bug comes back wearing a different message.
 */
class FileProviderCacheReset : ExternalResource() {
    override fun before() = clear()
    override fun after() = clear()

    private fun clear() {
        val cache = runCatching { FileProvider::class.java.getDeclaredField("sCache") }.getOrElse {
            throw AssertionError(
                "androidx FileProvider no longer has a static `sCache` field. The per-authority " +
                    "PathStrategy cache is what makes FileProvider tests fail in a class run but pass " +
                    "alone; find where it lives now rather than deleting this.",
                it,
            )
        }
        cache.isAccessible = true
        (cache.get(null) as MutableMap<*, *>).clear()
    }
}
