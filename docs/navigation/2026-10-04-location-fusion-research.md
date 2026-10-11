# Forager's own location fusion: steps, compass, satellites and towers (research)

**Status:** research, read-only. Nothing built or decided. Filed by the laptop planner on 2026-10-04 (UTC), at `main` `d4bd00bf`.

**Asked for by the owner,** verbatim (`RECORD.md` -509): "How much of the phone can we use to actually establish a position? Can the accelerometer or other sensor be used to track movement? Mobile tower signals get weaker when you walk away, and stronger when you walk towards. Can a position be triangulated by detecting these things, and putting it all together into a precise locatoon?", and then "GPS fusion will be useful and I think the real work will pay off, especially keeping it open source."

**How it was made, and how far to trust it.** Two research agents run by the planner on 2026-10-04, on the web only; both were cut off once by a network error and resumed. Claims are marked **[C]** read on the cited page, **[S]** seen only in a search-engine snippet (the page was blocked), **[I]** inference. **The planner has not re-checked the sources.** No patents were read. No repository file was read by the agents.

## Summary

1. **There is no ready-made open fusion to adopt.** Android's open "fused" provider does not fuse: it picks the newer or more accurate of GPS and network [C]. Google's real fusion is in Play services, which is proprietary and ruled out. The open Android fusion libraries found are car-tuned or small. **Forager builds its own,** using open references (Apache, MIT, BSD) [I].
2. **Steps and compass help most by smoothing and bridging gaps,** not by beating GPS. On one published phone test, adding steps to GNSS cut the mean error from about 15 m to about 8 m [C]; step length can be estimated to within about 1.5 to 3% of distance [C/S]; magnetometer heading errs by about 12° on average indoors [C]. No published phone test in a real forest was found.
3. **The satellites themselves can tell a good fix from a bad one,** even though the S22 always reports 3.79 m: Android exposes the number of satellites used and each one's signal strength [C/S]. That is a cheap, immediate improvement.
4. **Raw satellite measurements could do better still,** but whether the S22 (a Snapdragon model in the US) provides the needed data (dual-frequency L5, carrier phase) is unconfirmed; Google says Snapdragon flagships generally do not provide carrier phase [C]. It needs measuring on the phone.
5. **Cell towers give only a rough first fix,** typically 200 to 300 m and up to kilometres in rural areas [S/I], from an open database (OpenCelliD, CC BY-SA) [C]. The phone's network location already gives about 100 m indoors; the approximate-position work (-510) uses that.

## 1. What each part of the phone can contribute

| Source | Contribution | Evidence |
|---|---|---|
| **Step detector** | One event per footfall, under 2 s latency, low power [C] (`source.android.com` sensor types). Its per-step accuracy is not guaranteed (the counter only needs a day within 10%) [C]. | Own accelerometer detector logged alongside to measure it [I]. |
| **Step length** | Weinberg's model from vertical acceleration: about 2.5% length error, 3% position error over 26 m [S]; an LSTM method 1.4% [C]. A per-walker scale learned from GPS while the signal is good [I]. | Slopes shorten steps (3.5 to 7.5% on 2 to 7% grades, race-walkers only) [S]. |
| **Heading** | Rotation vector (with magnetometer, absolute north), game rotation vector (no magnetometer, relative only), geomagnetic rotation vector [C]. Magnetometer heading error about 12.5° held flat indoors [C]; man-made disturbance frequent [C]. | Use the gyro-based game rotation vector for turns; magnetometer only slowly, gated; correct bias from GPS course while moving [I]. |
| **Barometer** | Relative height to about ±1 m (a typical phone part) [C]; weather drifts it over tens of minutes [C]. | Short-term climb and descent; re-anchor to GPS height [I]. |
| **Satellite quality** | `GnssStatus`: satellites used in the fix, signal strength per satellite (C/N0), carrier frequency (L1 or L5) [S]. | A fix-quality measure that does not depend on the constant 3.79 m [I]. |
| **Raw satellite measurements** | Pseudorange, Doppler, carrier phase, mandatory on Android 10+ [C]; own position solutions, ionosphere-free L1/L5, carrier smoothing (about 0.6 m static in research) [C/S]. Full tracking (`setFullTracking`, API 31) stops duty cycling at a battery cost [C]. | S22: Snapdragon 8 Gen 1 in the US [C]; L5 unconfirmed; carrier phase probably absent [I]. |
| **Cell towers** | Serving and neighbour cells via `getAllCellInfo` (needs fine location permission) [S]; positions from OpenCelliD (CC BY-SA 4.0, world about 105 MB compressed) [C/S]; Mozilla's service retired in 2024 [S]. | 200 to 300 m typical, kilometres rural [S/I]. |
| **Trail matching** | A backtracking particle filter with map constraints reached about 3 m indoors [C]. | Walkers leave trails while foraging: a soft weight, never a snap, and later [I]. |

