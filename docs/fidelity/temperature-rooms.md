# Temperature and rooms

Plan and reference for room detection, room temperature, outdoor temperature and room stats. Gap-report section 3.3 holds the audit.

**This file did not exist when the task was written.** The task said to check against "reference values recorded in" it, but no such
record was in the repository, so the values below are the first record. Direct access to the RimWorld Wiki was not available
(`rimworldwiki.com` does not resolve from this environment), so the only vanilla evidence is a secondary summary from web searches on
2026-10-09 of the wiki's Temperature, Heat, Vent, Room, Impressiveness and Cleanliness pages. Anything those summaries did not state is
`UNVERIFIED`. No vanilla number below was confirmed against the game or a primary source.

## Vanilla reference

| Fact | Value | Conf |
|---|---|---|
| Temperature is stored per room, not per cell: every cell of a room shares one temperature | yes | V |
| A room's temperature is shaped by its roof and by the temperature of tiles next to its walls | yes | V |
| Doors, vents and missing roof tiles make heat exchange faster | yes | V |
| A room that is 75% roofed or less stays at the outdoor temperature (counts as outdoors) | 75% | V |
| Removing a wall or door that borders the outside turns the room into outdoor space | yes | V |
| Vents move heat between rooms but are not fully efficient; each extra vent loses efficiency | qualitative | V |
| Outdoor temperature depends on latitude, time of day, day of year and biome; cold snaps, volcanic winters and heat waves shift it | yes | V |
| Temperate forest seasonal range: about 35 C in summer to -25 C in winter (extremes, not means) | | V |
| Room stats: Impressiveness is built from Wealth, Beauty, Space and Cleanliness; mood effects depend on the impressiveness *stage*, not the exact value | | V |
| Cleanliness affects medical treatment quality, research speed and food-poisoning chance | | V |
| Standard heater and cooler output (heat per second, target temperature) | ? | UNVERIFIED |
| Exact heat-exchange rates through walls, doors and vents | ? | UNVERIFIED |
| Any per-material wall insulation for room heat | I recall none; **UNVERIFIED** | M |
| Day/night swing and its peak hour | ? | UNVERIFIED |
| Impressiveness formula, stage thresholds, per-building and per-terrain beauty and cleanliness values | ? | UNVERIFIED |
| Whether a door cell belongs to a room | recalled as its own region, in neither room; **UNVERIFIED** | M |

### Where the task's assumptions differ from what was found

