package com.zynergylabs.forager.app.ui.map

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.R
import com.zynergylabs.forager.app.map.initializeMapLibre

/**
 * Starts MapLibre before [content] (the real map) is composed, and shows "The map couldn't start." with a "Try again"
 * button in the map's place if it can't (dispatch 2026-10-18-04, Amendment 1; RECORD -1283 (2), -1284).
 *
 * Before this, `SightingsMap` called [initializeMapLibre] inside `remember {}` with nothing to catch it, and the
 * initialiser logs and rethrows, so a failed start-up threw during composition and most likely closed the app (read from
 * the code, not seen on a phone). Every map in the app is a `SightingsMap` composed through [SightingsMapSlot], which is
 * where this gate sits, so every map gets the same handling.
 *
 * The failure is logged by the initialiser itself ("MapLibre initialisation failed; it will be retried on the next
 * call."), once per attempt, so it is caught here without being swallowed. Try again re-runs [start]; the initialiser
 * remembers nothing after a failure, so the retry is a real one. On success [content] is composed exactly as it was
 * before this gate existed, with no wrapper around it, so the working map's layout and touch handling are unchanged.
 *
 * The message sits where the map would be and covers no map, so it is not map chrome and its surface is solid.
 *
 * [start] is [initializeMapLibre] in production; a test passes its own.
 */
@Composable
internal fun MapStartGate(
    modifier: Modifier,
    start: (Context) -> Unit = ::initializeMapLibre,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    val started = remember(attempt) { runCatching { start(context) }.isSuccess }
    if (started) {
        content()
    } else {
        MapStartFailed(modifier = modifier, onTryAgain = { attempt++ })
    }
}

@Composable
private fun MapStartFailed(modifier: Modifier, onTryAgain: () -> Unit) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.map_start_failed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onTryAgain) {
                Text(stringResource(R.string.map_start_try_again))
            }
        }
    }
}
