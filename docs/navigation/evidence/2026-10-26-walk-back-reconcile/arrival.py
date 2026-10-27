#!/usr/bin/env python3 -I
"""Two diagnostics added AFTER reconcile.py's first run, recorded as such in the report (section 2,
"Corrections after seeing the data"):

1. The end of the return. reconcile.py measured "back from here" to the recording's last kept point.
   On A the walker was within 10 m of the start 20 minutes before the recording ended. Here the return
   is also measured to FIRST ARRIVAL after the far point: the first kept point within R metres of the
   origin waypoint (R = 15 m, ARRIVAL_MIN_RADIUS_METERS in NavigationReadout.kt:71, and 30 m as a
   sensitivity), and the far-point comparison is redone against that end.

2. Why B's route home grew the whole way back. For every homeward point (far point to end), the
   straight-line distance to the nearest OUTWARD point (indices 0..far): a few metres means the same
   path offset by GPS or trail width (an epsilon question); hundreds means a loop (no epsilon joins it).
   And the route home at the far point and at the end for epsilon = 10, 15, 20, 30, 50 m, to show what
   raising epsilon would have done on this walk (which reopens the 2026-09-07 ruling, TrackSelfJoin.kt:9-20).

Figures only. Usage: python3 -I arrival.py [DB_PATH]
"""
import json
import os
import sys

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from trackmath import LAT, LNG, TS, haversine_m, moving_pace, barometric_heights, climb_descent  # noqa: E402
from reconcile import load, all_joins, route_home, fmt, RECORDINGS, DEFAULT_DB, DEFAULT_OUT, ELEVATION_HYSTERESIS_METERS, CLIMB_ALLOWANCE_SECONDS_PER_METER, MOVING_SPEED_FLOOR_METERS_PER_SECOND  # noqa: E402

ARRIVAL_RADII = (15.0, 30.0)
EPSILONS = (10.0, 15.0, 20.0, 30.0, 50.0)


def back_from(kept, i, end):
    """Elapsed, moving time, path and moving metres from kept[i] to kept[end]."""
    elapsed = (kept[end][TS] - kept[i][TS]) / 1000.0
    mov_s = mov_m = path = 0.0
    for k in range(i, end):
        d = haversine_m(kept[k][LAT], kept[k][LNG], kept[k + 1][LAT], kept[k + 1][LNG])
        dt = (kept[k + 1][TS] - kept[k][TS]) / 1000.0
        path += d
        if dt > 0 and d / dt >= MOVING_SPEED_FLOOR_METERS_PER_SECOND:
            mov_s += dt
            mov_m += d
    return dict(elapsed_s=elapsed, moving_s=mov_s, path_m=path, moving_m=mov_m)


