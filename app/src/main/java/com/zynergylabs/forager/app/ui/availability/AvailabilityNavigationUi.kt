package com.zynergylabs.forager.app.ui.availability

// Split-AvailabilityScreen Stage F: the compact navigation, moved verbatim out of
// AvailabilityScreen.kt. Three blocks, lines of the file as of 3bd0efe: 329-376 (CompactTab,
// CompactTab.icon), 2280-2427 (ForagerBottomNav, ForagerNavigationRail,
// shortLandscapeContentInsets, COMPACT_NAVIGATION_RAIL_TAG, ScreenEdge.horizontalInsetsSide) and
// 4804-4835 (EXIT_NAVIGATION_PROMPT_TAG, EXIT_NAVIGATION_PROMPT_EXIT_TAG,
// EXIT_NAVIGATION_PROMPT_KEEP_TAG, ExitNavigationPrompt). Same package as Stages A to E, so every
// same-package reference resolves unchanged. Pure move: no signature, name or body changed. Seven
// widenings, private -> internal: CompactTab, ForagerBottomNav, ForagerNavigationRail,
// shortLandscapeContentInsets, horizontalInsetsSide and ExitNavigationPrompt, because their callers
// stay in AvailabilityScreen.kt (CompactTab, ForagerBottomNav and ForagerNavigationRail are also
// used by CompactMapTab in AvailabilityCompactMapUi.kt); and CompactTab.icon, widened with its enum
// as the dispatch listed it, though its only callers are the two bars here. No symbol left behind
// is reached from here. Seam F (the wide layout) was released by the owner for this split, as
// recorded in the Understory amendment merged in #130.

import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.add
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.mapChromeContentColor
import com.zynergylabs.forager.app.ui.map.mapChromeFill
import com.zynergylabs.forager.app.ui.map.MapChromeOutlineSides
import com.zynergylabs.forager.app.ui.map.mapChromeEdgesReported
import com.zynergylabs.forager.app.ui.map.mapChromeShape
import com.zynergylabs.forager.app.ui.map.mapChromeOutline
import com.zynergylabs.forager.app.ui.log.ScreenEdge

/**
 * The compact bottom nav's five destinations, in trip order left to right — Pre-trip surfaces
 * (List, Seasonal) then the surface the user is actually in (Maps, a true centre — depends on
 * this being an odd count; a sixth destination would break the centring), then Post-trip and rare
 * (Journal, Tools). [ResultsTab] stays a 3-way enum, kept in sync with this enum's
 * own `selectedTab` (see [AvailabilityScreen]'s `ForagerBottomNav` call site) whenever the tapped
 * destination is one of those three.
 *
 * **Album is not a destination here any more.** It folds into [JOURNAL] as a third top tab
 * alongside Log/Drafts (see [LogGalleryScreen]'s own doc comment) — Album and log entries share a
 * phase and a frequency (per-find capture at the specimen, Post-trip review at home), so they
 * belong in one destination rather than two separate ones that only existed apart because they
 * were built apart.
 *
 * **[TOOLS] does not behave like the other four.** Tapping it never sets `compactTab` to
 * [TOOLS] — see [ForagerBottomNav]'s own call site — it opens [CompactToolsDrawerContent] as an
 * overlay instead, over whatever tab was already showing, the same drawer the removed MapIconBar
 * search icon used to open (that icon is gone — see `MapIconBar`'s own doc comment — this tab is
 * its replacement entry point, not a new destination alongside it). "Tools" is deliberately a
 * catch-all for things used but not wanted in the immediate way — per-trip and rare items that are
 * not destinations in their own right (trip planner, waypoints, and now Settings
 * too — see that composable's own doc comment). Search itself — basic species/category search,
 * Recent Searches, and Advanced Search — is **not** part of that list any more: it moved out into
 * [SearchDropdown], reached from [ActiveSearchSummary] up top rather than from this drawer, exactly
 * so it would not read as one more Tools entry. That inclusion rule is what the next addition here
 * has to argue against becoming a junk drawer.
 *
 * **One owner-ruled exception: "Offline maps"** (RECORD -868, the owner: "Add a quick button to the
 * Tools drawer for Offline Maps. Tapping it brings you to the Offline Maps page"; recorded here at
 * the planner's direction, RECORD -871). Downloading maps is per-trip, which fits the rule, but the
 * Offline maps page is a destination in its own right (Journal > Records, on its chip), which the
 * rule says Tools items are not. The row is a shortcut to that destination, the only entry here that
 * leaves the drawer; it does not move Offline maps into Tools. "Waypoints" in the list above is stale:
 * waypoints left Tools for Records in Journal restructure Stage 1 (`CompactToolsDrawerContent`'s doc
 * comment).
 */
