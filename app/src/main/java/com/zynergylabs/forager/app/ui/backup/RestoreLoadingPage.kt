package com.zynergylabs.forager.app.ui.backup

import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.zynergylabs.forager.app.R
import com.zynergylabs.forager.app.ui.theme.Spacing

/** Test tags for the restore loading page. */
internal const val RESTORE_PAGE_TAG = "restore-loading-page"
internal const val RESTORE_ICON_TAG = "restore-icon"

/** The icon's current scale, exposed so a test can see it pulse, stop, and grow on the tap. */
internal val RestoreIconScale = SemanticsPropertyKey<Float>("RestoreIconScale")
internal var SemanticsPropertyReceiver.restoreIconScale by RestoreIconScale

/** The icon's size. Well over the 48 dp a touch target needs, and the whole icon is the button. */
private val ICON_SIZE = 168.dp

/** The tap animation (owner, "1 A"): about 300 ms, the icon grows slightly and the page fades out over the Maps tab. */
private const val LEAVE_MILLIS = 300

/** How much the icon grows over the leave animation. */
private const val LEAVE_GROWTH = 0.25f

private const val PULSE_PEAK = 1.08f
private const val PULSE_HALF_MILLIS = 700
private const val ICON_PIXELS = 512

/**
 * The page the app shows after a restore commits (owner, "6 B"; "approve have a pulsing app icon with Done in the
 * center, be the done button to tap", then "Item 4: B"): the app icon, pulsing, with "Loading your restored journal…"
 * while every screen reads the restored data again; then the icon **stops**, the text reads "Your journal is
 * restored." and "Done" sits at the icon's centre, **and the icon is the button**.
 *
 * - Full screen and opaque, and it takes every touch, so nothing underneath is pressed through it (CLAUDE.md, the
 *   Surface pitfall): a Material3 `Surface` consumes the touches on its bounds, which is what makes it so, and a test
 *   touches the page outside the icon to prove it.
 * - **The tap animation** (owner, "1 A"): the icon grows slightly and the page fades out, about 300 ms, revealing the
 *   Maps tab that [BackupViewModel.onRestoreDoneTapped] asked for at the moment of the tap. With the system's animator
 *   duration scale at 0 (reduced motion) it leaves at once.
 * - The icon is a button with the content description "Done", 168 dp across (a touch target of at least 48 dp).
 *
 * [onLeft] is called when the exit animation ends, so the ViewModel can drop the page.
 */
@Composable
internal fun RestoreLoadingPage(page: RestorePage, onDoneTapped: () -> Unit, onLeft: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val pulse = remember { Animatable(1f) }
    val leave = remember { Animatable(0f) }

    LaunchedEffect(page) {
        when (page) {
            RestorePage.LOADING -> while (true) {
                pulse.animateTo(PULSE_PEAK, tween(PULSE_HALF_MILLIS, easing = FastOutSlowInEasing))
                pulse.animateTo(1f, tween(PULSE_HALF_MILLIS, easing = FastOutSlowInEasing))
            }
            RestorePage.DONE -> pulse.animateTo(1f, tween(200))
            RestorePage.LEAVING -> {
                val animationsOff = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
                if (!animationsOff) leave.animateTo(1f, tween(LEAVE_MILLIS, easing = FastOutSlowInEasing))
                onLeft()
            }
            RestorePage.NONE -> Unit
        }
    }

    val scale = pulse.value * (1f + LEAVE_GROWTH * leave.value)
    val icon = remember { ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap(ICON_PIXELS, ICON_PIXELS)?.asImageBitmap() }
    val done = page != RestorePage.LOADING

    Surface(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - leave.value }
            .testTag(RESTORE_PAGE_TAG),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
        ) {
            Box(
                modifier = Modifier
                    .size(ICON_SIZE)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .semantics(mergeDescendants = true) { restoreIconScale = scale }
                    .testTag(RESTORE_ICON_TAG)
                    .clip(CircleShape)
                    .then(
                        if (done) {
                            Modifier
                                .semantics { contentDescription = "Done"; role = Role.Button }
                                .clickable(onClick = onDoneTapped)
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(ICON_SIZE))
                if (done) {
                    // On a pill of the theme's own colour, so it reads over whatever the icon's art is doing behind it.
                    Text(
                        "Done",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f), RoundedCornerShape(50))
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    )
                }
            }
            Text(
                if (done) "Your journal is restored." else "Loading your restored journal…",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The page the app draws over everything while a restore reloads and after; nothing when there is none. `MainActivity`
 * puts it above the screen, and the ViewModel's state feeds it and the screen's `returnToMapRequest` alike.
 */
@Composable
internal fun BackupRestoreOverlay(controls: BackupControls, modifier: Modifier = Modifier) {
    if (controls.state.restorePage != RestorePage.NONE) {
        RestoreLoadingPage(
            page = controls.state.restorePage,
            onDoneTapped = controls.onRestoreDoneTapped,
            onLeft = controls.onRestorePageLeft,
            modifier = modifier,
        )
    }
}
