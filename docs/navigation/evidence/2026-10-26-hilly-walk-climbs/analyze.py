#!/usr/bin/env python3 -I
"""Climbs, grades, pace against grade, altitude noise and reported accuracy for one
track in a read-only copy of Forager's database. Method: section 1 of
docs/navigation/2026-10-26-hilly-walk-climbs.md, written before this ran.

Prints figures only (no coordinates, names or times of day) and writes the same
figures as JSON plus a per-leg CSV (grade, distance, time, speed; no coordinates)
to OUT_DIR, which defaults to the scratch path.

Usage: python3 -I analyze.py TRACK_ID_PREFIX [DB_PATH] [OUT_DIR]
"""
import csv
import json
import math
import os
import random
import sqlite3
import sys

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from trackmath import (  # noqa: E402
    LAT, LNG, ALT, ACC, TS, SPEED, PRES,
    haversine_m, is_network_rule, smoothed_gps, barometric_heights, choose_heights,
    climb_descent, moving_pace, median, percentile,
    MOVING_SPEED_FLOOR_METERS_PER_SECOND,
)

DEFAULT_DB = "/mnt/work/scratch/s26-hilly-walk/work/forager.db"
DEFAULT_OUT = "/mnt/work/scratch/s26-hilly-walk/out"
LEG_METERS = 50.0
GRADE_BINS = [(-math.inf, -0.15), (-0.15, -0.08), (-0.08, -0.03), (-0.03, 0.03),
              (0.03, 0.08), (0.08, 0.15), (0.15, math.inf)]
BOOTSTRAP_N = 2000
OUT_AND_BACK_METERS = 150.0


def load(db, prefix):
    conn = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
    ids = [r[0] for r in conn.execute("SELECT id FROM tracks WHERE id LIKE ?", (prefix + "%",))]
    if len(ids) != 1:
        sys.exit(f"track prefix {prefix!r} matches {len(ids)} tracks")
    rows = conn.execute(
        "SELECT lat, lng, altitude, accuracyMeters, timestampEpochMillis, "
        "speedMetersPerSecond, speedAccuracyMetersPerSecond, pressureHpa "
        "FROM track_points WHERE trackId = ? ORDER BY timestampEpochMillis ASC", (ids[0],)
    ).fetchall()
    return ids[0], rows


def cumulative_distance(points):
    cum = [0.0]
    for a, b in zip(points, points[1:]):
        cum.append(cum[-1] + haversine_m(a[LAT], a[LNG], b[LAT], b[LNG]))
    return cum


def legs_of(points, heights, leg_m=LEG_METERS):
    """Cut the path into legs of at least leg_m horizontal metres. Each leg: dist, dh (chosen
    heights, last minus first, None if either missing), moving_m, moving_s, doppler weighted
    speed, total_s, start/end index."""
    legs = []
    start = 0
    acc = 0.0
    seg_d = [haversine_m(a[LAT], a[LNG], b[LAT], b[LNG]) for a, b in zip(points, points[1:])]
    for i, d in enumerate(seg_d):
        acc += d
        if acc >= leg_m or i == len(seg_d) - 1:
            end = i + 1
            moving_m = moving_s = 0.0
            dop_sum = dop_s = 0.0
            for j in range(start, end):
                dt = (points[j + 1][TS] - points[j][TS]) / 1000.0
                if dt <= 0:
                    continue
                if seg_d[j] / dt >= MOVING_SPEED_FLOOR_METERS_PER_SECOND:
                    moving_m += seg_d[j]
                    moving_s += dt
                    s = points[j + 1][SPEED]
                    if s is not None and s >= MOVING_SPEED_FLOOR_METERS_PER_SECOND:
                        dop_sum += float(s) * dt
                        dop_s += dt
            h0, h1 = heights[start], heights[end]
            legs.append(dict(
                start=start, end=end, dist=acc,
                dh=(h1 - h0) if (h0 is not None and h1 is not None) else None,
                moving_m=moving_m, moving_s=moving_s,
                doppler=(dop_sum / dop_s) if dop_s else None,
                total_s=(points[end][TS] - points[start][TS]) / 1000.0,
            ))
            start = end
            acc = 0.0
    return legs


