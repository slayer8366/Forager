#!/usr/bin/env python3
"""Measures the basemap luminance ground a night-mode tile-inversion palette would be authored
against, from real tiles rather than a provisional constant.

Why this exists: MapPaletteTest's DAY_TILE_REFERENCE/NIGHT_TILE_REFERENCE are declared
provisional in docs/plans/understory-design-system.md's R5 ("The test bounds the regression; it
does not establish the property") -- nobody has measured what the app's own night-eligible
basemaps (Street, Topographical -- see MapMode.kt) actually look like, as-is or under the
luminance-only-hue-preserving inversion the deferred night-mode-colour-inversion work
(docs/plans/map-redesign.md, "Deferred: night-mode colour inversion") would apply.

What it measures, per basemap x zoom x location:
  - The as-rendered luminance distribution (min/max/median/spread), using the exact WCAG
    relative-luminance formula MapPaletteTest.kt's own relativeLuminance() uses, so results are
    directly comparable to the existing contrast floors.
  - The same distribution after a luminance-only, hue-preserving inversion: HLS lightness
    inverted (L -> 1-L), hue and saturation untouched. Python's stdlib colorsys is used rather
    than a hand-rolled HSL implementation.
  - For contrast: the same tiles under a naive RGB channel invert (255-R, 255-G, 255-B) -- the
    wrong transform, included only to make concrete, with real pixel numbers from real tiles,
    why "water turns orange" is the specific failure the hue-preserving approach avoids. Compares
    the hue of each tile's dominant saturated colours (its water blues, vegetation greens, etc.)
    across all three: as-is, HLS-inverted, and naive-inverted.

Network dependency: fetches real PNG tiles from tile.openstreetmap.org and
a.tile.opentopomap.org (the exact URLs Basemap.kt's own tileUrlTemplate values use). Requires
Pillow (`pip install pillow`). Not a CI check -- a one-off measurement tool, committed because
it's reusable: the walking-zoom band, the location set, or the transform can all be revisited
without re-deriving the tile-fetch/luminance plumbing from scratch.

Usage: python3 scripts/measure-night-inversion.py
"""

from __future__ import annotations

import colorsys
import math
import statistics
import sys
import urllib.request
from collections import Counter

try:
    from PIL import Image
except ImportError:
    sys.exit("Requires Pillow: pip install pillow")

# Night-eligible basemaps only -- see MapMode.kt's own doc comment ("Street and Topographical no
# longer offer a choice of tile provider... Satellite is new, always USGS"). Satellite is
# deliberately excluded here: this measurement is for the deferred colour-inversion work, which
# per Basemap.kt/BasemapStyles.kt only ever touches the raster-paint block applied to whichever
# basemap is active -- see the report's own finding that satellite is NOT currently code-gated
# out of that paint block, just design-intended to be.
BASEMAPS = {
    "OSM_STANDARD (Street)": "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
    "OPEN_TOPO_MAP (Topographical)": "https://a.tile.opentopomap.org/{z}/{x}/{y}.png",
}

# Real-world walking/foraging locations -- forest and trail terrain, not open water or bare
# desert, since this app's core use case is foot navigation in wooded terrain. One urban-edge
# trailhead included for road/building tile content, since Street's own worst case may not be a
# forest tile at all.
LOCATIONS = {
    "Mount Hood NF, OR": (45.3311, -121.7113),
    "Green Mountain NF, VT": (43.8083, -72.9146),
    "Marin Headlands, CA (urban-edge trailhead)": (37.8324, -122.4934),
}

# The walking zoom band: OPEN_TOPO_MAP's own hard ceiling (Basemap.kt's maxZoom = 17) bounds the
# shared range both basemaps can be measured at. z14 is where real trail/road-level detail starts
# appearing on either source; below that is regional-overview scale, not walking scale.
WALKING_ZOOMS = [14, 15, 16, 17]

