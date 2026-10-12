# Map size and generation

Plan and reference for the colony map's size, the ore count that scales with it, and the saved map size. Gap-report section 3.2
holds the audit. The vanilla values come from the decompiled source at version 1.6.9438.38202 (tag **D**), listed in
`decompiled-reference.md`, section 3.2. The pinned target is 1.6.4850, so a D value may differ from it (see that file).

## Decisions

1. **New colonies are 250 by 250.** Owner decision (tag **C**). `Game.MAP_SIZE` is 250, and the new-colony wizard and the
   start of a new game both use it.
2. **Saves keep the size they store.** A save records its width and height, and the reader uses them. No save is resized onto
   a different map. Saves written before this decision keep their 100 by 100 map; saves that store 250 by 250 keep that size
   (see "Saves on disk" below).
3. **`VERSION` is bumped from 36 to 37, with no change to any saved field.** The owner's goal asks for a bump with this change.
   The width and height were already written and read for every readable version (14 and up), so no field was added and no
   reader was gated. Version 37 is a marker: it is the first version written after new colonies moved to 250 by 250. Every
   version 36 save still loads and keeps its size. The bump is checked by `ColonyMapSizeTest.aVersion36SaveLoadsAndANewSaveIsVersion37`
   against the new fixture `colony_v36.sav`, which was written by the code before the bump, as the save rules require for a
   version change. Owner to confirm the bump is wanted (open item 5).
4. **Ore veins scale with the map's area and hilliness.** The old fixed 18 veins were the count on a 100 by 100 map. On a 250
   by 250 map that is 6.25 times too sparse. The count now follows the reference's blotches per 10,000 cells (see the table).
   The kinds and lump sizes are kept (C).
5. **Battle maps keep the 18 fixed veins.** A battle map is 44 by 44 with no world tile. The density rule would give it 2
   blotches, which would change every fight's ore for a reason that is not about map size. So a battle map is generated with the
   fixed plan (`GameMap.FIXED_ORE_VEINS`, passed by `Encounters.kt`). Its terrain and ore are the same as before this change.
6. **Only the size-dependent rows of 3.2 are adopted.** Each other row is listed below with its reason. Adopting a generation
   model row changes the terrain of every map and the random sequence after it. Those rows are a separate change, not part of
   the map-size change.
7. **Settling founds a colony at the new size.** `Game.settle` (Caravans.kt) used to copy the old colony's map size, so a colony founded from a 100 by 100 save was 100 by 100. It now uses `Game.MAP_SIZE`, as a new game does. The archived old colony keeps the size it stores. Test: `ColonyMapSizeTest.aColonyFoundedFromAHundredByHundredSaveIsTwoFiftyByTwoFifty`.

## Constants

Tags: **D** = decompiled source (repository, file, line in the table); **C** = Stillmere's own choice; **?** = not confirmed,
marked UNVERIFIED.