## 2. Combining them

- **A small filter:** an extended Kalman filter with the state east, north, heading bias and step-length scale; each step predicts the position, each GPS fix corrects it, weighted by its quality, with a gate that rejects jumps walking could not explain; heading bias and step scale learned from GPS while it is good and the walker is moving [I, both agents' recommendation].
- **Published results:** on a Huawei phone in Hong Kong, GNSS alone 15.0 m mean, with steps 8.0 m (a factor-graph method) [C]; an adaptive filter reported 2.7 to 4.2 m against about 30 m for GNSS alone [S]; a complete open GNSS/step Android app (CUMT-POS) exists [C].
- **Limits stated, not faked:** a floor on GPS noise (about 3 m); dead reckoning alone capped (for example 5 minutes or 300 m), after which the position shows as approximate [I].
- **Battery:** on a 2011 phone, motion sensors cost 50 to 80% of GPS's energy [C]; modern phones batch sensors in a low-power hub [C]; the S22's cost is unmeasured [I].

## 3. Open building blocks and their licences

- **Fit "no proprietary software":** EJML (Apache 2.0) for matrices [S]; Mad Location Manager (MIT, car-tuned, a reference only) [S]; GNSS Compare's core (Apache 2.0, its `gogpsextracts` module LGPL 3.0) [C]; RTKLIB (BSD 2-clause, C, heavy) [C]; GPSTest (Apache 2.0) for checking the S22's satellites [C].
- **Avoid copying:** An offline maps app's UI code (CC-BY-NC-ND, Play publication needs permission) [C]; GPL-only code unless Forager chooses a compatible code licence, which it has not (the owner: no code licence added).

## The planner's recommendation

**Data first, then the filter** (CLAUDE.md: "Don't build speculative correction or optimization logic without real data showing the case it's meant to handle"):

1. **A quick win now:** judge each GPS fix by its satellites (number used, signal strength) rather than the constant 3.79 m. It improves the off-track line, arrival and the approximate position at once.
2. **A walk logger:** a debug-only recorder of everything on a walk (GPS fixes, satellite status, raw satellite measurements if offered, steps, rotation, barometer), with no positions entering the repository, run on the owner's next walks in open ground and under canopy. It also answers whether the S22 offers L5 and carrier phase.
3. **The filter, built and tuned against those logs** in tests on the laptop, before it touches the live map: steps, relative heading and GPS, with stated limits.
4. **Later:** the barometer for height, soft trail matching, raw satellite positioning if the S22 supports it, and an OpenCelliD extract only if the network location proves not enough.

## Decisions for the owner

1. The order above: the quick win, the walk logger, then the filter.
2. The walk logger as a debug-only feature that records your walks for analysis, kept on the phone and copied off for the laptop, never in the repository.
3. Open references only (Apache, MIT, BSD), so no code licence decision is forced.
4. Cell towers deferred until the network location proves insufficient.

## Could not determine

Published phone step tracking in a real forest; the S22's L5, carrier phase, sensor parts and their battery cost; the effect of power lines and vehicles on heading; published accuracy of GPS-course heading correction for walkers; weather drift per hour on long walks; OpenCelliD's rural coverage and extract size; Hipparchus's licence; RtkGps's and KalmanLocationManager's licences; why the S22 reports a constant 3.79 m.