* **"Per-cell temperature simulation."** Vanilla keeps one temperature per room (V). Stillmere already does this, and a per-cell grid
  would be *less* faithful, so it was not built. Heat is exchanged between **neighbouring rooms** and between rooms and the outdoors,
  through each wall, door and rock edge. `GameMap.tempAt(cell)` still answers per cell (the room's value, or the outdoor value).
* **"Insulation from wall and building materials."** No per-material wall insulation was found for vanilla. `Thermal.wallConductance`
  takes the material but returns the same value for all, so the hook exists without inventing numbers. Doors and rock do differ (below).
* **Roofs** are still not modelled. Indoor/outdoor is decided by enclosure (see below), not by a roofed-cell fraction. Divergent.

## What the code does now

### Outdoor temperature (`Climate`)

`outdoorTemp = seasonalBase(biome, day) + diurnal(biome, hour) + tempOffset + weatherOffset(weather)`

| Constant | Value | Conf |
|---|---|---|
| Biome seasonal temperatures | `Biome.springT/summerT/fallT/winterT` (e.g. temperate 13, 24, 10, -3) | C |
| Blend toward next season | `(dayOfSeason - 1) / 15` of half the gap | C |
| Day/night swing | +/-7 C (+/-10 C desert and arid) | C |
| Coldest / warmest hour | 03:00 / 15:00 (sine, 24 h period) | C |
| Weather offsets | rain -2, snow -3, thunder -3, overcast -1 | C |
| Heat wave / cold snap / volcanic winter | +16 / -17 / -13 (set in `Events.kt`) | C (gap-report 3.3) |
| Time base | 2,500 ticks per hour, 60,000 per day, 15 days per season (`docs/fidelity/time.md`) | V |

Sine uses `StrictMath` so the result is bit-identical on any JVM and on Android (gap-report finding R3).

### Room detection (`GameMap.rebuildRooms`)

* Flood fill over four-neighbour cells. Walls, doors and rock separate; deep water is not part of any room.
* **A door is a separator, so it splits rooms** and belongs to none. It is *not* an opening in the sense of a gap: a room whose only
  exit is a door stays indoor. A missing wall segment (no building) joins the room to whatever is behind it.
* A region is outdoor if it touches the map edge. Otherwise it is indoor when it has at most 700 cells, or when more than half its
  cells are under natural (mountain) roof. Player roofs do not exist; the 700-cell limit stands in for "the player roofed it".
  Divergent from the 75% rule (V).
* **Room size** (`roomSize`) is the number of floor cells. Wall, door and rock cells are not counted.

### Heat (`Thermal.step`, once per slow tick = 625 ticks)

For each indoor room `r` with `size` cells: `T' = T + (mean - T) * rate + heat / size * 1.2`, where
`rate = min(0.5, 0.048 * Σc / size)`, `Σc` is the sum of conductances of its boundary edges and `mean` is the conductance-weighted
average temperature of whatever lies straight through each edge. Outdoor rooms are set to the outdoor temperature.

| Constant | Value | Conf |
|---|---|---|
| `EXCHANGE` | 0.048 per unit conductance per cell (a 5x5 room with one door closes about 4% of the gap per slow tick) | C |
| Wall edge conductance | 1.0, all materials | C |
| Door edge conductance | 3.0 | C |
| Rock edge conductance | 0.3, linked to ground temperature `(outdoor + 14) / 2` | C |
| Heat gain | `building heat / size * 1.2` degrees per slow tick | C |
| Heater / cooler | +14 / -14 heat; a heater works only below 21 C, a cooler only above | C (replaces nothing known) |
| Other heat sources | campfire +10, torch lamp +1.5, stove +3, smithy +4, generator +3, passive cooler -9 | C |
| Temperature clamp | -60 to 80 C | C |

Heat now flows **between neighbouring rooms** through doors and walls (before, rooms only exchanged with the outdoors), and the
old special-case "underground rooms hold a mild constant" is replaced by the rock-edge link.

### Room stats (`computeRoomStats`, `RoomRules`)

| Stat | Rule | Conf |
|---|---|---|
| Beauty | average over room cells of: floor beauty (bare = -0.2), building beauty x quality (counted once per building), +0.4 per non-tree plant, -0.9 per filth level, -0.1 under natural roof | C |
| Cleanliness | `-(total filth) / cells` | C |
| Wealth | building total cost + 50% of the market value of stacked items | C |
| Impressiveness | `beauty * 6 * min(1, cells/14) + min(cells,60) * 0.18 + min(wealth/450, 9) + cleanliness * 2.5` | C |
| Impressiveness stages | `impressLabel` thresholds 4, 8, 12, 17, 24, 34, 50, 80 | C |
| Role | prison, hospital, barracks (3+ beds), bedroom, dining (table and chair), rec, kitchen, workshop | C |

Stillmere's contributions per building (`BuildDef.beauty`) and floors (wood 0.3, stone 0.6, steel 0.3, carpet 1.1) are custom.
Which buildings and terrain count toward beauty and cleanliness in vanilla, and by how much, **could not be confirmed**.

## Tests (`TemperatureRoomsTest`, 18 tests)

* Season order and a hand-calculated day mean (temperate, day 22: 20.733 C); day/night extremes at 03:00 and 15:00 and a 14 C swing.
* Room detection on a 10x6 plan: with a door the rooms have 16 and 12 cells and the door belongs to neither; an opening gives one
  room of 29; a wall keeps them apart; an outside door keeps a room indoor; a broken outer wall makes it outdoor.
* A closed 3x3 room with a powered heater stays in 19.5 to 23.5 C at 0 C outdoors, and sinks to 0 C when unpowered; a cooler holds a
  40 C day near 21 C.
* A door leaks more than a wall; heat moves between rooms through a door; uniform temperatures do not drift.
* Same seed and inputs give bit-identical room temperatures.
* Room stats for a fixed layout: beauty 0.65, cleanliness -1/6, wealth 80, impressiveness 5.264, worked by hand in the test.
