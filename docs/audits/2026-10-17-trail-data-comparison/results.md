# Per-box results (computed by analyze.py)

| # | Box | OSM ways / km | FS segs / km (terra km) | BLM segs / km | USGS segs / km (federal km) | Federal merged km | Fed near OSM 15 m | OSM near fed 15 m | Fed-only km | OSM-only km | Fed near OSM 50 m | OSM near fed 50 m |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | mount-hood | 185 / 160.6 | 70 / 142.5 (88.7) | 0 / 0.0 | 181 / 122.5 (122.5) | 142.5 | 82% | 61% | 25.2 | 62.7 | 89% | 69% |
| 2 | eagle-cap | 100 / 99.5 | 0 / 0.0 (0.0) | 0 / 0.0 | 0 / 0.0 (0.0) | 0.0 | n/a | 0% | 0.0 | 99.5 | n/a | 0% |
| 3 | chamberlain-basin | 8 / 50.3 | 10 / 59.3 (59.3) | 0 / 0.0 | 18 / 59.3 (59.3) | 59.3 | 47% | 55% | 31.6 | 22.5 | 64% | 76% |
| 4 | oregon-badlands | 101 / 150.0 | 0 / 0.0 (0.0) | 75 / 54.6 | 75 / 54.7 (54.7) | 54.6 | 100% | 37% | 0.3 | 94.4 | 100% | 38% |
| 5 | cape-perpetua | 155 / 89.7 | 8 / 21.7 (21.7) | 0 / 0.0 | 11 / 21.8 (21.8) | 21.7 | 69% | 17% | 6.8 | 74.8 | 90% | 22% |
| 6 | tiger-mountain | 1054 / 225.5 | 0 / 0.0 (0.0) | 0 / 0.0 | 11 / 9.0 (0.0) | 0.0 | n/a | 0% | 0.0 | 225.5 | n/a | 0% |
| 7 | three-sisters | 28 / 63.4 | 15 / 58.4 (58.4) | 0 / 0.0 | 29 / 58.3 (58.3) | 58.4 | 79% | 71% | 12.1 | 18.3 | 86% | 78% |

## Connectivity (pieces, largest piece's share of length)

| # | Box | OSM | OSM paths only | FS | USGS | BLM | Federal merged |
|---|---|---|---|---|---|---|---|
| 1 | mount-hood | 22 pieces, 66% | 22 pieces, 76% | 15 pieces, 39% | 13 pieces, 49% | none | 15 pieces, 39% |
| 2 | eagle-cap | 7 pieces, 97% | 7 pieces, 97% | none | none | none | none |
| 3 | chamberlain-basin | 2 pieces, 100% | 2 pieces, 100% | 4 pieces, 65% | 3 pieces, 77% | none | 4 pieces, 65% |
| 4 | oregon-badlands | 15 pieces, 85% | 8 pieces, 94% | none | 4 pieces, 91% | 4 pieces, 91% | 4 pieces, 91% |
| 5 | cape-perpetua | 79 pieces, 20% | 37 pieces, 62% | 5 pieces, 42% | 5 pieces, 42% | none | 5 pieces, 42% |
| 6 | tiger-mountain | 254 pieces, 60% | 228 pieces, 65% | none | 5 pieces, 68% | none | none |
| 7 | three-sisters | 4 pieces, 70% | 4 pieces, 70% | 10 pieces, 72% | 6 pieces, 74% | none | 10 pieces, 72% |

## Offset of matched federal segments from the nearest OSM way

| # | Box | Matched segs | Sampled | Median m | Q1 m | Q3 m | FS vs USGS median m |
|---|---|---|---|---|---|---|---|
| 1 | mount-hood | 60 | 20 | 2.6 | 2.1 | 4.4 | 0.9 |
| 2 | eagle-cap | 0 | 0 | n/a | n/a | n/a | n/a |
| 3 | chamberlain-basin | 8 | 8 | 11.0 | 4.5 | 15.5 | 0.9 |
| 4 | oregon-badlands | 73 | 20 | 3.0 | 1.9 | 4.8 | n/a |
| 5 | cape-perpetua | 7 | 7 | 3.9 | 1.9 | 16.1 | 0.8 |
| 6 | tiger-mountain | 0 | 0 | n/a | n/a | n/a | n/a |
| 7 | three-sisters | 13 | 13 | 3.1 | 2.1 | 8.1 | 1.0 |

## Attributes (share of segments carrying the field)

### Box 1 mount-hood

OSM: name 49%, trail number (ref) 29%, allowed use (access/foot/bicycle/horse/motor_vehicle) 66%, closure/season (seasonal, opening_hours, access=no) 1%, difficulty (sac_scale, mtb:scale, trail_visibility) 18%, surface 48%, operator 2%, informal=yes 5%

