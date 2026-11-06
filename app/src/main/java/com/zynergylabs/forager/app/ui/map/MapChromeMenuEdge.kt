package com.zynergylabs.forager.app.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Dispatch 2026-11-05-06 (RECORD -2154, the owner: "Thin outline (Recommended)", whose option read "No shadow. A crisp 1 dp line at
 * the edge, the same hairline you chose for the icon bar, bottom bar and header (-966/-967). The window reads an even 80% right to
 * its edge"): the edge of a Material menu (`DropdownMenu`, `ExposedDropdownMenu`, both of which take these two as parameters in
 * material3 1.5.0-alpha26) where it is shown. Over a map its fill is the map chrome's 0.8, and Material's own 3 dp shadow
 * (`MenuDefaults.ShadowElevation`, `ElevationTokens.Level2`) showed through it as a band inside its edges, so there it has none and
 * carries the hairline. Where it covers no map its fill is solid, which hides that band, and it stays as Material draws it (A3).
 */

/** A menu's shadow: none over a map, Material's own elsewhere. */
internal fun mapChromeMenuShadowElevation(overMap: Boolean): Dp = if (overMap) 0.dp else MenuDefaults.ShadowElevation

/** A menu's border: the map-chrome hairline ([mapChromeOutline]) over a map, none elsewhere, as Material draws it. */
@Composable
@ReadOnlyComposable
internal fun mapChromeMenuBorder(overMap: Boolean): BorderStroke? = if (overMap) mapChromeOutline() else null
