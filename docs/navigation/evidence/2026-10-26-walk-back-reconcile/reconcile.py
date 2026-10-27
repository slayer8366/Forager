#!/usr/bin/env python3 -I
"""What the app's walk-back time would have said at every kept point of the two 2026-10-24
recordings, against how long the walker actually took to get back from there. Method: section 1
of docs/navigation/2026-10-26-walk-back-reconcile.md, committed before this ran.

Prints figures only (metres, seconds, counts, ratios, minutes since the recording's start): no
coordinates, no names, no times of day. Writes the same as JSON and a per-sample CSV to OUT_DIR.

Usage: python3 -I reconcile.py [DB_PATH] [OUT_DIR]
"""
import csv
import heapq
import json
import math
import os
import sqlite3
import sys

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from trackmath import (  # noqa: E402
    LAT, LNG, TS, PRES,
    haversine_m, is_network_rule, barometric_heights, climb_descent, moving_pace,
    MOVING_SPEED_FLOOR_METERS_PER_SECOND, MEASURED_PACE_MIN_MOVING_MILLIS,
)

DEFAULT_DB = "/mnt/work/scratch/s26-hilly-walk/work/forager.db"
DEFAULT_OUT = "/mnt/work/scratch/s26-hilly-walk/out/reconcile"
RECORDINGS = (("A", "9685146a"), ("B", "cad94737"))
SELF_JOIN_EPSILON_METERS = 10.0          # TrackSelfJoin.kt:32
ELEVATION_HYSTERESIS_METERS = 4.0        # ElevationHysteresis.THRESHOLD_METERS
CLIMB_ALLOWANCE_SECONDS_PER_METER = 2.2  # ClimbAllowance.kt:46
MEASURED_PACE_SETTLED_MOVING_MILLIS = 15 * 60 * 1000  # MovingPace.kt:299
DARKNESS_MARGIN_S = 60 * 60              # SundownPreferencesRepository.kt:62, default
SAMPLE_EVERY_S = 300
TOLERANCE = 0.01

# Positive controls: figures computed independently by Sorrel's ports (recompute.out) and by the
# earlier report (hilly-walk-climbs, out_and_back). A port that misses one is wrong.
CONTROLS = {
    "A": dict(whole_climb=52.8, whole_descent=43.1, far_route_m=459, far_joins=35, far_pace=0.874,
              far_route_climb=21.9, far_route_descent=0.0, far_allowance_s=48.2, far_pace_only_s=525,
              second_path_m=1121, second_moving_s=891, far_index=264),
    "B": dict(whole_climb=72.4, whole_descent=68.7, far_route_m=1956, far_joins=49, far_pace=0.863,
              far_route_climb=50.9, far_route_descent=61.5, far_allowance_s=247.4, far_pace_only_s=2266,
              second_path_m=1534, second_moving_s=1489, far_index=404),
}


def load(db, prefix):
    conn = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
    rows = conn.execute("SELECT id, originWaypointId FROM tracks WHERE id LIKE ?", (prefix + "%",)).fetchall()
    if len(rows) != 1:
        sys.exit(f"track prefix {prefix!r} matches {len(rows)} tracks")
    tid, origin_id = rows[0]
    pts = conn.execute(
        "SELECT lat, lng, altitude, accuracyMeters, timestampEpochMillis, "
        "speedMetersPerSecond, speedAccuracyMetersPerSecond, pressureHpa "
        "FROM track_points WHERE trackId = ? ORDER BY timestampEpochMillis ASC", (tid,)).fetchall()
    origin = None
    if origin_id is not None:
        o = conn.execute("SELECT lat, lng FROM waypoints WHERE id = ?", (origin_id,)).fetchone()
        origin = (o[0], o[1]) if o else None
    conn.close()
    kept = [p for p in pts if not is_network_rule(p[TS])]
    return kept, len(pts) - len(kept), origin


# ---- joinedTrackRoute (TrackSelfJoin.kt:78-154), every pair tested; joins computed once ----

