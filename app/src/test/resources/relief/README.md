# Relief fixture

`mbgl-offline-relief.db` is a MapLibre offline database **written by the SDK itself**, MapLibre Android 13.5.0, on a CI
emulator (API 34, x86_64): branch `waypoint-bubble-elevation-spike`, run 41752813862, dispatch 2026-11-03-07 D2. The
spike's instrumented test downloaded a tiny Trail Map region over Mount Hood (bounds 45.369..45.378, -121.702..-121.690,
zooms 10-15, pixel ratio 1.0) with `OfflineManager`, then the workflow pulled `files/mbgl-offline.db` with `run-as`.

Trimmed by hand on the laptop with sqlite3, to keep it at 200 KB: every tile but the relief one and every resource
deleted, then `VACUUM`. Kept as the SDK wrote them: the schema (`user_version` 6), the `regions` row, and the relief
tile's own `tiles` row (id 16: z11, x331, y733, `pixel_ratio` 1, `compressed` 1, 140876 bytes) and its `region_tiles`
link. Its summit pixel (175, 179) is rgb (141, 42, 5): 3370.01953125 m, the same as the live Worker's tile.

sha256 of the trimmed file: d6d11edd46a297806ea1696c7bbed03ce9e5d50101a36954a4c8d09fd0a7f865.
