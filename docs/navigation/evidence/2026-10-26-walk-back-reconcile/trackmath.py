"""Ports of Forager's read-time track arithmetic, line for line from the Kotlin at
pre-main cfef2f4cb (#421 merged). Copied from the 2026-10-26-hilly-walk-climbs evidence folder
(written against 0914bed23) with one addition: `bridged_across_gaps`, #420's bridging of a
pressure-less point, from domain/BarometricHeight.kt:117-130, applied in `barometric_heights`
as BarometricHeight.kt:51-54 applies it.

- haversine_m         <- domain/GeoDistance.kt:17-27
- is_network_rule     <- domain/NetworkProviderFix.kt:54
- smoothed_gps        <- domain/TrackHeights.kt:40-76 (smoothedGpsHeights, symmetricWindow)
- barometric_heights  <- domain/BarometricHeight.kt:48-108 (with bridging, :117-130)
- choose_heights      <- domain/TrackHeights.kt:18-21 (trackHeights)
- climb_descent       <- domain/ComputeTrackStatisticsUseCase.kt:66-91 == domain/ClimbAllowance.kt:91-111 (bankedClimb)
- moving_pace         <- domain/MovingPace.kt:101-206

A point is a tuple (lat, lng, altitude, accuracyMeters, timestampEpochMillis,
speedMetersPerSecond, speedAccuracyMetersPerSecond, pressureHpa) as `track_points`
stores it, ordered by timestamp. Pure Python, no third-party imports.
"""
import math
from statistics import median as _median

EARTH_MEAN_RADIUS_METERS = 6_371_008.8

# BarometricHeight.kt constants
STANDARD_SEA_LEVEL_HPA = 1013.25
PRESSURE_MIN_HPA = 500.0
PRESSURE_MAX_HPA = 1100.0
BARO_MAX_VERTICAL_SPEED_METERS_PER_SECOND = 0.5
BARO_WINDOW_HALF_MILLIS = 60_000
BARO_INITIAL_ANCHOR_MILLIS = 60_000
BARO_ANCHOR_TIME_CONSTANT_MILLIS = 60 * 60_000
BARO_ANCHOR_MAX_DISAGREEMENT_METERS = 30.0

# TrackHeights.kt constants
HEIGHT_SMOOTHING_HALF_WINDOW_MILLIS = 30_000
HEIGHT_MEDIAN_MAX_SIDE = 2

# MovingPace.kt constants
MOVING_SPEED_FLOOR_METERS_PER_SECOND = 0.5
DEFAULT_MOVING_SPEED_METERS_PER_SECOND = 3.2 / 3.6
MEASURED_PACE_MIN_MOVING_MILLIS = 5 * 60 * 1000

LAT, LNG, ALT, ACC, TS, SPEED, SPEED_ACC, PRES = range(8)


def haversine_m(lat_a, lng_a, lat_b, lng_b):
    lat1 = math.radians(lat_a)
    lat2 = math.radians(lat_b)
    half_dlat = math.radians(lat_b - lat_a) / 2
    half_dlng = math.radians(lng_b - lng_a) / 2
    h = math.sin(half_dlat) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(half_dlng) ** 2
    return 2 * EARTH_MEAN_RADIUS_METERS * math.asin(min(math.sqrt(h), 1.0))


def is_network_rule(timestamp_epoch_millis):
    return timestamp_epoch_millis % 1000 != 0


# ---- smoothed GPS heights (TrackHeights.kt) ----

def _symmetric_window(values, times, i, max_side):
    side = 0
    while (
        side < max_side
        and i - side - 1 >= 0
        and i + side + 1 < len(values)
        and times[i] - times[i - side - 1] <= HEIGHT_SMOOTHING_HALF_WINDOW_MILLIS
        and times[i + side + 1] - times[i] <= HEIGHT_SMOOTHING_HALF_WINDOW_MILLIS
    ):
        side += 1
    return values[i - side:i + side + 1]