def all_joins(pts, eps=SELF_JOIN_EPSILON_METERS):
    """Every non-consecutive pair (j < i - 1) within eps: the Kotlin's join edges over the whole
    track. A prefix 0..i uses only the pairs with both ends <= i, which is what the scan over that
    prefix would find (TrackSelfJoin.kt:95-115 compares each point with earlier ones only)."""
    n = len(pts)
    joins = [[] for _ in range(n)]
    for i in range(n):
        for j in range(i - 1):
            m = haversine_m(pts[i][LAT], pts[i][LNG], pts[j][LAT], pts[j][LNG])
            if m <= eps:
                joins[i].append((j, m))
                joins[j].append((i, m))
    return joins


def route_home(legs, joins, upto):
    """Dijkstra from point `upto` (the most recent) to point 0 over legs and joins among 0..upto.
    Returns (meters, route indices most-recent-first, joins on route)."""
    n = upto + 1
    if n == 1:
        return 0.0, [0], 0
    dist = [math.inf] * n
    prev = [-1] * n
    via = [False] * n
    src = upto
    dist[src] = 0.0
    q = [(0.0, src)]
    while q:
        d, u = heapq.heappop(q)
        if u == 0:
            break
        if d > dist[u]:
            continue
        cands = []
        if u > 0:
            cands.append((u - 1, legs[u - 1], False))
        if u < n - 1:
            cands.append((u + 1, legs[u], False))
        for v, m in joins[u]:
            if v <= upto:
                cands.append((v, m, True))
        for v, m, is_join in cands:
            c = d + m
            if c < dist[v]:
                dist[v] = c
                prev[v] = u
                via[v] = is_join
                heapq.heappush(q, (c, v))
    route = []
    v = 0
    joins_on = 0
    while v != src:
        route.append(v)
        if via[v]:
            joins_on += 1
        v = prev[v]
    route.append(src)
    route.reverse()
    return dist[0], route, joins_on


def fmt(sec):
    return f"{int(sec // 60)} min {sec % 60:02.0f} s"


def check(name, got, want, rel=TOLERANCE, results=None):
    ok = abs(got - want) <= max(rel * abs(want), 0.05 if abs(want) < 1 else 0.5)
    results.append(dict(control=name, got=got, want=want, ok=ok))
    print(f"    control {name}: got {got:.3f}, want {want}: {'ok' if ok else 'FAIL'}")
    return ok