def main():
    db = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_DB
    out = {}
    for label, prefix in RECORDINGS:
        kept, excluded, origin = load(db, prefix)
        n = len(kept)
        t0 = kept[0][TS]
        home = origin if origin else (kept[0][LAT], kept[0][LNG])
        far = max(range(n), key=lambda i: haversine_m(kept[0][LAT], kept[0][LNG], kept[i][LAT], kept[i][LNG]))
        legs = [haversine_m(a[LAT], a[LNG], b[LAT], b[LNG]) for a, b in zip(kept, kept[1:])]
        joins10 = all_joins(kept, 10.0)
        R = dict(label=label, far_index=far, far_min_in=(kept[far][TS] - t0) / 60000.0, end_index=n - 1, end_min_in=(kept[-1][TS] - t0) / 60000.0,
                 end_to_origin_m=haversine_m(kept[-1][LAT], kept[-1][LNG], home[0], home[1]))
        print(f"\n=== Recording {label}: far point {far} at {R['far_min_in']:.1f} min; last point at {R['end_min_in']:.1f} min, {R['end_to_origin_m']:.0f} m from the origin")

        # 1. First arrival after the far point.
        pace_far = moving_pace(kept[: far + 1])["speed"]
        route_m, route, joins_on = route_home(legs, joins10, far)
        h = barometric_heights(kept[: far + 1])
        up, down = climb_descent([h[k] for k in route], ELEVATION_HYSTERESIS_METERS)
        allow = (up + down) * CLIMB_ALLOWANCE_SECONDS_PER_METER
        origin_leg = haversine_m(kept[0][LAT], kept[0][LNG], home[0], home[1])
        pace_only = (route_m + origin_leg) / pace_far
        R["far"] = dict(route_m=route_m, pace=pace_far, pace_only_s=pace_only, allowance_s=allow, with_allowance_s=pace_only + allow)
        R["arrivals"] = {}
        for radius in ARRIVAL_RADII:
            arr = next((i for i in range(far + 1, n) if haversine_m(kept[i][LAT], kept[i][LNG], home[0], home[1]) <= radius), None)
            if arr is None:
                R["arrivals"][str(radius)] = None
                print(f"  first arrival within {radius:.0f} m of the origin after the far point: none (closest after far point "
                      f"{min(haversine_m(kept[i][LAT], kept[i][LNG], home[0], home[1]) for i in range(far + 1, n)):.0f} m)")
                continue
            b = back_from(kept, far, arr)
            R["arrivals"][str(radius)] = dict(index=arr, min_in=(kept[arr][TS] - t0) / 60000.0, **b,
                                              pace_only_over_moving=pace_only / b["moving_s"], with_over_moving=(pace_only + allow) / b["moving_s"],
                                              pace_only_over_elapsed=pace_only / b["elapsed_s"], route_over_walked=route_m / b["path_m"],
                                              walked_over_moving_m=b["path_m"] / b["moving_m"], return_speed_over_pace=(b["moving_m"] / b["moving_s"]) / pace_far,
                                              after_arrival_elapsed_s=(kept[-1][TS] - kept[arr][TS]) / 1000.0,
                                              after_arrival_path_m=sum(legs[arr:]))
            a = R["arrivals"][str(radius)]
            print(f"  first arrival within {radius:.0f} m: point {arr} at {a['min_in']:.1f} min; far point to there: elapsed {fmt(b['elapsed_s'])}, moving {fmt(b['moving_s'])}, "
                  f"path {b['path_m']:.0f} m (moving metres {b['moving_m']:.0f}); then {fmt(a['after_arrival_elapsed_s'])} and {a['after_arrival_path_m']:.0f} m more before the recording ended")
            print(f"    app at the far point: route {route_m:.0f} m, pace {pace_far:.3f}, pace-only {fmt(pace_only)}, with allowance {fmt(pace_only + allow)}; "
                  f"pace-only/moving {a['pace_only_over_moving']:.2f}, with-allowance/moving {a['with_over_moving']:.2f}, pace-only/elapsed {a['pace_only_over_elapsed']:.2f}; "
                  f"route/walked {a['route_over_walked']:.2f}, walked/moving-m {a['walked_over_moving_m']:.2f}, return speed/pace {a['return_speed_over_pace']:.2f}")

        # 2. Nearest outward point for each homeward point; epsilon sweep.
        near = []
        for i in range(far + 1, n):
            near.append(min(haversine_m(kept[i][LAT], kept[i][LNG], kept[j][LAT], kept[j][LNG]) for j in range(0, far + 1)))
        s = sorted(near)
        q = lambda p: s[min(int(p * len(s)), len(s) - 1)]  # noqa: E731
        R["nearest_outward_m"] = dict(n=len(s), min=s[0], q1=q(0.25), median=q(0.5), q3=q(0.75), p90=q(0.9), max=s[-1],
                                      share_within_10=sum(1 for x in s if x <= 10) / len(s), share_within_20=sum(1 for x in s if x <= 20) / len(s),
                                      share_within_50=sum(1 for x in s if x <= 50) / len(s), share_over_100=sum(1 for x in s if x > 100) / len(s))
        nm = R["nearest_outward_m"]
        print(f"  homeward points' distance to the nearest outward point ({nm['n']} points): min {nm['min']:.1f}, q1 {nm['q1']:.1f}, median {nm['median']:.1f}, q3 {nm['q3']:.1f}, p90 {nm['p90']:.1f}, max {nm['max']:.1f} m; "
              f"within 10 m {nm['share_within_10']:.2f}, within 20 m {nm['share_within_20']:.2f}, within 50 m {nm['share_within_50']:.2f}, over 100 m {nm['share_over_100']:.2f}")
        # Along the way back in tenths of the homeward phase: median nearest-outward distance per tenth.
        tenths = []
        for t in range(10):
            lo = int(t * len(near) / 10)
            hi = int((t + 1) * len(near) / 10)
            seg = sorted(near[lo:hi])
            tenths.append(seg[len(seg) // 2] if seg else None)
        R["nearest_outward_by_tenth_m"] = tenths
        print(f"  median nearest-outward distance by tenth of the way back: {[None if x is None else round(x, 0) for x in tenths]}")

        R["epsilon"] = {}
        for eps in EPSILONS:
            joins = all_joins(kept, eps)
            rf, _, jf = route_home(legs, joins, far)
            re_, _, je = route_home(legs, joins, n - 1)
            # at the point three-quarters of the way home (by index), too
            mid = far + 3 * (n - 1 - far) // 4
            rm, _, jm = route_home(legs, joins, mid)
            straight_mid = haversine_m(kept[mid][LAT], kept[mid][LNG], home[0], home[1])
            R["epsilon"][str(eps)] = dict(far_route_m=rf, far_joins=jf, end_route_m=re_, end_joins=je, threeq_index=mid, threeq_route_m=rm, threeq_joins=jm, threeq_straight_m=straight_mid)
            print(f"  epsilon {eps:4.0f} m: route home at the far point {rf:6.0f} m ({jf} joins); three-quarters of the way home (point {mid}, {straight_mid:.0f} m from the origin) {rm:6.0f} m ({jm} joins); at the end ({R['end_to_origin_m']:.0f} m from the origin) {re_:6.0f} m ({je} joins)")
        out[label] = R

    os.makedirs(DEFAULT_OUT, exist_ok=True)
    with open(os.path.join(DEFAULT_OUT, "arrival.json"), "w") as f:
        json.dump(out, f, indent=1, default=str)
    print("\nFigures only.")


if __name__ == "__main__":
    main()