def smoothed_gps(points):
    heights = [None] * len(points)
    start = 0
    n = len(points)
    while start < n:
        if points[start][ALT] is None:
            start += 1
            continue
        end = start
        while end + 1 < n and points[end + 1][ALT] is not None:
            end += 1
        run = points[start:end + 1]
        times = [p[TS] for p in run]
        raw = [p[ALT] for p in run]
        medians = []
        for i in range(len(raw)):
            w = sorted(_symmetric_window(raw, times, i, HEIGHT_MEDIAN_MAX_SIDE))
            medians.append(w[len(w) // 2])
        for i in range(len(medians)):
            w = _symmetric_window(medians, times, i, 10**9)
            heights[start + i] = sum(w) / len(w)
        start = end + 1
    return heights


# ---- barometric heights (BarometricHeight.kt) ----

def pressure_altitude_m(hpa):
    return 44_330.0 * (1.0 - (hpa / STANDARD_SEA_LEVEL_HPA) ** (1.0 / 5.255))


def _runs_of(values):
    runs, current = [], []
    for i, v in enumerate(values):
        if v is None:
            if current:
                runs.append(current)
            current = []
        else:
            current.append(i)
    if current:
        runs.append(current)
    return runs


def _median_kotlin(values):
    s = sorted(values)
    m = len(s) // 2
    return s[m] if len(s) % 2 == 1 else (s[m - 1] + s[m]) / 2.0


def bridged_across_gaps(values, times):
    """BarometricHeight.kt:117-130: each None with a value on both sides becomes the straight line,
    in time, between the nearest value before and after; a None before the first value or after
    the last stays None."""
    out = []
    before = -1
    n = len(values)
    for i, v in enumerate(values):
        if v is not None:
            before = i
            out.append(v)
            continue
        if before < 0:
            out.append(None)
            continue
        after = next((j for j in range(i + 1, n) if values[j] is not None), None)
        if after is None:
            out.append(None)
            continue
        span = times[after] - times[before]
        f = (times[i] - times[before]) / span if span > 0 else 0.0
        out.append(values[before] + (values[after] - values[before]) * f)
    return out


def barometric_heights(points, stats=None, bridge=True):
    """Returns heights (None where none). `stats`, if a dict, receives
    steps_taken_out (count) and steps_taken_out_meters (net metres removed)."""
    n = len(points)
    baro = []
    for p in points:
        hpa = p[PRES]
        if hpa is not None and PRESSURE_MIN_HPA <= hpa <= PRESSURE_MAX_HPA:
            baro.append(pressure_altitude_m(float(hpa)))
        else:
            baro.append(None)
    times = [p[TS] for p in points]
    if bridge:
        baro = bridged_across_gaps(baro, times)

    filtered = [None] * n
    for run in _runs_of(baro):
        for i in run:
            window = [baro[j] for j in run if abs(times[j] - times[i]) <= BARO_WINDOW_HALF_MILLIS]
            filtered[i] = _median_kotlin(window)

    steps_taken_out = 0.0
    step_count = 0
    previous = None
    previous_at = 0
    stepped = [None] * n
    for i, height in enumerate(filtered):
        if height is None:
            continue
        at = times[i]
        if previous is not None:
            seconds = max(at - previous_at, 1) / 1000.0
            if abs(height - previous) / seconds > BARO_MAX_VERTICAL_SPEED_METERS_PER_SECOND:
                steps_taken_out -= height - previous
                step_count += 1
        previous = height
        previous_at = at
        stepped[i] = height + steps_taken_out
    if stats is not None:
        stats["steps_taken_out"] = step_count
        stats["steps_taken_out_meters"] = steps_taken_out

    limited = [None] * n
    for run in _runs_of(stepped):
        for i in run:
            window = [stepped[j] for j in run if abs(times[j] - times[i]) <= BARO_WINDOW_HALF_MILLIS]
            limited[i] = sum(window) / len(window)

    first_idx = next((i for i in range(n) if limited[i] is not None), None)
    if first_idx is None:
        return limited
    first_at = points[first_idx][TS]
    initial = sorted(
        points[i][ALT] - limited[i]
        for i in range(n)
        if limited[i] is not None and points[i][ALT] is not None and points[i][TS] - first_at <= BARO_INITIAL_ANCHOR_MILLIS
    )
    anchor = 0.0 if not initial else initial[len(initial) // 2]
    anchor_at = first_at
    out = [None] * n
    for i in range(n):
        height = limited[i]
        if height is None:
            continue
        at = points[i][TS]
        gps = points[i][ALT]
        if gps is not None and at - first_at > BARO_INITIAL_ANCHOR_MILLIS:
            disagreement = gps - (height + anchor)
            if abs(disagreement) <= BARO_ANCHOR_MAX_DISAGREEMENT_METERS:
                fraction = min(max((at - anchor_at) / BARO_ANCHOR_TIME_CONSTANT_MILLIS, 0.0), 1.0)
                anchor += disagreement * fraction
            anchor_at = at
        out[i] = height + anchor
    return out


def choose_heights(points, stats=None):
    """trackHeights: barometric when at least half the points carry pressure, else smoothed GPS.
    Returns (heights, 'baro' | 'gps')."""
    with_pressure = sum(1 for p in points if p[PRES] is not None)
    if with_pressure > 0 and with_pressure * 2 >= len(points):
        return barometric_heights(points, stats), "baro"
    return smoothed_gps(points), "gps"


# ---- climb and descent with hysteresis (ComputeTrackStatisticsUseCase.kt) ----

def climb_descent(heights, threshold_m):
    gain = loss = 0.0
    reference = None
    for i in range(1, len(heights)):
        prev, cur = heights[i - 1], heights[i]
        if prev is None or cur is None:
            reference = None
            continue
        ref = reference if reference is not None else prev
        delta = cur - ref
        if abs(delta) >= threshold_m:
            if delta > 0:
                gain += delta
            else:
                loss += -delta
            reference = cur
        else:
            reference = ref
    return gain, loss


# ---- moving pace (MovingPace.kt) ----

def moving_pace(points):
    r = dict(intervals_examined=max(len(points) - 1, 0), intervals_moving=0, moving_millis=0,
             moving_meters=0.0, doppler_moving_millis=0, doppler_moving_meters=0.0,
             doppler_weighted_sum=0.0, points_without_speed=0, points_below_floor=0, points_counted=0)
    for i in range(1, len(points)):
        prev, pt = points[i - 1], points[i]
        dt = pt[TS] - prev[TS]
        if dt <= 0:
            continue
        m = haversine_m(prev[LAT], prev[LNG], pt[LAT], pt[LNG])
        if m / (dt / 1000.0) < MOVING_SPEED_FLOOR_METERS_PER_SECOND:
            continue
        r["intervals_moving"] += 1
        r["moving_millis"] += dt
        r["moving_meters"] += m
        s = pt[SPEED]
        if s is None:
            r["points_without_speed"] += 1
        elif s < MOVING_SPEED_FLOOR_METERS_PER_SECOND:
            r["points_below_floor"] += 1
        else:
            r["points_counted"] += 1
            r["doppler_weighted_sum"] += float(s) * dt
            r["doppler_moving_millis"] += dt
            r["doppler_moving_meters"] += m
    r["differencing_speed"] = r["moving_meters"] / (r["moving_millis"] / 1000.0) if r["moving_millis"] else None
    r["doppler_speed"] = r["doppler_weighted_sum"] / r["doppler_moving_millis"] if r["doppler_moving_millis"] else None
    if r["doppler_moving_millis"] >= MEASURED_PACE_MIN_MOVING_MILLIS:
        r["source"], r["speed"] = "DOPPLER", r["doppler_speed"]
    elif r["moving_millis"] >= MEASURED_PACE_MIN_MOVING_MILLIS:
        r["source"], r["speed"] = "DIFFERENCING", r["differencing_speed"]
    else:
        r["source"], r["speed"] = "DEFAULT", DEFAULT_MOVING_SPEED_METERS_PER_SECOND
    return r


def median(values):
    return _median(values) if values else float("nan")


def percentile(sorted_values, q):
    if not sorted_values:
        return float("nan")
    k = (len(sorted_values) - 1) * q
    f = math.floor(k)
    c = math.ceil(k)
    if f == c:
        return sorted_values[int(k)]
    return sorted_values[f] + (sorted_values[c] - sorted_values[f]) * (k - f)
