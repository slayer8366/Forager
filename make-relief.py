#!/usr/bin/env python3
"""Hill shading trial (owner's go via Rowan, RECORD -1926): Terrarium raster-dem tiles from the 3" grid.
Area: Mount Hood and the Columbia Gorge, 45-46 N, 123-121 W (cells N45W123, N45W122). No download.
Writes tiles/png256/{z}/{x}/{y}.png (256 px) and tiles/webp512/{z}/{x}/{y}.webp (512 px, lossless), z5-z11,
plus an MBTiles of each for `pmtiles convert`. Terrarium: h = R*256 + G + B/256 - 32768 (metres)."""
import math, os, sqlite3, sys, time
import numpy as np
from osgeo import gdal
from PIL import Image
gdal.UseExceptions()
T = os.path.dirname(os.path.abspath(__file__))
CELLS = ["/mnt/storage/elevation/hgt3/N45W123.hgt", "/mnt/storage/elevation/hgt3/N45W122.hgt"]
W, S, E, N = -123.0, 45.0, -121.0, 46.0
vrt = gdal.BuildVRT(f"{T}/area.vrt", CELLS)
R = 6378137.0
def merc(lon, lat): return R*math.radians(lon), R*math.log(math.tan(math.pi/4+math.radians(lat)/2))
def tile_bounds(z, x, y):
    n = 2**z; size = 2*math.pi*R/n; ox = -math.pi*R
    return ox+x*size, math.pi*R-(y+1)*size, ox+(x+1)*size, math.pi*R-y*size
def tiles(z):
    n = 2**z
    x0 = int((W+180)/360*n); x1 = int((E+180)/360*n - 1e-9)
    def ty(lat): return int((1-math.log(math.tan(math.radians(lat))+1/math.cos(math.radians(lat)))/math.pi)/2*n)
    for x in range(x0, x1+1):
        for y in range(ty(N), ty(S)+1): yield x, y
def terrarium(h):
    h = np.where(np.isfinite(h), h, 0.0)
    v = h + 32768.0
    r = np.floor(v/256); g = np.floor(v) % 256; b = np.floor((v - np.floor(v))*256)
    return np.dstack([r, g, b]).astype(np.uint8)
def mb(path, fmt):
    if os.path.exists(path): os.remove(path)
    db = sqlite3.connect(path)
    db.executescript("create table metadata(name text, value text); create table tiles(zoom_level integer, tile_column integer, tile_row integer, tile_data blob);")
    for k, v in {"name": "forager-hillshade-trial", "format": fmt, "type": "baselayer", "minzoom": "5", "maxzoom": "11",
                 "bounds": f"{W},{S},{E},{N}", "center": "-121.70,45.37,10",
                 "attribution": "Heights: USGS 3DEP; NRCan MRDEM", "encoding": "terrarium"}.items():
        db.execute("insert into metadata values(?,?)", (k, v))
    return db
t0 = time.time(); stats = {}
for kind, px, ext, fmt in (("png256", 256, "png", "png"), ("webp512", 512, "webp", "webp")):
    db = mb(f"{T}/{kind}.mbtiles", fmt); n = 0; byt = 0; t1 = time.time()
    for z in range(5, 12):
        for x, y in tiles(z):
            b = tile_bounds(z, x, y)
            ds = gdal.Warp("", vrt, format="MEM", dstSRS="EPSG:3857", outputBounds=b, width=px, height=px,
                           resampleAlg="bilinear", srcNodata=-32768, dstNodata=np.nan, outputType=gdal.GDT_Float32)
            img = Image.fromarray(terrarium(ds.GetRasterBand(1).ReadAsArray()), "RGB")
            d = f"{T}/tiles/{kind}/{z}/{x}"; os.makedirs(d, exist_ok=True); p = f"{d}/{y}.{ext}"
            if ext == "png": img.save(p, optimize=True)
            else: img.save(p, lossless=True, quality=100, method=6)
            data = open(p, "rb").read(); n += 1; byt += len(data)
            db.execute("insert into tiles values(?,?,?,?)", (z, x, (2**z-1)-y, data))
    db.commit(); db.close()
    stats[kind] = (n, byt, round(time.time()-t1, 1))
print({"stats(tiles, bytes, seconds)": stats, "total_s": round(time.time()-t0, 1)})
