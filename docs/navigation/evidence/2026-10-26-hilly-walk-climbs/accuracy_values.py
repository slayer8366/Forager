#!/usr/bin/env python3 -I
"""Reported horizontal accuracy, exact values (for -1623's question E), figures only.

For each track: the distinct accuracyMeters values with counts (top 8), and a proxy test of
whether the value carries information: kept points split at 6 m into a "tight" and a "loose"
cluster, and for each cluster the median |raw GPS altitude - barometric height| and the
median |Doppler speed - differenced speed| of the interval the point ends (moving intervals
only). If the tight cluster is no better on either proxy, the reported value does not
distinguish better fixes from worse ones on this phone.

Usage: python3 -I accuracy_values.py DB_PATH TRACK_ID_PREFIX [TRACK_ID_PREFIX ...]
"""
import sqlite3
import sys
from collections import Counter

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from trackmath import (  # noqa: E402
    LAT, LNG, ALT, ACC, TS, SPEED, haversine_m, is_network_rule, choose_heights, median,
    MOVING_SPEED_FLOOR_METERS_PER_SECOND,
)

SPLIT_M = 6.0


def main():
    db = sys.argv[1]
    conn = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
    for prefix in sys.argv[2:]:
        (tid,) = conn.execute("SELECT id FROM tracks WHERE id LIKE ?", (prefix + "%",)).fetchone()
        rows = conn.execute(
            "SELECT lat, lng, altitude, accuracyMeters, timestampEpochMillis, speedMetersPerSecond, "
            "speedAccuracyMetersPerSecond, pressureHpa FROM track_points WHERE trackId = ? "
            "ORDER BY timestampEpochMillis ASC", (tid,)).fetchall()
        kept = [r for r in rows if not is_network_rule(r[TS])]
        counts = Counter(round(float(r[ACC]), 4) for r in kept if r[ACC] is not None)
        print(f"track {tid[:8]}: {len(kept)} kept points, {len(counts)} distinct accuracy values")
        for value, n in counts.most_common(8):
            print(f"  {value:8.4f} m: {n:4d} ({100.0*n/len(kept):.1f} %)")
        heights, which = choose_heights(kept)
        tight, loose = {"alt": [], "spd": []}, {"alt": [], "spd": []}
        for i, p in enumerate(kept):
            if p[ACC] is None:
                continue
            bucket = tight if float(p[ACC]) < SPLIT_M else loose
            if p[ALT] is not None and heights[i] is not None:
                bucket["alt"].append(abs(p[ALT] - heights[i]))
            if i > 0 and p[SPEED] is not None:
                prev = kept[i - 1]
                dt = (p[TS] - prev[TS]) / 1000.0
                if dt > 0:
                    v = haversine_m(prev[LAT], prev[LNG], p[LAT], p[LNG]) / dt
                    if v >= MOVING_SPEED_FLOOR_METERS_PER_SECOND:
                        bucket["spd"].append(abs(float(p[SPEED]) - v))
        for name, b in (("tight (<6 m)", tight), ("loose (>=6 m)", loose)):
            print(f"  {name}: n_alt={len(b['alt'])} median |raw alt - {which} height| = {median(b['alt']):.2f} m; "
                  f"n_spd={len(b['spd'])} median |Doppler - differenced| = {median(b['spd']):.3f} m/s")


if __name__ == "__main__":
    main()
