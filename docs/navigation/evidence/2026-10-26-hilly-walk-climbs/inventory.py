#!/usr/bin/env python3 -I
"""List the tracks in a read-only copy of Forager's database, figures only.

Reads from the scratch path (never in a repository). Prints, per track: id prefix,
whether it is named (not the name), the UTC date, duration, stored points, points
the whole-second rule excludes, altitude and pressure coverage, haversine distance
over kept points, the raw positive-step sum of GPS altitude, and the app-method
climb (barometric when at least half the points carry pressure, else smoothed GPS;
4 m hysteresis). No coordinates, names or times of day are printed.

Usage: python3 -I inventory.py [DB_PATH]
"""
import sqlite3
import sys
from datetime import datetime, timezone

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from trackmath import (  # noqa: E402
    haversine_m, is_network_rule, climb_descent, choose_heights,
)

DEFAULT_DB = "/mnt/work/scratch/s26-hilly-walk/work/forager.db"


def main() -> None:
    db = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_DB
    conn = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
    tracks = conn.execute(
        "SELECT id, name, startedAtEpochMillis, endedAtEpochMillis, importedAtEpochMillis "
        "FROM tracks ORDER BY startedAtEpochMillis DESC"
    ).fetchall()
    print(f"tracks: {len(tracks)}")
    print("id       named date(UTC)  dur(min) stored excl  alt%  pres%  dist(km) rawUp(m) appClimb(m) appDesc(m) imported")
    for tid, name, started, ended, imported in tracks:
        rows = conn.execute(
            "SELECT lat, lng, altitude, accuracyMeters, timestampEpochMillis, "
            "speedMetersPerSecond, speedAccuracyMetersPerSecond, pressureHpa "
            "FROM track_points WHERE trackId = ? ORDER BY timestampEpochMillis ASC",
            (tid,),
        ).fetchall()
        stored = len(rows)
        kept = [r for r in rows if not is_network_rule(r[4])]
        excl = stored - len(kept)
        alt_cov = sum(1 for r in kept if r[2] is not None) / len(kept) if kept else 0.0
        pres_cov = sum(1 for r in kept if r[7] is not None) / len(kept) if kept else 0.0
        dist = sum(haversine_m(a[0], a[1], b[0], b[1]) for a, b in zip(kept, kept[1:]))
        raw_up = 0.0
        for a, b in zip(kept, kept[1:]):
            if a[2] is not None and b[2] is not None and b[2] > a[2]:
                raw_up += b[2] - a[2]
        if kept:
            heights, _which = choose_heights(kept)
            up, down = climb_descent(heights, 4.0)
        else:
            up = down = float("nan")
        end = ended if ended is not None else (kept[-1][4] if kept else started)
        dur_min = (end - started) / 60000.0
        date = datetime.fromtimestamp(started / 1000.0, tz=timezone.utc).strftime("%Y-%m-%d")
        print(
            f"{tid[:8]} {'yes' if name else 'no ':5} {date} {dur_min:8.1f} {stored:6d} {excl:4d} "
            f"{alt_cov*100:5.1f} {pres_cov*100:6.1f} {dist/1000:9.2f} {raw_up:8.0f} {up:11.0f} {down:10.0f} "
            f"{'yes' if imported else 'no'}"
        )


if __name__ == "__main__":
    main()
