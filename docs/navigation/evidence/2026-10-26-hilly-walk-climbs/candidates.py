#!/usr/bin/env python3 -I
"""Compare candidate tracks for "the hilly walk", figures only: how the recordings relate in
time and place (as offsets and separations, never positions), out-and-back closure, chosen
height range, smoothed-GPS against barometric climb at 4 m and 10 m hysteresis, and how
many kept points carry no Doppler speed (a network fix carries none).

Usage: python3 -I candidates.py DB_PATH TRACK_ID_PREFIX [TRACK_ID_PREFIX ...]
"""
import sqlite3
import sys

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from trackmath import (  # noqa: E402
    LAT, LNG, ALT, TS, SPEED, PRES, haversine_m, is_network_rule, smoothed_gps,
    barometric_heights, climb_descent,
)


def main():
    db = sys.argv[1]
    prefixes = sys.argv[2:]
    conn = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
    tracks = []
    for prefix in prefixes:
        (tid,) = conn.execute("SELECT id FROM tracks WHERE id LIKE ?", (prefix + "%",)).fetchone()
        rows = conn.execute(
            "SELECT lat, lng, altitude, accuracyMeters, timestampEpochMillis, speedMetersPerSecond, "
            "speedAccuracyMetersPerSecond, pressureHpa FROM track_points WHERE trackId = ? "
            "ORDER BY timestampEpochMillis ASC", (tid,)).fetchall()
        kept = [r for r in rows if not is_network_rule(r[TS])]
        tracks.append((tid[:8], kept))
    tracks.sort(key=lambda t: t[1][0][TS])
    t0 = tracks[0][1][0][TS]
    for name, kept in tracks:
        gps = smoothed_gps(kept)
        stats = {}
        baro = barometric_heights(kept, stats)
        hs = [h for h in baro if h is not None]
        raw = [p[ALT] for p in kept if p[ALT] is not None]
        print(f"track {name}: starts +{(kept[0][TS]-t0)/60000:.1f} min, ends +{(kept[-1][TS]-t0)/60000:.1f} min")
        print(f"  end-to-start distance {haversine_m(kept[0][LAT], kept[0][LNG], kept[-1][LAT], kept[-1][LNG]):.0f} m")
        print(f"  raw GPS height range {max(raw)-min(raw):.1f} m; net raw change {raw[-1]-raw[0]:+.1f} m")
        if hs:
            print(f"  baro height range {max(hs)-min(hs):.1f} m; net baro change {hs[-1]-hs[0]:+.1f} m")
        for thr in (4.0, 10.0):
            line = f"  hyst {thr:.0f} m: smoothed-GPS up/down {climb_descent(gps, thr)[0]:.0f}/{climb_descent(gps, thr)[1]:.0f} m"
            if hs:
                line += f"; baro up/down {climb_descent(baro, thr)[0]:.0f}/{climb_descent(baro, thr)[1]:.0f} m"
            print(line)
        if hs:
            print(f"  baro steps taken out: {stats['steps_taken_out']} (net {stats['steps_taken_out_meters']:+.1f} m)")
        print(f"  kept points without Doppler speed: {sum(1 for p in kept if p[SPEED] is None)} of {len(kept)}; "
              f"without pressure: {sum(1 for p in kept if p[PRES] is None)}")
    for i in range(len(tracks) - 1):
        a, b = tracks[i][1], tracks[i + 1][1]
        print(f"{tracks[i][0]} -> {tracks[i+1][0]}: gap {(b[0][TS]-a[-1][TS])/60000:.1f} min; "
              f"A.end to B.start {haversine_m(a[-1][LAT], a[-1][LNG], b[0][LAT], b[0][LNG]):.0f} m; "
              f"A.start to B.start {haversine_m(a[0][LAT], a[0][LNG], b[0][LAT], b[0][LNG]):.0f} m")


if __name__ == "__main__":
    main()
