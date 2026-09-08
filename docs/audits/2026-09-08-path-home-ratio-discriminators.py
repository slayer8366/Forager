# Companion to 2026-09-08-path-home-ratio-prebuild-report.md. Run with python3; no dependencies.
# Synthetic tracks only: the GPX exports are gitignored and were not available to the sandbox.
"""Path-home discriminators against synthetic tracks matched to the dispatch's measured statistics.

Independent of the Kotlin under investigation: its own haversine (IUGG mean radius, the same
constant GeoDistance uses so metres agree), its own path sum. Nothing here imports app code.
Tracks are SYNTHETIC reconstructions — the GPX exports are gitignored and not in this sandbox —
matched to: A = 135 pts, 733 m path, 6.4 m straight, 38x48 m box; B = 19 pts, 92 m, 3.0 m, 30x25 m.
"""
import math, random

R = 6_371_008.8
SPEED = 0.89  # m/s, the dispatch's moving average

def hav(a, b):
    la1, la2 = math.radians(a[0]), math.radians(b[0])
    dlat = math.radians(b[0]-a[0])/2; dlng = math.radians(b[1]-a[1])/2
    h = math.sin(dlat)**2 + math.cos(la1)*math.cos(la2)*math.sin(dlng)**2
    return 2*R*math.asin(min(1.0, math.sqrt(h)))

LAT0, LNG0 = 45.0, -122.0
M_PER_DEG_LAT = math.pi*R/180
M_PER_DEG_LNG = M_PER_DEG_LAT*math.cos(math.radians(LAT0))
def to_ll(x, y):  # metres east, north of origin -> lat/lng
    return (LAT0 + y/M_PER_DEG_LAT, LNG0 + x/M_PER_DEG_LNG)

def path_len(pts):
    return sum(hav(pts[i-1], pts[i]) for i in range(1, len(pts)))

def box_m(pts):
    xs = [(p[1]-LNG0)*M_PER_DEG_LNG for p in pts]; ys = [(p[0]-LAT0)*M_PER_DEG_LAT for p in pts]
    return max(xs)-min(xs), max(ys)-min(ys)

def max_radius(pts, origin):
    return max(hav(origin, p) for p in pts)

def mins(m):  # walking minutes at SPEED
    return m/SPEED/60

# ---- synthetic patch track: confined random walk with reflecting walls, legs ~ sampler floor
def patch(n, w, h, total, end_dist, seed):
    rnd = random.Random(seed)
    leg = total/(n-1)
    x, y = 0.0, 0.0; ang = rnd.uniform(0, 2*math.pi); pts = [(x, y)]
    for _ in range(n-1):
        ang += rnd.gauss(0, 1.1)
        nx, ny = x+leg*math.cos(ang), y+leg*math.sin(ang)
        if not (0 <= nx <= w): ang = math.pi-ang; nx = min(max(nx, 0), w)
        if not (0 <= ny <= h): ang = -ang; ny = min(max(ny, 0), h)
        x, y = nx, ny; pts.append((x, y))
    # pull the last point to end_dist from the start, keeping the leg count honest
    lx, ly = pts[-1]; d = math.hypot(lx, ly) or 1.0
    pts[-1] = (lx*end_dist/d, ly*end_dist/d)
    return [to_ll(*p) for p in pts]

def report_track(name, pts):
    o = pts[0]; L = path_len(pts); d = hav(pts[-1], o); bw, bh = box_m(pts)
    print(f"{name}: n={len(pts)} path={L:.1f} m straight={d:.1f} m ratio={L/d:.0f}x box={bw:.0f}x{bh:.0f} m maxR={max_radius(pts,o):.1f} m  retrace={mins(L):.1f} min straight={mins(d):.2f} min")
    return pts

print("== Synthetic fixtures (seeded; several seeds to show the statistics are not seed-specific)")
A = None
for s in (1, 2, 3):
    t = report_track(f"A seed{s}", patch(135, 38, 48, 733.0, 6.4, s))
    if s == 1: A = t
B = report_track("B seed1", patch(19, 30, 25, 92.0, 3.0, 1))

# ---- discriminators on a walk: given the running track, current fix, origin -> answers
def ratio(L, d): return L/d if d > 0 else float('inf')

class Hyst:  # enter above hi, leave below lo — the repo's band pattern
    def __init__(s, hi, lo): s.hi, s.lo, s.on = hi, lo, False
    def step(s, v):
        s.on = v > s.hi if not s.on else v >= s.lo
        return s.on

def straight_walk(pts, origin, bearing_deg, meters, leg=5.5):
    """append a straight walk from the last point"""
    out = list(pts); lx = (out[-1][1]-LNG0)*M_PER_DEG_LNG; ly = (out[-1][0]-LAT0)*M_PER_DEG_LAT
    b = math.radians(bearing_deg); n = int(meters/leg)
    for i in range(1, n+1): out.append(to_ll(lx+leg*i*math.sin(b), ly+leg*i*math.cos(b)))
    return out