# OSM_STANDARD alone reaches z18/z19 (Basemap.kt maxZoom = 19) -- sampled separately since
# OPEN_TOPO_MAP has no tiles there at all.
STREET_ONLY_ZOOMS = [18, 19]


def latlng_to_tile(lat: float, lng: float, zoom: int) -> tuple[int, int]:
    lat_rad = math.radians(lat)
    n = 2.0**zoom
    x = int((lng + 180.0) / 360.0 * n)
    y = int((1.0 - math.asinh(math.tan(lat_rad)) / math.pi) / 2.0 * n)
    return x, y


def fetch_tile(url_template: str, z: int, x: int, y: int) -> Image.Image:
    url = url_template.format(z=z, x=x, y=y)
    req = urllib.request.Request(url, headers={"User-Agent": "Forager-night-inversion-measurement/1.0"})
    with urllib.request.urlopen(req, timeout=20) as resp:
        data = resp.read()
    return Image.open(__import__("io").BytesIO(data)).convert("RGB")


def relative_luminance(r: int, g: int, b: int) -> float:
    """Exact formula MapPaletteTest.kt's relativeLuminance() uses -- kept identical so results are
    directly comparable to the app's own contrast floors, not a different luminance definition."""

    def channel(c: float) -> float:
        c = c / 255.0
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4

    return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)


def hls_lightness_invert(r: int, g: int, b: int) -> tuple[int, int, int]:
    """Hue-preserving inversion: HLS lightness flips (L -> 1-L), hue and saturation untouched."""
    h, l, s = colorsys.rgb_to_hls(r / 255.0, g / 255.0, b / 255.0)
    r2, g2, b2 = colorsys.hls_to_rgb(h, 1.0 - l, s)
    return round(r2 * 255), round(g2 * 255), round(b2 * 255)


def naive_rgb_invert(r: int, g: int, b: int) -> tuple[int, int, int]:
    """The wrong transform -- rotates hue for anything not on the grey axis. Measured only for
    the hue-rotation comparison table, never proposed as the actual implementation."""
    return 255 - r, 255 - g, 255 - b


def hue_degrees(r: int, g: int, b: int) -> tuple[float, float]:
    h, l, s = colorsys.rgb_to_hls(r / 255.0, g / 255.0, b / 255.0)
    return h * 360.0, s


def spread(values: list[float]) -> float:
    return max(values) - min(values)


def analyze_tile(img: Image.Image) -> dict:
    pixels = list(img.getdata())  # Pillow will remove this in 14 (2027-10-15); fine until then.
    # Subsample for speed -- every 4th pixel in each dimension, 4096 samples per 256x256 tile,
    # more than enough to characterize a distribution this coarse.
    sampled = [pixels[i] for i in range(0, len(pixels), 16)]

    as_is_lum = [relative_luminance(*p) for p in sampled]
    inverted = [hls_lightness_invert(*p) for p in sampled]
    inverted_lum = [relative_luminance(*p) for p in inverted]

    # Dominant saturated colours (S > 0.15 in HLS), for the hue-preservation comparison -- these
    # are the tile's water/vegetation/road/contour pixels, found by actual frequency rather than
    # guessed swatches.
    saturated_counter = Counter()
    for p in sampled:
        h, s = hue_degrees(*p)
        if s > 0.15:
            saturated_counter[p] += 1
    top_saturated = saturated_counter.most_common(5)

    return {
        "as_is": {
            "min": min(as_is_lum),
            "max": max(as_is_lum),
            "median": statistics.median(as_is_lum),
            "spread": spread(as_is_lum),
        },
        "inverted": {
            "min": min(inverted_lum),
            "max": max(inverted_lum),
            "median": statistics.median(inverted_lum),
            "spread": spread(inverted_lum),
        },
        "top_saturated": top_saturated,
        "as_is_pixels": as_is_lum,
        "inverted_pixels": inverted_lum,
    }


