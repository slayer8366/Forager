# Trail router test data

For `BRouterTrailRouterTest` (dispatch 2026-10-23-02).

## Data licence

`segments/W75_N40.rd5` and `brouter-cli/*.geojson` contain OpenStreetMap data, © OpenStreetMap contributors, available under the Open Database License (ODbL) 1.0, https://opendatacommons.org/licenses/odbl/1-0/. See https://www.openstreetmap.org/copyright.

## Files

- `segments/W75_N40.rd5`: a copy of the route cutter's fixture, `server/pmtiles-worker/test/fixtures/rd5/W75_N40.rd5` (152,665 bytes, SHA-256 `ff6d9d8e439c3cdf914b1566619f64466d9af993c28070ea893425e7f5ec5990`). Its README there says where it came from: part of Harriman and Bear Mountain State Parks, New York, built by BRouter 1.7.10's map creator. Copied rather than read from `server/`, so the app's tests don't depend on the Worker's tree.
- `brouter-cli/*.geojson`: what BRouter v1.7.10's own command-line router (`btools.server.BRouter`, the release jar, checked against its pin) returned for two of that fixture's routes, `inside-across-the-degree-line` and `inside-long`, on `segments/` with `hiking-mountain` and dynamic range off. Written by `make-expected.sh`; a rerun gives the same bytes, because the data is fixed.
