package com.zynergylabs.forager.app.map

/**
 * Sprite v5's images other than the tree glyph, as the v2 style records them in `metadata["forager:sprite-icons"]`
 * (`StreetStyleTest` checks the two agree): v4's 53 plus Trail Map's six icons and the wetland tufts (dispatch
 * 2026-10-22-03). Until dispatch 2026-10-22-06 Amendment 1 the app inverted these one by one on the phone; since then
 * the night copies ship in the app (`app/src/main/assets/night-sprite/v5`), so this list lives with the tests, where
 * `NightSpriteTest` holds the bundled night set to cover it.
 */
internal val STREET_SPRITE_ICON_NAMES: List<String> = listOf(
    "NL:S-road-1char", "NL:S-road-2char", "NL:S-road-3char", "NL:S-road-4char", "NL:S-road-5char",
    "US:I-1char", "US:I-2char", "US:I-3char", "US:I-4char", "US:I-5char",
    "aerodrome", "animal", "arrow", "artwork", "attraction", "bar", "beach", "beauty", "bench", "books", "building",
    "bus_stop", "cafe", "camp_site", "capital", "clothes", "convenience", "drinking_water", "electronics", "fast_food",
    "ferry_terminal", "forest", "garden",
    "generic_shield-1char", "generic_shield-2char", "generic_shield-3char", "generic_shield-4char", "generic_shield-5char",
    "library", "marina", "museum", "park", "parking", "peak", "picnic_site", "post_office", "ranger_station",
    "restaurant", "school", "stadium", "supermarket", "theatre", "toilets", "townspot", "trailhead_b", "train_station",
    "tufts", "university", "viewpoint_c", "zoo",
)
