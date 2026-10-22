#!/usr/bin/env python3
"""Route-data size per offline area, under each option. Juniper Sounding, 2026-10-21.

Every input is read from a named source; the arithmetic is the only thing this script adds.
Run: python3 -I route_data_sizes.py

Inputs (read):
- App radius limits: MIN_RADIUS_KM = 1 (domain/model/Region.kt:10), offline MAX_RADIUS_KM = 24
  (domain/OfflineMapRepository.kt:203), defaults 8 km (miles) and 10 km (km)
  (domain/model/DistanceUnit.kt:53-56). The download box is a square of side 2r from
  GeoDistance.boundingBox (GeoDistance.kt:69-83), equirectangular.
- rd5 grain: a 5x5 degree file holds 25 one-degree tiles (PhysicalFile.java:19, :99-107;
  WayLinker.java:436-442), each split divisor x divisor with divisor = 32 in the current format
  (WayLinker.java:63; PhysicalFile.java:133-134), so one cell is 1/32 degree on each axis.
- Whole-square size: W125_N45.rd5 = 54.9 MB on the Pi's usca run
  (docs/audits/2026-10-19-usca-routing-bands-report.md:133, pi-build 961a90d3). The other three
  Pacific Northwest squares' sizes are not recorded on pi-build. The public segments4 copies of
  all four (with elevation) totalled 154,567,417 bytes
  (docs/navigation/2026-10-03-engine-spike-report.md:35).
- Measured cut: a 60 x 60 km square in Mount Hood forest built from a clipped extract came to
  0.64 MB in two rd5 files (engine-spike report :185).
"""
import math

EARTH_R_M = 6_371_000.0  # the app's own mean radius is not re-read here; only degree spans matter
CELL_DEG = 1.0 / 32.0
SQUARE_BYTES_W125_N45 = 54.9e6
CELLS_PER_SQUARE = 25 * 32 * 32  # 25,600

def km_per_deg_lat():
    return 2 * math.pi * EARTH_R_M / 360 / 1000

def km_per_deg_lon(lat):
    return km_per_deg_lat() * math.cos(math.radians(lat))

def cells_for_box(side_km, lat, margin_cells=0):
    """Cells a side_km square touches, worst alignment: floor(side/cell)+2 on each axis, plus margin."""
    rows = math.floor(side_km / (CELL_DEG * km_per_deg_lat())) + 2 + 2 * margin_cells
    cols = math.floor(side_km / (CELL_DEG * km_per_deg_lon(lat))) + 2 + 2 * margin_cells
    return rows, cols, rows * cols

def main():
    lat = 45.5  # Pacific Northwest; the spike's square straddled 45 N
    avg_cell = SQUARE_BYTES_W125_N45 / CELLS_PER_SQUARE
    print(f"cell size at {lat} N: {CELL_DEG*km_per_deg_lat():.2f} km N-S x {CELL_DEG*km_per_deg_lon(lat):.2f} km E-W")
    print(f"average bytes per cell in W125_N45 (54.9 MB / 25,600 cells): {avg_cell:,.0f} B")
    # Calibrate against the one measured cut: 60 km square, 0.64 MB.
    r, c, n = cells_for_box(60, 45.0)
    forest_cell = 0.64e6 / n
    print(f"spike's 60 km square: {r}x{c}={n} cells -> {forest_cell:,.0f} B/cell measured in forest (0.64 MB / {n})")
    print()
    print("| radius km | box km | cells (rows x cols) | cut, square-average | cut, forest rate | whole square(s) |")
    print("|---|---|---|---|---|---|")
    for radius in (1, 8, 10, 24):
        side = 2 * radius
        r, c, n = cells_for_box(side, lat, margin_cells=1)  # one-cell margin all round
        print(f"| {radius} | {side} x {side} | {n} ({r} x {c}, with a one-cell margin) | "
              f"{n*avg_cell/1e6:.2f} MB | {n*forest_cell/1e6:.2f} MB | 54.9 MB (one), 109.8 MB (two, within 24 km of 45 N or 120 W) |")
    print()
    print("Fraction of a square a 24 km area is:", f"{cells_for_box(48, lat, 1)[2]/CELLS_PER_SQUARE:.1%}")

if __name__ == "__main__":
    main()
