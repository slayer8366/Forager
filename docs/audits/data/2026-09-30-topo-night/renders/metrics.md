| map zoom | source tileSize | tile level | dp per tile | letter height (dp) | tiles for a 412 x 892 dp view | x today |
|---|---|---|---|---|---|---|
| 7 | 256 | 8 | 256.0 | 10.0 | 12 | 1.0x |
| 7 | 128 | 9 | 128.0 | 5.0 | 34 | 2.9x |
| 7 | 64 | 10 | 64.0 | 2.5 | 111 | 9.5x |
| 7 | 32 | 11 | 32.0 | 1.2 | 401 | 34.2x |
| 8.5 | 256 | 10 | 181.0 | 7.1 | 19 | 1.0x |
| 8.5 | 128 | 11 | 90.5 | 3.5 | 60 | 3.1x |
| 8.5 | 64 | 12 | 45.3 | 1.8 | 209 | 10.8x |
| 8.5 | 32 | 13 | 22.6 | 0.9 | 776 | 40.0x |
| 9.4 | 256 | 10 | 337.8 | 13.2 | 8 | 1.0x |
| 9.4 | 128 | 11 | 168.9 | 6.6 | 22 | 2.7x |
| 9.4 | 64 | 12 | 84.4 | 3.3 | 68 | 8.4x |
| 9.4 | 32 | 13 | 42.2 | 1.6 | 238 | 29.5x |
| 11 | 256 | 12 | 256.0 | 10.0 | 12 | 1.0x |
| 11 | 128 | 13 | 128.0 | 5.0 | 34 | 2.9x |
| 11 | 64 | 14 | 64.0 | 2.5 | 111 | 9.5x |
| 11 | 32 | 15 | 32.0 | 1.2 | 401 | 34.2x |

smallest tileSize whose level is >= 11 (the dark regime), per map zoom: S <= 2^(mz - 1.5)
  mz 5: tileSize <= 11 -> level 11, 8.0 dp/tile, letters 0.3 dp, tiles 5906
  mz 6: tileSize <= 23 -> level 11, 16.0 dp/tile, letters 0.6 dp, tiles 1518
  mz 7: tileSize <= 45 -> level 11, 32.0 dp/tile, letters 1.2 dp, tiles 401
  mz 8: tileSize <= 91 -> level 11, 64.0 dp/tile, letters 2.5 dp, tiles 111
  mz 8.5: tileSize <= 128 -> level 11, 90.5 dp/tile, letters 3.5 dp, tiles 60
  mz 9: tileSize <= 181 -> level 11, 128.0 dp/tile, letters 5.0 dp, tiles 34
  mz 9.4: tileSize <= 239 -> level 11, 168.9 dp/tile, letters 6.6 dp, tiles 22

| render | mean | median (ground) |
|---|---|---|
| mz11_c_topo_v1_hillshade | 0.282 | 0.261 |
| mz11_ref_topo_v1 | 0.284 | 0.252 |
| mz7_0_today_topo_v1 | 0.422 | 0.410 |
| mz7_a_topo_v1_tilesize128 | 0.436 | 0.427 |
| mz7_a_topo_v1_tilesize32_crop96dp | 0.229 | 0.216 |
| mz7_a_topo_v1_tilesize64 | 0.444 | 0.450 |
| mz7_b_osm_v1 | 0.219 | 0.224 |
| mz7_c_osm_v1_hillshade | 0.223 | 0.209 |
| mz8.5_0_today_topo_v1 | 0.483 | 0.502 |
| mz8.5_a_topo_v1_tilesize128 | 0.230 | 0.191 |
| mz8.5_b_osm_v1 | 0.187 | 0.178 |
| mz8.5_c_osm_v1_hillshade | 0.199 | 0.190 |
| mz9.4_0_today_topo_v1 | 0.488 | 0.497 |
| mz9.4_a_topo_v1_tilesize128 | 0.226 | 0.187 |
| mz9.4_a_topo_v1_tilesize64 | 0.231 | 0.203 |
| mz9.4_b_osm_v1 | 0.172 | 0.167 |
| mz9.4_c_osm_v1_hillshade | 0.189 | 0.179 |
| switch_9.4_osm_v1 | 0.172 | 0.167 |
| switch_9.6_topo_v1 | 0.229 | 0.192 |
