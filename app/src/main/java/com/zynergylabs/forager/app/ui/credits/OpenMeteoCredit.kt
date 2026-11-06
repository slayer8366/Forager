package com.zynergylabs.forager.app.ui.credits

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.zynergylabs.forager.app.ui.motion.clickableWithShapedPress

/** Open-Meteo's required attribution wording (open-meteo.com/en/licence, read 2026-10-13). */
internal const val OPEN_METEO_CREDIT_TEXT = "Weather data by Open-Meteo.com"

/** Where Open-Meteo's licence says that attribution links: their own site. */
internal const val OPEN_METEO_CREDIT_URL = "https://open-meteo.com/"

internal const val OPEN_METEO_CREDIT_TAG = "open-meteo-credit"

/**
 * "Weather data by Open-Meteo.com", as a link, beside figures that came from Open-Meteo (dispatch
 * 2026-10-13-05). Their licence: "You must include a link next to any location Open-Meteo data are
 * displayed". Each place that draws their figures puts one of these at its own foot, in the same card,
 * and only while it is drawing a figure: a loading spinner or an error message shows no Open-Meteo data.
 *
 * The whole line is the touch target, at least 48 dp tall, though the text is small.
 */
@Composable
internal fun OpenMeteoCredit(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .testTag(OPEN_METEO_CREDIT_TAG)
            .heightIn(min = 48.dp)
            .clickableWithShapedPress(role = Role.Button, onClickLabel = "Open Open-Meteo.com") { openWebLink(context, OPEN_METEO_CREDIT_URL) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            OPEN_METEO_CREDIT_TEXT,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        )
    }
}

/** A plain web link; a test asserts its action and address without anything installed to open it. */
internal fun webLinkIntent(url: String): Intent = Intent(Intent.ACTION_VIEW, url.toUri())

/** Shown when nothing installed can open a web link. CLAUDE.md: report, don't swallow. */
internal const val NO_BROWSER_MESSAGE = "Nothing installed can open this link."

private const val TAG = "CreditsLink"

/**
 * Opens [url] in whatever the phone opens web links with. Checked before launching, with the
 * [ActivityNotFoundException] caught as well for the race where the browser goes away between the
 * check and the launch; either way the person is told, and the log says so (the same shape as
 * `launchDirections` in `ui/availability/AvailabilityPureFunctions.kt`).
 */
internal fun openWebLink(context: Context, url: String) {
    val intent = webLinkIntent(url)
    if (intent.resolveActivity(context.packageManager) == null) {
        Log.w(TAG, "No app resolves a web link to $url; told the user.")
        Toast.makeText(context, NO_BROWSER_MESSAGE, Toast.LENGTH_SHORT).show()
        return
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "The app for $url went away between the check and the launch; told the user.", e)
        Toast.makeText(context, NO_BROWSER_MESSAGE, Toast.LENGTH_SHORT).show()
    }
}
