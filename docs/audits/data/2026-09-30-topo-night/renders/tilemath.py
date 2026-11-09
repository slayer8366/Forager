import math, os
LAT, LON = 45.36, -122.6
V = 256.0   # viewport, dp (square, centred on the point)
def tile_xy(z):
    n = 2 ** z
    x = (LON + 180.0) / 360.0 * n
    y = (1.0 - math.asinh(math.tan(math.radians(LAT))) / math.pi) / 2.0 * n
    return x, y
def level(mz, size):   # MapLibre 13.5.0 coveringZoomLevel for raster: round(zoom + log2(512/size)), half up
    return int(math.floor(mz + math.log2(512 / size) + 0.5))
def shown_dp(mz, size):  # dp one tile of level(mz,size) covers on screen
    return 512.0 * 2 ** (mz - level(mz, size))
def needed(mz, size, v=V):
    n = level(mz, size); d = shown_dp(mz, size); tx, ty = tile_xy(n); h = v / 2 / d
    return n, d, [(int(math.floor(tx - h)) + i, int(math.floor(ty - h)) + j)
                  for i in range(int(math.floor(tx + h)) - int(math.floor(tx - h)) + 1)
                  for j in range(int(math.floor(ty + h)) - int(math.floor(ty - h)) + 1)]
if __name__ == "__main__":
    here = os.path.dirname(os.path.abspath(__file__)); tdir = os.path.join(here, "..", "tiles")
    extra = set()
    for mz in (7, 8.5, 9.4, 11):
        for size in (256, 128, 64):
            n, d, tl = needed(mz, size)
            missing = [t for t in tl if not os.path.exists(os.path.join(tdir, f"{n}_{t[0]}_{t[1]}.png"))]
            print(f"mz {mz:>4} src {size:>3}: level {n}, tile {d:6.1f} dp, need {len(tl)} tiles, missing {len(missing)}")
            extra.update((n,) + t for t in missing)
    print(len(extra), sorted(extra))
