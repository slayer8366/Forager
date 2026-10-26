#!/usr/bin/env python3
"""A replica of the way home's figure as the waypoint route reuses it, and four options for the waypoint figure.

Built from app/src/main/java/com/zynergylabs/forager/app/domain/wayhome/WayHomeProgress.kt at a00742ffb
(origin/waypoint-route-on-map, PR #413): WayHomeLine.init (:30-67), placeOnWayHome (:133-154), metersPastLineStart
(:170-180); PathHome.kt nextHopBand (:123-134); GeoDistance.metersBetween (haversine, IUGG radius) and metersPerDegree;
the test ground Ground.at (WayHomeFakes.kt:22-26); hasArrived and APPROACHING_ZONE_METERS (domain/NavigationReadout.kt).
Validated against the literals WayHomeProgressTest.kt pins at the same commit before any option is read (section 1 of the
output). Run: python3 -I waypoint_figure.py > waypoint_figure.out
"""
import math

EARTH_MEAN_RADIUS_METERS = 6_371_008.8
WAY_HOME_LINE_STEP_METERS = 10.0
WAY_HOME_TRAIL_JOIN_METERS = 25.0
HOP_FAR_ENTER_ABOVE_METERS = 50.0
HOP_FAR_LEAVE_BELOW_METERS = 45.0
HOP_ENTER_ABOVE_METERS = 25.0
HOP_LEAVE_BELOW_METERS = 20.0
LIVE_FIX_MAX_ACCURACY_METERS = 50.0
WAY_HOME_FIGURE_REACH_METERS = LIVE_FIX_MAX_ACCURACY_METERS + WAY_HOME_LINE_STEP_METERS  # 60
WAY_HOME_TRAIL_PLACE_ALLOWANCE_METERS = WAY_HOME_TRAIL_JOIN_METERS  # 25
ARRIVAL_MIN_RADIUS_METERS = 15.0
APPROACHING_ACCURACY_MULTIPLIER = 2.0
APPROACHING_ZONE_METERS = 100.0
FEET_PER_METER = 3.280839895


def meters_between(a, b):
    lat1 = math.radians(a[0]); lat2 = math.radians(b[0])
    hdlat = math.radians(b[0] - a[0]) / 2; hdlng = math.radians(b[1] - a[1]) / 2
    h = math.sin(hdlat) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(hdlng) ** 2
    return 2 * EARTH_MEAN_RADIUS_METERS * math.asin(min(1.0, math.sqrt(h)))


def meters_per_degree(lat):
    per_lat = math.pi * EARTH_MEAN_RADIUS_METERS / 180.0
    return per_lat, per_lat * math.cos(math.radians(lat))


BASE = (45.02, -120.01)
_PER_LAT, _PER_LNG = meters_per_degree(BASE[0])


def at(east, north):
    """Ground.at: metres east and north of BASE, as the way-home tests lay their lines out."""
    return (BASE[0] + north / _PER_LAT, BASE[1] + east / _PER_LNG)


def east_north(p):
    return (p[1] - BASE[1]) * _PER_LNG, (p[0] - BASE[0]) * _PER_LAT


class Line:
    """WayHomeLine: parts in walking order, filled so no stretch is over 10 m. kinds[i] is the stretch i -> i+1's kind."""

    def __init__(self, parts):
        # parts: list of (kind, [points], meters or None). kind in LEADIN, TRAIL, FINAL (the app: STRAIGHT, TRAIL, STRAIGHT).
        self.nodes, self.along, self.kinds = [], [], []
        total = 0.0
        for kind, points, meters in parts:
            geometric = [meters_between(points[i], points[i + 1]) for i in range(len(points) - 1)]
            s = sum(geometric)
            if meters is None:
                meters = s
            scale = meters / s if s > 0 else 0.0
            if not self.nodes and points:
                self.nodes.append(points[0]); self.along.append(total); self.kinds.append(kind)
            for i, g in enumerate(geometric):
                a, b = points[i], points[i + 1]
                steps = max(1, math.ceil(g / WAY_HOME_LINE_STEP_METERS))
                self.kinds[-1] = kind
                for st in range(1, steps + 1):
                    t = st / steps
                    self.nodes.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t))
                    self.along.append(total + g * scale * t)
                    self.kinds.append(kind)
                total += g * scale
        self.total = self.along[-1] if self.along else 0.0

    def meters_from(self, p):
        return min(meters_between(p, n) for n in self.nodes)

    def point_along(self, s):
        """The point s metres along the line (for a walker truly on it)."""
        s = max(0.0, min(self.total, s))
        for i in range(len(self.nodes) - 1):
            if self.along[i] <= s <= self.along[i + 1]:
                a, b = self.nodes[i], self.nodes[i + 1]
                span = self.along[i + 1] - self.along[i]
                t = (s - self.along[i]) / span if span > 0 else 0.0
                return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
        return self.nodes[-1]