FS: name 100%, trail number 100%, allowed use (any *_managed/accpt or allowed_terra_use) 0%, closure/season (any *_restricted) 0%, difficulty/class (trail_class, typical_trail_grade) 100%, surface 100%, accessibility_status 6%; subsets {'TrailNFS_Basic': 70}; trail types {'SNOW': 33, 'TERRA': 37}

USGS: name 100%, trail number 100%, allowed use (hikerpedestrian/bicycle/packsaddle/motorcycle) 0%, closure/season (seasonopen) 0%, difficulty/class 0%, surface 100%; originators {'U.S. Forest Service / FS': 181}; source edit dates {'min': '2026-03-29', 'median': '2026-03-29', 'max': '2026-03-29', 'n': 181}; source descriptions {'USFS Trails Update 03/2026': 181}

BLM: name (ROUTE_PRMRY_NM) n/a, trail number (FAMS_ID or ROUTE_PLAN_ID) n/a, allowed use (PLAN_ALLOW_MODE_TRNSPRT, PLAN_MODE_TRNSPRT, PLAN_OHV_ROUTE_DSGNTN) n/a, closure/season (PLAN_SEASON_RSTRCT_CODE, PLAN_ACCESS_RSTRCT) n/a, difficulty/class (none in schema; OBSRVE_ROUTE_USE_CLASS is a use class) n/a, surface (OBSRVE_SRFCE_TYPE) n/a, accuracy stated (ACCURACY_FT) n/a; by layer { 2: 0, 3: 0, 4: 0, 5: 0, 6: 0, 7: 0 }

OSM highway mix: {'track': 40, 'path': 138, 'footway': 7}; km { track: 25.5, path: 131.9, footway: 3.3 }; excluded {'sidewalk': 1}; OSM-only km by highway { track: 17.4, path: 42.1, footway: 3.2 }

### Box 2 eagle-cap

OSM: name 48%, trail number (ref) 39%, allowed use (access/foot/bicycle/horse/motor_vehicle) 18%, closure/season (seasonal, opening_hours, access=no) 0%, difficulty (sac_scale, mtb:scale, trail_visibility) 14%, surface 67%, operator 2%, informal=yes 4%

FS: name n/a, trail number n/a, allowed use (any *_managed/accpt or allowed_terra_use) n/a, closure/season (any *_restricted) n/a, difficulty/class (trail_class, typical_trail_grade) n/a, surface n/a, accessibility_status n/a; subsets {}; trail types {}

USGS: name n/a, trail number n/a, allowed use (hikerpedestrian/bicycle/packsaddle/motorcycle) n/a, closure/season (seasonopen) n/a, difficulty/class n/a, surface n/a; originators {}; source edit dates None; source descriptions {}

BLM: name (ROUTE_PRMRY_NM) n/a, trail number (FAMS_ID or ROUTE_PLAN_ID) n/a, allowed use (PLAN_ALLOW_MODE_TRNSPRT, PLAN_MODE_TRNSPRT, PLAN_OHV_ROUTE_DSGNTN) n/a, closure/season (PLAN_SEASON_RSTRCT_CODE, PLAN_ACCESS_RSTRCT) n/a, difficulty/class (none in schema; OBSRVE_ROUTE_USE_CLASS is a use class) n/a, surface (OBSRVE_SRFCE_TYPE) n/a, accuracy stated (ACCURACY_FT) n/a; by layer { 2: 0, 3: 0, 4: 0, 5: 0, 6: 0, 7: 0 }

OSM highway mix: {'path': 99, 'footway': 1}; km { path: 98.9, footway: 0.6 }; excluded {}; OSM-only km by highway { path: 98.9, footway: 0.6 }

### Box 3 chamberlain-basin

OSM: name 100%, trail number (ref) 100%, allowed use (access/foot/bicycle/horse/motor_vehicle) 62%, closure/season (seasonal, opening_hours, access=no) 0%, difficulty (sac_scale, mtb:scale, trail_visibility) 0%, surface 62%, operator 0%, informal=yes 0%

FS: name 100%, trail number 100%, allowed use (any *_managed/accpt or allowed_terra_use) 100%, closure/season (any *_restricted) 100%, difficulty/class (trail_class, typical_trail_grade) 100%, surface 100%, accessibility_status 0%; subsets {'TrailNFS_MGMT': 10}; trail types {'TERRA': 10}

USGS: name 100%, trail number 100%, allowed use (hikerpedestrian/bicycle/packsaddle/motorcycle) 0%, closure/season (seasonopen) 0%, difficulty/class 0%, surface 100%; originators {'U.S. Forest Service / FS': 18}; source edit dates {'min': '2026-03-29', 'median': '2026-03-29', 'max': '2026-03-29', 'n': 18}; source descriptions {'USFS Trails Update 03/2026': 18}

