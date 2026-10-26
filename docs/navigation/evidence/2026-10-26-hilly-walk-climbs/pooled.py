#!/usr/bin/env python3 -I
"""Pool the legs of several recordings (the per-leg CSVs analyze.py writes: grade, distance,
moving metres and seconds, no coordinates) and fit the time model once over all of them:
seconds per metre = a + b*(ascent per metre) + c*(descent per metre), weighted by leg
distance, with a 2,000-resample bootstrap. Also the pace by grade bin over the pooled legs
and what the model says a return leg's climb costs in equivalent flat path.

Usage: python3 -I pooled.py LEGS_CSV [LEGS_CSV ...]
"""
import csv
import math
import sys

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from analyze import wls, bootstrap, grade_of, speed_of, GRADE_BINS  # noqa: E402
from trackmath import percentile  # noqa: E402


def main():
    legs = []
    for path in sys.argv[1:]:
        with open(path, newline="") as f:
            for row in csv.DictReader(f):
                if row["dh_m"] in ("", "None") or row["speed_mps"] in ("", "None"):
                    continue
                legs.append(dict(dist=float(row["dist_m"]), dh=float(row["dh_m"]),
                                 moving_m=float(row["moving_m"]), moving_s=float(row["moving_s"])))
    legs = [l for l in legs if l["moving_s"] > 0 and l["moving_m"] > 0 and l["dist"] > 0]
    print(f"pooled usable legs: {len(legs)}; path {sum(l['dist'] for l in legs):.0f} m; "
          f"moving {sum(l['moving_m'] for l in legs):.0f} m in {sum(l['moving_s'] for l in legs):.0f} s")
    print("grade bin        legs   dist(m)  weighted(m/s)   q1   median   q3")
    for lo, hi in GRADE_BINS:
        sel = [l for l in legs if lo <= grade_of(l) < hi]
        if not sel:
            print(f"{lo:>6} to {hi:<6}   0")
            continue
        sp = sorted(speed_of(l) for l in sel)
        w = sum(l["moving_m"] for l in sel) / sum(l["moving_s"] for l in sel)
        lo_s = "  -inf" if lo == -math.inf else f"{lo*100:+5.0f}%"
        hi_s = "inf   " if hi == math.inf else f"{hi*100:+5.0f}%"
        print(f"{lo_s} to {hi_s} {len(sel):4d} {sum(l['dist'] for l in sel):9.0f} {w:13.3f} "
              f"{percentile(sp, 0.25):6.3f} {percentile(sp, 0.5):7.3f} {percentile(sp, 0.75):6.3f}")
    for with_descent in (True, False):
        c = wls(legs, with_descent)
        ci, ratio = bootstrap(legs, with_descent)
        label = "flat + up + down" if with_descent else "flat + up"
        print(f"model {label}: a={c[0]:.3f} s/m (flat {1/c[0]:.3f} m/s), b={c[1]:.2f} s per m climbed"
              + (f", c={c[2]:.2f} s per m descended" if with_descent else ""))
        print(f"  90% CI: a {ci[0][0]:.3f}..{ci[0][2]:.3f}; b {ci[1][0]:.2f}..{ci[1][2]:.2f}"
              + (f"; c {ci[2][0]:.2f}..{ci[2][2]:.2f}" if with_descent else ""))
        print(f"  b/a = {c[1]/c[0]:.2f} m of flat path per m climbed (90% CI {ratio[0]:.2f}..{ratio[2]:.2f}); Naismith 8.33")
        for climb in (50, 100, 200):
            print(f"  {climb} m of climb costs {climb*c[1]:.0f} s = {climb*c[1]/c[0]:.0f} m of flat path (CI {climb*ci[1][0]:.0f}..{climb*ci[1][2]:.0f} s)")


if __name__ == "__main__":
    main()
