# Grass terrain: fertility, growth and harvest

Plan and reference for the grass terrain (gap-report section 3.9, "Missing: grass"; section 3.2, "Missing: Grass"). The vanilla
values come from the decompiled source at version 1.6.9438.38202 (tag **D**), listed in `decompiled-reference.md`, sections 3.2 and
3.9. The pinned build is 1.6.9438.38202, the same as the source, so the D values match it (see that file).

## What the decompiled source says about grass

* The source has no grass terrain. `TerrainDefOf.cs` names no grass terrain, and no code gives a grass terrain's fertility. A
  terrain's fertility is a required XML value: the source checks that a terrain def has one (`Verse/TerrainDef.cs:530-532`).
* Grass in vanilla is a plant: `Plant_Grass` (`RimWorld/ThingDefOf.cs:1198`), grown by the plant rules, with its growth days and
  yield in XML. Animals graze it (`RimWorld/AnimalProductionUtility.cs:138-141`, `Verse/AnimalPenManager.cs:70-71`). Neither gives
  a number for the grass plant itself.
* So the grass terrain is a Stillmere design (**C**), and its values are either the plant rules above (D, applied to the grass
  plant) or Stillmere's own choices, marked **C** or **UNVERIFIED** below.

## Decisions

1. **Grass is a terrain (`Terrain.GRASS`) with a grass cover (`PlantType.GRASS`).** The terrain is fertile ground; the cover is a
   plant on it. The cover grows with the plant rules, can be harvested for hay, and regrows after a harvest. Both are appended at
   the end of their enums, so no existing ordinal moves.
2. **Terrain fertility 1.0 (UNVERIFIED).** The same as soil, so a grass cell gives the fertility factor 1.0 (D
   `RimWorld/PlantUtility.cs:358` with sensitivity 0.5, `PlantProperties.cs:97`). The vanilla value is XML and is not in the source.
3. **Growth uses the shared plant rules, unchanged.** Growth per slow tick is `growthPerSlowTick`: one over the grow days per
   day at full factors (D `RimWorld/Plant.cs:346`), times the fertility, temperature and light factors (D
   `RimWorld/PlantUtility.cs:358, 361-367, 370-381`). The temperature band is the default 6 to 42 C, with growth limits at 0 and
   58 C (D `RimWorld/PlantProperties.cs:87, 89, 91, 93`). Rain and trait bonuses do not apply (D `RimWorld/Plant.cs:289-302`).
4. **Harvest from 65 percent growth (D).** A grass cover is harvestable above the harvest minimum (D `RimWorld/Plant.cs:115`,
   default `PlantProperties.cs:55`), like a crop. Before this change, a regrowing plant was harvestable only when fully grown.
   Only the grass changes: the rule now applies to crops and to grass. Trees, berries, mirewort and brambles keep the full-growth
   rule.
5. **Yield (D formula, Stillmere count).** `count x (0.5 + 0.5 x share)` from 65 to 100 percent growth, zero at or below 65
   percent, randomly rounded (D `RimWorld/Plant.cs:934-947`, zero at `:917-919`, the round at `:947`). The health term is 1 (C:
   Stillmere plants have no hit points; the reference's term is `Plant.cs:942`). The difficulty factor is 1: the reference applies
   the difficulty's crop yield factor to every plant whose `harvestYieldAffectedByDifficulty` flag is set (default true,
   `RimWorld/PlantProperties.cs:61`), and Stillmere uses the default difficulty, whose factor is 1 (D `RimWorld/DifficultyDef.cs:44`,
   from the reference table).
6. **Regrowth after a harvest (D mechanism, C value).** A harvested grass cover keeps its growth at a low value and grows back
   (D `RimWorld/Plant.cs:661`, growth set to `harvestAfterGrowth`, a plant with no such value is destroyed, `PlantProperties.cs:205`).
   The value is Stillmere's 0.05, the value the regrowing plants already use (C). The vanilla value is XML (UNVERIFIED).
7. **Cutting removes the cover.** A cut (not a harvest) removes the cover, as for other plants.
8. **Sowing over grass (C).** A growing zone may be sown directly over a grass cover, and the crop replaces the cover when the
   sowing is done. The sowing checks (`findGrow`, `driveSow`) accept a grass cover; other plants still block sowing. Vanilla's
   behaviour here is not checked against the source.