def usable_legs(legs):
    return [l for l in legs if l["dh"] is not None and l["moving_s"] > 0 and l["moving_m"] > 0 and l["dist"] > 0]


def grade_of(leg):
    return leg["dh"] / leg["dist"]


def speed_of(leg):
    return leg["moving_m"] / leg["moving_s"]


def steepest(points, heights, cum, window_m):
    """Largest and smallest grade over any stretch of path at least window_m long, ending at
    each point (the shortest stretch reaching back at least window_m)."""
    best_up = -math.inf
    best_down = math.inf
    j = 0
    for i in range(len(points)):
        while cum[i] - cum[j] > window_m and j < i:
            j += 1
        # j is the first index within window_m of i; use j-1 so the stretch is at least window_m
        k = j - 1 if j > 0 and cum[i] - cum[j - 1] >= window_m else None
        if k is None or heights[i] is None or heights[k] is None:
            continue
        d = cum[i] - cum[k]
        if d <= 0:
            continue
        g = (heights[i] - heights[k]) / d
        best_up = max(best_up, g)
        best_down = min(best_down, g)
    return (best_up if best_up > -math.inf else None, best_down if best_down < math.inf else None)


def wls(legs, with_descent=True):
    """Weighted least squares of seconds per metre on [1, up_rate, down_rate], weights = dist.
    Returns (a, b, c) or (a, b, None). Solves the normal equations directly."""
    rows = []
    for l in legs:
        g = grade_of(l)
        up = max(g, 0.0)
        down = max(-g, 0.0)
        y = l["moving_s"] / l["moving_m"]
        x = [1.0, up, down] if with_descent else [1.0, up]
        rows.append((x, y, l["dist"]))
    k = 3 if with_descent else 2
    xtx = [[0.0] * k for _ in range(k)]
    xty = [0.0] * k
    for x, y, w in rows:
        for i in range(k):
            xty[i] += w * x[i] * y
            for j in range(k):
                xtx[i][j] += w * x[i] * x[j]
    return solve(xtx, xty)


def solve(a, b):
    n = len(b)
    m = [row[:] + [b[i]] for i, row in enumerate(a)]
    for col in range(n):
        piv = max(range(col, n), key=lambda r: abs(m[r][col]))
        if abs(m[piv][col]) < 1e-12:
            return None
        m[col], m[piv] = m[piv], m[col]
        for r in range(n):
            if r != col:
                f = m[r][col] / m[col][col]
                for c in range(col, n + 1):
                    m[r][c] -= f * m[col][c]
    return [m[i][n] / m[i][i] for i in range(n)]


def bootstrap(legs, with_descent, n=BOOTSTRAP_N, seed=1):
    rnd = random.Random(seed)
    coefs = []
    for _ in range(n):
        sample = [legs[rnd.randrange(len(legs))] for _ in legs]
        c = wls(sample, with_descent)
        if c is not None:
            coefs.append(c)
    out = []
    for i in range(len(coefs[0])):
        v = sorted(c[i] for c in coefs)
        out.append((percentile(v, 0.05), percentile(v, 0.5), percentile(v, 0.95)))
    ratios = sorted(c[1] / c[0] for c in coefs if c[0] > 0)
    return out, (percentile(ratios, 0.05), percentile(ratios, 0.5), percentile(ratios, 0.95))


def predict_seconds(coefs, legs):
    total = 0.0
    for l in legs:
        g = grade_of(l)
        spm = coefs[0] + coefs[1] * max(g, 0.0) + (coefs[2] * max(-g, 0.0) if len(coefs) > 2 else 0.0)
        total += spm * l["moving_m"]
    return total


def accuracy_stats(values):
    v = sorted(float(x) for x in values if x is not None)
    if not v:
        return dict(count=0)
    return dict(
        count=len(v), missing=0, distinct=len(set(v)), min=v[0], p50=percentile(v, 0.5),
        p90=percentile(v, 0.9), p95=percentile(v, 0.95), max=v[-1],
        share_above_10=sum(1 for x in v if x > 10) / len(v),
        share_above_20=sum(1 for x in v if x > 20) / len(v),
    )