def next_hop_band(previous, hop):
    far = hop >= HOP_FAR_LEAVE_BELOW_METERS if previous == 'FAR' else hop > HOP_FAR_ENTER_ABOVE_METERS
    if far:
        return 'FAR'
    counted = hop > HOP_ENTER_ABOVE_METERS if previous == 'NONE' else hop >= HOP_LEAVE_BELOW_METERS
    return 'COUNTED' if counted else 'NONE'


def meters_past_line_start(line, current):
    start = line.nodes[0]
    nxt = next((n for n in line.nodes if meters_between(start, n) > 0.5), None)
    if nxt is None:
        return 0.0
    per_lat, per_lng = meters_per_degree(start[0])
    se, sn = (nxt[1] - start[1]) * per_lng, (nxt[0] - start[0]) * per_lat
    te, tn = (current[1] - start[1]) * per_lng, (current[0] - start[0]) * per_lat
    return meters_between(start, current) if se * te + sn * tn <= 0.0 else 0.0


def place(line, fix, previous_index=None, previous_band='NONE', reach=WAY_HOME_FIGURE_REACH_METERS,
          allowance=WAY_HOME_TRAIL_PLACE_ALLOWANCE_METERS, straight_on_final=False, waypoint=None):
    """placeOnWayHome, with the option knobs: reach (O4), allowance (O2/O4), straight_on_final (O3).
    Returns None for OffLine, else dict(index, hop, band, figure, figure_index)."""
    d = [meters_between(fix, n) for n in line.nodes]
    last = len(d) - 1
    frm = previous_index if previous_index is not None else 0

    def earliest(lo, within):
        for i in range(lo, last + 1):
            if d[i] <= within:
                return i
        return None

    index = earliest(frm, WAY_HOME_TRAIL_JOIN_METERS)
    if index is None: index = earliest(frm, HOP_FAR_ENTER_ABOVE_METERS)
    if index is None: index = earliest(0, WAY_HOME_TRAIL_JOIN_METERS)
    if index is None: index = earliest(0, HOP_FAR_ENTER_ABOVE_METERS)
    if index is None:
        return None
    hop = d[index]
    band = next_hop_band(previous_band, hop)
    if band == 'FAR':
        return None
    fi = earliest(0, reach)
    if fi is None and reach < WAY_HOME_FIGURE_REACH_METERS:
        # O4 only (correction after the first run, kept in waypoint_figure.before-o4-fallback.out): when no node is within
        # the accuracy reach, the fix is further from the line than the phone's own claim, so today's 60 m rule governs.
        # The app's `?: index` fallback (WayHomeProgress.kt:148) is sound only because its reach (60) exceeds the place's
        # far band (50); with a shorter reach it would count from the needle's place, which can be ahead of the walker.
        fi = earliest(0, WAY_HOME_FIGURE_REACH_METERS)
    if fi is None:
        fi = index
    on_trail = line.kinds[fi] == 'TRAIL'
    figure = line.total - line.along[fi] + (allowance if on_trail else 0.0) + (meters_past_line_start(line, fix) if fi == 0 else 0.0)
    if straight_on_final and line.kinds[index] == 'FINAL' and waypoint is not None:
        figure = meters_between(fix, waypoint)
    return dict(index=index, hop=hop, band=band, figure=figure, figure_index=fi, on_trail=on_trail)


def has_arrived(straight, accuracy):
    return straight <= max(ARRIVAL_MIN_RADIUS_METERS, APPROACHING_ACCURACY_MULTIPLIER * (accuracy or 0.0))


def ft(m):
    return round(m * FEET_PER_METER)