BLM: name (ROUTE_PRMRY_NM) n/a, trail number (FAMS_ID or ROUTE_PLAN_ID) n/a, allowed use (PLAN_ALLOW_MODE_TRNSPRT, PLAN_MODE_TRNSPRT, PLAN_OHV_ROUTE_DSGNTN) n/a, closure/season (PLAN_SEASON_RSTRCT_CODE, PLAN_ACCESS_RSTRCT) n/a, difficulty/class (none in schema; OBSRVE_ROUTE_USE_CLASS is a use class) n/a, surface (OBSRVE_SRFCE_TYPE) n/a, accuracy stated (ACCURACY_FT) n/a; by layer { 2: 0, 3: 0, 4: 0, 5: 0, 6: 0, 7: 0 }

OSM highway mix: {'path': 8}; km { path: 50.3 }; excluded {}; OSM-only km by highway { path: 22.5 }

### Box 4 oregon-badlands

OSM: name 31%, trail number (ref) 0%, allowed use (access/foot/bicycle/horse/motor_vehicle) 47%, closure/season (seasonal, opening_hours, access=no) 7%, difficulty (sac_scale, mtb:scale, trail_visibility) 7%, surface 50%, operator 0%, informal=yes 0%

FS: name n/a, trail number n/a, allowed use (any *_managed/accpt or allowed_terra_use) n/a, closure/season (any *_restricted) n/a, difficulty/class (trail_class, typical_trail_grade) n/a, surface n/a, accessibility_status n/a; subsets {}; trail types {}

USGS: name 100%, trail number 0%, allowed use (hikerpedestrian/bicycle/packsaddle/motorcycle) 100%, closure/season (seasonopen) 0%, difficulty/class 0%, surface 100%; originators {'Bureau of Land Management / BLM': 75}; source edit dates {'min': '2026-03-17', 'median': '2026-03-17', 'max': '2026-03-17', 'n': 75}; source descriptions {'BLM Trails Update 03/2026': 75}

BLM: name (ROUTE_PRMRY_NM) 100%, trail number (FAMS_ID or ROUTE_PLAN_ID) 100%, allowed use (PLAN_ALLOW_MODE_TRNSPRT, PLAN_MODE_TRNSPRT, PLAN_OHV_ROUTE_DSGNTN) 100%, closure/season (PLAN_SEASON_RSTRCT_CODE, PLAN_ACCESS_RSTRCT) 100%, difficulty/class (none in schema; OBSRVE_ROUTE_USE_CLASS is a use class) 100%, surface (OBSRVE_SRFCE_TYPE) 100%, accuracy stated (ACCURACY_FT) 100%; by layer { 2: 0, 3: 0, 4: 2, 5: 73, 6: 0, 7: 75 }

OSM highway mix: {'track': 35, 'path': 53, 'footway': 13}; km { track: 32.4, path: 91.0, footway: 26.6 }; excluded {}; OSM-only km by highway { track: 32.3, path: 55.1, footway: 7.0 }

### Box 5 cape-perpetua

OSM: name 30%, trail number (ref) 28%, allowed use (access/foot/bicycle/horse/motor_vehicle) 32%, closure/season (seasonal, opening_hours, access=no) 0%, difficulty (sac_scale, mtb:scale, trail_visibility) 0%, surface 59%, operator 27%, informal=yes 0%

FS: name 100%, trail number 100%, allowed use (any *_managed/accpt or allowed_terra_use) 100%, closure/season (any *_restricted) 100%, difficulty/class (trail_class, typical_trail_grade) 100%, surface 100%, accessibility_status 100%; subsets {'TrailNFS_MGMT': 8}; trail types {'TERRA': 8}

USGS: name 100%, trail number 100%, allowed use (hikerpedestrian/bicycle/packsaddle/motorcycle) 0%, closure/season (seasonopen) 0%, difficulty/class 0%, surface 100%; originators {'U.S. Forest Service / FS': 11}; source edit dates {'min': '2026-03-29', 'median': '2026-03-29', 'max': '2026-03-29', 'n': 11}; source descriptions {'USFS Trails Update 03/2026': 11}

BLM: name (ROUTE_PRMRY_NM) n/a, trail number (FAMS_ID or ROUTE_PLAN_ID) n/a, allowed use (PLAN_ALLOW_MODE_TRNSPRT, PLAN_MODE_TRNSPRT, PLAN_OHV_ROUTE_DSGNTN) n/a, closure/season (PLAN_SEASON_RSTRCT_CODE, PLAN_ACCESS_RSTRCT) n/a, difficulty/class (none in schema; OBSRVE_ROUTE_USE_CLASS is a use class) n/a, surface (OBSRVE_SRFCE_TYPE) n/a, accuracy stated (ACCURACY_FT) n/a; by layer { 2: 0, 3: 0, 4: 0, 5: 0, 6: 0, 7: 0 }

