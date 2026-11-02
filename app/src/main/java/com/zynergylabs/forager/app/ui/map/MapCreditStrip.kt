package com.zynergylabs.forager.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics

/** D25, D26: the credit strip. Test-first stub: always shown, never fades. */
@Composable
internal fun MapCreditStrip(
    basemap: Basemap,
    useOfflineTiles: Boolean,
    layerCredits: List<String>,
    nightMode: Boolean,
    ownStreet: Boolean,
    userCameraGestures: Int,
    modifier: Modifier = Modifier,
) {
    MapCreditCaption(
        text = attributionCaption(mapCreditsFor(basemap, useOfflineTiles, layerCredits, nightMode, ownStreet)),
        modifier = modifier.semantics { mapCreditStripAlpha = 1f },
    )
}

/** Test-first stub. */
internal fun drawsOpenStreetMapRaster(basemap: Basemap, useOfflineTiles: Boolean, night: Boolean, ownStreet: Boolean): Boolean = false

/** The alpha the credit strip draws at now, 1 shown and 0 faded into the "i", readable in tests. */
val MapCreditStripAlphaKey = SemanticsPropertyKey<Float>("MapCreditStripAlpha")
var SemanticsPropertyReceiver.mapCreditStripAlpha by MapCreditStripAlphaKey

/** OSMF's attribution guidelines: the credit may fade "automatically after five seconds". */
internal const val MAP_CREDIT_STRIP_SHOWN_MILLIS = 5_000L