# ---------------------------------------------------------------- options
def option_knobs(name, accuracy):
    """The four options as knob settings for place()."""
    if name == 'O1':
        return dict()
    if name == 'O2':
        return dict(allowance=0.0)
    if name == 'O3':
        return dict(straight_on_final=True)
    if name == 'O4':
        r = min(60.0, max(ARRIVAL_MIN_RADIUS_METERS, APPROACHING_ACCURACY_MULTIPLIER * accuracy) + WAY_HOME_LINE_STEP_METERS)
        return dict(reach=r, allowance=0.0)
    raise ValueError(name)


OPTIONS = ['O1', 'O2', 'O3', 'O4@4', 'O4@10', 'O4@20']


def knobs_for(label):
    name, _, acc = label.partition('@')
    return option_knobs(name, float(acc) if acc else 4.0)


# ---------------------------------------------------------------- cases
def waypoint_route(walker, trail_points, waypoint):
    parts = [('LEADIN', [walker, trail_points[0]], None), ('TRAIL', trail_points, None), ('FINAL', [trail_points[-1], waypoint], None)]
    return Line(parts), waypoint


def case_W(final_len=40.0, square=True):
    trail = [at(0, 0), at(350, 0)]
    wp = at(350, final_len) if square else at(350 + final_len, 0)
    return waypoint_route(at(0, -40), trail, wp)


def case_Bn():
    trail = [at(0, 0), at(200, 0), at(200, 30)]
    return waypoint_route(at(0, -40), trail, at(240, 30))


def case_U(gap=30.0):
    trail = [at(0, 0), at(200, 0), at(200, gap), at(0, gap)]
    return waypoint_route(at(-40, 0), trail, at(0, gap + 40))


def case_R():
    # The re-routed line on W after a stray to (175, 75): lead-in from there to the trail, the trail's remainder, the final leg.
    trail = [at(175, 0), at(350, 0)]
    return waypoint_route(at(175, 75), trail, at(350, 40))


# ---------------------------------------------------------------- walks
def walk(line, waypoint, labels, accuracy=4.0, step=1.0, report_at=None, displaced=None):
    """Walk the line with a fix at the true position (or displaced by (east, north) metres), carrying the place as
    AvailabilityWaypointRoute.show() does (lastPlace, band). Returns rows: s, truth, straight, {label: figure}, arrived, approaching."""
    rows = []
    prev = {lab: (None, 'NONE') for lab in labels}
    s = 0.0
    while s <= line.total + 1e-9:
        true_p = line.point_along(s)
        fix = true_p
        if displaced:
            e, n = east_north(true_p)
            fix = at(e + displaced[0], n + displaced[1])
        straight = meters_between(fix, waypoint)
        row = dict(s=s, truth=line.total - s, straight=straight, arrived=has_arrived(straight, accuracy),
                   approaching=straight <= APPROACHING_ZONE_METERS, fig={})
        for lab in labels:
            pi, pb = prev[lab]
            res = place(line, fix, pi, pb, waypoint=waypoint, **knobs_for(lab))
            if res is None:
                row['fig'][lab] = None
                prev[lab] = (None, 'FAR')
            else:
                row['fig'][lab] = res['figure']
                prev[lab] = (res['index'], res['band'])
        rows.append(row)
        s += step
    return rows