def scan(name, pts, checkpoints_m, noise_m=0.0, seed=7):
    """walk the track point by point; at path-length checkpoints report every candidate."""
    rnd = random.Random(seed); o = pts[0]
    r3, r2 = Hyst(3.0, 2.5), Hyst(2.0, 1.7)
    contained = True; contained_flipped_at = None
    print(f"\n-- {name}")
    print(f"{'path m':>8} {'straight':>9} {'ratio':>7} {'maxR':>6} {'whole<50m':>9} {'ratio>3 switch':>15} {'ratio>2 switch':>15} {'shown retrace':>14} {'shown straight':>14}")
    nxt = 0
    for i in range(1, len(pts)):
        cur = pts[:i+1]; L = path_len(cur)
        c = cur[-1]
        if noise_m:
            c = to_ll((c[1]-LNG0)*M_PER_DEG_LNG + rnd.gauss(0, noise_m), (c[0]-LAT0)*M_PER_DEG_LAT + rnd.gauss(0, noise_m))
        d = hav(c, o); rt = ratio(L, d); mr = max_radius(cur, o)
        if contained and mr > 50: contained = False; contained_flipped_at = L
        on3 = r3.step(rt); on2 = r2.step(rt)
        if nxt < len(checkpoints_m) and L >= checkpoints_m[nxt]:
            nxt += 1
            print(f"{L:8.0f} {d:9.1f} {rt:7.2f} {mr:6.0f} {'yes' if contained else 'no':>9} {('STRAIGHT '+f'{mins(d):.1f}m') if on3 else ('RETRACE '+f'{mins(L):.1f}m'):>15} {('STRAIGHT '+f'{mins(d):.1f}m') if on2 else ('RETRACE '+f'{mins(L):.1f}m'):>15} {mins(L):13.1f}m {mins(d):13.1f}m")
    if contained_flipped_at: print(f"   whole-track containment (50 m of origin) flipped at path {contained_flipped_at:.0f} m and can never flip back")

# Scenario 1: patch A, then walk away in a straight line to 1 km
S1 = straight_walk(A, A[0], 30, 1000)
scan("S1: patch A then walk straight away 1 km (no noise)", S1, [733, 800, 850, 900, 1000, 1100, 1200, 1400, 1733])
scan("S1 with 5 m gaussian fix noise on the current position", S1, [733, 800, 850, 900, 1000, 1100, 1200, 1400, 1733], noise_m=5.0)

# Case D: 3.4 km track, 3.0 km straight (a wiggly linear track), then the return leg along it
def wiggly(meters, straight, leg=5.5, seed=3):
    rnd = random.Random(seed); amp = math.sqrt(max(0.0, (meters/straight)**2 - 1))  # lateral slope giving path/straight ratio
    pts = []; x = 0.0; n = int(straight/leg)
    for i in range(n+1):
        pts.append(to_ll(amp*leg*(i % 2), i*leg))
    return pts
D = wiggly(3400, 3000)
report_track("D outbound", D)
Dback = D + D[-2::-1]  # walk back over the same points
scan("D: 3.4 km out, then retrace to the car", Dback, [3400, 3600, 4000, 4800, 5200, 5800, 6400, 6700, 6790])

# Loop: 1 km radius circle, origin on the rim, walked anticlockwise; the straight line home crosses the interior
def loop(r, leg=5.5):
    n = int(2*math.pi*r/leg); return [to_ll(r*math.cos(2*math.pi*i/n) - r, r*math.sin(2*math.pi*i/n)) for i in range(n+1)]
Lp = loop(1000)
scan("Loop: 1 km radius, origin on the rim (path/straight at 3/4 round = 4.71/1.41)", Lp, [1000, 2000, 3000, 4000, 4700, 5000, 5500, 6000, 6200])

# A patch 100 m from the car: 100 m approach, then the A patch
approach = straight_walk([A[0]], A[0], 0, 100)
shift = approach[-1]
A100 = approach + [(p[0] + (shift[0]-A[0][0]), p[1] + (shift[1]-A[0][1])) for p in A[1:]]
report_track("A100: 100 m approach then patch A", A100)
o = A100[0]; L = path_len(A100); d = hav(A100[-1], o)
print(f"   ratio={L/d:.1f} maxR={max_radius(A100,o):.0f} m; whole-track containment in 50 m: {'yes' if max_radius(A100,o)<=50 else 'no'}; retrace {mins(L):.1f} min, straight {mins(d):.1f} min")

