// Runs BRouter v1.7.10's own command-line router (btools.server.BRouter, the jar from the release zip that
// fetch-brouter.sh downloads and checks) and returns what it produced.
//
// The router is used in its four-argument mode, BRouter.java:16-62 at v1.7.10:
//   java -cp brouter-1.7.10-all.jar btools.server.BRouter <segmentDir> <profileDir> "<query>" <outBase>
// It writes <outBase>0.geojson when it finds a track (RoutingEngine.java:244-282) and prints any error
// message on stdout as its last line (BRouter.java:56-58). It always exits 0 (BRouter.java:62), so a failure
// is read from the absence of the track file and the message, never from the exit code.
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

export function brouterHome() {
  const home = process.env.BROUTER_HOME;
  if (!home) {
    throw new Error("BROUTER_HOME is not set. Run test/rd5/fetch-brouter.sh <dir> and set BROUTER_HOME=<dir>.");
  }
  const jar = path.join(home, "brouter-1.7.10-all.jar");
  if (!fs.existsSync(jar)) throw new Error(`No BRouter jar at ${jar}. Run test/rd5/fetch-brouter.sh first.`);
  return { jar, profiles: path.join(home, "profiles2") };
}

// from and to are { lat, lon }. profileParams become "profile:<key>=<value>" in the query, which BRouter
// turns into assignments placed ahead of the profile's own (RoutingParamCollector.java:241-243,
// BExpressionContext.java:861-866).
export function route(segmentDir, from, to, profileParams = {}) {
  const { jar, profiles } = brouterHome();
  const work = fs.mkdtempSync(path.join(os.tmpdir(), "rd5-route-"));
  const outBase = path.join(work, "track");
  const query = [
    `lonlats=${from.lon},${from.lat}|${to.lon},${to.lat}`,
    "profile=hiking-mountain",
    "alternativeidx=0",
    "format=geojson",
    ...Object.entries(profileParams).map(([k, v]) => `profile:${k}=${v}`),
  ].join("&") + "&"; // BRouter.java:24-26 needs an "&" after lonlats
  const run = spawnSync("java", ["-cp", jar, "btools.server.BRouter", segmentDir, profiles, query, outBase], {
    encoding: "utf8",
  });
  try {
    if (run.error) throw run.error;
    if (run.status !== 0) {
      throw new Error(`BRouter exited ${run.status}: ${run.stderr}`);
    }
    const trackFile = `${outBase}0.geojson`;
    const lines = run.stdout.trim().split("\n");
    if (!fs.existsSync(trackFile)) {
      return { feature: null, message: lines[lines.length - 1] };
    }
    const feature = JSON.parse(fs.readFileSync(trackFile, "utf8")).features[0];
    return { feature, message: null };
  } finally {
    fs.rmSync(work, { recursive: true, force: true });
  }
}

// BRouter's own integrity check: decodes every cell of every non-empty tile (PhysicalFile.java:29-90).
// It prints a stack trace between rows of asterisks on an IOException and returns normally, so the
// output is what is read.
export function integrityCheck(file) {
  const { jar } = brouterHome();
  // Its debug output runs to megabytes; spawnSync's 1 MB default buffer would kill it with SIGTERM.
  const run = spawnSync("java", ["-cp", jar, "btools.mapaccess.PhysicalFile", file], {
    encoding: "utf8",
    maxBuffer: 256 * 1024 * 1024,
  });
  if (run.error) throw run.error;
  return { status: run.status, output: `${run.stdout}${run.stderr}` };
}

// Points of a track as [lat, lon].
export const points = (feature) => feature.geometry.coordinates.map(([lon, lat]) => [lat, lon]);

export function metres(a, b) {
  const rad = Math.PI / 180;
  const dLat = (b[0] - a[0]) * rad;
  const dLon = (b[1] - a[1]) * rad * Math.cos(((a[0] + b[0]) / 2) * rad);
  return 6371000 * Math.hypot(dLat, dLon);
}