def print_walk(title, line, waypoint, rows, labels, marks):
    print(f"\n### {title}  (line {line.total:.1f} m)")
    print("s = metres walked along the line; truth = line metres left; straight = straight line to the waypoint; figures in m (ft).")
    head = f"{'s':>6} {'truth':>6} {'straight':>8} " + " ".join(f"{lab:>14}" for lab in labels) + "  state"
    print(head)
    arrived_row = next((r for r in rows if r['arrived']), None)
    marks = list(marks)
    if arrived_row:
        marks.append(arrived_row['s'])
    seen = set()
    for r in rows:
        if any(abs(r['s'] - m) < 0.5 for m in marks) and round(r['s']) not in seen:
            seen.add(round(r['s']))
            figs = " ".join((f"{f:6.1f} ({ft(f):4d})" if f is not None else f"{'off':>14}") for f in (r['fig'][l] for l in labels))
            state = 'ARRIVED' if r['arrived'] else ('Approaching' if r['approaching'] else '')
            print(f"{r['s']:6.1f} {r['truth']:6.1f} {r['straight']:8.1f} {figs}  {state}")
    if arrived_row:
        print(f"'Arrived' first shows at s = {arrived_row['s']:.1f} (straight {arrived_row['straight']:.1f} m); the last figure before it is the row just above.")
        before = [r for r in rows if r['s'] < arrived_row['s']]
        if before:
            last = before[-1]
            print("Last figure before 'Arrived': " + ", ".join(f"{l} {last['fig'][l]:.1f} m ({ft(last['fig'][l])} ft)" if last['fig'][l] is not None else f"{l} off" for l in labels) + f"; truth {last['truth']:.1f} m.")
    # long-by summary over the walk, per option, before arrival
    print("Long by (figure minus truth) over the walk before 'Arrived', per option: min / max; 'short' lists any row under the truth.")
    for l in labels:
        diffs = [(r['fig'][l] - r['truth'], r['s']) for r in rows if r['fig'][l] is not None and not r['arrived']]
        if diffs:
            shorts = [(d, s) for d, s in diffs if d < -0.01]
            print(f"  {l:>6}: {min(d for d, _ in diffs):7.1f} / {max(d for d, _ in diffs):7.1f}" + (f"; SHORT at {len(shorts)} rows, worst {min(shorts)[0]:.1f} at s={min(shorts)[1]:.0f}" if shorts else "; never short"))
    # where does the figure rise while the walker advances?
    for l in labels:
        rises = [(rows[i - 1]['s'], rows[i]['fig'][l] - rows[i - 1]['fig'][l]) for i in range(1, len(rows))
                 if rows[i]['fig'][l] is not None and rows[i - 1]['fig'][l] is not None and rows[i]['fig'][l] > rows[i - 1]['fig'][l] + 0.5 and not rows[i]['arrived']]
        if rises:
            total_rise = sum(d for _, d in rises)
            print(f"  {l:>6} rises while the walker advances: {len(rises)} steps, {total_rise:.1f} m in all; first at s={rises[0][0]:.0f} (+{rises[0][1]:.1f})")


# ---------------------------------------------------------------- sweeps
def sweep(line, waypoint, labels, accuracy=4.0, pos_step=5.0, r_step=2.5, b_step=10, with_previous=True):
    """The walker truly at every pos_step along the line; the fix displaced by every radius up to 50 m at every bearing.
    previous = the place the walker would have from a true fix at the same spot (as the screen carries lastPlace), or None."""
    out = {l: dict(n=0, short=0, worst_short=0.0, worst_long=0.0, short_s=None, short_r=None, short_b=None) for l in labels}
    s = 0.0
    while s <= line.total + 1e-9:
        true_p = line.point_along(s)
        truth = line.total - s
        e0, n0 = east_north(true_p)
        prev_by_label = {}
        for l in labels:
            if with_previous:
                res = place(line, true_p, None, 'NONE', waypoint=waypoint, **knobs_for(l))
                prev_by_label[l] = (res['index'], res['band']) if res else (None, 'NONE')
            else:
                prev_by_label[l] = (None, 'NONE')
        r = 0.0
        while r <= LIVE_FIX_MAX_ACCURACY_METERS + 1e-9:
            for b in range(0, 360, b_step):
                fix = at(e0 + r * math.sin(math.radians(b)), n0 + r * math.cos(math.radians(b)))
                if has_arrived(meters_between(fix, waypoint), accuracy):
                    continue  # the HUD shows 'Arrived', not the figure
                for l in labels:
                    pi, pb = prev_by_label[l]
                    res = place(line, fix, pi, pb, waypoint=waypoint, **knobs_for(l))
                    if res is None:
                        continue
                    o = out[l]
                    o['n'] += 1
                    diff = res['figure'] - truth
                    if diff < -0.01:
                        o['short'] += 1
                        if diff < o['worst_short']:
                            o['worst_short'], o['short_s'], o['short_r'], o['short_b'] = diff, s, r, b
                    if diff > o['worst_long']:
                        o['worst_long'] = diff
                if r == 0.0:
                    break
            r += r_step
        s += pos_step
    return out