def main():
    prefix = sys.argv[1]
    db = sys.argv[2] if len(sys.argv) > 2 else DEFAULT_DB
    out_dir = sys.argv[3] if len(sys.argv) > 3 else DEFAULT_OUT
    os.makedirs(out_dir, exist_ok=True)
    tid, rows = load(db, prefix)
    R = {"track_id_prefix": tid[:8]}

    kept = [r for r in rows if not is_network_rule(r[TS])]
    excluded = [r for r in rows if is_network_rule(r[TS])]
    R["stored_points"] = len(rows)
    R["kept_points"] = len(kept)
    R["excluded_points"] = len(excluded)
    R["excluded_share"] = len(excluded) / len(rows) if rows else None

    # 1. distance, duration, moving
    cum = cumulative_distance(kept)
    R["distance_m"] = cum[-1]
    R["duration_s"] = (kept[-1][TS] - kept[0][TS]) / 1000.0
    pace = moving_pace(kept)
    R["moving_pace"] = {k: v for k, v in pace.items()}
    R["interval_s"] = dict(
        median=median([(b[TS] - a[TS]) / 1000.0 for a, b in zip(kept, kept[1:])]),
        p90=percentile(sorted((b[TS] - a[TS]) / 1000.0 for a, b in zip(kept, kept[1:])), 0.9),
    )

    # 2. heights, three series
    raw = [p[ALT] for p in kept]
    gps_s = smoothed_gps(kept)
    baro_stats = {}
    with_pressure = sum(1 for p in kept if p[PRES] is not None)
    R["altitude_coverage"] = sum(1 for a in raw if a is not None) / len(kept)
    R["pressure_coverage"] = with_pressure / len(kept)
    baro = barometric_heights(kept, baro_stats) if with_pressure else None
    chosen, which = choose_heights(kept)
    R["app_height_source"] = which
    R["baro_steps_taken_out"] = baro_stats
    series = {"raw_gps": raw, "app_smoothed_gps": gps_s}
    if baro is not None:
        series["app_barometric"] = baro
        # Diagnostic beyond the app: where the points without pressure fall (each one breaks a
        # barometric run and resets the climb reference, so climb across it is dropped), and the
        # same barometric pipeline with pressure linearly interpolated in time across them.
        missing = [i for i, p in enumerate(kept) if p[PRES] is None]
        runs = []
        for i in missing:
            if runs and i == runs[-1][-1] + 1:
                runs[-1].append(i)
            else:
                runs.append([i])
        R["pressure_gaps"] = dict(
            missing_points=len(missing), gap_runs=len(runs),
            positions_pct=[round(100.0 * r[0] / len(kept), 1) for r in runs],
            longest_gap_points=max((len(r) for r in runs), default=0),
            longest_gap_s=max(((kept[r[-1] + 1][TS] - kept[r[0] - 1][TS]) / 1000.0 for r in runs if r[0] > 0 and r[-1] + 1 < len(kept)), default=0),
            gaps_inside_walk=sum(1 for r in runs if r[0] > 0 and r[-1] + 1 < len(kept)),
        )
        bridged = []
        last_known = None
        for i, p in enumerate(kept):
            if p[PRES] is not None:
                bridged.append(p)
                last_known = i
                continue
            nxt = next((j for j in range(i + 1, len(kept)) if kept[j][PRES] is not None), None)
            if last_known is None or nxt is None:
                bridged.append(p)
                continue
            a, b = kept[last_known], kept[nxt]
            f = (p[TS] - a[TS]) / (b[TS] - a[TS]) if b[TS] != a[TS] else 0.0
            bridged.append(p[:PRES] + (a[PRES] + (b[PRES] - a[PRES]) * f,))
        series["barometric_bridged"] = barometric_heights(bridged)
        hb = [h for h in series["barometric_bridged"] if h is not None]
        R["height_range_bridged"] = (max(hb) - min(hb)) if hb else None
    R["climb"] = {}
    for name, h in series.items():
        R["climb"][name] = {}
        for thr in (0.0, 4.0, 10.0):
            up, down = climb_descent(h, thr)
            R["climb"][name][f"hyst_{int(thr)}m"] = dict(up=up, down=down)
    R["net_height_change_chosen"] = (chosen[-1] - chosen[0]) if (chosen[0] is not None and chosen[-1] is not None) else None
    hs = [h for h in chosen if h is not None]
    R["height_range_chosen"] = (max(hs) - min(hs)) if hs else None

    # 3. legs and grades
    legs = legs_of(kept, chosen)
    use = usable_legs(legs)
    R["legs"] = dict(total=len(legs), usable=len(use), dropped=len(legs) - len(use))
    R["steepest"] = {}
    for w in (100, 200, 500):
        up, down = steepest(kept, chosen, cum, w)
        R["steepest"][f"{w}m"] = dict(up=up, down=down)

    # 4. pace against grade
    bins = []
    for lo, hi in GRADE_BINS:
        sel = [l for l in use if lo <= grade_of(l) < hi]
        sp = sorted(speed_of(l) for l in sel)
        dop = [l["doppler"] for l in sel if l["doppler"] is not None]
        bins.append(dict(
            lo=lo if lo > -math.inf else None, hi=hi if hi < math.inf else None, legs=len(sel),
            dist_m=sum(l["dist"] for l in sel),
            weighted_speed=(sum(l["moving_m"] for l in sel) / sum(l["moving_s"] for l in sel)) if sel else None,
            q1=percentile(sp, 0.25) if sp else None, median=percentile(sp, 0.5) if sp else None,
            q3=percentile(sp, 0.75) if sp else None,
            doppler_mean=(sum(dop) / len(dop)) if dop else None, doppler_legs=len(dop),
        ))
    R["grade_bins"] = bins

    # 5. time model
    c3 = wls(use, True)
    c2 = wls(use, False)
    R["model"] = {}
    if c3:
        ci3, ratio3 = bootstrap(use, True)
        R["model"]["flat_up_down"] = dict(a=c3[0], b=c3[1], c=c3[2], b_over_a=c3[1] / c3[0],
                                          ci90=ci3, b_over_a_ci90=ratio3)
    if c2:
        ci2, ratio2 = bootstrap(use, False)
        R["model"]["flat_up"] = dict(a=c2[0], b=c2[1], b_over_a=c2[1] / c2[0], ci90=ci2, b_over_a_ci90=ratio2)
    R["naismith_b_over_a"] = (3600 / 600) / (3600 / 5000)

    # 6. out-and-back
    end_to_start = haversine_m(kept[0][LAT], kept[0][LNG], kept[-1][LAT], kept[-1][LNG])
    R["end_to_start_m"] = end_to_start
    if end_to_start <= OUT_AND_BACK_METERS:
        far_i = max(range(len(kept)), key=lambda i: haversine_m(kept[0][LAT], kept[0][LNG], kept[i][LAT], kept[i][LNG]))
        first, second = kept[:far_i + 1], kept[far_i:]
        h_first, h_second = chosen[:far_i + 1], chosen[far_i:]
        legs1 = usable_legs(legs_of(first, h_first))
        legs2 = usable_legs(legs_of(second, h_second))
        pace1 = moving_pace(first)
        pace2 = moving_pace(second)
        cum2 = cumulative_distance(second)
        actual2 = pace2["moving_millis"] / 1000.0
        flat_est = cum2[-1] / pace1["speed"] if pace1["speed"] else None
        flat_est_moving = pace2["moving_meters"] / pace1["speed"] if pace1["speed"] else None
        cf = wls(legs1, True)
        grade_est = predict_seconds(cf, legs2) if cf and legs1 else None
        cf2 = wls(legs1, False)
        grade_est2 = predict_seconds(cf2, legs2) if cf2 and legs1 else None
        R["out_and_back"] = dict(
            far_index=far_i, first_dist_m=cum[far_i], second_dist_m=cum2[-1],
            first_climb_4m=climb_descent(h_first, 4.0), second_climb_4m=climb_descent(h_second, 4.0),
            first_pace=pace1["speed"], first_pace_source=pace1["source"],
            second_actual_moving_s=actual2, second_moving_m=pace2["moving_meters"],
            flat_estimate_s_over_path=flat_est, flat_estimate_s_over_moving_m=flat_est_moving,
            grade_estimate_s=grade_est, grade_estimate_flat_up_only_s=grade_est2,
            first_half_model=cf, legs_first=len(legs1), legs_second=len(legs2),
        )
    else:
        R["out_and_back"] = None

    # 7. noise
    resid = [a - c for a, c in zip(raw, chosen) if a is not None and c is not None]
    steps = [abs(b - a) for a, b in zip(raw, raw[1:]) if a is not None and b is not None]
    if resid:
        mean_r = sum(resid) / len(resid)
        sd = math.sqrt(sum((x - mean_r) ** 2 for x in resid) / max(len(resid) - 1, 1))
        med_r = median(resid)
        mad = median([abs(x - med_r) for x in resid])
    else:
        sd = mad = mean_r = None
    R["noise"] = dict(
        resid_raw_minus_chosen=dict(n=len(resid), mean=mean_r, sd=sd, mad=mad),
        raw_step_abs=dict(n=len(steps), median=median(steps), p95=percentile(sorted(steps), 0.95), max=max(steps) if steps else None),
        raw_positive_step_sum=sum(max(b - a, 0.0) for a, b in zip(raw, raw[1:]) if a is not None and b is not None),
    )
    if baro is not None:
        resid_gb = [g - b for g, b in zip(gps_s, baro) if g is not None and b is not None]
        R["noise"]["smoothed_gps_minus_baro"] = dict(n=len(resid_gb), sd=math.sqrt(sum((x - sum(resid_gb) / len(resid_gb)) ** 2 for x in resid_gb) / max(len(resid_gb) - 1, 1)) if resid_gb else None,
                                                     min=min(resid_gb) if resid_gb else None, max=max(resid_gb) if resid_gb else None)

    # 8. reported horizontal accuracy
    R["accuracy"] = dict(
        gps_rule=accuracy_stats([p[ACC] for p in kept]),
        network_rule=accuracy_stats([p[ACC] for p in excluded]),
        gps_rule_missing=sum(1 for p in kept if p[ACC] is None),
        network_rule_missing=sum(1 for p in excluded if p[ACC] is None),
    )
    acc_vals = [float(p[ACC]) for p in kept if p[ACC] is not None]
    hist = {}
    for v in acc_vals:
        b = f"{int(v // 2) * 2}-{int(v // 2) * 2 + 2}"
        hist[b] = hist.get(b, 0) + 1
    R["accuracy"]["gps_rule_hist_2m"] = dict(sorted(hist.items(), key=lambda kv: int(kv[0].split("-")[0])))
    sp_acc = [float(p[6]) for p in kept if p[6] is not None]
    R["speed_accuracy_gps_rule"] = accuracy_stats(sp_acc)

    with open(os.path.join(out_dir, f"{tid[:8]}-figures.json"), "w") as f:
        json.dump(R, f, indent=1, default=str)
    with open(os.path.join(out_dir, f"{tid[:8]}-legs.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["leg", "dist_m", "dh_m", "grade", "moving_m", "moving_s", "speed_mps", "doppler_mps", "total_s"])
        for i, l in enumerate(legs):
            w.writerow([i, round(l["dist"], 1), None if l["dh"] is None else round(l["dh"], 2),
                        None if l["dh"] is None else round(grade_of(l), 4), round(l["moving_m"], 1),
                        round(l["moving_s"], 1), round(speed_of(l), 3) if l["moving_s"] > 0 else None,
                        None if l["doppler"] is None else round(l["doppler"], 3), round(l["total_s"], 1)])
    print(json.dumps(R, indent=1, default=str))


if __name__ == "__main__":
    main()