internal enum class CompactTab(val label: String) {
    LIST("List"),
    SEASONAL("Seasonal"),
    MAP("Maps"),
    JOURNAL("Journal"),
    TOOLS("Tools"),
}

/** The compact bottom nav's icon per destination — see [ForagerBottomNav]. */
internal fun CompactTab.icon(): ImageVector = when (this) {
    CompactTab.LIST -> Icons.AutoMirrored.Filled.List
    CompactTab.SEASONAL -> Icons.Filled.WbSunny
    CompactTab.MAP -> Icons.Filled.Map
    CompactTab.JOURNAL -> Icons.Filled.MenuBook
    // Same icon Settings itself used before this dispatch folded it into Tools' own drawer — no
    // "tools/build" icon exists in this app's icon set (only the core Material icons module is a
    // dependency, not the extended one a wrench/handyman glyph would need), and Settings is still
    // genuinely part of what this destination opens.
    CompactTab.TOOLS -> Icons.Filled.Settings
}

/**
 * Replaces the compact-only top [SecondaryTabRow] — decision #4 in `docs/plans/map-redesign.md`,
 * back down to [CompactTab]'s **5** destinations as of this dispatch: List, Seasonal, Maps,
 * Journal, Tools. **Corrected twice now**: this comment said "5" before Album was added as its own
 * destination (2026-08-28 correction), then needed correcting again to "6" once Album actually
 * landed, and is "5" again for real now that Album folded into Journal and Settings folded into
 * Tools — re-derived directly against [CompactTab.entries] rather than trusted from either prior
 * count, per this project's own repeated history of exactly this drift.
 *
 * [selectedTab] is [AvailabilityScreen]'s own `compactTab`, with one exception: [CompactTab.TOOLS]
 * never actually becomes the selected `compactTab` (see that entry's own doc comment — tapping it
 * opens a drawer instead), so [isDrawerOpen] stands in for its own highlight specifically. Every
 * other entry highlights the ordinary way.
 *
 * **Two call sites, never both for the same tab at once** (fullscreen-fixes dispatch, Item 1, third
 * design): `compactMainScaffold`'s own `bottomBar` slot renders this, unconditionally opaque, for
 * every tab except Map; the Map tab's own instance lives inside `CompactMapTab`'s own content `Box`
 * instead, `selectedTab` hardcoded to [CompactTab.MAP] there — see that composable's own doc
 * comment for why the nav can't stay in `bottomBar` for that one tab.
 *
 * Its own real rendered height (not a fixed constant) is measured, not guessed — see
 * `compactMainScaffold`'s own `bottomNavHeightPx` doc comment for why a flat Material3 spec value
 * (80dp) undershoots this on a real device by exactly the system navigation-bar inset, invisible
 * under Robolectric (CLAUDE.md's own "Known pitfalls").
 */
