# Generated ruins

Plan, constants and rows for ruins on colony maps. Gap-report section 3.2 holds the audit. The vanilla values come from the
decompiled source at version 1.6.9438.38202 (tag **D**), read from the clone at `/home/user/chillu1/rimworlddecompiled`. The
pinned build is 1.6.9438.38202, the same as the source, so the D values match it (see `decompiled-reference.md`).

The reference's section 3.2 has no ruins row. Every ruin value below is cited to its decompiled file and line directly, which
the reference allows for a value with no table row. The loot table is not in the source: its weights and counts are XML in
vanilla, so they are **C** (Stillmere's own choice) and **UNVERIFIED**. Round 20 opened every D citation at its line in the clone.
Each row whose cited line did not hold its claim was changed to C or not adopted, with the reason (see "Round 20 changes").

## Plan

1. **Where.** Ruins are generated on colony maps only: a map that has a world tile (`GameMap.generate` with a non-null `local`).
   That covers new colonies and settled colonies. Battle maps have no world tile, so they get no ruins and every fight is
   unchanged. `GameMap.generate` takes a `ruins` flag (default true) so the tests can make the same map without ruins.
2. **Shape.** A ruin is a square of side drawn from the size curve (D). It is clipped to the map (D). Its walls are the outline
   of that square (C: vanilla's walls come from a generated shape, see the table). Its interior is floored with stone tiles. The
   walls are damaged by the vanilla rule for destroyed buildings (D: a wall far from the damage side falls more often, the destroy
   chance rises as the power 1.32 of the position, and the pass stops once 65 percent of the buildables are gone). One wall is an
   entrance.
3. **Walls and floors.** Ruin walls are `WOOD_WALL` buildings, built, in stone or steel (wood is excluded by the vanilla rule,
   D). A surviving wall keeps a hit-point share from its position (D). The interior floor is `STONE_FLOOR` where it survives.
4. **Placement.** Each ruin's centre is drawn from the scatterer rules (D): 1000 tries, at least 5 cells from the edge, at least
   10 cells from an earlier centre, and a footprint not within 2 cells of another ruin (D). The footprint must be buildable
   ground: passable and not shallow water. The vanilla rule refuses river and road ground (`RimWorld/GenStep_ScatterRuinsSimple.cs:90-93`)
   and artificial buildings (`:98-101`). Stillmere's rock is terrain, so its rock test and its any-building test are C.
5. **Connectivity guard (C).** After a ruin's walls are chosen, every side of the map must still be reachable from the colony's
   start with the walls blocking. A ruin that would seal a side is dropped. The check uses the same side rule that
   `connectSides` uses. This is Stillmere's guard. The vanilla game does not seal sides this way, and it is not in the source.
6. **Start area (C).** No ruin footprint may come within 6 cells of the start, so the start pad stays clear. Vanilla sets no such
   distance for ruins, so this is Stillmere's choice.
7. **Rubble.** Each destroyed wall cell gets a pile of rough rock with probability 0.5 (C: vanilla uses 0.5 around a tank trap, see
   the table). Rough rock stands in for vanilla's wall rubble (see the rows not adopted). Rubble is placed after the plant pass.
8. **Loot.** Each ruin gets 3 to 10 stacks (D). Each stack is a weighted pick from the loot table below, placed on a random cell of
   the footprint that has no item and no plant. The order of picks is the order of `GenerateLoot` (D, `RimWorld/MapGenUtility.cs:904-916`).
   The plant and building tests are C. Loot that finds no cell is dropped (C; vanilla searches the whole map instead).
9. **Plants.** Plants are cleared from the footprint after the plant pass. The plant pass runs unchanged, so its random sequence is
   the same as before ruins existed. Terrain, ore, roofs and rock types are identical to the map without ruins, cell for cell.
10. **Own generator.** Ruins draw from a generator of their own, seeded from the map seed with a salt (C). The map's own
    generator is not used for ruins.
11. **Old saves.** A save stores its map. Loading builds the map from the file and never calls `GameMap.generate`
    (`SaveGame.read` at `SaveGame.kt:450` and `:511`). So no old save changes.
12. **Save format: unchanged.** No field is added and no enum constant is added. Ruin walls are `WOOD_WALL` buildings with the
    existing material field (STONE or STEEL), ruin floors are `STONE_FLOOR`, and rubble and loot are item stacks. `VERSION`
    stays 39. No fixture is needed, because nothing in the format changed (the same rule `caves.md` used).

## Constants

Tags: **D** = decompiled source (file and line in the table, under `RimWorld/`, `Verse/` or `RimWorld.SketchGen/`, `RimWorld.BaseGen/`
or `RimWorld.Planet/` in the decompiled tree); **C** = Stillmere's own choice; **?** = not confirmed, marked UNVERIFIED.

| Constant | Value | Source | Where in sim/ | Status |
|---|---|---|---|---|
| Ruin size curve, points (side, weight) | (6, 0), (6.001, 4), (10, 1), (30, 0) | D `RimWorld/GenStep_ScatterRuinsSimple.cs:12-18` | `Ruins.SIZE_CURVE` | adopted |
| Size draw | Density of `Rand.ByCurve`: a piecewise linear density, the segment weight is `(x1 - x0) x (y0 + y1)`. Sampled by segment, then by rejection on the segment's density. Vanilla's choice within a segment is a different sampler (a flip) with the same linear density | D `Verse/Rand.cs:456-478` (the segment weights, at `:473` and `:478`) and `:479-495` (the choice within a segment) | `Ruins.drawSize` | adopted (distribution); the sampler is C |
| Size rounding | Nearest whole number, ties to even | D `RimWorld/GenStep_ScatterRuinsSimple.cs:41` (`Mathf.RoundToInt`). The tie rule is the engine's and is not in the tree | `Ruins.drawSize` (`kotlin.math.round`) | adopted; tie rule **?** UNVERIFIED (same question as `map.md`) |
| Square footprint | Side x side, centred on the candidate; the origin is the centre minus side / 2 | D `RimWorld/GenStep_ScatterRuinsSimple.cs:36`; `Verse/CellRect.cs:414-424` | `Ruins.centred` | adopted |
| Clip to the map | The footprint is clipped to the map before use, so an edge ruin is smaller | D `RimWorld/GenStep_ScatterRuinsSimple.cs:110`; `Verse/CellRect.cs:753` | `Ruins.clip` | adopted |
| Destroy chance exponent | 1.32 | D `RimWorld/GenStep_ScatterRuinsSimple.cs:22` (same as the default at `RimWorld.SketchGen/SketchResolver_DamageBuildings.cs:13`) | `Ruins.DESTROY_EXP` | adopted |
| Damage position | Relative position along the chosen direction, flipped for east and south (chance `num^1.32`) | D `RimWorld.SketchGen/SketchResolver_DamageBuildings.cs:70-76` | `Ruins.damage` | adopted |
| Damage direction | One of four directions, drawn before the order is shuffled | D `RimWorld.SketchGen/SketchResolver_DamageBuildings.cs:23` (`Rot4.Random`), `Verse/Rot4.cs:142` | `Ruins.damage` | adopted |
| Buildables in random order | Walls and floors together, one shuffled list | D `RimWorld.SketchGen/SketchResolver_DamageBuildings.cs:25-26`; `RimWorld/Sketch.cs:584-588` (vanilla's list holds terrain and things) | `Ruins.damage` | adopted; the walls and floors list is a C mapping of vanilla's list |
| Destroy cap | Stop once destroyed > 0.65 x buildables | D `RimWorld.SketchGen/SketchResolver_DamageBuildings.cs:9`, applied at `:33-35` | `Ruins.damage` | adopted |
| Hit points of a surviving wall | round(max x (1 - num) x Rand(1, 1.2)), clamped to 1 to max | D `RimWorld.SketchGen/SketchResolver_DamageBuildings.cs:103`; the factor 1.2 at `:11` | `Ruins.damage` | adopted |
| Used-rect padding | 2 cells around each placed ruin | D `RimWorld/GenStep_ScatterRuinsSimple.cs:30`, checked at `:63-73` | `Ruins.canScatterAt` | adopted |
| Used-rect check, inside the placed rect | A candidate may not overlap a placed rect (no padding) | D `RimWorld/GenStep_ScatterRuinsSimple.cs:79` | `Ruins.canPlaceIn` | adopted |
| Minimum spacing between centres | 10 cells; a centre within 10 cells (squared distance at most 100) of an earlier centre is refused | D `Verse/GenStep_Scatterer.cs:17`, `:179`, `:232-242` | `Ruins.canScatterAt` | adopted |
| Earlier centres include refused ones | A centre is recorded after its attempt, even when the ruin is not placed | D `Verse/GenStep_Scatterer.cs:94-95` | `Ruins.placeWalls` | adopted |
| Edge distance of the centre | At least 5 cells from the edge: `Rand.Range(5, size - 5)` (exclusive upper bound) | D `Verse/GenStep_Scatterer.cs:137`; `Verse/CellFinder.cs:138-147`; `Verse/Rand.cs:162` (exclusive) | `Ruins.findCentre` (`Rng.range` is inclusive, so the upper bound is `size - 6`) | adopted |
| Tries for a centre | 1000 | D `Verse/CellFinderLoose.cs:33` | `Ruins.findCentre` | adopted |
| Size drawn once per search | One size for all tries of one search | D `RimWorld/GenStep_ScatterRuinsSimple.cs:39-43` (`TryFindScatterCell` draws `randomSize` before the search) | `Ruins.findCentre` | adopted |
| Stop when no centre is found | The genstep stops for the map | D `Verse/GenStep_Scatterer.cs:80-93` | `Ruins.placeWalls` | adopted |
| Count per 10,000 cells | Count = round(w x h / round(10,000 / per10k)), with per10k drawn from the range. Vanilla uses the map side squared, the same on a square map | D formula `Verse/GenStep_Scatterer.cs:244-251`, `:266-274` | `Ruins.ruinCount` | formula adopted |
| Count per 10,000 cells: the range | 0.5 to 1.0 (on 250 x 250 that is 3 to 6 ruins) | The vanilla range is XML (`countPer10kCellsRange`, default zero at `Verse/GenStep_Scatterer.cs:11`). Not in the source | `Ruins.COUNT_PER_10K_MIN`, `_MAX` | **C, UNVERIFIED** |
| Heavy support at the centre | Buildable ground at the centre (no rock, water or shallow water) | D `RimWorld/GenStep_ScatterRuinsSimple.cs:51` (the affordance check); Stillmere's mapping of heavy support is C | `Ruins.buildable` | adopted for the check; the mapping is C |
| Walls: buildable ground | Every footprint cell must be passable and not shallow water. Rock is excluded (C); vanilla allows natural rock at this check | D `RimWorld/GenStep_ScatterRuinsSimple.cs:90-94` (no river, road, or unbuildable terrain); the mapping to Stillmere's terrain is C | `Ruins.buildable` | C mapping |
| Wall outline | Stillmere's walls are the outline of its square | **C.** Vanilla places walls from a generated shape: each cell of an open shape (`RimWorld.SketchGen/SketchResolver_Monument.cs:57`) or each empty cell beside a closed shape (`:76`). The cited lines place no rectangle outline | `Ruins.build` | C (the outline is Stillmere's choice) |
| Wall material | Wood excluded. Stone or steel, one choice for the whole ruin | D `RimWorld.SketchGen/SketchResolver_MonumentRuin.cs:15` (`allowWood` is false unless set); D `RimWorld.SketchGen/SketchResolver_Monument.cs:98-99` (connected walls share one material, a flag that is set in the monument step). The outline is one connected group before the damage pass, so one material per ruin is a C reading of those lines. The two choices stone and steel are C | `Ruins.build` | wood exclusion adopted; one material per ruin is a C reading; the choice of two is C |
| Floors | The interior is floored, with stone tiles | D `RimWorld.SketchGen/SketchResolver_Monument.cs:101-110` (floors on at `:101`, stone floors only at `:107`, and for a closed monument the floor fill is rooms only at `:106`, which for one closed room is its interior). The stone tile is Stillmere's `STONE_FLOOR`, which is C | `Ruins.build` | adopted; the floor type mapping is C |
| Entrance | One wall of a closed ruin is replaced by a door. Stillmere has no door in a ruin, so the entrance is an open cell (C) | D `RimWorld.SketchGen/SketchResolver_Monument.cs:152` (for a closed monument, a wall bordering the edge is replaced by a door); the door is one of the buildables the damage cap counts (`RimWorld/Sketch.cs:584-588`). The choice of cell (a non-corner outline cell whose outside neighbour is walkable) and the open cell are C | `Ruins.build` | rule adopted; the choice and the open cell are C |
| Closed monument | Every ruin is treated as closed: the open-shape chance is not used | D `RimWorld.SketchGen/SketchResolver_Monument.cs:39`, `:11-17` (the open chance); Stillmere has no open shapes | `Ruins.build` | C (the choice is always closed) |
| Start area clearance | No ruin footprint within 6 cells of the start | C (Stillmere's choice; the simple ruins genstep sets no start distance in its code) | `Ruins.canScatterAt` | C |
| Connectivity guard | Every side reachable from the start with the walls blocking | C | `Ruins.reachesEverySide` | C |
| Rubble: chance per destroyed wall | 0.5 | **C.** Vanilla uses 0.5 to scatter filth around an ancient tank trap (`RimWorld.BaseGen/SymbolResolver_AncientComplex_Defences.cs:77`). It does not use it per destroyed wall | `Ruins.finish` | C |
| Rubble: rough rock per cell | 1 stack | C (Stillmere's rough rock, `STONE_CHUNK`, is the nearest pile of rock; `Defs.kt`) | `Ruins.finish` | C |
| Loot: stacks per ruin | 3 to 10, inclusive. Vanilla counts things, and Stillmere calls each one a stack (C) | D `RimWorld.BaseGen/SymbolResolver_LootScatter.cs:8` and `RimWorld/MapGenUtility.cs:164`; inclusive by `Verse/IntRange.cs:34` (`RandomInRange` is `Rand.RangeInclusive`) | `Ruins.finish` | adopted |
| Loot placement | One stack per cell, cell and stack picked at random, in the order of `GenerateLoot` | D `RimWorld/MapGenUtility.cs:904-916` (the loop). The cell test in vanilla is no item (`:935-938`) and standable (`:939-942`); Stillmere also requires no building and no plant (C) | `Ruins.finish` | adopted (the loop); the plant and building tests are C |
| Loot table | See the table below | **C, UNVERIFIED.** The vanilla loot is a ThingSetMaker (XML), not in the source | `Ruins.LOOT_TABLE` | C |
| Loot value budget | Not used: see the rows not adopted | D `RimWorld/MapGenUtility.cs:166` (1800 to 2000 market value) | — | not adopted |
| Salt of the ruin generator | A Stillmere constant, xor'd with the map seed | C | `Ruins.RNG_SALT` | C |

### The loot table

Weights are relative. Counts are per stack. Each draw is one stack (not a pile of several). Every entry is an existing
`ItemType`. Gear is made at normal quality and full condition (C). Every value here is **C, UNVERIFIED**. Stack limits are
`ItemType.stack` in `Defs.kt`.

| Item (`ItemType`) | Weight | Count per stack | Stack limit (`Defs.kt`) |
|---|---|---|---|
| `STEEL` | 4 | 15 to 45 | 75 |
| `STONE` (cut stone) | 3 | 10 to 30 | 75 |
| `CLOTH` | 3 | 10 to 30 | 75 |
| `SILVER` | 3 | 20 to 60 | 500 |
| `GOLD` | 1 | 5 to 15 | 500 |
| `HARDALLOY` | 1 | 5 to 15 | 75 |
| `COMPONENT` | 2 | 1 to 4 | 50 |
| `MEDS_STERILE_DRESSING` | 2 | 1 to 3 | 25 |
| `SCULPTURE_SMALL` | 1 | 1 | 1 |
| `W_RIFLE` (stutter gun) | 1 | 1 | 1 |
| `A_PADDED_COAT` | 1 | 1 | 1 |

## Rows not adopted

| Source | Status | Reason |
|---|---|---|
| Monument shapes: `AbstractShapeGenerator` shapes, the open and closed split, and symmetry (`RimWorld.SketchGen/SketchResolver_Monument.cs:46-94`, `:11-17`) | not adopted | Stillmere ruins are the outline of a square with one room inside. The shape generator is not in the source tree as a Stillmere rule, and shapes change every ruin. A later change |
| Furniture: urns, steles, tables, sarcophagi (`SketchResolver_Monument.cs` `TryPlaceFurniture`) | not adopted | No Stillmere definitions for these. The owner's scope is walls, rubble and loot |
| Columns (`SketchResolver_AddColumns`, `SketchResolver_Monument.cs:112-119`) | not adopted | Scope: no columns in Stillmere |
| Monument doors from the open or closed split (`SketchResolver_Monument.cs:129-148`, the `TryAddDoorsToClosedRooms` path) | not adopted, replaced | One entrance opening is kept (C). Doors are not in ruins; they need a door object with hit points, which the ruin does not model |
| Rubble as `Filth_RubbleBuilding` (`RimWorld/ThingDefOf.cs:419`, `RimWorld/GenStep_ScatterMechanoidRemains.cs:68`) | not adopted | Stillmere's filth is a dirt level (0 to 4) that falls in the rain (`World.kt`, rain loop) and is cleaned by jobs. Rubble that vanished in the rain would be wrong. A new terrain for rubble would add an enum constant that the app's `when` expressions must handle, and the app cannot be built here. Rough rock persists and can be hauled |
| Loot value budget, 1800 to 2000 market value (`RimWorld/MapGenUtility.cs:166`, `SymbolResolver_LootScatter.cs:25`) | not adopted | The vanilla budget is in vanilla market value, and each thing's value is XML. Stillmere's `ItemType.value` is its own, so the same budget would give hundreds of steel. Stillmere sizes loot by stack count (3 to 10, D), not by value |
| `GenerateLoot`'s search for a cell on the whole map when the rect is full (`MapGenUtility.cs:921-932`) | not adopted | Loot with no cell is dropped (C). Ruins are small, so a full footprint is rare |
| The ancient ruins mutator and its genstep (`RimWorld/TileMutatorWorker_AncientRuins.cs:10`, `:19-21`, the genstep `AncientRuins_Special`) | **excluded: DLC** | The worker returns unless `ModsConfig.OdysseyActive` (Odyssey, DLC). CLAUDE.md excludes DLC |
| World tile gate for the simple ruins | not adopted | The simple ruins genstep has no gate in its code. Its def and any tile gate are XML, not in the source, so **UNVERIFIED**. Stillmere gives ruins to every colony map (C) |
| Natural rock inside a ruin footprint (`CanPlaceAncientBuildingInRange` allows natural rock, `GenStep_ScatterRuinsSimple.cs:98-101`) | not adopted | Stillmere's rock is terrain, not an edifice, so a wall cannot stand on it. Rock is excluded from the footprint (C) |
| Clearing natural rock beside the ruin (`clearSurroundingArea`, `GenStep_ScatterRuinsSimple.cs:122-136`) | not adopted | The default is false (`:20`), and Stillmere has no rock edifices to clear |
| Random wall material from every wall stuff (`SketchResolver_AssignRandomStuff`) | partly adopted | Wood is excluded (D, `:15`). Connected walls share one material (D, `:98-99`), so one material per ruin is adopted as a C reading. The list of allowed stones and metals is Stillmere's (stone and steel, C) |
| Seed salt from the genstep (`RimWorld/GenStep_ScatterRuinsSimple.cs:32`, `SeedPart`) | not adopted | Vanilla mixes its seed parts in its own way. Stillmere uses its own salt (C); the value is not needed to match the output |
| Ruins on battle maps | not adopted (C) | A battle map has no world tile, so `GameMap.generate` gets no `local` and places no ruins. Its fights keep their terrain and their random sequence |

Floors under the walls are not placed, and floors inside a closed ruin fill its one room. That is the rooms-only case of the
floor rule (`:106`), and it is adopted in the Floors row above.

## Saves on disk

No saved field changed, no enum constant was added, and no value is stored that a version-39 reader cannot read. A ruin is a
`WOOD_WALL` building with a material that is already saved (`SaveGame.kt:233`, `:478`), a `STONE_FLOOR` floor (`SaveGame.kt:216`,
`:457`), and item stacks (rough rock and loot). `VERSION` stays 39.

* A save made before this change loads as before, and its map is not regenerated (`SaveGame.kt:450` builds the map from the
  file; `:511` passes it to `Game`).
* A colony with ruins round-trips cell for cell (test `RuinsTest.aColonyWithRuinsSavesAndLoadsAsItWas`).

## Tests

`sim/src/test/kotlin/io/github/teamomuito/colony/sim/RuinsTest.kt`, new, 12 tests, all passing.

* The same seed gives the same ruins (buildings, floors, items).
* A map with ruins has the same terrain, ore, roofs, rock types and spawn as the same map without them; its plants are the same
  outside the footprints, and there are none inside them.
* Every ruin wall is a built `WOOD_WALL` in stone or steel, with hit points from 1 to its maximum.
* Every side of the map stays reachable from the start, and no building is within 6 cells of the start.
* Ruins are at least 10 cells apart by centre, and their footprints are more than 2 cells apart.
* No ruin loses more than 65 percent of its buildables.
* Sizes are 6 to 30 and the size curve puts about half of its draws at 10 or under.
* The count per 10,000 cells matches the formula (62,500 cells gives 3 at 0.5 and 6 at 1.0).
* Loot per ruin is 3 to 10 stacks (3 or more for a ruin of 36 cells or more), from the loot table, on cells with no building, item or plant.
* Battle maps (no world tile) get no ruins.
* A colony with ruins saves and loads as it was.

## Existing tests changed

Five existing tests changed in this work. Three of them are in `GameTest.kt`, where the reviewer's findings are. Four changed only
what a test puts on the map or what it counts. The fifth, the mental break test, changed its assertion from one seed to twelve.

* `ItemsTest.aTraderSellsGearAtItsQualityAndBuyersGetItBack`: "the rifle left the map" checked that no rifle was on the map. A ruin
  can hold rifles, so the check is now that the rifle the test put down (`rifle`, by identity) is gone.
* `JobAiTest.aHaulEndsWhenItsItemVanishesBeforePickup`: the setup removed steel and weapons only. Ruin loot (and the start stock) are
  other haul targets, so the colonist started a second haul once the first ended. The setup now removes every item, so only the steel
  the test drops is left to haul. The assertions are unchanged.
* `GameTest.kt`, `MultiTileTest.footprintsBlockAndRotate` (reviewer finding): it counted every building on the map and expected two. A
  colony map's ruin walls are buildings too, so it now counts from a baseline taken before it places anything (`before + 2`, before and
  after the save round-trip). The two buildings it checks are the same.
* `GameTest.kt`, `MaterialTest.insufficientMaterialWaitsWithoutSpendingAnything` (reviewer finding): it expected no hardalloy on the map
  before it started. Ruin loot holds hardalloy (11 on the failing seed), so the test now removes the map's hardalloy first. Its wall
  and its four and five hardalloy steps are unchanged.
* `GameTest.kt`, `AdvancedTest.mentalBreaksHappenToUnhappyColonists` (reviewer finding): it ran one colony (seed 6) for 40,000 ticks and
  expected a break. A break is a roll every 250 ticks at about 0.8 percent (`BreakRules.chance`, extreme level), so an unhappy colonist
  breaks in that window about 7 times in 10. Seed 6 with ruins never rolled a break in the window, and the mood path was the same with
  and without ruins (the difference is the break roll, which the ruins change through the random sequence). Measured over seeds 6 to 17
  with the same setup: 7 of 12 broke with ruins, and 9 of 12 without, which is the spread expected from that chance. The test now
  asserts that at least five of the twelve colonists break within 40,000 ticks. A broken break mechanism gives zero, so the assertion
  still tests the mechanism.

The sim suite is run in foreground chunks: one run of `./gradlew :sim:test --console=plain` does not finish within the 600-second
limit. The six calls and their counts (788 tests in total) are in the gap report, section 20.

## Round 20 changes

Round 20 checked each D citation against the clone. The rows below were changed, because the cited line did not hold the claim.

* **Wall outline:** D to C. Lines 57 and 76 of `SketchResolver_Monument.cs` place walls from a generated shape, not a rectangle.
* **Rubble chance 0.5:** D to C. The 0.5 is a filth scatter chance around an ancient tank trap (`SymbolResolver_AncientComplex_Defences.cs:77`),
  not a per-wall rule.
* **Floors:** the `SketchResolver_FloorFill.cs:63` citation was removed. That line is in the open-shape path, and a closed ruin uses the
  rooms fill (`:106`). The floor rule now cites `SketchResolver_Monument.cs:101-110` only.
* **Buildables:** the `SketchResolver_FloorFill.cs:63` citation was removed. The walls and floors list is a C mapping of vanilla's list.
* **Wall material:** "one material for each connected group" is now the cited flag (`:98-99`), with one material per ruin as a C reading,
  because the outline is one connected group before damage.
* **Loot placement:** the cited lines hold the no-item and standable tests (`:935-942`). The plant and building tests are C.
* **Entrance:** the rule is `:152`. The choice of cell and the open cell are C.
* **Monument shapes (not adopted):** `:98-100` was the material line, not symmetry. The row now cites `:46-94`.
* **Natural rock (not adopted):** the artificial-building test is `:98-101`.
* **Floors under the walls (not adopted):** removed as a separate row, because the closed-room floor is the adopted floor rule.
* **Start area and connectivity:** the reason text no longer relies on a negative citation. Both are C.
* **Count per 10,000 cells:** the vanilla map side is squared. This is the same on a square map, and Stillmere's maps are square.
* **Size draw:** vanilla's choice within a segment is a flip method with the same linear density. Stillmere's rejection sampler gives the same
  density, so the row says so.
* **Edge distance:** `Rng.range` is inclusive, so the code's upper bound `size - 6` is vanilla's exclusive `size - 5`.

No new citation was added to `decompiled-reference.md`. The ruin generator's code and its numbers are unchanged in this round. The
measurements below were taken before this round, with the same code.

## Measured on 20 seeds (250 by 250, temperate)

Seeds spread out (`k x 1,000,003 + 987,654,321,987`), so the first draw of the ruin generator varies (see the note below).

| Quantity | Value |
|---|---|
| Ruins per map | 4.5 on average, from 3 to 6 (equal to each map's target) |
| Average side | 12.3 cells |
| Walls per ruin (surviving and destroyed) | 44 |
| Buildables destroyed by the damage pass | 43 percent |
| Walls: stone and steel | 45 and 45 |
| Interior floors per ruin | 77 |
| Rough rock piles per map (rubble) | 46 |
| Loot stacks per map | 29, totalling 512 units |

The rough rock is a stack of one per pile (`STONE_CHUNK` stacks at 1). Colonists haul it like any rock, so 46 piles per map is a lot of
haul work. That is a design question for the owner (open item 3).

**Note on small seeds.** The existing `Rng` takes its state from `seed xor 0x5DEECE66D`, so consecutive small seeds (1, 2, 3, ...) give
nearly the same first draw. The ruin count is the first draw, so on seeds 1 to 20 every map had 4 ruins. Real game seeds are large
(the app takes the clock), so the count varies in play. This is an existing property of `Rng`, which this change does not alter. The
tests use seeds 1 to 12 and pass, since they check properties, not counts.

## Open items

1. **Owner:** the count per 10,000 cells (0.5 to 1.0) and the loot table are C and UNVERIFIED. The vanilla values are XML, so
   they need the defs to confirm.
2. **Owner:** whether the simple ruins genstep runs on every Core map or only on some. The source has no gate, but the def is
   not in the tree.
3. **Owner:** the rubble (rough rock per destroyed wall, with a C chance of 0.5). Rough rock stacks at 1, so the rubble is about 46 piles
   per 250 by 250 map (measured above). Colonists would haul each one, which may be too much work. A pile per ruin, or a rubble
   terrain, are the options.
4. **Owner:** whether a ruin should have a door object. Stillmere models doors with hit points, and a ruin door would need one.
