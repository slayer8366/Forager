package com.zynergylabs.forager.app.ui.log

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * The one place [DecodedPhoto] gets the dispatcher it decodes on. Production is [Dispatchers.IO],
 * exactly as before this seam existed; nothing in `main/` ever sets [override].
 *
 * **Why it exists** (dispatch 2026-09-28-351). The owner chose, for the leaving-the-journal CI
 * flake: "tests control the photo-loading thread. The app loads photos exactly as it does now,
 * tests swap in a thread they control, it is one small change to app code, and it covers every
 * test that shows a photo." The cause is inferred from CI stacks and was never reproduced on
 * demand (`docs/audits/2026-10-01-leaving-journal-flake-diagnosis.md`, -349, 6 failures in 99 CI
 * runs; `docs/audits/2026-10-01-decoded-photo-thread-interim.md`, -348): `DecodedPhoto`'s
 * `withContext(Dispatchers.IO)` returns on an IO worker, the Compose test environment resumes the
 * effect inline on that worker when the main thread is outside a test-clock frame, and the worker
 * then recomposes and applies while the main thread is in its own layout pass. A test default that
 * keeps the decode, and so the resumption, on the main thread removes the second thread. The
 * default is installed by the unit-test source set (`PhotoDecodeTestEnvironment`), so every
 * Robolectric test gets it without editing the class.
 *
 * Rejected: a `CompositionLocal` or a parameter on [DecodedPhoto], because a test default for every
 * existing test would then mean editing every `setContent` or every caller up the tree; a check
 * for "running under Robolectric" inside the production code, because production code that knows
 * it is under test is the thing this seam exists to avoid.
 *
 * A thread-pinning default is only safe for a decode that does not block on another thread, so a
 * test that holds the decode on a latch asks for a real background decode instead
 * (`PhotoDecodeTestEnvironment.useBackgroundDecode`).
 */
internal object PhotoDecodeDispatcher {
    @Volatile
    private var override: CoroutineDispatcher? = null

    /** What [DecodedPhoto] decodes on: [Dispatchers.IO] unless [override] has been set. Read at each decode. */
    val current: CoroutineDispatcher get() = override ?: Dispatchers.IO

    /** For the unit-test source set only: `null` restores the production default. */
    fun override(dispatcher: CoroutineDispatcher?) {
        override = dispatcher
    }
}
