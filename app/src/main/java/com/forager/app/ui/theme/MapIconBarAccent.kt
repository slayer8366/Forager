package com.forager.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The fill/on-fill pair [com.forager.app.ui.availability.MapIconBar]'s add row (and the record row
 * while active) use for their own permanent accent circle — deliberately not
 * `MaterialTheme.colorScheme.primary`/`error` directly, even though every value here is one of
 * those same roles' own hues.
 *
 * Material's tonal inversion — a *lighter* tone for a role in dark theme, a *darker* one in light
 * theme — exists so that role stays legible drawn directly on a plain
 * `MaterialTheme.colorScheme.surface`. Neither icon-bar row does that: both sit on the bar's own
 * opaque fill (`MapIconStackButtonColorDark`/`MapIconStackButtonColorLight` in `AvailabilityScreen.kt`),
 * which is what actually carries the "read against whatever's behind it" job here. Left on
 * Material's roles, the result reads backwards for a reason that has nothing to do with the map or
 * the bar: [Lichen], the *lighter* green, is dark theme's `primary`, and [ForestGreen], the
 * *darker* one, is light theme's — real, reported confusion ("light theme applies to dark theme and
 * vice versa"), not a defect in either colour's own value. [ADD_LIGHT]/[ADD_DARK] swap that pairing
 * so each theme's icon-bar accent is the tone that actually matches it — [Lichen] (pale) in light
 * theme, [ForestGreen] (deep) in dark theme — carrying over the same on-accent contrast pairing
 * `Theme.kt` already defines for each colour ([ForestDeep] is [Lichen]'s own `onPrimary` there;
 * [NearWhite] is [ForestGreen]'s), just applied to the other theme. [error]'s [SignalRed]/
 * [SignalRedLight] pair gets the same treatment in [RECORD_LIGHT]/[RECORD_DARK].
 *
 * Packaged here rather than as loose `Color` imports into `AvailabilityScreen.kt`, mirroring
 * [MapPalette]'s own precedent: a component whose colours are deliberately not derived from the
 * ambient `ColorScheme` is an owned type in `ui/theme/`, not raw palette literals reaching into a
 * feature package (`scripts/verify-design-tokens.sh` check 2 exempts imports of this type by name,
 * the same way it already exempts [MapPalette]).
 */
data class MapIconBarAccent(val fill: Color, val onFill: Color) {
    companion object {
        val ADD_LIGHT = MapIconBarAccent(fill = Lichen, onFill = ForestDeep)
        val ADD_DARK = MapIconBarAccent(fill = ForestGreen, onFill = NearWhite)
        val RECORD_LIGHT = MapIconBarAccent(fill = SignalRedLight, onFill = SignalRedOnContainerDark)
        val RECORD_DARK = MapIconBarAccent(fill = SignalRed, onFill = PureWhite)
    }
}
