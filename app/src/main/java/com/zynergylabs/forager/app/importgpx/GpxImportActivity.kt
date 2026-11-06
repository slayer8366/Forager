package com.zynergylabs.forager.app.importgpx

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.zynergylabs.forager.app.ForagerApplication
import com.zynergylabs.forager.app.domain.GpxImportFailure
import com.zynergylabs.forager.app.domain.GpxImportOutcome
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The "invisible doorway" (the owner's answer in -636): what Open with and Share reach when another app
 * hands Forager a GPX file (plan T16). It has no screen of its own (a translucent theme, no content). It
 * reads the file, saves it through the app's one [com.zynergylabs.forager.app.domain.ImportGpxUseCase],
 * then opens Forager on Records showing the new track ([mainActivityIntentAfterImport]) and finishes.
 *
 * **Why not MainActivity's own intent filters.** Open with into MainActivity would start a second
 * MainActivity in the sender's task, whose TrackRecordingViewModel takes up a running recording and sends
 * the recording service ACTION_START again (the verify findings in -636). This Activity builds no
 * TrackRecordingViewModel, sends nothing to the service, and touches no recording, navigation or alert
 * state: it writes ended tracks and their waypoints and nothing else.
 *
 * The save runs [NonCancellable]: leaving this Activity in the moment it is open must not cut an import
 * in half; the use case either keeps the whole file or removes what it wrote. `configChanges` in the
 * manifest keeps a rotation from recreating it, so a file is not imported twice.
 */
class GpxImportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ForagerApplication).container
        val uri = gpxUriFrom(intent)
        lifecycleScope.launch {
            val outcome = withContext(NonCancellable) {
                if (uri == null) {
                    container.errorLog.w(TAG, "An Open with or Share reached GPX import with no file in it (action ${intent?.action}).", IllegalArgumentException("no Uri"))
                    GpxImportOutcome.Failed(GpxImportFailure.UNREADABLE)
                } else {
                    container.importGpxUseCase(ContentUriGpxFileSource(contentResolver, uri, container.errorLog))
                }
            }
            startActivity(mainActivityIntentAfterImport(this@GpxImportActivity, outcome))
            finish()
        }
    }

    private companion object {
        const val TAG = "ImportGpx"
    }
}

/** The file an Open with (`ACTION_VIEW`, the Intent's data) or a Share (`ACTION_SEND`, its `EXTRA_STREAM`) carries. */
fun gpxUriFrom(intent: Intent?): Uri? = when (intent?.action) {
    Intent.ACTION_VIEW -> intent.data
    Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
    else -> null
}
