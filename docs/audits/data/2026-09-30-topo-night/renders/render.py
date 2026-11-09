#!/usr/bin/env python3
"""Still renders for the topo-night comparison (planner request after the owner rejected the built paint).

Everything uses today's V1 exactly (brightness-min 1, max 0, hue-rotate 180), through measure.shade, the Python
mirror of RasterShaderModel.kt (checked against nightColorOf's formula, see README). A 'view' is a 256 x 256 dp
square centred on 45.36 N, 122.6 W drawn at 3 px per dp (about the S22's density), tiles bilinear-resampled the way
MapLibre's raster layer samples them, then the paint applied per pixel.

Hillshade follows MapLibre Native 13.5.0 (include/mbgl/shaders/gl/hillshade_prepare.hpp and hillshade.hpp, method
'standard', the default), see README for where it differs.
"""
import math, os, sys
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__)); DATA = os.path.join(HERE, "..")
sys.path.insert(0, HERE); sys.path.insert(0, DATA)
import measure as m, tilemath

RS = 3; V = 256; OUT = V * RS
TOPO_DIRS = [os.path.join(DATA, "tiles"), os.path.join(HERE, "tiles-extra")]
OSM_DIRS = [os.path.join(DATA, "osm-tiles"), os.path.join(HERE, "osm-extra")]
DEM_DIRS = [os.path.join(HERE, "dem-tiles")]

def find(dirs, n, x, y):
    for d in dirs:
        p = os.path.join(d, f"{n}_{x}_{y}.png")
        if os.path.exists(p): return p
    raise FileNotFoundError(f"tile {n}/{x}/{y} not in {dirs}")

def view_geometry(n, d, v=V):
    tx, ty = tilemath.tile_xy(n); h = v / 2 / d
    return tx, ty, h, list(range(int(math.floor(tx - h)), int(math.floor(tx + h)) + 1)), list(range(int(math.floor(ty - h)), int(math.floor(ty + h)) + 1))

def mosaic(dirs, n, xs, ys, mode="RGB"):
    im = Image.new(mode, (256 * len(xs), 256 * len(ys)))
    for i, x in enumerate(xs):
        for j, y in enumerate(ys):
            im.paste(Image.open(find(dirs, n, x, y)).convert(mode), (256 * i, 256 * j))
    return im

def resample(mos, tx, ty, h, xs, ys, out=OUT):
    box = ((tx - h - xs[0]) * 256, (ty - h - ys[0]) * 256, (tx + h - xs[0]) * 256, (ty + h - ys[0]) * 256)
    return mos.resize((out, out), Image.BILINEAR, box=box)

def raster_view(dirs, mz, size, v=V):
    """v = viewport in dp (square). Smaller than V only where fetching a full window would be dozens of tiles."""
    n = tilemath.level(mz, size); d = tilemath.shown_dp(mz, size)
    tx, ty, h, xs, ys = view_geometry(n, d, v)
    return resample(mosaic(dirs, n, xs, ys), tx, ty, h, xs, ys, int(v * RS)), n, d

def apply_paint(im, paint):
    cache = {}; out = []
    for c in im.getdata():
        if c not in cache:
            o = m.shade((c[0] / 255, c[1] / 255, c[2] / 255), **paint); cache[c] = tuple(round(v * 255) for v in o)
        out.append(cache[c])
    r = Image.new("RGB", im.size); r.putdata(out); return r

# ---- hillshade ---------------------------------------------------------------------------------------------
def dem_elevation(n, xs, ys):
    w, hgt = 256 * len(xs), 256 * len(ys); el = [[0.0] * w for _ in range(hgt)]
    for i, x in enumerate(xs):
        for j, y in enumerate(ys):
            px = Image.open(find(DEM_DIRS, n, x, y)).convert("RGB").load()
            for yy in range(256):
                row = el[256 * j + yy]
                for xx in range(256):
                    r, g, b = px[xx, yy]; row[256 * i + xx] = r * 256 + g + b / 256 - 32768
    return el