@Composable
internal fun ForagerBottomNav(
    selectedTab: CompactTab,
    isDrawerOpen: Boolean,
    onTabSelected: (CompactTab) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Opaque by default — the three docked, `bottomBar`-hosted instances. The Map tab's own
     * overlay instance (inside `CompactMapTab`'s Box, floating over the map) passes 80%, the
     * standing chrome-over-the-map treatment this file's own map-chrome family uses everywhere
     * else — restored on the owner's own call after a screenshot. History, so it isn't flipped a
     * third time without knowing: fullscreen-maps Part 2a first floated it at 80% *while
     * fullscreen*; the fullscreen-fixes dispatch made it slide fully off-screen in fullscreen and
     * went opaque, reasoning it was never both visible and over the map at once; this restores
     * translucency for the non-fullscreen state, where it *is* always over the map.
     */
    containerColor: Color = navigationBarContainerColor(),
    /**
     * Dispatch 2026-10-12-06, RECORD -937 (the owner: "let's give the botton navigation buttons the same rounded edge treatment
     * so it looks uniform"): the radius of the bar's two top corners. The Map tab's overlay instance passes the card's radius;
     * the bar stays full width, runs to the bottom edge and keeps its fill. A clip, so the corners are cut from its touch area as
     * well as its drawing: a touch in a corner gap reaches the map beneath. 0: square. Since dispatch 2026-10-17-09 (RECORD
     * -1224, -1225) the docked instance on the other tabs passes the same radius, solid, so the bar keeps its shape across the
     * tab switch; its corner gaps show the page's background, and nothing is under them to touch.
     */
    topCornerRadius: Dp = 0.dp,
) {
    // Dispatch 2026-10-17-09: the shape the bar is clipped to, also set on the node for tests (mapChromeShape).
    val barShape = if (topCornerRadius > 0.dp) RoundedCornerShape(topStart = topCornerRadius, topEnd = topCornerRadius) else RectangleShape
    NavigationBar(
        containerColor = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .then(if (topCornerRadius > 0.dp) Modifier.clip(barShape) else Modifier)
            // Dispatch 2026-10-13-02 (RECORD -966, -967): over the map (rounded, the Map tab's instance), the icon bar's outline
            // along the bar's top edge and its two rounded corners. RECORD -989 (the owner: "give the side of the bottom navigation
            // strip the outline too. All the way down. Just not the bottom since it's supposed to meld into the android navbar"):
            // down both sides to the bar's bottom as well; its bottom edge carries none. Until -989 its sides carried none either.
            .then(
                if (topCornerRadius > 0.dp) {
                    Modifier.mapChromeEdgesReported(
                        mapChromeOutline(), topCornerRadius, topCornerRadius, 0.dp, 0.dp,
                        MapChromeOutlineSides(left = true, top = true, right = true, bottom = false),
                    )
                } else {
                    Modifier
                },
            )
            .testTag(COMPACT_BOTTOM_NAV_TAG)
            .mapChromeContainerColor(containerColor)
            .mapChromeShape(barShape),
    ) {
        CompactTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = if (tab == CompactTab.TOOLS) isDrawerOpen else selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon(), contentDescription = null) },
                label = { Text(tab.label) },
                // Colored entirely from MaterialTheme.colorScheme rather than the fixed Bark/
                // Color.White an earlier revision used — that hardcoding was a real bug, not a
                // style choice: it left this bar the same dark brown regardless of system light/
                // dark theme, while every other surface in the app (and this same bar's own
                // active-tab color, already colorScheme.primary) switched with it.
                // NavigationBarItemDefaults.colors' own defaults already give the unselected/
                // indicator roles sensible theme-following values, so this only overrides
                // selectedIconColor/selectedTextColor to keep the app's own forest green
                // (colorScheme.primary — ForestGreen in light theme, MossGreen in dark, per that
                // theme's own doc comment) as the active-tab accent, matching this file's other
                // hand-picked accents rather than leaving it at M3's default secondary-container
                // tint.
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

/**
 * [ForagerBottomNav] turned on its side for a short landscape window — landscape build step B1,
 * `docs/plans/landscape-phone-design.md` P2/P3 and Resolutions R12 and R14. A Material 3
 * [NavigationRail] on the charger-port edge ([portEdge], from `ui/adaptive`'s `portEdgeFor`), next
 * to the system navigation bar, which the capture found on that same edge at both landscape
 * rotations.
 *
 * The same destinations, labels, icons, colours and selection behaviour as [ForagerBottomNav], in
 * [RAIL_ORDER_TOP_TO_BOTTOM]: the bar's left-to-right order read from the bottom up, List lowest and Tools highest (RECORD
 * -1312; until then [CompactTab.entries] top to bottom, List highest). It shares the bar's `onTabSelected` handler, so a
 * tab switch is one piece of logic in either orientation. [CompactTab.TOOLS] highlights on
 * [isDrawerOpen] exactly as it does there. No header Search action yet: that comes with P8's
 * search sheet in B2 (R14).
 *
 * Two containers, one rail (R12 as revised on the owner's correction). On the Map tab it is an
 * overlay at 80% ([containerColor], the standing opacity for chrome over the map, as
 * [ForagerBottomNav]'s overlay uses), so the map under it never changes size when it hides; on
 * every other tab it is opaque, beside the content. It takes the `navigationBars` inset on its
 * own side only; the top inset comes from the Scaffold padding it sits inside, like the content
 * beside or under it. Robolectric reports zero insets (CLAUDE.md, "Known pitfalls"), so where it
 * sits against the real system bar is a device item (B4).
 */
@Composable
internal fun ForagerNavigationRail(
    selectedTab: CompactTab,
    isDrawerOpen: Boolean,
    onTabSelected: (CompactTab) -> Unit,
    portEdge: ScreenEdge,
    modifier: Modifier = Modifier,
    containerColor: Color = navigationBarContainerColor(),
    /**
     * Dispatch 2026-10-12-08, RECORD -952 (the owner: "In landscape mode: Can we have the rounded corners on the app navigation
     * buttons (the one on the right side of the display) like it is in portrait?"): the radius of the rail's two corners that face
     * the map, its inner side's top and bottom, as [ForagerBottomNav]'s `topCornerRadius` rounds the bar's two top corners. A
     * clip, so the corners are cut from its touch area as well as its drawing and a touch in a corner gap reaches the map. 0:
     * square. Since dispatch 2026-10-17-09 the docked rail beside the other tabs' content passes the same radius, solid.
     */
    innerCornerRadius: Dp = 0.dp,
) {
    // The inner side is the side away from the port edge, where the map is.
    val innerCorners = if (portEdge == ScreenEdge.Left) {
        AbsoluteRoundedCornerShape(topRight = innerCornerRadius, bottomRight = innerCornerRadius)
    } else {
        AbsoluteRoundedCornerShape(topLeft = innerCornerRadius, bottomLeft = innerCornerRadius)
    }
    // Dispatch 2026-10-17-09: the shape the rail is clipped to, also set on the node for tests (mapChromeShape).
    val railShape = if (innerCornerRadius > 0.dp) innerCorners else RectangleShape
    NavigationRail(
        containerColor = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        // Dispatch 2026-10-18-01 (the owner, S22 check group 2: "have the rail extend fully to the side so there's no cutoff"):
        // the cut-out's band on the rail's side is the rail's too, as the navigation bar's always was. The surface runs under
        // both to the screen's edge and only the items are padded clear of them, on every tab. Until then this was the
        // navigation bar alone, and beside List, Seasonal and Journal the content insets reserved the cut-out outside the rail,
        // so a strip of page background showed between it and the edge whenever the punch hole was on the rail's side.
        windowInsets = WindowInsets.navigationBars.union(WindowInsets.displayCutout).only(portEdge.horizontalInsetsSide()),
        modifier = modifier
            .fillMaxHeight()
            .then(if (innerCornerRadius > 0.dp) Modifier.clip(railShape) else Modifier)
            // Dispatch 2026-10-13-02 (RECORD -966, -967): over the map, the icon bar's outline along the rail's inner edge and its
            // two rounded corners there. The inner edge is the one line between the rail and the landscape strip and navigation
            // card beside it, which leave that side open while the rail is shown (MapChromeOutlineSides). RECORD -990 (the owner:
            // "can landscape get the same navigation bar outline treatment?", -989's for the bottom bar): along its top and bottom
            // too, out to the screen's edge; its outer side, which meets the system's navigation buttons, carries none. Until -990
            // its top and bottom carried none either.
            .then(
                if (innerCornerRadius > 0.dp) {
                    if (portEdge == ScreenEdge.Left) {
                        Modifier.mapChromeEdgesReported(
                            mapChromeOutline(), 0.dp, innerCornerRadius, innerCornerRadius, 0.dp,
                            MapChromeOutlineSides(left = false, top = true, right = true, bottom = true),
                        )
                    } else {
                        Modifier.mapChromeEdgesReported(
                            mapChromeOutline(), innerCornerRadius, 0.dp, 0.dp, innerCornerRadius,
                            MapChromeOutlineSides(left = true, top = true, right = false, bottom = true),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .testTag(COMPACT_NAVIGATION_RAIL_TAG)
            .mapChromeContainerColor(containerColor)
            .mapChromeShape(railShape),
    ) {
        // Dispatch 2026-10-13-02, RECORD -976 (the owner: "Landscape: the side navigation buttons (list, seasonal, maps, journal,
        // tools) is crammed on the bar, unevenly stacked between the top and the bottom. Can the buttons be evenly spaced?"):
        // Material's NavigationRail stacks its items from the top, 4 dp apart (a Column with no arrangement parameter), which
        // left the free height all below Tools. The items now sit in one inner column over the rail's whole height, arranged by
        // RailItemsEvenly: the free height shared out equally before, between and after them. In the rail's own content, so it
        // holds wherever the rail is shown: over the map and beside the other tabs.
        // Rejected, from CI: equal weighted spacers between the items (this branch's first version, b85b0af3). Each spacer is a
        // child of Material's 4 dp-spaced Column, so six more 4 dp gaps; on a rail too short for its items they pushed the
        // items into each other (AvailabilityScreenShortLandscapeTest, run 38513414352: Tools' top inside Journal's bounds),
        // where main's stack only squeezed the last. Rejected too: weighting the items themselves, which would stretch each
        // item's touch area and indicator over its share of the height.
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = RailItemsEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RAIL_ORDER_TOP_TO_BOTTOM.forEach { tab ->
                NavigationRailItem(
                    selected = if (tab == CompactTab.TOOLS) isDrawerOpen else selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    icon = { Icon(tab.icon(), contentDescription = null) },
                    label = { Text(tab.label) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }
    }
}

/**
 * Dispatch 2026-10-18-08, RECORD -1312 (the owner, from the S26 promotion check: "the app's navigation tab had the icons reversed
 * when going from portrait to landscape. A button a user would expect to be at one side ends up on the other", then "List is on
 * the left/bottom always"): the rail's items, top to bottom. The bottom bar's left-to-right order, [CompactTab.entries], read from
 * the bottom up, so List is lowest and Tools highest, in both landscape rotations (the rail is on the screen's right in both,
 * `mainWindowRailEdgeFor`). The one place the rail's order is decided: every rail, over the map and beside the other tabs, on a
 * phone and a tablet, is a [ForagerNavigationRail], which reads only this. The bar's order is not changed.
 *
 * The list itself is reversed, not the arrangement, so the semantics tree runs in the same order as the screen, top to bottom, and
 * a screen reader reads the rail as it is drawn: Tools first, List last. Rejected: keeping [CompactTab.entries] and laying the items
 * out from the bottom, which would leave the tree List first while the screen shows List last, so the reading order would rest on
 * how the platform sorts by position rather than on this list. With no room at all [RailItemsEvenly] still stacks from the top, so
 * in that case it is now List, not Tools, that is squeezed at the bottom.
 */
internal val RAIL_ORDER_TOP_TO_BOTTOM: List<CompactTab> = CompactTab.entries.reversed()

/** The least gap between two rail items: Material's NavigationRail spaces its items 4 dp apart. Declared before
 * [RailItemsEvenly], whose initializer reads it: a top-level property is initialised in file order (CI run 38515463361 at
 * 7fcff2f7 failed to compile with it after). */
private val RAIL_ITEM_MIN_GAP = 4.dp

/**
 * Dispatch 2026-10-13-02, RECORD -976: the rail's items spread evenly over its height. With room, the free height is shared
 * equally before the first item, between each two and after the last. With less room than that at [RAIL_ITEM_MIN_GAP] apiece,
 * the items keep that gap between them, Material's own, and the stack is centred; with no room at all they stack from the
 * top that gap apart, as Material's rail did before.
 */
private val RailItemsEvenly: Arrangement.Vertical = object : Arrangement.Vertical {
    override val spacing: Dp = RAIL_ITEM_MIN_GAP

    override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
        if (sizes.isEmpty()) return
        val minGap = RAIL_ITEM_MIN_GAP.toPx()
        val slack = (totalSize - sizes.sum()).toFloat()
        val (endMargin, gap) = if (slack >= (sizes.size + 1) * minGap) {
            val even = slack / (sizes.size + 1)
            even to even
        } else {
            ((slack - (sizes.size - 1) * minGap) / 2).coerceAtLeast(0f) to minGap
        }
        var y = endMargin
        sizes.forEachIndexed { i, size ->
            outPositions[i] = y.roundToInt()
            y += size + gap
        }
    }
}

/**
 * The Scaffold content insets for every tab but Map in a short landscape window (landscape B1,
 * P4/P5 and Resolutions R12 and R17 as revised). These tabs have the opaque rail beside them
 * ([ForagerNavigationRail], which takes the `navigationBars` inset on the port side itself), so:
 * at the top, `statusBars`; on the side away from [railEdge], `displayCutout`, so nothing sits in the cut-out band there;
 * at the bottom nothing for a system bar, since in landscape the 3-button bar is on the port side. On the rail's own side
 * nothing since dispatch 2026-10-18-01: the rail takes that side's cut-out itself and runs to the screen's edge under it
 * ([ForagerNavigationRail]'s `windowInsets`); until then both sides' cut-outs were reserved here, outside the rail. The Map tab does not use this: its map is
 * full-bleed and only its controls are padded (compactMainScaffold's `mapControlsPadding`).
 *
 * No keyboard, since dispatch 2026-10-17-09, RECORD -1242 (the owner: "A: Ignore the keyboard, like Maps (Recommended)").
 * This was `.add(WindowInsets.ime.only(WindowInsetsSides.Bottom))` so the keyboard pushed content up, as `safeDrawing` does in
 * portrait; but it shrank the whole Row, the docked rail included ("Maps" clipped and the lower items hidden on the owner's
 * S22, build 1.0.3416). The keyboard is now reserved on the content column beside the rail (compactMainScaffold), so the
 * page still ends above it and the rail keeps its full height under it, as the Maps tab's overlaid rail does.
 */
@Composable
internal fun shortLandscapeContentInsets(railEdge: ScreenEdge): WindowInsets =
    WindowInsets.statusBars.only(WindowInsetsSides.Top)
        .add(WindowInsets.displayCutout.only(railEdge.opposite.horizontalInsetsSide()))

/** The bottom navigation bar (either instance), for tests (dispatch 2026-09-28-104, continuation -12, item 6). */
internal const val COMPACT_BOTTOM_NAV_TAG = "compact-bottom-nav"

/** Tags [ForagerNavigationRail]'s container, so a test can measure the rail's own bounds. */
internal const val COMPACT_NAVIGATION_RAIL_TAG = "compact-navigation-rail"

/** The absolute window side for a left or right [ScreenEdge]; the rail is only ever on one of those. */
internal fun ScreenEdge.horizontalInsetsSide(): WindowInsetsSides =
    if (this == ScreenEdge.Left) WindowInsetsSides.Left else WindowInsetsSides.Right

/** The exit-navigation prompt's buttons, for the back-navigation tests. */
internal const val EXIT_NAVIGATION_PROMPT_TAG = "exit-navigation-prompt"
internal const val EXIT_NAVIGATION_PROMPT_EXIT_TAG = "exit-navigation-prompt-exit"
internal const val EXIT_NAVIGATION_PROMPT_KEEP_TAG = "exit-navigation-prompt-keep"

/**
 * What system back raises while navigating, instead of exiting — see the back chain in
 * [AvailabilityScreen] for why back can only ever raise and dismiss this. Same [AlertDialog]
 * shape as [ThreeWayActionDialog] and the Cartography editor's leave prompt, not a new dialog
 * style. Wording is the owner's: exiting navigation is not stopping the recording, and the text
 * says so, because a user who thinks "Exit" ends the track will keep navigating when they meant
 * to stop, or the reverse. Dismissing by any route — the Keep button, a tap outside, the dialog
 * window's own back — keeps navigating; only the Exit button calls [onExit].
 */
@Composable
internal fun ExitNavigationPrompt(
    onExit: () -> Unit,
    onKeepNavigating: () -> Unit,
    /** Whether a map is drawn on screen beneath the prompt: its container is then at the map chrome's alpha. */
    overMap: Boolean = false,
) {
    val dialogColor = mapChromeFill(navigationBarContainerColor(), overMap)
    AlertDialog(
        onDismissRequest = onKeepNavigating,
        title = { Text("Exit navigation?") },
        text = { Text("Your track will keep recording.", modifier = Modifier.mapChromeContentColor(LocalContentColor.current)) },
        confirmButton = {
            TextButton(onClick = onExit, modifier = Modifier.testTag(EXIT_NAVIGATION_PROMPT_EXIT_TAG)) { Text("Exit") }
        },
        dismissButton = {
            TextButton(onClick = onKeepNavigating, modifier = Modifier.testTag(EXIT_NAVIGATION_PROMPT_KEEP_TAG)) { Text("Keep navigating") }
        },
        modifier = Modifier.testTag(EXIT_NAVIGATION_PROMPT_TAG).mapChromeContainerColor(dialogColor),
        containerColor = dialogColor,
    )
}
