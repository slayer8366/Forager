#!/usr/bin/env python3 -I
"""Added after the first run (recorded in the report as beyond the committed method): how often the
owner's recorded walks come back the way they went out. For every track in the copy longer than 20
minutes: duration, distance from the last point to the first, and over the homeward half (after the
point farthest from the start) the share of points within 10 m (epsilon) and within 50 m of some
outward point, plus the self-joined route home at the end against the straight line there. No dates,
no coordinates, no names: tracks are listed by duration only.

Usage: python3 -I loops.py [DB_PATH]
"""
import sqlite3
import sys

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from trackmath import LAT, LNG, TS, haversine_m, is_network_rule  # noqa: E402
from reconcile import all_joins, route_home, DEFAULT_DB  # noqa: E402

MIN_MINUTES = 20


def main():
    db = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_DB
    conn = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
    tracks = conn.execute("SELECT id FROM tracks WHERE importedAtEpochMillis IS NULL").fetchall()
    rows = []
    for (tid,) in tracks:
        pts = conn.execute("SELECT lat, lng, altitude, accuracyMeters, timestampEpochMillis, speedMetersPerSecond, "
                           "speedAccuracyMetersPerSecond, pressureHpa FROM track_points WHERE trackId = ? ORDER BY timestampEpochMillis", (tid,)).fetchall()
        kept = [p for p in pts if not is_network_rule(p[TS])]
        if len(kept) < 2:
            continue
        minutes = (kept[-1][TS] - kept[0][TS]) / 60000.0
        if minutes < MIN_MINUTES:
            continue
        n = len(kept)
        far = max(range(n), key=lambda i: haversine_m(kept[0][LAT], kept[0][LNG], kept[i][LAT], kept[i][LNG]))
        home = [min(haversine_m(kept[i][LAT], kept[i][LNG], kept[j][LAT], kept[j][LNG]) for j in range(far + 1)) for i in range(far + 1, n)]
        legs = [haversine_m(a[LAT], a[LNG], b[LAT], b[LNG]) for a, b in zip(kept, kept[1:])]
        joins = all_joins(kept)
        route_end, _, _ = route_home(legs, joins, n - 1)
        rows.append(dict(
            minutes=minutes, points=n, end_to_start_m=haversine_m(kept[0][LAT], kept[0][LNG], kept[-1][LAT], kept[-1][LNG]),
            far_m=haversine_m(kept[0][LAT], kept[0][LNG], kept[far][LAT], kept[far][LNG]), homeward_points=len(home),
            within_10=sum(1 for x in home if x <= 10) / len(home) if home else None,
            within_50=sum(1 for x in home if x <= 50) / len(home) if home else None,
            median_nearest_m=sorted(home)[len(home) // 2] if home else None,
            route_home_at_end_m=route_end, retrace_m=sum(legs), is_1024_outing=tid[:8] in ("9685146a", "cad94737"),
        ))
    conn.close()
    rows.sort(key=lambda r: -r["minutes"])
    print(f"{len(rows)} recorded tracks longer than {MIN_MINUTES} min (imports excluded), longest first:")
    print("  minutes | points | far from start m | end to start m | homeward points within 10 m / within 50 m of an outward point, median nearest m | route home at the end m vs retrace m | 10-24?")
    for r in rows:
        if r["within_10"] is None:
            print(f"  {r['minutes']:6.1f} | {r['points']:5d} | {r['far_m']:6.0f} | {r['end_to_start_m']:5.0f} | no homeward points (the last point is the farthest) | {r['route_home_at_end_m']:6.0f} vs {r['retrace_m']:6.0f} |")
            continue
        print(f"  {r['minutes']:6.1f} | {r['points']:5d} | {r['far_m']:6.0f} | {r['end_to_start_m']:5.0f} | "
              f"{r['within_10']:.2f} / {r['within_50']:.2f}, {r['median_nearest_m']:5.0f} | {r['route_home_at_end_m']:6.0f} vs {r['retrace_m']:6.0f} | {'yes' if r['is_1024_outing'] else ''}")
    print("Figures only.")


if __name__ == "__main__":
    main()