def print_sweep(title, out, labels):
    print(f"\n### Sweep: {title}")
    print("Walker every 5 m along the line; fix displaced 0 to 50 m (2.5 m steps) at every 10 degrees; placements where the HUD would say 'Arrived' are left out.")
    print(f"{'option':>6} {'placements':>10} {'short':>6} {'worst short':>12} {'worst long':>11}  where the worst short was")
    for l in labels:
        o = out[l]
        where = f"walker at s={o['short_s']:.0f}, fix {o['short_r']:.1f} m off at {o['short_b']} deg" if o['short'] else ""
        print(f"{l:>6} {o['n']:>10} {o['short']:>6} {o['worst_short']:>12.1f} {o['worst_long']:>11.1f}  {where}")


# ---------------------------------------------------------------- main
def validate():
    """Section 1: the replica against WayHomeProgressTest's pinned literals at a00742ffb."""
    print("## 1. Replica validation against WayHomeProgressTest.kt (a00742ffb) pinned literals, tolerance 0.5 m")
    hairpin = Line([('TRACK', [at(0, -50), at(0, 0)], None),
                    ('TRAIL', [at(0, 0), at(200, 0), at(200, 20), at(0, 20)], None),
                    ('TRACK', [at(0, 20), at(0, 120)], None)])
    checks = []
    checks.append(("line total 570", hairpin.total, 570.0))
    checks.append(("fix (0,-50) -> 570", place(hairpin, at(0, -50))['figure'], 570.0))
    checks.append(("fix (195,0) -> 380 + 25", place(hairpin, at(195, 0))['figure'], 405.0))
    checks.append(("fix (-35,-50) -> 605 (RECORD -1634)", place(hairpin, at(-35, -50))['figure'], 605.0))
    checks.append(("fix (-49.9,-50) -> 619.9", place(hairpin, at(-49.9, -50))['figure'], 619.9))
    for b in (5, 10, 20, 30, 45):
        checks.append((f"fix (0,-50-{b}) -> 570+{b}", place(hairpin, at(0, -50 - b))['figure'], 570.0 + b))
    checks.append(("fix (20,-50) -> 590", place(hairpin, at(20, -50))['figure'], 590.0))
    for n in (-50, -45, -30, -10):
        checks.append((f"fix (0,{n}) -> 570", place(hairpin, at(0, n))['figure'], 570.0))
    ok = True
    for name, got, want in checks:
        good = abs(got - want) <= 0.5
        ok &= good
        print(f"  {'PASS' if good else 'FAIL'}  {name}: {got:.4f}")
    off = place(hairpin, at(-50.1, -50)) is None
    print(f"  {'PASS' if off else 'FAIL'}  fix (-50.1,-50) -> OffLine")
    ok &= off
    # Also the documented 60 m figures from the two option reviews (not CI literals; hand figures agreed by Juniper and Sorrel)
    print("  Documented 60 m figures (Juniper/Sorrel, not CI literals): H fix (100,30) 495; T (200,0) 405, (100,0) 505, (0,0) 570")
    for p, want in (((100, 30), 495.0), ((200, 0), 405.0), ((100, 0), 505.0), ((0, 0), 570.0)):
        got = place(hairpin, at(*p))['figure']
        print(f"  {'agree' if abs(got - want) <= 0.5 else 'DIFFER'}  fix {p}: {got:.1f}")
    wide = Line([('TRACK', [at(0, -50), at(0, 0)], None), ('TRAIL', [at(0, 0), at(200, 0), at(200, 70), at(0, 70)], None), ('TRACK', [at(0, 70), at(0, 170)], None)])
    got = place(wide, at(105, 49.9))['figure']
    print(f"  {'agree' if abs(got - 515.0) <= 0.5 else 'DIFFER'}  P: legs 70 m apart, fix (105,49.9): {got:.1f} (documented 515)")
    print(f"\nVALIDATION {'PASSED' if ok else 'FAILED'}: {'the figures below may be read' if ok else 'nothing below may be cited'}")
    return ok