def analyse(label, kept, excluded, origin, out_dir):
    n = len(kept)
    t0 = kept[0][TS]
    legs = [haversine_m(a[LAT], a[LNG], b[LAT], b[LNG]) for a, b in zip(kept, kept[1:])]
    cum = [0.0]
    for d in legs:
        cum.append(cum[-1] + d)
    joins = all_joins(kept)
    origin_leg = haversine_m(kept[0][LAT], kept[0][LNG], origin[0], origin[1]) if origin else 0.0
    far = max(range(n), key=lambda i: haversine_m(kept[0][LAT], kept[0][LNG], kept[i][LAT], kept[i][LNG]))
    end = kept[-1]

    # Interval flags for moving time, once.
    mov = []
    for i in range(1, n):
        dt = (kept[i][TS] - kept[i - 1][TS]) / 1000.0
        mov.append(dt > 0 and legs[i - 1] / dt >= MOVING_SPEED_FLOOR_METERS_PER_SECOND)
    # Suffix sums: moving time and moving metres from i to the end.
    suf_mov_s = [0.0] * n
    suf_mov_m = [0.0] * n
    for i in range(n - 2, -1, -1):
        dt = (kept[i + 1][TS] - kept[i][TS]) / 1000.0
        suf_mov_s[i] = suf_mov_s[i + 1] + (dt if mov[i] else 0.0)
        suf_mov_m[i] = suf_mov_m[i + 1] + (legs[i] if mov[i] else 0.0)

    rows = []
    for i in range(n):
        route_m, route, joins_on = route_home(legs, joins, i)
        pace = moving_pace(kept[: i + 1])
        at_least = pace["source"] == "DEFAULT" or (
            (pace["doppler_moving_millis"] if pace["source"] == "DOPPLER" else pace["moving_millis"]) < MEASURED_PACE_SETTLED_MOVING_MILLIS)
        total_path = route_m + origin_leg
        pace_only_s = total_path / pace["speed"]
        elapsed_so_far = (kept[i][TS] - t0) / 1000.0
        moving_so_far = pace["moving_millis"] / 1000.0
        back_elapsed = (end[TS] - kept[i][TS]) / 1000.0
        back_path = cum[-1] - cum[i]
        straight_home = haversine_m(kept[i][LAT], kept[i][LNG], origin[0], origin[1]) if origin else haversine_m(kept[i][LAT], kept[i][LNG], kept[0][LAT], kept[0][LNG])
        rows.append(dict(
            i=i, min_in=elapsed_so_far / 60.0, route_m=route_m, joins_on=joins_on, retrace_m=cum[i],
            straight_home_m=straight_home, origin_leg_m=origin_leg, pace=pace["speed"], pace_source=pace["source"],
            governing_moving_s=(pace["doppler_moving_millis"] if pace["source"] == "DOPPLER" else pace["moving_millis"]) / 1000.0,
            at_least=at_least, pace_only_s=pace_only_s, elapsed_so_far_s=elapsed_so_far, moving_so_far_s=moving_so_far,
            stop_ratio_so_far=(elapsed_so_far / moving_so_far) if moving_so_far > 0 else None,
            back_elapsed_s=back_elapsed, back_moving_s=suf_mov_s[i], back_path_m=back_path, back_moving_m=suf_mov_m[i],
            route=route,
        ))

    # Heights at the 5-minute marks and the far point: barometric over the track so far, bridged, banked along the route.
    marks = []
    t = 0
    while t <= rows[-1]["elapsed_so_far_s"]:
        idx = max(r["i"] for r in rows if r["elapsed_so_far_s"] <= t)
        marks.append(idx)
        t += SAMPLE_EVERY_S
    if far not in marks:
        marks.append(far)
    if n - 1 not in marks:
        marks.append(n - 1)
    marks = sorted(set(marks))
    allowance = {}
    for idx in marks:
        h = barometric_heights(kept[: idx + 1])
        rh = [h[k] for k in rows[idx]["route"]]
        up, down = climb_descent(rh, ELEVATION_HYSTERESIS_METERS)
        allowance[idx] = dict(climb_m=up, descent_m=down, allowance_s=(up + down) * CLIMB_ALLOWANCE_SECONDS_PER_METER)

    # Whole-recording heights for the control.
    hw = barometric_heights(kept)
    wc, wd = climb_descent(hw, ELEVATION_HYSTERESIS_METERS)

    print(f"\n=== Recording {label}: {n} kept points ({excluded} excluded), origin leg {origin_leg:.1f} m, far point index {far} at {rows[far]['min_in']:.1f} min, duration {rows[-1]['min_in']:.1f} min")
    print("  Positive controls (independent figures from recompute.out and the earlier report):")
    ctl = CONTROLS[label]
    cr = []
    check("whole climb (bridged)", wc, ctl["whole_climb"], results=cr)
    check("whole descent (bridged)", wd, ctl["whole_descent"], results=cr)
    check("far index", far, ctl["far_index"], results=cr)
    fr = rows[far]
    check("far route m", fr["route_m"], ctl["far_route_m"], results=cr)
    check("far joins on route", fr["joins_on"], ctl["far_joins"], results=cr)
    check("far pace", fr["pace"], ctl["far_pace"], results=cr)
    check("far route climb", allowance[far]["climb_m"], ctl["far_route_climb"], results=cr)
    check("far route descent", allowance[far]["descent_m"], ctl["far_route_descent"], results=cr)
    check("far allowance s", allowance[far]["allowance_s"], ctl["far_allowance_s"], results=cr)
    check("far pace-only s (hop 0, no origin leg)", fr["route_m"] / fr["pace"], ctl["far_pace_only_s"], results=cr)
    check("second-half path m", fr["back_path_m"], ctl["second_path_m"], results=cr)
    check("second-half moving s", fr["back_moving_s"], ctl["second_moving_s"], results=cr)
    all_ok = all(c["ok"] for c in cr)
    print(f"  controls: {'all pass' if all_ok else 'FAILURE: a port is wrong; nothing below is evidence'}")

    def decomposition(r):
        d = {}
        if r["back_path_m"] <= 0 or r["back_moving_s"] <= 0 or r["back_moving_m"] <= 0:
            return None
        d["route_over_walked"] = r["route_m"] / r["back_path_m"]
        d["route_over_retrace"] = r["route_m"] / r["retrace_m"] if r["retrace_m"] > 0 else None
        d["retrace_over_walked"] = r["retrace_m"] / r["back_path_m"]
        d["walked_over_moving_m"] = r["back_path_m"] / r["back_moving_m"]
        d["return_speed_over_pace"] = (r["back_moving_m"] / r["back_moving_s"]) / r["pace"]
        d["effect2"] = d["walked_over_moving_m"] * d["return_speed_over_pace"]
        app = r["route_m"] / r["pace"]  # route only, so the identity is exact; the origin leg is reported beside it
        d["app_route_only_s"] = app
        d["app_over_moving"] = app / r["back_moving_s"]
        d["identity_residual"] = d["app_over_moving"] - d["route_over_walked"] * d["effect2"]
        d["pace_only_over_moving"] = r["pace_only_s"] / r["back_moving_s"]
        d["pace_only_over_elapsed"] = r["pace_only_s"] / r["back_elapsed_s"] if r["back_elapsed_s"] > 0 else None
        return d

    print("\n  Every 5 min (and the far point *, the end): minutes in | route m (joins) | retrace m | straight home m | pace m/s src label | walk back pace-only / with allowance (climb+descent m) | from here to the end: elapsed, moving, path m | pace-only/moving, with-allowance/moving, pace-only/elapsed")
    table = []
    for idx in marks:
        r = rows[idx]
        a = allowance[idx]
        with_s = r["pace_only_s"] + a["allowance_s"]
        homeward = idx >= far
        line = dict(
            min_in=r["min_in"], i=idx, far=(idx == far), homeward=homeward, route_m=r["route_m"], joins_on=r["joins_on"], retrace_m=r["retrace_m"],
            straight_home_m=r["straight_home_m"], pace=r["pace"], pace_source=r["pace_source"], at_least=r["at_least"],
            pace_only_s=r["pace_only_s"], allowance_s=a["allowance_s"], route_climb_m=a["climb_m"], route_descent_m=a["descent_m"], with_allowance_s=with_s,
            back_elapsed_s=r["back_elapsed_s"], back_moving_s=r["back_moving_s"], back_path_m=r["back_path_m"],
            ratio_pace_only_moving=(r["pace_only_s"] / r["back_moving_s"]) if r["back_moving_s"] > 0 else None,
            ratio_with_moving=(with_s / r["back_moving_s"]) if r["back_moving_s"] > 0 else None,
            ratio_pace_only_elapsed=(r["pace_only_s"] / r["back_elapsed_s"]) if r["back_elapsed_s"] > 0 else None,
            home_before_sunset_if_left_at_start_back=(r["back_elapsed_s"] < with_s + DARKNESS_MARGIN_S),
            decomposition=decomposition(r),
        )
        table.append(line)
        tag = "*" if idx == far else (" " if not homeward else "h")
        rm = line["ratio_pace_only_moving"]
        rw = line["ratio_with_moving"]
        re_ = line["ratio_pace_only_elapsed"]
        print(f"  {tag} {r['min_in']:6.1f} | {r['route_m']:6.0f} ({r['joins_on']:3d}) | {r['retrace_m']:6.0f} | {r['straight_home_m']:5.0f} | "
              f"{r['pace']:.3f} {r['pace_source'][:4]} {'at least' if r['at_least'] else 'about   '} | "
              f"{fmt(r['pace_only_s'])} / {fmt(with_s)} ({a['climb_m']:.0f}+{a['descent_m']:.0f}) | "
              f"{fmt(r['back_elapsed_s'])}, {fmt(r['back_moving_s'])}, {r['back_path_m']:5.0f} | "
              f"{rm if rm is None else round(rm, 2)}, {rw if rw is None else round(rw, 2)}, {re_ if re_ is None else round(re_, 2)}")

    # Decomposition at the far point and over the homeward phase (every kept point from far to end-1).
    fd = decomposition(rows[far])
    print(f"\n  Decomposition at the far point ({rows[far]['min_in']:.1f} min in):")
    for k, v in fd.items():
        print(f"    {k}: {v:.4f}" if isinstance(v, float) else f"    {k}: {v}")
    hw_rows = [rows[i] for i in range(far, n - 1)]
    decs = [decomposition(r) for r in hw_rows]
    decs = [d for d in decs if d is not None]

    def summary(key):
        vals = sorted(d[key] for d in decs if d.get(key) is not None)
        if not vals:
            return None
        return dict(n=len(vals), min=vals[0], q1=vals[len(vals) // 4], median=vals[len(vals) // 2], q3=vals[3 * len(vals) // 4], max=vals[-1])
    hw_summary = {k: summary(k) for k in ("route_over_walked", "route_over_retrace", "retrace_over_walked", "walked_over_moving_m",
                                           "return_speed_over_pace", "effect2", "pace_only_over_moving", "pace_only_over_elapsed", "identity_residual")}
    print(f"\n  Homeward phase, every kept point from the far point to the last-but-one ({len(decs)} points): median [min, max]")
    for k, s in hw_summary.items():
        if s:
            print(f"    {k}: {s['median']:.3f} [{s['min']:.3f}, {s['max']:.3f}]")
    short_share = sum(1 for d in decs if d["pace_only_over_moving"] < 1.0) / len(decs) if decs else None
    print(f"    share of homeward points where pace-only < actual moving (reads short): {short_share:.2f}")
    # With allowance, at the homeward marks only (heights are computed there).
    hw_marks = [l for l in table if l["homeward"] and l["ratio_with_moving"] is not None]
    short_with = sum(1 for l in hw_marks if l["ratio_with_moving"] < 1.0)
    print(f"    homeward 5-min marks with allowance reading short: {short_with} of {len(hw_marks)}")
    # First half of the homeward phase (by index), where P3 says A's allowance does not close the gap.
    first_half = [l for l in hw_marks if l["i"] <= (far + n - 1) // 2]
    print(f"    of those in the first half of the homeward phase: {sum(1 for l in first_half if l['ratio_with_moving'] < 1.0)} of {len(first_half)}")

    # Predictability: candidates at the far point and over the homeward phase.
    def candidates(r, a_s):
        p = r["pace"]
        c = dict(C1_route=r["route_m"] / p, C2_route_plus_allowance=r["route_m"] / p + (a_s or 0.0), C3_retrace=r["retrace_m"] / p,
                 C4_straight=r["straight_home_m"] / p)
        sr = r["stop_ratio_so_far"] or 1.0
        out = {}
        for k, v in c.items():
            out[k + "_over_moving"] = v / r["back_moving_s"] if r["back_moving_s"] > 0 else None
            out[k + "_x_stop_ratio_over_elapsed"] = v * sr / r["back_elapsed_s"] if r["back_elapsed_s"] > 0 else None
        out["stop_ratio_so_far"] = sr
        out["stop_ratio_back"] = r["back_elapsed_s"] / r["back_moving_s"] if r["back_moving_s"] > 0 else None
        return out
    far_c = candidates(rows[far], allowance[far]["allowance_s"])
    print("\n  Predictability at the far point: predicted / actual")
    for k, v in far_c.items():
        print(f"    {k}: {v:.3f}" if isinstance(v, float) else f"    {k}: {v}")
    hw_c = [candidates(r, None) for r in hw_rows if r["back_moving_s"] > 0]
    hw_c_summary = {}
    for k in hw_c[0]:
        vals = sorted(c[k] for c in hw_c if c.get(k) is not None)
        hw_c_summary[k] = dict(median=vals[len(vals) // 2], min=vals[0], max=vals[-1]) if vals else None
    print("  Over the homeward phase (C2 without allowance there: heights only at marks): median [min, max]")
    for k, s in hw_c_summary.items():
        if s:
            print(f"    {k}: {s['median']:.3f} [{s['min']:.3f}, {s['max']:.3f}]")

    # Pace source and label timeline (P6).
    first_measured = next((r for r in rows if r["pace_source"] != "DEFAULT"), None)
    first_about = next((r for r in rows if not r["at_least"]), None)
    print(f"\n  Pace: first measured at {first_measured['min_in']:.1f} min in ({first_measured['pace_source']}); label 'about' from {first_about['min_in'] if first_about else None} min in; "
          f"far point at {rows[far]['min_in']:.1f} min; stop ratio so far at far point {rows[far]['stop_ratio_so_far']:.2f}, on the way back {rows[far]['back_elapsed_s']/rows[far]['back_moving_s']:.2f}")
    # Margin check at the far point.
    fw = rows[far]["pace_only_s"] + allowance[far]["allowance_s"]
    print(f"  From the far point: walk back with allowance {fmt(fw)}; actual elapsed back {fmt(rows[far]['back_elapsed_s'])}; "
          f"home before sunset if left at start-back-by with a 60 min margin: {rows[far]['back_elapsed_s'] < fw + DARKNESS_MARGIN_S} (slack {fmt(fw + DARKNESS_MARGIN_S - rows[far]['back_elapsed_s'])})")
    # Climb variation across pre-far marks vs far (P7).
    pre = [allowance[i]["climb_m"] + allowance[i]["descent_m"] for i in marks if i < far and i > 0]
    farc = allowance[far]["climb_m"] + allowance[far]["descent_m"]
    print(f"  Climb+descent along the route at pre-far marks: {[round(x, 1) for x in pre]}; at the far point {farc:.1f}")

    R = dict(label=label, kept=n, excluded=excluded, origin_leg_m=origin_leg, far_index=far, far_min_in=rows[far]["min_in"], duration_min=rows[-1]["min_in"],
             controls=cr, controls_all_ok=all_ok, table=table, far_decomposition=fd, homeward_summary=hw_summary, homeward_points=len(decs),
             homeward_short_share_pace_only=short_share, homeward_marks_short_with_allowance=[short_with, len(hw_marks)],
             far_candidates=far_c, homeward_candidates=hw_c_summary,
             first_measured_min=first_measured["min_in"] if first_measured else None, first_about_min=first_about["min_in"] if first_about else None,
             pre_far_route_climb_total_m=pre, far_route_climb_total_m=farc)
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, f"{label}-reconcile.json"), "w") as f:
        json.dump(R, f, indent=1, default=str)
    with open(os.path.join(out_dir, f"{label}-points.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["i", "min_in", "route_m", "joins_on", "retrace_m", "straight_home_m", "pace", "pace_source", "at_least", "pace_only_s",
                    "back_elapsed_s", "back_moving_s", "back_path_m", "back_moving_m", "stop_ratio_so_far", "homeward"])
        for r in rows:
            w.writerow([r["i"], round(r["min_in"], 2), round(r["route_m"], 1), r["joins_on"], round(r["retrace_m"], 1), round(r["straight_home_m"], 1),
                        round(r["pace"], 4), r["pace_source"], r["at_least"], round(r["pace_only_s"], 1), round(r["back_elapsed_s"], 1),
                        round(r["back_moving_s"], 1), round(r["back_path_m"], 1), round(r["back_moving_m"], 1),
                        None if r["stop_ratio_so_far"] is None else round(r["stop_ratio_so_far"], 3), r["i"] >= far])
    return R


def main():
    db = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_DB
    out_dir = sys.argv[2] if len(sys.argv) > 2 else DEFAULT_OUT
    for label, prefix in RECORDINGS:
        kept, excluded, origin = load(db, prefix)
        analyse(label, kept, excluded, origin, out_dir)
    print("\nFigures only. Nothing from the copy is written anywhere but OUT_DIR (figures), and nothing in it is a coordinate or a time of day.")


if __name__ == "__main__":
    main()
