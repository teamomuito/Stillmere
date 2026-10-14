# Heat exchange between rooms, walls, doors, roofs and the outdoors

Gap-report section 3.3 (heat exchange). Reference: `docs/fidelity/decompiled-reference.md`, section 3.3, rows 80-108 (temperature and rooms).
Source tree: the decompiled RimWorld tree at version 1.6.9438.38202 (`/home/user/chillu1/rimworlddecompiled`, read only). That is the pinned build (CLAUDE.md), the same as the source, so every D value here matches it.

Tags:
* **D** = taken from the decompiled source: repository `Chillu1/RimWorldDecompiled`, file and line cited, opened in the clone. No code,
  identifier, comment or string is copied; the logic is written again in Kotlin.
* **C** = Stillmere's own value or rule. Where a vanilla line is cited for comparison only, the value is not adopted under a D tag.
* **?** = `UNVERIFIED`: not in the tree, or not confirmed.
* **not adopted** = Stillmere keeps its own behaviour; the reason is given.

## Status

Implemented in `sim/` (`Climate.kt` `Thermal`, `GameMap.kt` links and doors, `World.kt` `roomClimate` and `doorOpenShares`, `Jobs.kt` step
hook, `SaveGame.kt` version 40). Save format changed: version 40 (see "Save format"). Tests: `HeatExchangeTest` (new, 25 tests),
`LegacyHeatMigrationTest` (new, loads `colony_v39.sav`), and `TemperatureRoomsTest` (changed, see "Tests"). Not committed.

Full suite after the heater change: one run of `./gradlew :sim:test --console=plain`, 826 tests, 0 failures, 0 errors, 0 skipped.

Heater output changed from 14 to 35.62 heat units (`Buildings.kt`, `HEATER`). This is Stillmere's own choice (C): the smallest value to two
decimals that lets a sealed 3 by 3 room with one heater, one door and a built roof reach 21 C at 0 C outdoors. The room reaches 21 and then
cycles between about 16.3 and 21 C (mean 20.4 C); it is not held at 21. The cooler (-14) is unchanged. The output is not saved, so the save
version is not changed (still 40). See "Heater output" under "Consequences".

Full suite: one run of `./gradlew :sim:test --console=plain` for this round: 818 tests, 0 failures, 0 errors, 0 skipped. An earlier run's two
failures (`CaravanBattleTest`, an array index on a map that had never been rebuilt) were fixed by `GameMap.firstCellOfRoom`.

## The model

Each slow step (`Game.SLOW_TICK`, 625 ticks) an indoor room moves toward the temperatures it is linked to. Vanilla's rates are per tick,
and vanilla runs its room passes every 120 ticks (`Verse/MapTemperature.cs:68`). Here each per-tick rate is integrated over the 625 ticks
of the step, so a room's new temperature is `T + (target - T) x (1 - e^-G)`, where `G` is the room's total exponent over the step and
`target` the exponent-weighted mean of its links. This is exact for constant targets and cannot overshoot.

### Links

Each room cell and each cardinal neighbour that is a separator gives one link (`GameMap.buildHeatLinks`).

* **Wall or rock edge.** The cell two steps across, as vanilla's far-side rule, which applies to a neighbour with no region (`Verse/RoomTempTracker.cs:116-175`). Stillmere also applies the rule to deep-water edges (C); whether vanilla gives deep water no region is not checked here (?):
  * a room there: weight 1 toward that room, or toward the outdoors when that room is outdoor (`:251`, one unit per far-side cell);
  * no room there: weight 0.5 toward the outdoors (`:248`, half the outdoor gap), unless that far cell touches the room itself (`:159-168`);
  * off the map: no link (`:155`).
* **Door.** The room on the far side, with the door's share of the time open (see "Doors"). A door with no room behind it gives no link
  (`Verse/GenTemperature.cs:417-420`: a single room changes nothing).
* **Roof.** Not a link; a term per room (see "Roofs").

### Doors

* Passes: while open, every 34 ticks; while closed, every 375 ticks (`RimWorld/BuildingProperties.cs:258, 256`). A slow step has the expected
  passes `open share / 34 + (1 - open share) / 375` per tick.
