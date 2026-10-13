# Temperature and rooms

Plan and reference for room detection, room temperature, outdoor temperature and room stats. Gap-report section 3.3 holds the audit.

**This file did not exist when the task was written.** The task said to check against "reference values recorded in" it, but no such
record was in the repository, so the values below are the first record. Direct access to the RimWorld Wiki was not available
(`rimworldwiki.com` does not resolve from this environment), so the only vanilla evidence is a secondary summary from web searches on
2026-10-09 of the wiki's Temperature, Heat, Vent, Room, Impressiveness and Cleanliness pages. Anything those summaries did not state is
`UNVERIFIED`. No vanilla number below was confirmed against the game or a primary source. A row is tagged V only where it gives a
URL; a row from a summary with no recorded URL is M, UNVERIFIED.

## Vanilla reference

| Fact | Value | Conf |
|---|---|---|
| Temperature is stored per room, not per cell: every cell of a room shares one temperature | yes | D: one `temperatureInt` per room (`Verse/RoomTempTracker.cs`) |
| A room's temperature is shaped by its roof and by the temperature of tiles next to its walls | yes | D: wall term `Verse/RoomTempTracker.cs:251`, roof terms `:281` and the thin roof term |
| Doors, vents and missing roof tiles make heat exchange faster | yes | D: door intervals and rate (`Verse/TemperatureTuning.cs:30-34`), vent rate 14 (`:36`), no-roof rate 0.0007 (`Verse/RoomTempTracker.cs:30`) |
| A room that is 75% roofed or less stays at the outdoor temperature (counts as outdoors) | 75% | D, `Verse/Room.cs:103-108` (quarter rule at :105); see `roofs.md` |
| Removing a wall or door that borders the outside turns the room into outdoor space | yes | D in part: a room uses the outdoor temperature when it touches the map edge, or when its open roof cells reach a quarter of its cells, rounded up (`Verse/Room.cs:99-105`); the wall removal that opens a room is not traced here |
| Vents move heat between rooms but are not fully efficient; each extra vent loses efficiency | qualitative | D for the rate: a running vent equalises at 14 (`RimWorld/Building_Vent.cs:38`, `Verse/TemperatureTuning.cs:36`); the loss per extra vent is not in this code, so `UNVERIFIED` |
| Outdoor temperature depends on latitude, time of day, day of year and biome; the temperature events deep chill, smoke pall and scorching spell shift it (names in names.md section 6.3) | yes | V (`https://rimworldwiki.com/wiki/Temperature`, a search result read in round 9; the page itself was not read) |
| Temperate forest seasonal range: about 35 C in summer to -25 C in winter (extremes, not means) | | V: 35 C to -25 C on the Biome page (https://rimworldwiki.com/wiki/Biome, search snippet), but the Temperate forest page gives a minimum of -10 C (https://rimworldwiki.com/wiki/Temperate_forest); the sources conflict, so `UNVERIFIED` |
| Room stats: Impressiveness is built from Wealth, Beauty, Space and Cleanliness; mood effects depend on the impressiveness *stage*, not the exact value | | D for the inputs: `RimWorld/RoomStatWorker_Impressiveness.cs:8-23`; the stage effect is not checked here, `UNVERIFIED` |
| Cleanliness affects medical treatment quality, research speed and food-poisoning chance | | M, UNVERIFIED (search summary, no URL recorded) |
| Standard heater and cooler output (heat per second, target temperature) | ? | D for the formulas (`RimWorld/Building_Heater.cs:15-16`, `RimWorld/Building_Cooler.cs:8-41`, `Verse/GenTemperature.cs:349-366`) and the default target 21 (`RimWorld/CompTempControl.cs:11`); the energy per second is XML (code default 12, `RimWorld/CompProperties_TempControl.cs:7`). V (search snippets): 21 heat per second and 175 W (https://rimworldwiki.com/wiki/Heater); the sim's 35.62 and 200 W differ and are not changed (owner decision) |
| Exact heat-exchange rates through walls, doors and vents | ? | D: wall 0.00017 (`Verse/RoomTempTracker.cs:22`), door rate 1 with intervals 34 and 375 ticks (`Verse/TemperatureTuning.cs:30-34`), vent rate 14 (`:36`), 120-tick interval (`:28`), roof 0.0007 (`Verse/RoomTempTracker.cs:30`). The sim's `Climate.kt` values match these |
| Any per-material wall insulation for room heat | none in the room heat code | D: `Verse/RoomTempTracker.cs` and `Verse/GenTemperature.cs` have no wall material term; insulation appears only in the apparel comfort range (`Verse/GenTemperature.cs:70-71`) |
| Day/night swing and its peak hour | ? | D: amplitude 7 (`Verse/TemperatureTuning.cs:20`, `Verse/GenTemperature.cs:194-202`); coolest at day fraction 0.18 (4.32 h at longitude 0; the sim was 3 h before round 22), warmest at 0.68 (16.32 h) |
| Impressiveness formula, stage thresholds, per-building and per-terrain beauty and cleanliness values | ? | D for the formula (`RimWorld/RoomStatWorker_Impressiveness.cs:8-23`); the sim's weights differ and are not changed. The stage thresholds and the per-building beauty and cleanliness are XML, `UNVERIFIED` |
| Whether a door cell belongs to a room | recalled as its own region, in neither room; **UNVERIFIED** | M |

### Where the task's assumptions differ from what was found

* **"Per-cell temperature simulation."** Vanilla keeps one temperature per room (M, UNVERIFIED; row 15). Stillmere already does this, and a per-cell grid
  would be *less* faithful, so it was not built. Heat is exchanged between **neighbouring rooms** and between rooms and the outdoors,
  through each wall, door and rock edge. `GameMap.tempAt(cell)` still answers per cell (the room's value, or the outdoor value).
* **"Insulation from wall and building materials."** No per-material wall insulation was found for vanilla. `Thermal.wallConductance`
  takes the material but returns the same value for all, so the hook exists without inventing numbers. Doors and rock do differ (below).
* **Roofs** are modelled (`docs/fidelity/roofs.md`, version 38). Indoor/outdoor is decided by the roofed share: a room is indoor only
  when it does not touch the map edge and fewer than a quarter of its cells are open. Enclosure alone no longer makes a room indoor.

## What the code does now

### Outdoor temperature (`Climate`)

`outdoorTemp = seasonalBase(biome, day) + diurnal(biome, hour) + tempOffset + weatherOffset(weather)`

| Constant | Value | Conf |
|---|---|---|
| Biome seasonal temperatures | `Biome.springT/summerT/fallT/winterT` (e.g. temperate 13, 24, 10, -3) | C |
| Blend toward next season | `(dayOfSeason - 1) / 15` of half the gap | C |
| Day/night swing | +/-7 C in every biome (`DIURNAL_SWING`) | D, `Verse/TemperatureTuning.cs:20` (the daily amplitude, 7), the same amplitude as the cosine at `Verse/GenTemperature.cs:194-202`, which has no biome term. The +/-10 C that desert and arid had (`DIURNAL_SWING_DRY`, C, Stillmere's own value) had no vanilla basis and is removed (round 25). The reference's temperature code does have biome terms: a constant outdoor temperature (`Verse/MapTemperature.cs:29-31, 45-47`) and the vacuum factor (`Verse/GenTemperature.cs:451`). Neither is a daily swing, and Stillmere models neither |
| Coldest / warmest hour | 04:19 / 16:19 (sine, 24 h period; round 22, was 03:00 / 15:00) | D: the base game's cosine has its minimum at day fraction 0.18 and its maximum at 0.68, at longitude 0 (`Verse/GenTemperature.cs:194-202`, `RimWorld/GenDate.cs:145-147`) |
| Random daily variation (not modelled) | vanilla adds a per-tile Perlin offset, scaled by 7, to the outdoor temperature on top of the sun cycle; the swing above is the sun cycle only | D, `RimWorld.Planet/TileTemperaturesComp.cs:53-56` (the x 7 at :55), used for the outdoor temperature at :50 and :87. Not in the sim; a gap, not adopted |
| Weather offsets | rain -2, snow -3, thunder -3, overcast -1 | C |
| Scorching spell / deep chill / smoke pall | +16 / -17 / -13 (set in `Events.kt`; unchanged by the renames) | C (gap-report 3.3) |
| Time base | 2,500 ticks per hour, 60,000 per day, 15 days per season (`docs/fidelity/time.md`) | V (https://rimworldwiki.com/wiki/Ticks, https://rimworldwiki.com/wiki/Time) |

Sine uses `StrictMath` so the result is bit-identical on any JVM and on Android (gap-report finding R3).

### Room detection (`GameMap.rebuildRooms`)

* Flood fill over four-neighbour cells. Walls, doors and rock separate; deep water is not part of any room.
* **A door is a separator, so it splits rooms** and belongs to none. It is *not* an opening in the sense of a gap: a room whose only
  exit is a door stays indoor. A missing wall segment (no building) joins the room to whatever is behind it.
* A region is outdoor if it touches the map edge. Otherwise it is indoor when fewer than a quarter of its cells are open: unroofed,
  neither a built roof nor natural roof (`GameMap.roomIsIndoor`, Verse/Room.cs:103-108). The 75 percent rule is vanilla's (D). Before
  version 38 the rule was at most 700 cells or a natural-roof majority; that rule now only migrates old saves (`roofs.md`).
* **Room size** (`roomSize`) is the number of floor cells. Wall, door and rock cells are not counted.

### Heat (`Thermal.step`, once per slow tick = 625 ticks)

**Superseded in part by `docs/fidelity/heat-exchange.md`** (round of section 3.3, heat exchange). The rates, the door and roof rules, the
limits and the save of room temperatures are there, each with its source. In brief: walls and rock exchange at vanilla's per-tick rates
(0.00017 per unit weight per cell, far-side rule with half weight to the outdoors through rock), a door closes its share of the gap on each
pass (every 34 ticks open, 375 closed), an unroofed cell and a thin roof exchange with the outdoors (0.0007 and 0.00005 per tick), a thick
roof pulls a warm room toward 15 C, a door cell reads the mean of its sides, and room temperatures are clamped to -273.15 to 1000 C.
A heater adds 35.62 heat units (Stillmere's choice, C, `heat-exchange.md`, "Heater output"); a cooler still adds -14 (unchanged).

The earlier constants of this section (`EXCHANGE` 0.048, `MAX_RATE`, door conductance 3, rock conductance 0.3 toward a ground temperature
of (outdoor + 14) / 2, and the -60 to 80 C clamp) no longer exist in the code.

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

## Tests

`TemperatureRoomsTest` (19 tests; the rooms in them are roofed, since a room is indoor only when roofed) and `HeatExchangeTest` (25 tests,
see `heat-exchange.md`).

* Season order and a hand-calculated day mean (temperate, day 22: 20.733 C); day/night extremes at 04:19 and 16:19 (round 22; was 03:00 and 15:00) and a 14 C swing.
* Room detection on a 10x6 plan: with a door the rooms have 16 and 12 cells and the door belongs to neither; an opening gives one
  room of 29; a wall keeps them apart; an outside door keeps a room indoor; a broken outer wall makes it outdoor.
* A closed 3x3 room with a powered heater (output 35.62) swings 19.5 to 24.1 C at 14 C outdoors; at 0 C it reaches 21 C and cycles between
  16.3 and 21.0 C with a mean of 20.4 C (control acts once per slow step, so it is reached, not held); a cooler holds a 30 C room near 21 C. These values come from the vanilla rates and the built roof, not the
  old constants.
* A door leaks more than a wall; heat moves between rooms through a door; uniform temperatures do not drift.
* Same seed and inputs give bit-identical room temperatures.
* Room stats for a fixed layout: beauty 0.65, cleanliness -1/6, wealth 80, impressiveness 5.264, worked by hand in the test.