| Constant | Value | Source | Where in sim/ | Status |
|---|---|---|---|---|
| New colony map side | 250 | C (owner decision). The reference list of selectable sizes includes 250: D `RimWorld/Dialog_AdvancedGameConfig.cs:12` (200, 225, 250, 275, 300, 325) | `Game.MAP_SIZE` (Game.kt) | adopted |
| Saved map width and height | two integers at the start of the map record, before the biome | C. Read unconditionally for every readable version (14 and up); written at the same offset by version 37 | `SaveGame.write` (`o.writeInt(m.w); o.writeInt(m.h)`), `SaveGame.read` (`val w = i.readInt(); val h = i.readInt()`) | no change |
| Save version | 37 (from 36) | C (owner's goal). No field changed; see Decisions, item 3 | `SaveGame.VERSION` | bumped |
| Ore blotches per 10,000 cells: flat | 4 | D `RimWorld/GenStep_RocksFromGrid.cs:131` | `GameMap.oreBlotchesPer10k` | adopted |
| Ore blotches per 10,000 cells: small hills | 8 | D `RimWorld/GenStep_RocksFromGrid.cs:134` | `GameMap.oreBlotchesPer10k` | adopted |
| Ore blotches per 10,000 cells: large hills | 11 | D `RimWorld/GenStep_RocksFromGrid.cs:137` | `GameMap.oreBlotchesPer10k` | adopted |
| Ore blotches per 10,000 cells: impassable | 16 | D `RimWorld/GenStep_RocksFromGrid.cs:143` | `GameMap.oreBlotchesPer10k` (for `Hills.MOUNTAIN`) | adopted; mapping **?** UNVERIFIED (see below) |
| Ore blotches per 10,000 cells: default (switch default) | 10 | D `RimWorld/GenStep_RocksFromGrid.cs:127` (the switch's default value) | `GameMap.oreBlotchesPer10k` (for `null`, a map with no world tile) | adopted; using the default for `null` is C |
| Mountainous (not used for ore) | 15 | D `RimWorld/GenStep_RocksFromGrid.cs:140` | not used: `Hills.MOUNTAIN` maps to impassable for ore | not adopted (reason below) |
| Blotch count | one blotch per `round(10,000 / density)` cells; count = `round(w x h / that)` | D `Verse/GenStep_Scatterer.cs:266-274`: the cell size is the nearest whole number of 10,000 over the density, and the count is the nearest whole number of the area over that cell size. The source uses a square side. Stillmere uses `kotlin.math.round` (half to even) and `w x h`, which is the same for square maps | `GameMap.oreBlotchCount` | adopted. **Tie mode UNVERIFIED**: the engine's rounding helper is not in the source tree, so the half-to-even mode is not confirmed. It changes one value: density 10 on 250 gives 62.5, which is 62 here and 63 if ties round up |
| Kind shares of the blotches (and the battle plan) | steel 8, silver 3, gold 2, hardalloy 2, components 3 (total 18): `GameMap.FIXED_ORE_VEINS` | C: the old fixed vein counts, kept as weights and as the battle plan. Vanilla kind choice is weighted by XML commonality; the source does not give the numbers (reference row 64, ambiguous) | `GameMap.FIXED_ORE_VEINS` | kept |
| Share rounding | cumulative: each kind gets `round(total x cumulative share / 18)` minus the previous kinds, so the veins add up to the blotch count | C | `GameMap.oreVeinPlan` | new |
| Lump size per vein | steel 7 cells, components 3, others 4 | C (unchanged) | `GameMap.oreLumpSize` | kept |
| Attempts to start a vein | 80 random starts, each vein grows through rock | C (unchanged) | `GameMap.generate` | kept |
| Battle map side | 44 | C (unchanged) | `Encounters.kt:15` | kept; battle maps keep the fixed 18 veins (same call, `veins = GameMap.FIXED_ORE_VEINS`) |
| Ticks per in-game day, the benchmark's unit | 60,000 | D `RimWorld/GenDate.cs:9`, already matched in `time.md` | `TICKS_PER_DAY` (Defs.kt) | matches |

**Hilliness mapping, unconfirmed.** Stillmere's `Hills.MOUNTAIN` is labelled "Impassable mountains" with cost 99
(`WorldMap.kt:10`). For ore it maps to vanilla's impassable value (16, `GenStep_RocksFromGrid.cs:143`). For rock, the code uses
Stillmere's own threshold of 0.5 (`GameMap.generate`, `rockThr`). The reference gives **0.636** for mountainous rock: the
rock threshold 0.7 (`MapGenTuning.cs:5`, read at `GenStep_RocksFromGrid.cs:55`) over the mountainous factor 1.1
(`MapGenTuning.cs:13`), and 0.583 for impassable (0.7 over 1.2, `MapGenTuning.cs:15`). So 0.5 is not the reference value for
either. It is Stillmere's own value, it was not changed by this work, and the rock threshold is a "not size" row (see the
table below), so changing it changes the rock on every mountain map. The two mappings differ: `MOUNTAIN` is mountainous for
rock and impassable for ore. For the ore, 16 gives 100 blotches on 250 by 250, and 15 would give 94. Owner to confirm (open
item 1).

## Vanilla reference: 3.2 rows, adopted or not

Each row of `decompiled-reference.md` section 3.2 is listed. "Size" means the value depends on the map's width or area.

| Reference row | Status | Reason |
|---|---|---|
| Selectable colony map sizes (200 to 325) | adopted in part | 250 is kept as the only size offered, which the owner's decision requires. The other sizes (200, 225, 275, 300, 325) are not offered, since offering them would contradict "new colonies use 250". Open for the owner (see Open items) |
| Ore blotch count per map | **adopted** | Size-dependent. Values and formula above |
| Ore kinds | not adopted | Ambiguous in the reference (kind weights come from XML, row 64). Stillmere keeps its five kinds and weights |
| Terrain type count | not adopted | Ambiguous: the count comes from XML (row 65) |
| Shallow water becomes ice in tundra | not adopted | Not size. The reference has no ice rule in source (row 66, ambiguous); Stillmere keeps its own rule |
| Tundra gravel patches | not adopted | Not size. Ambiguous (row 67): the gravel terrain and patch makers come from XML |
| Map-level lake shape | not adopted | Scope and model. The reference's lake is a map-wide field with radius 0.6 of the map width (`TileMutatorWorker_Lake.cs:29`, applied at `:45`), displaced by 40 at 0.006 and 15 at 0.015 (`:52-53`), centred within 0.4 of the map size (`:65`), with thresholds 0.75, 0.5 and 0.45 on that field (`:93-101`). Stillmere's lake is a disc of radius 8 to 13 cells. A width-scaled field would turn the pond into a map-wide sea, which is a change of model, not of size |
| River placement and direction on the map | not adopted | Model. The reference picks a centre point between 30% and 70% of each axis (`TileMutatorWorker_River.cs:173`, inside `GetRiverCenter` at `:171`) and runs a meandering river through it. Stillmere's river is a straight band at 22% of the span. Taking only the centre range would move a straight band, not adopt the reference's river |
| River channel widths | not adopted | Not size. The half width is the river width over 2 with noise (`TileMutatorWorker_River.cs:220`), the width comes from the river def (`RiverDef.cs:29`, 10), and the bank size is 0.3 of the biome's riverbank size (`:106`). Deep water needs a depth above 4 (`:279`). Stillmere's band uses 1.6 and 3.2 cells. Belongs to the river rework |
| River network on the world map | not adopted | World-level model, not the colony map |
| Hilliness of world tiles | not adopted | World-level model (`WorldGenStep_Terrain.cs:221`) |
| Hilliness elevation factors for map relief | not adopted | Not size. It changes the elevation of every map (`MapGenTuning.cs:7-15`) |
| Base elevation noise | not adopted | Not size. The reference draws a random base frequency per map. Stillmere's fixed 26-cell scale is kept; changing it changes every map's terrain |
| Elevation shape extras (detail, warp, stretch, rotation) | not adopted | Not size. A new elevation model |
| Fertility noise | not adopted | Not size. Changes soil on every map |
| Rock threshold by hilliness | not adopted | Not size. The reference gives scaled thresholds (0.7 over the factor, `GenStep_RocksFromGrid.cs:55`, `MapGenTuning.cs:5`) that do not map one to one onto Stillmere's raw ones. Changing them changes the rock on every map. See "Hilliness mapping" for the MOUNTAIN value |
| Gravel band | not adopted | Not size. Changes the terrain of every map |
| Rock objects and natural roofs | not adopted | Not size. Roof rules belong to the roofs and rooms area |
| Rock types per map | not adopted | Not size. The reference picks 2 or 3 types per world tile; Stillmere uses five types on one noise |
| Colony start spot | not adopted | Not size. Stillmere carves a 7 by 7 pad near the middle, and `findOpenPad` already works at any size. The reference searches for a district with tightness 7 and minimum size 10 cells, a different algorithm |
| Wild plants and trees (density rules) | not adopted | Not size. Changes plants on every map |
| Tree density by biome | not adopted | Ambiguous (row 71): the values are derived from XML |
| Coastal wetness thresholds | not adopted | The reference's coast mutator applies only to tiles that carry it. The base generator has no wetness field (row 68) |
| Biome choice rule and the six biome rules (temperate, boreal, tundra, desert, tropical, arid) | not adopted | World-level biome model, not size |
| Missing biomes (grasslands, extreme desert, swamps, cold bog) | not adopted | Not size. Stillmere has six biomes; new biomes are a separate change |
| Inland lakes on the world map | not adopted | World-level, not size |
| World grid size | not adopted | World-level, not size |

## Saves on disk

The 19 committed fixtures in `sim/src/test/resources/legacy/` store these sizes (measured on the current code):

| Size stored | Fixtures |
|---|---|
| 100 by 100 | colony_v24, colony_v25, colony_v25_graves, colony_v25_head, colony_v25_thoughts, colony_v26, colony_v27, colony_v28, colony_v34, colony_v35 |
| 250 by 250 | colony_v25_adults, colony_v27_sites, colony_v29, colony_v30, colony_v31, colony_v32, colony_v32_xp, colony_v33, colony_v36 |

The premise that every older save is 100 by 100 does not hold for these fixtures: eight of the older ones, and the new `colony_v36`, store 250 by 250. The
`names.md` note on `colony_v29.sav` says its map is the version 29 default of 250 by 250, but the git history of `MAP_SIZE` shows
100 from the first commit until `eb9784c`. The fixtures do not record the code that wrote them, so which of the 250 saves were written after `eb9784c` is open (open item 3). Each save keeps
the size it stores, so none of them is resized. The test checks each fixture against its stored size.

`colony_v36.sav` is new. It is a new colony at 250 by 250 (seed 7, quiet enclave, half a day of steps), written by the code
before the bump to version 37, as the save rules require for a version change. It is 1.2 MB, the same as the other 250 by 250
fixtures.

## Tests

* `ColonyMapSizeTest`: a new colony is 250 by 250; `colony_v28` (100 by 100) keeps 100 by 100; every legacy fixture keeps the
  size it stores (`everyLegacySaveKeepsTheSideItStores`, a table of all 19 fixtures, with a check that the table covers every
  `.sav` in `legacy/`); a rewrite keeps it too; a 250 by 250 colony round-trips; `aVersion36SaveLoadsAndANewSaveIsVersion37`; `aColonyFoundedFromAHundredByHundredSaveIsTwoFiftyByTwoFifty` (a colony founded from a 100 by 100 colony is 250 by 250).
* The nine legacy migration tests that check "the current version" compare with `SaveGame.VERSION` (37). Before, they had
  the literal 36, and one test name said "IsVersionThirtySix"; it now says "IsTheCurrentVersion".
* `MapGenTest` (`oreBlotchesFollowTheAreaAndHilliness`): the counts at 100 and 250, the default value for a map with no world
  tile, and the battle map's density count (2 blotches, which battle maps do not use).
* `MapGenTest` (`oreVeinPlanAddsUpToTheBlotchCount`): the kinds add up to the count, in the order and shares of
  `FIXED_ORE_VEINS`, for each hilliness value and sizes 44, 100 and 250.
* `MapGenTest` (`battleMapsKeepTheFixedVeins`, new): the fixed plan is 18 veins and 93 cells, and a 44 by 44 battle map with that
  plan has at most 93 ore cells and at least one.
* `MapGenTest` (`rockTypesOreAndVeinsStayInTheirRanges`): now generates 250 by 250 maps, with each map's own vein plan known from
  its planet's hilliness. The ore check changed. It was a floor on the ore share (3%, then 1%), which measured 2.6% and was set
  for the old 18 veins. It is now a placement check: the ore placed must be at least 90% of the planned ore cells. Measured over
  72 maps (12 seeds, 6 biomes) on 250 by 250: 13,833 of 13,887 planned cells, 99.6%. The upper bound of 15% of rock stays.
* **Colony test helpers at 250.** `GameTest.newGame`, `JobAiTest.newGame`, `CombatModelTest.arena`, `CombatPipelineTest.arena`,
  and `SkillsTraitsTest.arena` build `Game.MAP_SIZE` (250) colonies. `GameTest`'s `WorldTest` map and the rock-type loop of
  `MapGenTest` are 250 too. Some tests still pass 100 explicitly: the river and lake shape tests, the determinism checks in
  `MapGenTest`, the legacy 100 by 100 fixture, and the `PerfBenchTest` benchmarks. Those check the generator at a given size, not
  the colony.
* **Setups instead of seeds (no seed was changed from its HEAD value).** Four tests had setups that depended on the layout near
  home, and they failed at 250. Each was made independent of the layout, and each keeps its HEAD seed and its assertions:
  * `ContentTest.dormantMechsWakeNearColonists` (seed 54): no colonist has any work, and the colonist that walks to the mech is
    drafted there. Drafting ends its job and holds it still, so only the distance to the mech decides the wake-up. Before, only
    hauling was off, and a colonist with another job walked back within the 200 ticks.
  * `RulesTest.outfitPolicyKeepsArmorOffWorkers` (seed 62): no colonist has any work. The test uses a colonist who can wear
    apparel (not `Trait.BARE_SKINNED`). The vest lies on that colonist's own cell. When the policy changes to soldier, the
    colonist's current job ends, so its next decision is about gear. Before, the colonist was still in a wander or idle job and
    chose gear only when that ended.
  * `JobAiTest.anInterruptedHaulDropsItsLoadAndReleasesItsReservations` (seed 209, JobAiTest.kt): the window for the eat job is one interrupt
    cycle, `tk(10)` ticks (25 ticks, the `HEALTH_STRIDE` of `Game.kt`), not 10 steps. The old window was shorter than the cycle.
  * `LifeTest.couplesConceiveAndBabiesAreBornAndFed` (seed 31): before the feeding window, every colonist has no work,
    no job, and full food, rest and joy, so none goes for food first. The old milk is removed and 10 fresh units are put down,
    because milk spoils and an old stack rots away before the feeder reaches it.
  * `SettleTest.caravanFoundsNewColonyAndOldOneIsArchived` (seed 22): the random ambush and find roll on the road is the
    one part of a trip that depends on the seed. The test turns it off with `Game.randomCaravanEvents` (new, default `true`, not
    saved; `enterTile` in `Caravans.kt`). With the default, nothing changes.
* **Probe, before the final run (temporary, removed).** Each setup was swept over seeds before the final version was kept:
  the dormant, outfit and interrupted-haul scenarios over seeds 1 to 40 and the HEAD seeds (43 seeds), and the settle and life
  scenarios over the test's seed and seeds 1 to 10 (11 seeds). The failing counts, in the order the setup was changed:

  | Scenario | Failing seeds, first setup at 250 | Failing seeds, later setups | Final setup |
  |---|---|---|---|
  | Dormant mech | 4 of 40 (hauling off only) | 10 of 43 (all work off, no draft) | 0 of 43 (drafted at the spot) |
  | Outfit | 16 of 43 (hauling off, vest on the next cell) | 7 of 43 (all work off, vest on own cell), 1 of 43 (job ended, a bare-skinned colonist) | 0 of 43 (apparel-capable colonist) |
  | Interrupted haul | 10 of 43 (10-step window) | none | 0 of 43 (`tk(10)` window) |
  | Settle | 2 of 11 (random ambush on) | none | 0 of 11 (events off) |
  | Life | 4 of 11 (first setup) | 6 of 11 (work off, jobs ended), 5 of 11 (adults fed) | 0 of 11 (every colonist fed; old milk removed) |

* **Final run.** `./gradlew :sim:test --console=plain`: 722 tests in 72 suites, 0 failures, 0 errors, 0 skipped (12 min 33 s). An earlier run
  had 9 failures, all the literal 36 in the legacy tests; they now compare with `SaveGame.VERSION`.
* `PerfBenchTest`: two opt-in benchmarks, `benchOneDayHundredByHundred` and `benchOneDayTwoFiftyByTwoFifty`, time one in-game day
  of `Game.step` (`TICKS_PER_DAY` steps). Set `COLONY_BENCH=1` to run them.

## Benchmark

Measured in the previous round, before the setup and version changes; the map generation of colony maps has not changed since
except for the battle maps, which are not in this benchmark. Run with
`COLONY_BENCH=1 ./gradlew :sim:test --console=plain --tests '*PerfBenchTest.benchOneDay*' -i` on this sandbox. Each size gets the
same seed (4242), biome (temperate) and scenario (quiet enclave, five colonists). One day is 60,000 steps. Map generation is
timed apart from the steps. Three runs:

| Run | 100 by 100: setup | run | per step | 250 by 250: setup | run | per step |
|---|---|---|---|---|---|---|
| 1 (first in the JVM, so 100 by 100 includes JIT warm-up) | 170 ms | 3,013 ms | 0.050 ms | 104 ms | 5,879 ms | 0.098 ms |
| 2 | 193 ms | 2,895 ms | 0.048 ms | 88 ms | 6,103 ms | 0.102 ms |
| 3 | 161 ms | 3,823 ms | 0.064 ms | 113 ms | 5,949 ms | 0.099 ms |

* 250 by 250 costs about twice the time per step for 6.25 times the area. The per-step time is steady across the three runs
  for 250 by 250 (about 0.10 ms) and varies more for 100 by 100 (0.048 to 0.064 ms).
* The two sizes end the day with different ore counts (43 ore cells on 100 by 100, 258 on 250 by 250), so the sims are not the same
  game. The state hash is the same in every run for each size, so each run is deterministic.
* **The phone budget is unmeasured, because no phone is available here.** These numbers are a sandbox measurement on one JVM. They
  show the scaling. At 1x the app runs 60 steps per real second, so a step has 16.7 ms. That is the limit a phone would have to
  meet, and the phone was not measured against it.

## Open items

1. The owner should confirm the mapping of `Hills.MOUNTAIN` to impassable (16) for ore, and decide the rock threshold for it. The
   code uses 0.5, which is Stillmere's own value. The reference gives 0.636 for mountainous and 0.583 for impassable. Changing it
   changes the rock on every mountain map, so it is a separate change.
2. The owner should decide whether the size picker offers any size other than 250. The reference's list is 200 to 325.
3. The 250 saves in the fixtures have no traceable origin (see "Saves on disk").
4. Five test helpers and the `WorldTest` and rock-type checks now build 250 by 250 colonies. The explicit-size generation tests
   still pass 100 (river, lake, determinism). The owner may want those re-checked at 250 as well.
5. The bump to version 37 marks a change of size, not of layout. The owner should confirm that a version marker is wanted,
   since a version 37 save can only be read by a build that knows version 37.
6. Battle maps keep the 18 fixed veins. Whether battle ore should follow the map-size rule is the owner's decision; it is not
   changed here.
7. The river, lake, elevation and other generation-model rows are listed above as not adopted. Each needs its own change.