def derivative_image(el, n):
    """hillshade_prepare.fragment: Sobel, scaled by tileSize / 2^(exaggeration + 28.2562 - zoom), stored as deriv/8 + 0.5."""
    hgt, w = len(el), len(el[0])
    factor = 0.4 if n < 2 else 0.35 if n < 4.5 else 0.3
    exaggeration = (n - 15) * factor if n < 15 else 0.0
    k = 256 / 2 ** (exaggeration + (28.2562 - n))
    img = Image.new("RGB", (w, hgt)); px = img.load()
    def e(x, y): return el[min(hgt - 1, max(0, y))][min(w - 1, max(0, x))]
    for y in range(hgt):
        for x in range(w):
            a = e(x-1, y-1); b = e(x, y-1); c = e(x+1, y-1); d = e(x-1, y); f = e(x+1, y); g = e(x-1, y+1); hh = e(x, y+1); i = e(x+1, y+1)
            dx = ((c + f + f + i) - (a + d + d + g)) * k; dy = ((g + hh + hh + i) - (a + b + b + c)) * k
            px[x, y] = (round(min(1, max(0, dx / 8 + .5)) * 255), round(min(1, max(0, dy / 8 + .5)) * 255), 255)
    return img

def mix(a, b, t): return tuple(a[i] * (1 - t) + b[i] * t for i in range(4))
def premult(hexrgb, alpha): r, g, b = (int(hexrgb[i:i+2], 16) / 255 for i in (1, 3, 5)); return (r * alpha, g * alpha, b * alpha, alpha)

def standard_hillshade(dx, dy, azimuth_deg, exaggeration, shadow, highlight, accent):
    """hillshade.fragment standard_hillshade(); colours premultiplied RGBA."""
    azimuth = math.radians(azimuth_deg) + math.pi
    slope = math.atan(0.625 * math.hypot(dx, dy))
    aspect = math.atan2(dy, -dx) if dx != 0 else math.pi / 2 * (1 if dy > 0 else -1)
    E = exaggeration; base = 1.875 - E * 1.75; maxv = .5 * math.pi
    scaled = ((base ** slope - 1) / (base ** maxv - 1)) * maxv if abs(E - .5) > 1e-6 else slope
    acc = (1 - math.cos(scaled)) * min(1, max(0, E * 2))
    shade = abs(((aspect + azimuth) / math.pi + .5) % 2.0 - 1)
    sc = math.sin(scaled) * min(1, max(0, E * 2))
    shade_color = tuple(v * sc for v in mix(shadow, highlight, shade))
    return tuple(accent[i] * acc * (1 - shade_color[3]) + shade_color[i] for i in range(4))

def hillshade_over(base, mz, exaggeration, shadow, highlight, accent, azimuth_deg=335.0):
    n = int(math.floor(mz + 1)); d = 512.0 * 2 ** (mz - n)        # raster-dem: coveringZoomLevel floors (not Raster/Video)
    tx, ty, h, xs, ys = view_geometry(n, d)
    deriv = resample(derivative_image(dem_elevation(n, xs, ys), n), tx, ty, h, xs, ys).load()
    out = base.copy(); op = out.load(); ebase = base.load()
    for yy in range(OUT):
        Ty = ty - h + (yy + .5) / OUT * 2 * h
        lat = math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * Ty / 2 ** n)))); scale = math.cos(math.radians(lat))
        for xx in range(OUT):
            r, g, _ = deriv[xx, yy]
            dx = ((r / 255) * 8 - 4) / scale; dy = ((g / 255) * 8 - 4) / scale
            f = standard_hillshade(dx, dy, azimuth_deg, exaggeration, shadow, highlight, accent)
            dst = ebase[xx, yy]
            op[xx, yy] = tuple(round(min(1.0, f[i] + dst[i] / 255 * (1 - f[3])) * 255) for i in range(3))
    return out, n, d

# ---- sheets ----------------------------------------------------------------------------------------------------
def caption(im, text):
    bar = Image.new("RGB", (im.width, 34), (24, 24, 24)); ImageDraw.Draw(bar).text((8, 10), text, fill=(235, 235, 235))
    out = Image.new("RGB", (im.width, im.height + 34)); out.paste(bar, (0, 0)); out.paste(im, (0, 34)); return out

def sheet(panels, path, scale=1.0):
    cs = [caption(im, t) for t, im in panels]; w = sum(c.width for c in cs) + 8 * (len(cs) - 1)
    s = Image.new("RGB", (w, cs[0].height), (60, 60, 60)); x = 0
    for c in cs: s.paste(c, (x, 0)); x += c.width + 8
    if scale != 1.0: s = s.resize((int(s.width * scale), int(s.height * scale)), Image.LANCZOS)
    s.save(path.replace('.png', '.jpg'), quality=90)

def lightness(im):
    px = list(im.getdata()); return sum(sum(p) for p in px) / (3 * 255 * len(px))