def main():
    if not validate():
        return
    labels = OPTIONS
    print("\n## 2. Options")
    print("O1 today's rule (60 m reach, +25 m on the trail); O2 the reach alone (no 25 m); O3 today's rule until the walker's place is on the final leg, then the straight line to the waypoint;")
    print("O4@a: reach = max(15, 2a) + 10 m (a = reported accuracy, m), no 25 m, and today's 60 m rule when nothing is within that reach: at a=4 the reach is 25 m; a=10, 30 m; a=20, 50 m. Arrival at straight <= max(15, 2a); the walks use a=4 (what Hazel fed the S22).")

    print("\n## 3. Walks with the fix at the true position")
    line, wp = case_W()
    rows = walk(line, wp, labels)
    print_walk("W: the S22 walk's shape (lead-in 40 m square to the trail, straight trail 350 m, final leg 40 m square)", line, wp, rows, labels,
               marks=[0, 20, 40, 59, 61, 100, 200, 290, 330, 350, 370, 390, 400, 410, 415, 420, 425])
    print("Hazel read 412 ft at the end of the trail (s = 390, 131 ft left): compare O1 at s = 390 above.")

    for L, square in ((10, True), (25, True), (100, True), (250, True), (100, False)):
        line, wp = case_W(final_len=L, square=square)
        rows = walk(line, wp, labels)
        print_walk(f"F: W with the final leg {L} m, {'square to' if square else 'in line with'} the trail", line, wp, rows, labels,
                   marks=[390, 390 + L * 0.25, 390 + L * 0.5, 390 + L * 0.75, 390 + L - 60, 390 + L - 30, 390 + L - 20])

    line, wp = case_Bn()
    rows = walk(line, wp, labels)
    print_walk("Bn: a 90-degree bend 30 m before the trail's end, final leg 40 m", line, wp, rows, labels, marks=[200, 240, 260, 270, 280, 290, 300, 305])

    for gap in (30.0, 20.0):
        line, wp = case_U(gap)
        rows = walk(line, wp, labels)
        print_walk(f"U: the trail turns back on itself, legs {gap:.0f} m apart; the final leg leaves the return leg's end", line, wp, rows, labels,
                   marks=[40, 140, 240, 270, 300, 340, 400, 440, 470, 480, 490, 500, 505])

    line, wp = case_R()
    rows = walk(line, wp, labels)
    print_walk("R: after a re-route from a stray at (175, 75): new lead-in 75 m south, trail 175 m, final leg 40 m", line, wp, rows, labels,
               marks=[0, 10, 25, 30, 50, 60, 65, 75, 100, 200, 250, 270, 280])

    print("\n## 4. Walks with a displaced fix on W (the whole walk with the same error), fix 20 m and 40 m north of the true position (toward the waypoint's side)")
    line, wp = case_W()
    for disp in ((0, 20), (0, 40), (0, -40), (30, 0), (-30, 0)):
        rows = walk(line, wp, labels, displaced=disp)
        print_walk(f"W with every fix displaced by {disp} m (east, north)", line, wp, rows, labels, marks=[100, 200, 330, 370, 390, 400, 410, 415])

    print("\n## 5. Error sweeps: every fix the gate admits (0 to 50 m, every 10 degrees), walker every 5 m along the line")
    for title, (line, wp) in (("W", case_W()), ("F: final leg 100 m square", case_W(100.0)), ("Bn", case_Bn()), ("U, legs 30 m apart", case_U(30.0)), ("U, legs 20 m apart", case_U(20.0))):
        out = sweep(line, wp, labels, with_previous=True)
        print_sweep(f"{title}, previous place = the walker's true place", out, labels)
    out = sweep(*case_W(), labels, with_previous=False)
    print_sweep("W, previous = none (as WayHomeProgressTest.assertNeverShort tests)", out, labels)

    print("\n## 6. O4's shortness against the true error, on W: the walker at the end of the trail (s = 390), fix displaced north by d")
    line, wp = case_W()
    true_p = line.point_along(390.0)
    e0, n0 = east_north(true_p)
    print(f"{'d':>4} " + " ".join(f"{l:>8}" for l in ('O1', 'O2', 'O4@4', 'O4@10', 'O4@20')) + "   truth 40.0")
    for d in (0, 5, 10, 15, 17.5, 20, 25, 30, 40, 50):
        fix = at(e0, n0 + d)
        figs = []
        for l in ('O1', 'O2', 'O4@4', 'O4@10', 'O4@20'):
            res = place(line, fix, None, 'NONE', waypoint=wp, **knobs_for(l))
            figs.append(res['figure'] if res else float('nan'))
        print(f"{d:4.1f} " + " ".join(f"{f:8.1f}" for f in figs))


if __name__ == '__main__':
    main()
