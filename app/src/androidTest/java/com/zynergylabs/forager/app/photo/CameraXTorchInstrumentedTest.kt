package com.zynergylabs.forager.app.photo

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The torch, on the real camera — the one part of the flash work no Robolectric test can reach
 * (`CameraXCaptureSession`'s own doc: there is no camera provider in that harness). This drives the
 * **production** session and reads **CameraX's own** `CameraInfo.torchState`, not the session's
 * `flashMode`, so a session that reported a mode it never asked the hardware for would fail here.
 *
 * Device check steps 8 and 9 (dispatch of 2026-09-22). Requires a camera with a flash unit; the
 * test says so rather than passing vacuously if there is none.
 *
 * The app's own CAMERA permission is used: this runs in the app's process, and the check's phone
 * already granted it.
 */
@RunWith(AndroidJUnit4::class)
class CameraXTorchInstrumentedTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var provider: ProcessCameraProvider
    private lateinit var owner: TestOwner

    /** A lifecycle the test drives, since `bindToLifecycle` needs one at least STARTED. */
    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private companion object {
        const val TAG = "CameraXTorchTest"
    }

    @Before
    fun setUp() {
        provider = ProcessCameraProvider.getInstance(context).get()
        owner = TestOwner()
        onMain { owner.registry.currentState = Lifecycle.State.RESUMED }
    }

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private fun torchState(): Int? =
        provider.getCameraInfo(CameraSelector.DEFAULT_BACK_CAMERA).torchState.value

    /** Polls CameraX's own torch state, since `enableTorch` completes asynchronously. Returns how long it took, or null if it never got there. */
    private fun awaitTorch(expected: Int, timeoutMillis: Long = 3_000): Long? {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMillis) {
            if (torchState() == expected) return System.currentTimeMillis() - start
            Thread.sleep(25)
        }
        return null
    }

    private fun openSession(): CameraXCaptureSession {
        val session = CameraXCaptureSession(context.applicationContext)
        onMain { session.open(owner) }
        val start = System.currentTimeMillis()
        while (session.state !is CameraSessionState.Ready && System.currentTimeMillis() - start < 10_000) {
            Thread.sleep(50)
        }
        assertEquals("the camera opened", CameraSessionState.Ready, session.state)
        return session
    }

    @Test
    fun torch_goes_on_and_off_with_the_flash_mode() {
        val session = openSession()
        assertTrue("this camera has a flash unit; without one the check says nothing", session.hasFlashUnit)
        try {
            onMain { session.setFlashMode(FlashMode.Torch) }
            val onAfter = awaitTorch(TorchState.ON)
            Log.i(TAG, "DEVICE-CHECK torch ON after ${onAfter}ms; hasFlashUnit=${session.hasFlashUnit}")
            assertTrue("CameraX reports the torch ON after Torch (waited ${onAfter}ms)", onAfter != null)

            onMain { session.setFlashMode(FlashMode.Off) }
            val offAfter = awaitTorch(TorchState.OFF)
            Log.i(TAG, "DEVICE-CHECK torch OFF after ${offAfter}ms")
            assertTrue("CameraX reports the torch OFF after Off (waited ${offAfter}ms)", offAfter != null)
        } finally {
            onMain { session.close() }
        }
    }

    @Test
    fun closing_with_the_torch_on_puts_it_out() {
        val session = openSession()
        assertTrue("this camera has a flash unit", session.hasFlashUnit)
        onMain { session.setFlashMode(FlashMode.Torch) }
        assertTrue("precondition: the torch is on", awaitTorch(TorchState.ON) != null)

        onMain { session.close() }
        // Read once immediately, to see whether OFF is already visible the moment close() returns —
        // that is the "before the session unbinds" case. Then allow up to a second, which is the
        // weaker claim the dispatch accepts. The report says which of the two was observed.
        val immediate = torchState()
        val within = awaitTorch(TorchState.OFF, timeoutMillis = 1_000)
        Log.i(TAG, "DEVICE-CHECK close: torchState immediately after close()=$immediate (${TorchState.OFF}=OFF, ${TorchState.ON}=ON); OFF observed after ${within}ms; session.flashMode=${session.flashMode}")
        assertTrue(
            "the torch is out within a second of close (immediately after close it read $immediate, OFF seen after ${within}ms)",
            within != null,
        )
        assertEquals("the session's own mode is reset by close", FlashMode.Off, session.flashMode)
    }
}