* Rate: each pass moves a room the door rate divided by its cells, of the way to the mean of the two sides (`RimWorld/BuildingProperties.cs:260`;
  `Verse/GenTemperature.cs:427-453`: the room's change is its gap to the mean times the rate, over its cell count).
* To the outdoors (the outdoors does not move, `Verse/GenTemperature.cs:427-453`): the gap to the outdoors closes by half a share per pass,
  `1 - (1 - share / 2)^passes`, exact (`GenTemperature.cs:408`, the mean is the midpoint).
* Between two indoor rooms (both move, `Verse/GenTemperature.cs:427-453`): each closes its share of the gap to the mean, so the two meet at
  the mean without crossing.
* Open state: vanilla holds a swing door open for at least 300 ticks after a pawn approaches it (`RimWorld/Building_Door.cs:411`, the
  minimum, in the approach handler). Stillmere applies the same 300 on entry instead (C, see "Constants"). While a pawn stands in the door,
  the close delay is re-armed to 110 ticks: `:47` is 110 times the close speed factor (1, `RimWorld/BuildingProperties.cs:250, 254`), `:367`
  re-arms it while a pawn occupies the door, and `:470` is the default close delay of the open call. Vanilla also re-arms the delay once it
  has run out, when a friendly pawn touched the door recently (`:379`); that is not adopted (see "Not adopted"). The time left is a
  per-building counter, `Building.doorHold`. Each slow step spends up to 625 of it; the share spent is the open share of that step. Stillmere
  counts only non-animal pawns for the entry and presence holds (C, `Jobs.kt`, `goTo`; `World.kt`, `doorOpenShares`).
* A slide hatch (`BuildDef.SHUTTER_DOOR`) is open while it is powered, which is Stillmere's own passability rule (`GameMap.walkable`, C). It
  needs no state.
* The state is needed because an open window spans slow steps. It is saved (version 40).

### Roofs

For an indoor room (fewer than a quarter of its cells open, `Verse/Room.cs:105`):

* an unroofed cell exchanges with the outdoors at 0.0007 per tick (`Verse/RoomTempTracker.cs:30, :281`);
* a thin roof (a built roof; Stillmere has one kind, `docs/fidelity/roofs.md`) exchanges at 0.00005 per tick (`:28, :272`);
* the outdoor difference is taken as vanilla takes it: amplified above 100 degrees, 5 times the excess (`:254-262`). The roof's weight in
  the step follows the gap at the start of the step;
* a thick roof (natural rock roof, C: see "Not adopted") pulls a room warmer than 15 degrees down toward 15, never up (`:290-296`;
  `Verse/TemperatureTuning.cs:11`), at 0.00005 per tick.

### Door cells and limits

* In Stillmere a door cell belongs to no room (C; row 103 of the table below). Its temperature is the mean of the distinct rooms on its two
  sides (`Verse/GenTemperature.cs:408, 414`). An outdoor side counts at its own temperature, which Stillmere keeps at the outdoor temperature
  (C). With no room beside it, the outdoor temperature (C).
* Room temperatures are clamped to -273.15 to 1000 degrees (`Verse/RoomTempTracker.cs:56`; `Verse/TemperatureTuning.cs:5, 7`), as vanilla.
  The former -60 to 80 degree bound was C and is replaced.

### Heat from buildings

A heater adds `35.62 x 1.2 / cells` degrees per slow step while the room is below 21 (`RimWorld/CompProperties_TempControl.cs:9` for the
setpoint, which matches). The 35.62 heat units are Stillmere's choice (C), not a vanilla value: the built heater's output is not in the tree
(row 105). A cooler adds `-14 x 1.2 / cells` while the room is above 21; its output is unchanged this round (C). See "Heater output" and
"Consequences".

## Constants

| Constant | Value | Source | Tag |
|---|---|---|---|
| Wall exchange per tick, per unit of weight, per room cell | 0.00017 | `Verse/RoomTempTracker.cs:22` (the wall factor), applied at `:251` | D |
| Weight of a far-side cell with a room behind it | 1 | `Verse/RoomTempTracker.cs:251` (one unit per far-side cell) | D |
| Weight toward the outdoors of a far cell with no room | 0.5 | `Verse/RoomTempTracker.cs:248` | D |
| Sampling of far-side cells | all of them, each slow step | `Verse/RoomTempTracker.cs:20, 222` (20 percent per pass, cycled); the expectation is the same | C |
| Room pass interval | integrated over the slow step | `Verse/MapTemperature.cs:68` (every 120 ticks) | D (interval); C (integration) |
| Door rate (fraction of the gap to the mean per pass, per cell) | 1.0 | `RimWorld/BuildingProperties.cs:260`; `Verse/TemperatureTuning.cs:34` | D |
| Door interval, open | 34 ticks | `RimWorld/BuildingProperties.cs:258`; `Verse/TemperatureTuning.cs:30` | D (code default; the Door def's XML value is definition data, ?) |
| Door interval, closed | 375 ticks | `RimWorld/BuildingProperties.cs:256`; `Verse/TemperatureTuning.cs:32` | D (same caveat) |
| Largest share of the gap closed per pass | 0.99 | C (keeps the exponent finite; vanilla's limiter, `Verse/GenTemperature.cs:421-438`, only holds the share at or below 1) | C |
| Door hold on approach (minimum) | 300 ticks | `RimWorld/Building_Door.cs:411` (the minimum of 300 in the approach handler) | D (value); C (Stillmere applies it on entry, `Jobs.kt`, `goTo`) |
| Door hold while a pawn stands in it | 110 ticks | `RimWorld/Building_Door.cs:47` (110 times the close factor), `:367` (re-armed while a pawn occupies the door), `:470` (default close delay of the open call) | D; C (Stillmere counts non-animal pawns) |
| Door close speed factors | 1 (powered and unpowered) | `RimWorld/BuildingProperties.cs:250, 254` (the opening factors are not used here) | D |
| Opening time (`TicksToOpenNow`) | not used here | `RimWorld/Building_Door.cs:234-241` (the value depends on the door-opening speed stat, which is definition data) | ? (not adopted) |
| Pawns that open doors | Stillmere: non-animal pawns | `RimWorld/Building_Door.cs:428-431` (the first test: a pawn that cannot open doors never opens one; the ability is definition data) | D (rule); C (Stillmere's `!isAnimal` test stands in for the ability) |
| Unroofed cell exchange with the outdoors, per tick | 0.0007 | `Verse/RoomTempTracker.cs:30`, applied at `:281` | D |
| Thin (built) roof exchange with the outdoors, per tick | 0.00005 | `Verse/RoomTempTracker.cs:28`, applied at `:272` | D |
| Amplification of a large outdoor difference | 1 up to 100 degrees; 5 times the excess above | `Verse/RoomTempTracker.cs:257-261` | D |
| Thick roof pull toward the deep-ground temperature, per tick | 0.00005 (the underground rate 0.002 is not adopted) | `Verse/RoomTempTracker.cs:290-296` | D |
| Deep-ground temperature | 15 degrees, pulls down from above only | `Verse/TemperatureTuning.cs:11`; `Verse/RoomTempTracker.cs:290-296` | D |
| Natural roof is thick | yes (Stillmere keeps one flag) | `RimWorld/GenStep_RocksFromGrid.cs:59`: vanilla's thick threshold is an elevation value, not rock noise | C, ? |
| Built roof is thin | yes | `docs/fidelity/roofs.md` (one kind, thin) | C |
| Indoor rule (quarter open) | unchanged | `Verse/Room.cs:99-109`, `:105` | D (already in place) |
| Room temperature limits | -273.15 to 1000 degrees | `Verse/RoomTempTracker.cs:56`; `Verse/TemperatureTuning.cs:5, 7` | D (replaces -60 to 80, C) |
| Door cell temperature | mean of the rooms on its two sides | `Verse/GenTemperature.cs:408, 414` | D |
| Heater and cooler setpoint | 21 | `RimWorld/CompProperties_TempControl.cs:9` (row 104, matches) | D |
| Heater heat per unit | 35.62 | Stillmere's choice, not a vanilla value. The built heater's output is definition data the tree does not hold; `RimWorld/CompProperties_TempControl.cs:7` gives only the code default (`energyPerSecond` 12). Chosen as the smallest value to two decimals that reaches 21 C at 0 C outdoors (threshold 35.618; 35.61 settles at 20.995, see "Heater output") | C (Stillmere choice) |
| Cooler heat per unit | -14 | Stillmere's own number, unchanged this round; not vanilla (definition data, not in the tree) | C, ? (unchanged) |
| Degrees per slow step per unit of heat, per room cell | 1.2 | Stillmere's own number, unchanged this round | C |
| Slide hatch open | while powered | Stillmere's passability rule (`GameMap.walkable`) | C |
| Door open timing | on entry; vanilla opens on approach (`:401-412`) | Stillmere's movement step (`Jobs.kt`, `goTo`) | C |

## Rows of the reference table (section 3.3)

| Row | Before | This round |
|---|---|---|
| 80 Temperature granularity (per room) | matches | kept |
| 81 Outdoor rooms track the outdoor temperature | matches | kept |
| 82 Heat pushed into outdoor space is discarded | matches | kept |
| 83 Heat moves between neighbouring rooms through walls and doors | matches | extended: far-side rule for rock (deep water too, C), door rule, roofs |
| 84 Wall material insulation | matches | kept (no material term in vanilla) |
| 85 Room temperature limits | differs | **adopted**: -273.15 to 1000 (`Verse/RoomTempTracker.cs:56`) |
| 99 Wall heat exchange rate | differs | **adopted**: 0.00017 per tick per unit weight per cell (`:22`, `:251`) |
| 100 Vent exchange | differs (missing) | **not adopted**: Stillmere has no vent building, and a building is outside this area. Open |
| 101 Indoor and outdoor rule | differs | unchanged this round (adopted in the roofs round) |
| 103 Doorway as a room | differs | **not adopted** as a room: a door belongs to no room in Stillmere (rows 80-83). Its temperature is **adopted** (above). A door as the far cell of a wall edge is approximated (below) |
| 104 Heater setpoint | matches | kept |
| 105 Heater output and efficiency | ambiguous | **Stillmere's choice**: the built heater's output is definition data the tree does not hold (`RimWorld/CompProperties_TempControl.cs:7`). Heat per unit is set to 35.62 (C, see "Heater output"). No efficiency falloff (C) |

Rows outside this area (86-98, 106-108) are not changed here.

## Not adopted, with the reason

* **Vent exchange** (row 100): no vent building in Stillmere. Open.
* **Doorway as a room** (row 103): a door belongs to no room in Stillmere, so the doorway's room stats and region rules are not adopted.
  Only its temperature is.
* **A door as the far cell of a wall edge**: Stillmere counts it as a cell with no room (half weight toward the outdoors). Vanilla's treatment
  of that case is not checked against a cited line (?). The configuration (a door directly behind a one-cell wall) is rare. Open.
* **Friendly-touch re-arm** (`RimWorld/Building_Door.cs:379`): when the close timer has run out, a friendly pawn that touched the door recently
  keeps it open. Not adopted: Stillmere has no friendly-touch state (C).
* **Vacuum branch** (`Verse/RoomTempTracker.cs:231-245`, `:271`): Stillmere has no vacuum biome (no vacuum biome in `sim/`).
* **Underground rate** (`:295`, 0.002): Stillmere has no underground map generator (no underground map generator in `sim/`).
* **Thin and thick natural roof split** (`RimWorld/GenStep_RocksFromGrid.cs:59`): the elevation threshold that decides it is not kept, so every
  natural roof is treated as thick. **?** for the rock masses where the split would matter.
* **Opening time of a door** (`Building_Door.cs:234-241`): depends on the door-opening speed stat, which is definition data. **?**.
* **Vanilla's 120-tick passes and their sampling** (`RoomTempTracker.cs:20-22`, `:222`): replaced by exact integration of the per-tick rates
  over the slow step. The expectation matches; the variance is not reproduced.
* **Heater output** (row 105): the tree has no value for the built heater, so the output is Stillmere's choice (35.62, C). See "Heater output".
  No efficiency falloff is applied (C).

## Consequences

### Heater output

The heater output is 35.62 heat units (C, Stillmere's choice). The reference has no value for the built heater (row 105), so the output is
set by what the room needs. The test room is a sealed 3 by 3 room (9 cells, 11 wall edges, one closed door, one built roof). The figures
below come from a double-precision replay of the slow step for this room; the two heated-room tests in `TemperatureRoomsTest` check them:

* It closes 0.2261 of its gap to the outdoors per slow step. The exponent is 0.1299 (walls) + 0.0953 (door) + 0.0313 (roof) = 0.2564.
* With the heater always on, the room settles where `output x 1.2 / 9` equals 0.2261 x 21, that is at an output of 35.618. Below that the
  heater cannot reach 21 at 0 C outdoors: 35.61 settles at 20.995 C and never reaches 21. The smallest value to two decimals that reaches
  21 is 35.62 (it settles at 21.001 C, so it reaches 21 only just).
* For comparison, 36 settles at 21.2 C. Over a long run it cycles between 16.3 and 21.05 C with a mean of 19.7 C, while 35.62 has a mean of
  20.4 C, so 35.62 keeps the room nearer 21 on average.
* The cooler (-14) is unchanged. It holds 21 up to about 29 C outdoors for the same room (it settles at 21 when outdoors are 29.25 C).
* The output is a game value and is not saved, so the save version does not change.

### What the heated room does now

* At 0 C outdoors the room reaches the setpoint and the thermostat cuts out there. Over a long run it cycles between 16.25 and 21.0 C (the
  peak is 21.0002 C) with a mean of 20.4 C, and it is below 19 C for about 11 percent of the time. Control acts once per slow step (625
  ticks), so each pulse adds 4.75 degrees, and the room loses 22.6 percent of its temperature per step between pulses. It is reached, not
  held. Before the heater round (output 14) the same room settled near 8 C at 0 C and never reached 21; that is why the output was changed.
  The swing follows from the slow step and the exchange, not from a vanilla value; vanilla's swing is not checked (?).
* At 14 C outdoors the swing is 19.5 to 24.1 C (`TemperatureRoomsTest.anEnclosedRoomWithAHeaterHoldsItsTargetTemperature`, band 19 to 25).
  The old band of 19 to 23 cannot hold for any output that reaches 21 at 0 C: at 14 C the pulse peaks at about 24 (24.1 at 35.62, 24.2 at 36).
* Colonists in heated rooms in winter reach 21 at 0 C again, but are not held there: about 11 percent of the time they are below 19 C,
  down to 16.3 C. A finer control step would narrow this; it is not made here.

## Save format

Version 40 adds two things, both read only from version 40 (`SaveGame.kt`, comment above `VERSION`):
* one integer per building, the swing door's open time left (`Building.doorHold`), written after the occupant;
* at the end of the stream, the number of indoor rooms, then for each one the first cell of the room and its temperature. A load applies
  them after the rooms are rebuilt, so a loaded game keeps the heat its rooms had. Before this change a load reset every indoor room to the
  outdoor temperature (the temperatures were not saved at all). A map that was never rebuilt has no rooms and writes none.

A version-39 save has neither: its doors start shut and its indoor rooms start at the outdoor temperature, as before. The fixture
`sim/src/test/resources/legacy/colony_v39.sav` (with `colony_v39_expect.txt`) was written by the version-39 code before the change, by a
temporary generator since deleted. `LegacyHeatMigrationTest` loads it and checks the new fields. The fixture is not committed (no commit was
asked for); it is untracked in the working tree.

## Tests

* `HeatExchangeTest` (new, 25 tests): the wall rate and its closed form; half weight; roof rates; amplification above 100; the thick roof
  pull; door passes and the open-to-closed ratio (375 / 34); a door to the outdoors; two rooms on an open door meeting at their mean
  without crossing; a one-cell room staying in range; a door with no room behind it; the door-cell mean; a rock edge at half weight; the door
  hold (300, then spent), the presence hold (110), the slide hatch (open while powered); the temperature limits; no overshoot; the save round
  trip of the door hold and the room temperature; same seed, same bits with doors and colonists.
* `TemperatureRoomsTest` (changed): the three `Thermal.step` calls take the new arguments; `anEnclosedRoomWithAHeaterHoldsItsTargetTemperature`
  now runs at 14 C outdoors and checks 19 to 25 C (the swing is 19.5 to 24.1, from the 4.75-degree pulses of the 35.62 output; the band was 19
  to 23 with the output of 14, and 23 cannot hold for an output that reaches 21 at 0 C, see "Heater output"). `aCoolerHoldsAHotRoomAtItsTarget`
  runs at 30 C outdoors (the cooler is unchanged). New: `aHeaterReachesTheSetpointAtZeroDegreesOutdoors` (peak 21.0002 C, trough 16.25 C,
  mean about 20.3 C over the sampled steps, at 0 C outdoors; it checks that the room reaches 21 but does not hold it). It replaces
  `aHeaterCannotHoldTheSetpointAtZeroDegreesOutdoors`, whose settle point of 8 C the 35.62 output makes false. The rooms in these tests have no colonists, since one
  standing in the doorway holds the door open. `aDoorLeaksMoreHeatThanAWall` and `heatMovesBetweenNeighbouringRoomsThroughADoor` are unchanged.
* Version assertions: `RoofsTest` and `ColonyMapSizeTest` expect 40; `GrassTest` checks `SaveGame.VERSION` (its message said 39); the v39
  fixture is in `ColonyMapSizeTest`'s table at 250 by 250.
* The old `TemperatureRoomsTest` heat values were calibrated to the old rate (0.048 per unit per cell); the new numbers above replace them.

## Open

* Heater output (row 105): 35.62 is Stillmere's choice (C), the smallest value to two decimals that reaches 21 C at 0 C outdoors (threshold
  35.618). The owner may change it; it is a game value with no save change.
* Swing of a heated room under thermostat control (once per slow step): vanilla's swing is not checked (?). Stillmere's is one pulse wide.
* Vent exchange (row 100).
* A door as the far cell of a wall edge (above).
* The door def's XML values for the intervals, and the opening time (definition data).
* Natural roof thickness (above).
* Two indoor rooms of unequal size on one door: the mean rule is applied once per slow step. It is exact for equal sizes; for unequal
  sizes the error is not measured.