# Denominator noise on A: origin and current each ± 7.3 m -> the straight line is not measurable
print("\n== Straight-line noise on A: origin and current fix each perturbed by N(0, 7.3 m), 2000 draws")
rnd = random.Random(11); o = A[0]; c = A[-1]; L = path_len(A); ds = []
for _ in range(2000):
    oo = to_ll((o[1]-LNG0)*M_PER_DEG_LNG + rnd.gauss(0, 7.3), (o[0]-LAT0)*M_PER_DEG_LAT + rnd.gauss(0, 7.3))
    cc = to_ll((c[1]-LNG0)*M_PER_DEG_LNG + rnd.gauss(0, 7.3), (c[0]-LAT0)*M_PER_DEG_LAT + rnd.gauss(0, 7.3))
    ds.append(hav(oo, cc))
ds.sort()
print(f"   straight: p5={ds[100]:.1f} m median={ds[1000]:.1f} m p95={ds[1900]:.1f} m; ratio at p95 = {L/ds[1900]:.0f}x, at p5 = {L/ds[100]:.0f}x")

import heapq
print("\n\n==== Track-network candidate (option 4) on the same fixtures ====")
def network_home(pts, eps):
    """shortest path along the walked track from the last point to the first, where the track is
    joined to itself wherever two points are within eps. Returns (metres, number of closures used)."""
    n = len(pts); adj = [[] for _ in range(n)]
    for i in range(1, n):
        d = hav(pts[i-1], pts[i]); adj[i-1].append((i, d)); adj[i].append((i-1, d))
    # O(n^2) closure scan — fine at this size; a grid hash makes it O(n) in production
    for i in range(n):
        for j in range(i+2, n):
            d = hav(pts[i], pts[j])
            if d <= eps: adj[i].append((j, d)); adj[j].append((i, d))
    dist = [math.inf]*n; dist[n-1] = 0.0; pq = [(0.0, n-1)]
    while pq:
        d, u = heapq.heappop(pq)
        if d > dist[u]: continue
        for v, w in adj[u]:
            if d + w < dist[v]: dist[v] = d + w; heapq.heappush(pq, (dist[v], v))
    return dist[0]

A = patch(135, 38, 48, 733.0, 6.4, 1)
S1 = straight_walk(A, A[0], 30, 1000)
D = wiggly(3400, 3000); Dback = D + D[-2::-1]
Lp = loop(1000)
approach = straight_walk([A[0]], A[0], 0, 100); shift = approach[-1]
A100 = approach + [(p[0] + (shift[0]-A[0][0]), p[1] + (shift[1]-A[0][1])) for p in A[1:]]

def scan_network(name, pts, checkpoints, eps=12.0):
    print(f"\n-- {name} (eps = {eps} m)")
    print(f"{'path m':>8} {'straight':>9} {'retrace':>9} {'network':>9}  network/straight")
    nxt = 0
    for i in range(1, len(pts)):
        cur = pts[:i+1]; L = path_len(cur)
        if nxt < len(checkpoints) and L >= checkpoints[nxt]:
            nxt += 1; d = hav(cur[-1], cur[0]); nw = network_home(cur, eps)
            print(f"{L:8.0f} {d:9.1f} {L:9.0f} {nw:9.1f}  {nw/d if d>0 else float('inf'):6.2f}")

for eps in (8.0, 12.0, 20.0):
    print(f"\n== eps {eps} m: A network home = {network_home(A, eps):.1f} m (retrace {path_len(A):.0f}, straight {hav(A[-1],A[0]):.1f});  A100 = {network_home(A100, eps):.1f} m (retrace {path_len(A100):.0f}, straight {hav(A100[-1],A100[0]):.1f})")
scan_network("S1: patch A then walk straight away", S1, [733, 800, 900, 1200, 1733])
scan_network("D: out 3.4 km then back over the same ground", Dback, [3403, 4000, 4800, 5800, 6400, 6700, 6794])
scan_network("Loop 1 km radius (no self-closure until the rim is regained)", Lp, [1000, 3000, 4700, 6000, 6201])

# the switchback hazard: two legs 10 m apart across a slope the walker zig-zagged; eps 12 joins them
zig = [to_ll(0, i*5.5) for i in range(0, 40)]                      # 220 m north
zig += [to_ll(10, 220 - i*5.5) for i in range(1, 40)]               # back south, 10 m east (a switchback)
zig += [to_ll(10 + i*5.5, 0) for i in range(1, 40)]                 # then 220 m east
print(f"\n== switchback: retrace {path_len(zig):.0f} m, straight {hav(zig[-1],zig[0]):.0f} m, network eps 8 = {network_home(zig, 8):.0f} m, eps 12 = {network_home(zig, 12):.0f} m")
print("   at eps 12 the two 10 m-apart legs join: the route home cuts 10 m of off-track ground once; nearest-point projection would cut the same and more, anywhere")
