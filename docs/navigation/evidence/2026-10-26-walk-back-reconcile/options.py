#!/usr/bin/env python3 -I
"""Figures for the options in section 4 of the report, from reconcile.py's outputs (A/B-reconcile.json,
A/B-points.csv) and arrival.json, so no figure in that section is hand arithmetic. Added after the first
run. Figures only.

Usage: python3 -I options.py [OUT_DIR]
"""
import csv
import json
import os
import sys

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from reconcile import fmt, DEFAULT_OUT  # noqa: E402

FACTORS = (1.0, 1.3, 2.3)   # 1.3 is B's walked/straight at its far point, 2.3 is A's to first arrival
LOOKBACK_MIN = 10.0


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_OUT
    arrival = json.load(open(os.path.join(out, "arrival.json")))
    for label in ("A", "B"):
        R = json.load(open(os.path.join(out, f"{label}-reconcile.json")))
        pts = list(csv.DictReader(open(os.path.join(out, f"{label}-points.csv"))))
        for p in pts:
            for k in p:
                if k not in ("pace_source", "at_least", "homeward", "stop_ratio_so_far"):
                    p[k] = float(p[k])
            p["stop_ratio_so_far"] = float(p["stop_ratio_so_far"]) if p["stop_ratio_so_far"] else None
        far = R["far_index"]
        fp = pts[far]
        print(f"\n=== Recording {label}")

        # O1: the loop detector's signal at each homeward 5-min mark: change in straight line home and in route home over the previous 10 min.
        print(f"  O1 detector at homeward marks: minutes in | straight home m (change over previous {LOOKBACK_MIN:.0f} min) | route home m (change) | signal (straight falling by >100 m and route not falling)")
        for line in R["table"]:
            if not line["homeward"]:
                continue
            t = line["min_in"]
            earlier = max((p for p in pts if p["min_in"] <= t - LOOKBACK_MIN), key=lambda p: p["min_in"], default=None)
            if earlier is None:
                continue
            ds = line["straight_home_m"] - earlier["straight_home_m"]
            dr = line["route_m"] - earlier["route_m"]
            signal = ds < -100 and dr >= 0
            floored = ""
            if signal:
                floored = " | floored figures: " + ", ".join(
                    f"k={k}: {fmt(line['straight_home_m'] * k / line['pace'])}" for k in FACTORS)
            print(f"    {t:6.1f} | {line['straight_home_m']:5.0f} ({ds:+6.0f}) | {line['route_m']:5.0f} ({dr:+6.0f}) | {'YES' if signal else 'no '} | as built {fmt(line['with_allowance_s'])}; actual to end: elapsed {fmt(line['back_elapsed_s'])}, moving {fmt(line['back_moving_s'])}{floored}")

        # O4: the lean removed: pace = all path so far / moving time so far.
        moving_so_far = fp["min_in"] * 60.0 / fp["stop_ratio_so_far"]
        eff = fp["retrace_m"] / moving_so_far
        fig = fp["route_m"] / eff
        arr = arrival[label]["arrivals"].get("15.0")
        actual_moving = arr["moving_s"] if arr else fp["back_moving_s"]
        actual_elapsed = arr["elapsed_s"] if arr else fp["back_elapsed_s"]
        print(f"  O4 at the far point: effective pace {eff:.3f} m/s (path so far {fp['retrace_m']:.0f} m / moving {fmt(moving_so_far)}) against the app's {fp['pace']:.3f}; "
              f"figure {fmt(fig)} against as-built pace-only {fmt(fp['pace_only_s'])}; actual walking to {'first arrival' if arr else 'the end'} {fmt(actual_moving)}: ratio {fig / actual_moving:.2f}")

        # O5: a stop allowance from the stop ratio so far.
        with_allow = fp["pace_only_s"] + R["table"][[l["i"] for l in R["table"]].index(far)]["allowance_s"]
        back_ratio = actual_elapsed / actual_moving
        print(f"  O5 at the far point: as built {fmt(with_allow)} x stop ratio so far {fp['stop_ratio_so_far']:.2f} = {fmt(with_allow * fp['stop_ratio_so_far'])} against actual elapsed {fmt(actual_elapsed)}: ratio {with_allow * fp['stop_ratio_so_far'] / actual_elapsed:.2f}; "
              f"the walker's stop ratio on the way back was {back_ratio:.2f}, {back_ratio / fp['stop_ratio_so_far']:.2f} of the way out's")

        # Label timeline.
        print(f"  label: 'at least' until {R['first_about_min']:.1f} min of {R['duration_min']:.1f}; pace measured from {R['first_measured_min']:.1f} min")
    print("\nFigures only.")


if __name__ == "__main__":
    main()