def percentile(values: list[float], p: float) -> float:
    s = sorted(values)
    k = (len(s) - 1) * p
    f, c = math.floor(k), math.ceil(k)
    if f == c:
        return s[int(k)]
    return s[f] + (s[c] - s[f]) * (k - f)


def main() -> None:
    all_as_is_lum: list[float] = []
    all_inverted_lum: list[float] = []
    pooled_as_is_pixels: list[float] = []
    pooled_inverted_pixels: list[float] = []
    rows = []

    print("| Basemap | Location | Zoom | As-is min/max/median/spread | Inverted min/max/median/spread |")
    print("|---|---|---|---|---|")

    for basemap_name, url_template in BASEMAPS.items():
        for location_name, (lat, lng) in LOCATIONS.items():
            for zoom in WALKING_ZOOMS:
                x, y = latlng_to_tile(lat, lng, zoom)
                try:
                    img = fetch_tile(url_template, zoom, x, y)
                except Exception as e:  # noqa: BLE001 -- measurement tool, report and continue
                    print(f"| {basemap_name} | {location_name} | z{zoom} | FETCH FAILED: {e} | |")
                    continue
                result = analyze_tile(img)
                a, inv = result["as_is"], result["inverted"]
                all_as_is_lum.append(a)
                all_inverted_lum.append(inv)
                pooled_as_is_pixels.extend(result["as_is_pixels"])
                pooled_inverted_pixels.extend(result["inverted_pixels"])
                rows.append((basemap_name, location_name, zoom, result))
                print(
                    f"| {basemap_name} | {location_name} | z{zoom} | "
                    f"{a['min']:.4f} / {a['max']:.4f} / {a['median']:.4f} / {a['spread']:.4f} | "
                    f"{inv['min']:.4f} / {inv['max']:.4f} / {inv['median']:.4f} / {inv['spread']:.4f} |"
                )

    print()
    print("## Worst case across all night-eligible sources/zooms sampled")
    print()
    worst_as_is_min = min(r["min"] for r in all_as_is_lum)
    worst_as_is_max = max(r["max"] for r in all_as_is_lum)
    worst_inv_min = min(r["min"] for r in all_inverted_lum)
    worst_inv_max = max(r["max"] for r in all_inverted_lum)
    print(f"As-is: luminance ranges {worst_as_is_min:.4f} to {worst_as_is_max:.4f} across all samples.")
    print(f"Inverted: luminance ranges {worst_inv_min:.4f} to {worst_inv_max:.4f} across all samples.")
    print()
    print(
        "Raw min/max above are dominated by outlier pixels (thin black text/borders, small white "
        "gaps) that are not representative of \"the ground\" a marker sits against -- pooled "
        "percentiles across every sampled pixel (not per-tile) are the more useful reference:"
    )
    print()
    print("| Stat | As-is | Inverted |")
    print("|---|---|---|")
    for label, p in [("P5", 0.05), ("P10", 0.10), ("P50 (median)", 0.50), ("P90", 0.90), ("P95", 0.95)]:
        print(f"| {label} | {percentile(pooled_as_is_pixels, p):.4f} | {percentile(pooled_inverted_pixels, p):.4f} |")

    print()
    print("## Hue preservation check: dominant saturated colours per tile")
    print()
    print("| Basemap | Location | Zoom | RGB (as-is) | Hue as-is | Hue HLS-inverted | Hue naive-inverted |")
    print("|---|---|---|---|---|---|---|")
    for basemap_name, location_name, zoom, result in rows:
        for rgb, _count in result["top_saturated"][:2]:
            h_as_is, _ = hue_degrees(*rgb)
            h_hls, _ = hue_degrees(*hls_lightness_invert(*rgb))
            h_naive, _ = hue_degrees(*naive_rgb_invert(*rgb))
            print(
                f"| {basemap_name} | {location_name} | z{zoom} | {rgb} | "
                f"{h_as_is:.0f}° | {h_hls:.0f}° | {h_naive:.0f}° |"
            )


if __name__ == "__main__":
    main()