OSM highway mix: {'track': 67, 'footway': 76, 'path': 7, 'steps': 5}; km { track: 61.1, footway: 18.4, path: 10.0, steps: 0.0 }; excluded {'sidewalk': 1}; OSM-only km by highway { track: 61.0, footway: 11.1, path: 2.7, steps: 0.0 }

### Box 6 tiger-mountain

OSM: name 23%, trail number (ref) 0%, allowed use (access/foot/bicycle/horse/motor_vehicle) 38%, closure/season (seasonal, opening_hours, access=no) 1%, difficulty (sac_scale, mtb:scale, trail_visibility) 2%, surface 65%, operator 7%, informal=yes 1%

FS: name n/a, trail number n/a, allowed use (any *_managed/accpt or allowed_terra_use) n/a, closure/season (any *_restricted) n/a, difficulty/class (trail_class, typical_trail_grade) n/a, surface n/a, accessibility_status n/a; subsets {}; trail types {}

USGS: name 64%, trail number 0%, allowed use (hikerpedestrian/bicycle/packsaddle/motorcycle) 100%, closure/season (seasonopen) 0%, difficulty/class 0%, surface 0%; originators {'Washington State Parks and Recreation Commission / State': 11}; source edit dates None; source descriptions {'Washington State Trails 01/2020': 11}

BLM: name (ROUTE_PRMRY_NM) n/a, trail number (FAMS_ID or ROUTE_PLAN_ID) n/a, allowed use (PLAN_ALLOW_MODE_TRNSPRT, PLAN_MODE_TRNSPRT, PLAN_OHV_ROUTE_DSGNTN) n/a, closure/season (PLAN_SEASON_RSTRCT_CODE, PLAN_ACCESS_RSTRCT) n/a, difficulty/class (none in schema; OBSRVE_ROUTE_USE_CLASS is a use class) n/a, surface (OBSRVE_SRFCE_TYPE) n/a, accuracy stated (ACCURACY_FT) n/a; by layer { 2: 0, 3: 0, 4: 0, 5: 0, 6: 0, 7: 0 }

OSM highway mix: {'track': 61, 'path': 270, 'footway': 649, 'bridleway': 13, 'steps': 61}; km { track: 25.2, path: 98.5, footway: 96.4, bridleway: 4.9, steps: 0.6 }; excluded {'sidewalk': 621, 'crossing': 333}; OSM-only km by highway { track: 25.2, path: 98.5, footway: 96.4, bridleway: 4.9, steps: 0.6 }

### Box 7 three-sisters

OSM: name 64%, trail number (ref) 32%, allowed use (access/foot/bicycle/horse/motor_vehicle) 36%, closure/season (seasonal, opening_hours, access=no) 4%, difficulty (sac_scale, mtb:scale, trail_visibility) 46%, surface 39%, operator 0%, informal=yes 0%

FS: name 100%, trail number 100%, allowed use (any *_managed/accpt or allowed_terra_use) 100%, closure/season (any *_restricted) 100%, difficulty/class (trail_class, typical_trail_grade) 100%, surface 100%, accessibility_status 0%; subsets {'TrailNFS_MGMT': 15}; trail types {'TERRA': 15}

USGS: name 100%, trail number 100%, allowed use (hikerpedestrian/bicycle/packsaddle/motorcycle) 0%, closure/season (seasonopen) 0%, difficulty/class 0%, surface 100%; originators {'U.S. Forest Service / FS': 29}; source edit dates {'min': '2026-03-29', 'median': '2026-03-29', 'max': '2026-03-29', 'n': 29}; source descriptions {'USFS Trails Update 03/2026': 29}

BLM: name (ROUTE_PRMRY_NM) n/a, trail number (FAMS_ID or ROUTE_PLAN_ID) n/a, allowed use (PLAN_ALLOW_MODE_TRNSPRT, PLAN_MODE_TRNSPRT, PLAN_OHV_ROUTE_DSGNTN) n/a, closure/season (PLAN_SEASON_RSTRCT_CODE, PLAN_ACCESS_RSTRCT) n/a, difficulty/class (none in schema; OBSRVE_ROUTE_USE_CLASS is a use class) n/a, surface (OBSRVE_SRFCE_TYPE) n/a, accuracy stated (ACCURACY_FT) n/a; by layer { 2: 0, 3: 0, 4: 0, 5: 0, 6: 0, 7: 0 }

OSM highway mix: {'path': 28}; km { path: 63.4 }; excluded {}; OSM-only km by highway { path: 18.3 }