9. **Grass covers generate on grass ground only, and grass ground is soil.** On a map outside the desert and arid biomes (the
   code's `desert` flag covers both), a SOIL cell becomes GRASS where its own patch noise is above `GRASS_PATCH_SHARE` (C). A GRASS
   cell without another plant gets a grass cover where its cover noise is above `GRASS_COVER_SHARE` (C). Generated covers start
   fully grown, as the generator's other plants do (C). Both noises are separate `Noise` objects seeded from the map seed. No RNG
   draw is made. The plant pass tests a cell's terrain only for passability, water, ice and fertility above 0.1, and GRASS gives the
   same answer as SOIL for each of these, so the trees, berries, mirewort and brambles are placed on the same cells as before.
10. **The start spot is unchanged.** The open-pad search treats GRASS as SOIL (`findOpenPad`, soil-only pass), so the pad on every
    seed is the same cell as before the change (C, preserves the start for each seed).
11. **Save format.** No field is added. The terrain is saved by ordinal in one byte, and the plant type by ordinal in an int, so
    both values are written and read as before, and no reader is gated. A grass ground cell is terrain ordinal 10, and a grass cover
    is plant type ordinal 21 (`PlantType.GRASS`, after `MEADOW_GRASS` at 20). The version is bumped from 38 to 39 as a marker (see
    below).

## Constants

Tags: **D** = decompiled source (repository, file, line in the table); **C** = Stillmere's own choice; **UNVERIFIED** = not
confirmed by any source here.

| Constant | Value | Source | Where in sim/ | Status |
|---|---|---|---|---|
| Grass terrain fertility | 1.0 | UNVERIFIED (vanilla XML; `TerrainDef.cs:530` says the value is required). Set to soil's value | `Terrain.GRASS` (Defs.kt) | new |
| Grass terrain passable, path cost | true, 1 | C (same as soil) | `Terrain.GRASS` | new |
| Grass cover grow days | 3.5 | UNVERIFIED (vanilla XML). Set to Stillmere's sward, the nearest existing grass-type value | `PlantType.GRASS` | new |
| Grass cover yield (hay) | 4 per full plant | UNVERIFIED (vanilla grass has no hay yield; the yield is XML, `PlantProperties.cs:48`). Stillmere's choice so that harvest gives something | `PlantType.GRASS` | new, C |
| Grass cover harvest work | 90 | C (Stillmere's sward cut work, the nearest existing value) | `PlantType.GRASS` | new |
| Grass cover flammability | 0.5 (default) | UNVERIFIED (vanilla XML). Stillmere's default | `PlantType` default | new |
| Grass cover growth band | 6 to 42 C (default) | D `RimWorld/PlantProperties.cs:89, 91` (default optimal band); growth limits 0 and 58 C at `:87, 93` | `PlantType.minTemp`, `maxTemp` defaults | adopted |
| Growth per day at full factors | 1 / grow days | D `RimWorld/Plant.cs:346` | `growthPerSlowTick` (World.kt), shared | adopted (already) |
| Fertility factor | fertility x 0.5 + 0.5 (1.0 for grass) | D `RimWorld/PlantUtility.cs:358`, sensitivity `PlantProperties.cs:97` | `fertilityGrowthFactor` (World.kt), shared | adopted (already) |
| Temperature factor | ramp 0 to 1 at 6 C, 1 in band, ramp to 0 at 58 C | D `RimWorld/PlantUtility.cs:370-381` | `temperatureGrowthFactor`, shared | adopted (already) |
| Light factor | ramp 0.51 to 1.0 glow | D `RimWorld/PlantUtility.cs:361-367`, `PlantProperties.cs:75, 77` | `lightGrowthFactor`, shared | adopted (already) |
| Harvest minimum growth | above 65 percent | D `RimWorld/Plant.cs:115`, default `PlantProperties.cs:55` | `HARVEST_MIN_GROWTH`, `Plant.mature` | adopted for grass (new for grass) |
| Yield formula | count x (0.5 + 0.5 x share from 65 to 100 percent), zero at or below 65 percent | D `RimWorld/Plant.cs:934-947` (zero: `:917-919`; health `:942`; round `:947`) | `harvestYieldExact` (World.kt), shared | adopted (already) |
| Health term | 1 | C: no hit points for plants (`Plant.cs:942` is the reference term) | `harvestYieldExact` | kept (already) |
| Regrowth after harvest | growth 0.05 | D mechanism `RimWorld/Plant.cs:661`, `PlantProperties.cs:205`; value C (XML in vanilla) | `driveCut` (JobDrivers.kt), `regrows` | reused, C value |
| Wild-plant fertility requirement | fertility above 0 | D `RimWorld/WildPlantSpawner.cs:413` | grass covers only on GRASS cells (fertility 1.0) | adopted (the cover rule implies it) |
| Grass patch noise | scale 14, share above 0.55 | C | `GameMap.generate`, `GRASS_PATCH_SCALE`, `GRASS_PATCH_SHARE` | new |
| Grass cover noise | scale 6, share above 0.35 | C | `GameMap.generate`, `GRASS_COVER_SCALE`, `GRASS_COVER_SHARE` | new |
| Generated cover growth | 1.0 (fully grown) | C (the generator's existing rule for trees and berries) | `GameMap.generate` | new |
| Save version | 39 (from 38) | C (marker, see below) | `SaveGame.VERSION` | bumped |

## Rows of the reference not adopted, with the reason

Section 3.9 (plants):

* **Starting growth of spawned plants** (`Plant.cs:22` default 15 percent; `WildPlantSpawner.cs:58-59`, random 15 to 150 percent).
  The reference marks this ambiguous, and the random draw would need an RNG draw at generation, which would change every seed's
  plants. Covers start fully grown (C).
* **Death from lack of light** (`Plant.cs:214`, 450,000 ticks unlit). A grass cover under a roof grows more slowly, through the
  light ramp, and does not die. The unlit counter needs a persisted field on every plant, a separate change (deferred in economy.md).
* **Plant lifespan and old-age damage** (`Plant.cs:210`, `PlantProperties.cs:73`). Dying damage needs plant hit points, which
  Stillmere does not have. Grass does not age out.
* **Wild plant regrowth and sapling spread** (`WildPlantSpawner.cs:286-318`, low confidence). Grass does not spread in this change:
  the covers are placed at generation only, and harvested covers regrow in place.
* **Growing-zone fertility minimum of 0.9** (`Designator_ZoneAdd_Growing.cs:45`, `PlantProperties.cs:95`). Grass is not sown, and
  the sowing rule keeps Stillmere's "fertility above 0" (economy.md).
* **Animal grazing** (`AnimalProductionUtility.cs:138-150`, `AnimalPenManager.cs:70-71`). Animals are a separate row (3.10). Grass
  nutrition per day is not used here.

Section 3.2 (map generation):

* **Fertility noise** (`GenStep_ElevationFertility.cs:88`: gradient noise at frequency 0.021, six octaves). Stillmere's fertility
  is per terrain type. Adopting a per-cell fertility field would change the fertility of every cell of every map.
* **Terrain type count and the terrain patch makers** (`TerrainDefOf.cs`, `TerrainPatchMaker.cs:14`, XML). Ambiguous: the terrain
  set comes from XML. Stillmere adds one terrain, GRASS, with its own patch rule.
* **Base elevation, elevation extras, rock thresholds, gravel band and river and lake shapes** (`GenStep_ElevationFertility.cs`,
  `MapGenTuning.cs`, `TileMutatorWorker_River.cs`, `TileMutatorWorker_Lake.cs`). These change the terrain of every map and are
  not about grass. They stay as in map.md.
* **Wild plant density rules** (`WildPlantSpawner.cs:413-418`, the density curve, `GenStep_Plants.cs:7`). Trees, berries,
  mirewort and brambles keep their own rules. The grass cover uses its own noise (C), since the reference's density comes from
  XML commonality (`BiomeDef.cs:304`, ambiguous).
* **Tree density by biome** (`BiomeDef.cs:304`, ambiguous). No change.

## Measured on the current code (eight seeds, 250 by 250, `GameMap.generateFor`)

| Biome | Grass ground, share of the map | Grass ground, share of soil and grass | Covers, share of grass ground |
|---|---|---|---|
| Temperate | 16.6 percent (14.8 to 19.9) | 34 to 40 percent | 69 percent (65 to 73) |
| Boreal | 17.1 percent (15.0 to 20.4) | 34 to 39 percent | 66 percent (62 to 71) |
| Tundra | 10.6 percent (8.6 to 11.8) | 34 to 42 percent | 82 percent (78 to 84) |
| Tropical | 12.9 percent (10.9 to 13.5) | 34 to 39 percent | 60 percent (55 to 63) |
| Desert, arid | 0 | 0 | 0 |

A temperate map has about 6,500 to 8,500 grass covers (about 11 percent of its cells). The figures are a sample, not a target;
the shares are C (Stillmere's noise thresholds).

## Changes to existing values and tests

* `SaveGame.VERSION`: 38 to 39, as a marker. Nothing is read or written differently. A version-38 reader refuses a version-39 save
  with its normal "Unsupported save version" error, instead of failing on the new terrain or plant ordinal. This is a judgement
  (CLAUDE.md says to bump when what is saved changes), and the owner may reverse it.
* `ColonyMapSizeTest.aVersion36SaveLoadsAndANewSaveIsTheCurrentVersion` asserted the literal 38. It now asserts 39.
* `RoofsTest.theRoofGridRoundTripsThroughASave` asserted `SaveGame.VERSION == 38`. It now asserts 39.
* `ColonyMapSizeTest.storedSides`: `colony_v38.sav` (250) added. Without it, `everyLegacySaveKeepsTheSideItStores` fails, because
  every fixture must be in the table.
* No other existing test changed. Every test that compares with `SaveGame.VERSION` follows the constant.
* `Plant.mature` (GameMap.kt): grass uses the harvest minimum (65 percent). No other plant changes.
* `Game.canBuildAt` (Game.kt): a grow tray may be placed on a grass cover. Other plants still block a tray.
* `findGrow` (Jobs.kt) and `driveSow` (JobDrivers.kt): a grass cover does not block sowing.
* `GameMap.generate`: GRASS terrain and grass covers are new, and `findOpenPad`'s soil-only pass accepts GRASS. Terrain values change
  on SOIL cells of non-desert maps; passability, cost, fertility and the RNG draws do not change.

## Fixtures

* `legacy/colony_v38.sav`: a version-38 colony, written by the code before this change (seed 7, quiet enclave, 30,000 steps, the
  same recipe as `colony_v36.sav`), from the temporary generator that was deleted after use. It has no grass. It loads, and a
  rewrite is version 39 with the same map.
* A save containing grass (a 250 by 250 map with grass ground and grass covers at several growth stages, with their ages)
  round-trips through `SaveGame.write` and `SaveGame.read`, cell for cell (`GrassTest.aSaveWithGrassRoundTrips`).
  `GrassTest.aVersion38SaveHasNoGrassAndLoadsAsTheCurrentVersion` checks that `colony_v38.sav` loads with no grass and is rewritten
  as version 39.

## Tests (`GrassTest.kt`, 16 tests, all passing)

* Enums and values: the ordinals of the old values stay (`Terrain.SOIL` 0, `Terrain.MUD` 9, `PlantType.MEADOW_GRASS` 20), and grass is
  last in both enums. Grass ground is passable with fertility 1.0 and soil's cost. The grass cover regrows, is not a crop or a tree,
  and yields hay.
* Growth: the shared rules give one over the grow days per day at full factors, zero at the growth limits, half at 50 C, and half
  the rate at fertility zero. Grass on sand grows slower than on fertile ground.
* Harvest: a grass cover is harvestable above 65 percent and a berry bush is not at 70 percent. The yield is zero at 65 percent,
  three quarters of the count at 82.5 percent, and the full count at full growth.
* Behaviour: a colonist harvests a grass cover for hay, and the cover regrows in place near 5 percent growth. A growing zone is sown
  over a grass cover (`findGrow` returns a sowing job).
* Generation: grass ground and covers on a 250 by 250 temperate map (share of the map and of the grass, on three seeds); no grass on
  a desert or arid map; the same layout for the same seed; the start pad is the same cell whether grass counts as soil or not.
* Saves: the grass round trip above, and the version 38 fixture.
* Not tested: the grow tray rule (`Game.canBuildAt`) and the room-beauty and garden-sense effects of a grass cover.

## Open items

1. **Owner:** confirm the version 39 marker (see "Changes").
2. **Owner:** the grass terrain and cover numbers that are not in the source: terrain fertility 1.0, grow days 3.5, yield 4 hay,
   harvest work 90, and the patch and cover shares. All are UNVERIFIED or C.
3. **Owner:** grass is a plant that yields hay here. Vanilla grass has no harvest product in the source (its `harvestYield` is XML),
   so the hay yield is a Stillmere choice (C).
4. Garden-sense and room beauty count a grass cover as a plant (`Mood.kt`, "Among the plants"; `Rooms.kt`, plant beauty). Not
   checked against the source.
5. Grass does not spread, and grass does not feed animals (3.10).
6. **Not compiled:** the `:app` module was not built, as the run's rules say. Three `when` expressions over `Terrain` have no `else`
   and needed a `GRASS` branch: `GameView.kt` (`terrainColor`), `Sprites.kt` (`baseColor`, and the ground drawing statement, where
   grass ground takes the soil branch with its tufts) and `Overview.kt` (the map colours). The plant `when`s over `PlantType` have
   `else` branches, so a grass cover is drawn as the sward (`Sprites.kt`, the else branch) and by `GameView.kt`'s else branch. A grass
   cover is therefore drawn like a sward or a crop, not as its own sprite.
7. **Testing:** the full `:sim:test` run takes about 12 minutes, longer than the 10-minute foreground limit, so it was run in three
   parts: all classes but `LifeTest`, `RansomTest` and `ColonyTest` (2 min 57 s), `RansomTest` and `ColonyTest` (5 min 22 s), and
   `LifeTest` (6 min 8 s). All three passed. The baseline at HEAD was 750 tests with one failure, the new fixture not yet in the table
   (fixed above). The count is now 766 (`@Test` annotations), with `GrassTest` at 16.
