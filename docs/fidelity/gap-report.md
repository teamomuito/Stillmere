# Fidelity gap report

Audit date: 2026-10-09. Scope: the whole repository at commit `7e01e02` plus the merge of `main`. No game code was changed
to produce this report.

Target (from `CLAUDE.md`): a faithful recreation of Core (base game, no DLC) RimWorld `<VERSION>`. **`<VERSION>` is
still a literal placeholder in `CLAUDE.md`.** Several vanilla values changed between releases (for example the length of a
day), so every "vanilla" figure below is for the current release unless stated, and the version must be pinned before
Phase 2 starts.

## How to read this report

**Status** is one of Matches / Partial / Missing / Divergent / DLC-sourced.

**Confidence tags** on each vanilla figure:

| Tag | Meaning |
|---|---|
| **V** | Reported by a RimWorld Wiki page, as summarised by a web search on 2026-10-09. This is a secondary source: the wiki host does not resolve from this environment, so no page was read directly. Every V cell gives its page URL; a figure without one is M, UNVERIFIED. Re-check against the pages when direct access exists. |
| **M** | Recalled from memory, or from a search summary with no URL recorded. **UNVERIFIED.** Do not implement from this without a source. |
| **C** | Custom to Stillmere. No vanilla counterpart is known. |
| **?** | Could not determine. **UNVERIFIED.** |

**Priority** (1 to 3): 1 = affects the core loop, breaks a project rule, or blocks later phases; 2 = important but can wait
for its phase; 3 = long tail or polish.

Wiki pages the **V** figures came from: [Ticks](https://rimworldwiki.com/wiki/Ticks), [Time](https://rimworldwiki.com/wiki/Time),
[Food](https://rimworldwiki.com/wiki/Food), [Passion](https://rimworldwiki.com/wiki/Passion), [Skill](https://rimworldwiki.com/wiki/Skill),
[Raid points](https://rimworldwiki.com/wiki/Raid_points). Map size comes from third-party guides and the Ludeon forum, not the wiki.

## 1. Build and test status

| Check | Result |
|---|---|
| `./gradlew :sim:test` | **Passed.** 337 tests, 0 failures, 0 errors, 0 skipped, in 22 test files (about 5,100 lines). Needed repeated retries: Maven Central returned HTTP 429 (rate limit) on dependency downloads, and the Gradle cache filled a little more on each try until the build went through. |
| `./gradlew -PwithApp :app:assembleRelease` | **Not run to completion. Cannot be built here.** `ANDROID_HOME` is unset, there is no Android SDK, and Gradle could not resolve the Android Gradle Plugin `com.android.application:8.7.3` (the proxy denies `dl.google.com` with 403). Nothing in `app/` was compiled or run, so every statement about the app below is from reading source. |
| Compiler warnings | 3, all in tests (`IncidentTest.kt:260`, `PerfBenchTest.kt:110,147`). |
| `PerfBenchTest` | 3 of its 4 tests are wrapped in `if (enabled)` and do nothing by default. They count as passes. |

**What the tests do and do not prove.** The suite checks internal consistency and regressions (saves round-trip, reservations,
region reachability, determinism of fights and incidents). **No test asserts a vanilla number.** Passing tests say nothing
about fidelity.

## 2. Cross-cutting findings

### 2.1 Rule violations (project rules in `CLAUDE.md`)

| # | Finding | Where | Priority |
|---|---|---|---|
| R1 | **Wall clock and non-seeded randomness inside `sim/`.** `System.currentTimeMillis()` is a default argument, and `UUID.randomUUID()` names new saves. Neither changes simulation state (they are save metadata), but the rule is "never read the wall clock inside `sim/`". | `SaveStore.kt:115`, `SaveStore.kt:88` | 1 (small fix). **Fixed:** default is now 0 and save IDs count up. |
| R2 | **Hash-ordered collection written to disk.** `Game.incidentLast` is a `HashMap<Incident, Long>` keyed by an enum, and `SaveGame.kt:128` iterates it when saving. Enum `hashCode()` is identity-based, so entry order can differ between JVM runs and the same game can serialise to different bytes. `Game.kt` already warns about exactly this for `researchDone`. `PerformanceEquivalenceTest.theSameSeedGivesTheSameSaveBytes` passes only because the comparison runs in one JVM. | `Game.kt:92`, `SaveGame.kt:127-128` | 1 (small fix: `EnumMap`). **Fixed:** `EnumMap` in `Game` and the loader. |
| R3 | **Platform-dependent floating point.** `Math.sin`/`Math.cos` are allowed to differ by 1 ulp between JVM and Android runtime. They feed map generation (lake position), drop-pod landing, and the outdoor temperature curve. A seed could generate a slightly different map on a phone than in the unit tests. **Fixed.** Every `Math.sin`, `Math.cos`, `Math.pow`, `Math.hypot` and `atan2` in `sim/` is now `StrictMath` (`GameMap.kt`, `Events.kt`, `Game.kt`, `Caravans.kt`, `WorldMap.kt`). The outdoor temperature curve was already `StrictMath` in `Climate.kt`. | 2 |

Everything else checked is clean: no wall-clock reads in the tick loop, one seeded `Rng` (same LCG as `java.util.Random`, state saved
and restored), and the two `shuffled(java.util.Random(...))` calls are seeded from the sim RNG or the world seed.

### 2.2 Names and text copied from RimWorld

**Status: resolved for labels and text, with open items (round 7).** Branch `ccr-41984ede-stlmp4`, working tree, not committed. The name check in verify finds no old RimWorld label in a code literal (names.md section 8). The old names live only in `docs/fidelity/names.md`, the legacy test data, and `LegacyNames.kt` as CRC-32 values (names.md section 4). Open items remain for the owner: generic labels kept on purpose (names.md section 7 and open item 5), enum identifiers that echo ordinary words (names.md open item 9), and borderline near-copy pairs (names.md open item 16). The mapping, the rules and the save handling are in [names.md](names.md).

* **Creatures, drugs, plants, materials, food, research, buildings, weapons, apparel, traits, backstory titles, thought labels, skills, hediffs and events** that RimWorld coined or uses in a distinctive form: renamed in original wording (names.md sections 6 to 6.4).
* **Scenarios and the difficulty label** that echoed a RimWorld storyteller or preset: renamed (names.md section 6).
* **Enum constants.** Constants that echoed a RimWorld label phrase were renamed in place, so each keeps its ordinal (names.md sections 5, 5.1 and 5.2). Round 7 renamed 26 identifiers (section 5.2). `app/` received the same renames but cannot be compiled here (no Android SDK).
* **Saved text:** backstory titles, thought labels, the temporary event name, one incident name and the pirate camp texts (site names and pending-fight labels) are saved as text or by name. Thought labels and the temporary event name are translated on load for saves below version 30, backstory titles for saves below version 31, and the pirate camp texts for saves below version 28 (`LegacyNames`). Graveyard causes, log lines and caravan last-event notes are translated below the version of each rename (round 15; names.md section 2). The old names are stored as CRC-32 values, so they are not written in code. The save version was set to 31 by the names migration and is 40 at `HEAD` (names.md sections 2 to 4).
* **Kept on purpose, UNVERIFIED:** ordinary words, real-world object, animal, biome, disease and weather names, and the occupations of the backstory titles. Several may also be RimWorld labels. Names.md section 7 lists them.

**Open items (see names.md section 9):**

1. **Not committed.** The change set of rounds 12 to 16 is not committed yet. The fixtures in `sim/src/test/resources/legacy/` (including `colony_v29.sav`) are tracked at `HEAD`, except the round 15 rows of `old_text_cases.tsv` (names.md section 9, item 1).
2. **Save version 30.** Round 7 changed saved text (four thought labels and the temporary event name renamed, two more thought labels and one temporary event name given new final names), so the version went from 29 to 30. Fixtures exist for versions 25 (two files), 26, 27, 28 and 29 (names.md section 2).
3. **Fixture provenance.** The lower-case adult title in `colony_v25.sav` is what HEAD writes: `Game.kt:190` lower-cases the adult title of every randomly made colonist. Loading is not affected (names.md section 9, item 3).
4. **`colony_v25_thoughts.sav`** came from a generator outside this repository and cannot be regenerated from the repo. The `colony_v25_head.sav` file, written by the code at git `HEAD`, covers the same rename rows (names.md section 2).
5. **Kept generic labels** may be RimWorld wording. Owner decision.
6. **Incident constants** saved by name were not renamed, except the one named in names.md section 4 (`SKYHORN_VISIT`, an owner decision).
7. **Trait descriptions** were rewritten in round 4 in original wording. They were not compared with RimWorld's wording.
8. **Origins not confirmed:** several old names are tagged M, UNVERIFIED (names.md section 9, item 8).
9. **Enum identifiers** that echo ordinary words remain (names.md open item 9).
10. **Backstory and thought labels.** Three backstory titles were renamed in version 31 (Unclaimed child, Battlefield bandager, Roadwalker; names.md section 6.5). Drug use and similar thought labels are kept and UNVERIFIED (names.md open item 10).
11. **Vanilla reference names** in the vanilla columns of this report are recalled benchmarks, not Stillmere labels. Where this report used to name a RimWorld label, it now points to names.md (names.md open item 11).


### 2.3 Other repository findings

| # | Finding | Priority |
|---|---|---|
| X1 | **Release signing key and its password are committed** in plain text (`app/release.jks`, `app/build.gradle.kts:22-28`). The comment says this is deliberate so any build can update an installed copy. Anyone can sign an APK that installs over yours. Needs your decision. | 2 |
| X2 | README download and release links point at `teamomuito/rimworld` (the old repo name), as does a comment in `.github/workflows/android.yml`. GitHub redirects renamed repos, but the links are stale. | 3 |
| X3 | **Fixed.** README counts and claims (caves, ores, butchery) were stale: it says 65 buildings and 53 research projects; the code has 72 buildable buildings (76 defined, 4 legacy) and 57 research projects. | 3 |
| X4 | `docs/fidelity/<system>.md` files, which `CLAUDE.md` requires for every implemented constant, **do not exist yet.** Only this report does. | 1 |

## 3. Systems

### 3.1 Time and determinism

**Status: Matches for the time base, pending the save-format decision (see `docs/fidelity/time.md`). Determinism: Matches, with two rule fixes.** **Priority 1.**
**Files:** `Defs.kt` (calendar and `tk()`), `Game.kt` (`tick`, `step`, `daylight`, `outdoorTemp`, `SLOW_TICK`, `HEALTH_STRIDE`), `Noise.kt` (`Rng`), `SaveStore.kt`, `SaveGame.kt`, `app/MainActivity.kt` (real-time pace).

| Constant | Stillmere now | Vanilla | Conf |
|---|---|---|---|
| Ticks per in-game hour | 2,500 | 2,500 | V (https://rimworldwiki.com/wiki/Ticks) |
| Ticks per day | 60,000 | 60,000 | V (https://rimworldwiki.com/wiki/Ticks) |
| Days per season / per year | 15 / 60 | 15 / 60 | V (https://rimworldwiki.com/wiki/Time) |
| Real-time base rate at 1x | 60 ticks per second (app) | 60 | V (https://rimworldwiki.com/wiki/Ticks) |
| Real minutes per game day at 1x | about 16.7 | about 16.7 | V (derived; https://rimworldwiki.com/wiki/Ticks) |

**Done in this change:** every constant that was written in old ticks (1,000 per hour) is converted with `tk()` or `TIME_SCALE`, so
it keeps the same game-time length. Per-tick rates (work, XP, joy, rest, repair, sleep) are divided by `TIME_SCALE`. A parity harness
over 8 seeds compared the old and new builds on walking, food, rest, joy, wounds, fire, crops, combat, mining and cutting: they agree
within sampling noise, and mined and cut counts match exactly.

**Determinism:** `sim/` reads no clock and no unseeded randomness (enforced by a test that scans the sources). Enum-keyed maps that
reach the save file are now `EnumMap`, so save bytes do not depend on JVM identity hashes (R2). The save timestamp is a caller-supplied
value, defaulting to 0 (R1). Save IDs count up instead of using UUIDs. A test runs the same seed twice and compares a SHA-256 of the
full save bytes; another checks that save and load leave the state identical, and that a loaded game keeps running identically.

**Saves:** version 25 converts every tick-valued field when a version 14 to 24 save is loaded, so existing saves keep working.
See `docs/fidelity/time.md` (migration section) and `LegacySaveMigrationTest`, which uses a real pre-change save.

### 3.2 Map generation

**Status: Divergent.** **Priority 1.**
**Files:** `GameMap.kt` (`generate`, `generateFor`), `Ruins.kt` (ruins, `docs/fidelity/ruins.md`), `Noise.kt`, `Defs.kt` (`Terrain`, `RockType`, `Ore`, `Biome`, `PlantType`), `Game.kt` (`MAP_SIZE`, `findStartSpot`), `Animals.kt` (`populateWildlife`).

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| Colony map size | 250 x 250 default (`Game.MAP_SIZE`, owner's decision). The new-colony wizard offers 200, 225, 250, 275, 300 and 325 (`Game.SELECTABLE_MAP_SIZES`, round 21). Saves store their width and height and keep them when read: 100 x 100 for 10 committed fixtures, 250 x 250 for 12 (`docs/fidelity/map.md`, "Saves on disk") | Selectable sizes 200, 225, 250, 275, 300, 325 (D, decompiled 1.6.9438.38202, `RimWorld/Dialog_AdvancedGameConfig.cs:12`; 350 and 400 are test-only). The pinned build is 1.6.9438.38202, the same as the source | Matches the list (round 21): all six sizes are offered, and 350 and 400 are not. Default 250. The larger sides are unmeasured on a phone (map.md, open item 8) | C for 250 x 250 as the default; D for the selectable list, from the pinned build (the same version as the source) |
| Battle map size | 44 x 44 | ? | | ? |
| Biomes | 6 | Core has more (12 recalled, including swamps, ice sheet, extreme desert, sea ice) | 6 missing | M |
| Rock types | 5 (granite, marble, limestone, sandstone, slate) | 5 | none | M |
| Ore kinds | 5 (steel, silver, gold, hardalloy, components) | steel, silver, gold, hardalloy, jade, uranium, compacted machinery recalled | at least 2 missing | M |
| Ore blotches per map | Round 17: 4, 8, 11, 16 blotches per 10,000 cells for flat, small hills, large hills and impassable; 10 for a map with no world tile. Battle maps keep their 18 fixed veins. Before round 17 it was 18 fixed veins on every map | Same densities by hilliness (D, `RimWorld/GenStep_RocksFromGrid.cs:127-143`). One blotch covers round(10,000 / density) cells, and the count is the area over that, rounded (D, `Verse/GenStep_Scatterer.cs:266-274`) | Matches. `Hills.MOUNTAIN` as impassable (16) is UNVERIFIED; mountainous would be 15. The rock threshold for `MOUNTAIN` is 0.5 here, and the reference gives 0.636 (mountainous) | D; mapping ? |
| Ore vein layout | Kind shares of the blotches: 8 steel, 3 silver, 2 gold, 2 hardalloy, 3 components. Lumps of 7 cells (steel), 4 (silver, gold, hardalloy), 3 (components) | ? | | C |
| Soil fertility | soil 1.0, rich soil 1.4 | same | none | M |
| Trees | 8 kinds, density by biome | many more kinds | partial | M |
| Terrain types | 10 | more, with several stone floors and water types | partial | M |

**What exists:** value-noise elevation/fertility/wetness, rivers (65% of seeds, or from the world tile), a lake when the world tile
borders water, mountains by an elevation threshold tuned by the world tile's hilliness, ore veins in rock, forests by biome,
berry bushes, feral mirewort, brambles, wildlife herds, a flat start area.

**Missing:**
* **Grass ground and grass cover** are in (round 18, `grass.md`): grass ground is a terrain with fertility 1.0 (UNVERIFIED), and the cover grows, is harvested for hay from 65 percent growth, and regrows. Still missing: animals grazing it (3.10), grass spread, flowers, bushes, most wild plants.
* **Caves** are generated on hill tiles (round 19, `caves.md`): tunnels through the rock, with natural roofs, gravel floor and openings onto the surface. Only colonies on large hills can have them (about 4 percent of starts). Still missing: the cave floor's water and gravel patches, cave hives, and cave-ins (`JobDrivers.kt:444` states cave-ins are not modelled). The base game's pocket-map exits and other cave content are DLC and excluded. Steam geysers and map-edge features are still missing.
* **Ruins** are generated on colony maps (round 20, `ruins.md`): square ruins of stone or steel walls with a stone floor, walls damaged by the vanilla destroy rule, rough rock where walls fell, and 3 to 10 loot stacks per ruin from a Stillmere loot table. Battle maps get none. Terrain, ore and roofs are unchanged; plants are cleared only inside the footprints. The save format is unchanged (version 39). Still missing: the monument shapes, furniture and doors, filth rubble (rough rock stands in), and the ruin count and loot table, which are UNVERIFIED (vanilla values are XML).
* **Roofs.** Built roofs, roof collapse and roofless rooms are in the sim (3.3, `roofs.md`). The app has a Build roof tool; removing a roof is still missing.
* **Ore as vanilla places it:** the subsoil auger reads the ore under its own cell, but ore only exists inside rock, which cannot be built on. See 3.9 (defect).
* Rock chunks on the surface, mining yields and mine work are custom (`Ore.work` 1000 to 2200, plain rock 1000).

### 3.3 Temperature and rooms

**Status: Partial** (was Divergent). Details and sources: `docs/fidelity/temperature-rooms.md`; heat exchange through walls, doors, roofs and open doors: `docs/fidelity/heat-exchange.md`; roofs, roof collapse and roofless rooms: `docs/fidelity/roofs.md`. **Priority 1.**
**Files:** `Climate.kt` (outdoor temperature, `Thermal.step`), `GameMap.kt` (`rebuildRooms`, `roomIsIndoor`, `buildHeatLinks`, `tempAt`, `roof`, `setRoof`), `Roofs.kt` (support, collapse, roof building), `Rooms.kt` (`computeRoomStats`, `RoomRules`), `World.kt` (`roomClimate`), `Game.kt` (`outdoorTemp`, `designate`), `Mood.kt` (`comfortTick`), `Pawn.kt` (`comfyMin/Max`), `SaveGame.kt` (version 40).

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| Comfortable range | 16 to 26 C, shifted by apparel insulation | 16 to 26 C | none | M |
| Temperature granularity | per room, with heat links to neighbouring rooms and the outdoors | per room (every cell of a room shares one temperature) | **none** (a per-cell grid was not built: it would be less faithful) | M, UNVERIFIED (search summary, no URL recorded) |
| Day/night swing | +/-7 C in every biome (round 25: the +/-10 C for desert and arid was C and is removed), coldest 04:19, warmest 16:19 (round 22; was 03:00 and 15:00) | D, `Verse/TemperatureTuning.cs:20`, `Verse/GenTemperature.cs:194-202` (amplitude 7 for every biome; coldest at day fraction 0.18 at longitude 0) | | D |
| Scorching spell / deep chill offset (vanilla event names: names.md section 6.3) | +16 / -17 C | ? (recalled near +17 / -20) | | UNVERIFIED |
| Smoke pall offset (vanilla event name: names.md section 6.3) | -13 C for 6 days | ? | | UNVERIFIED |
| Heat exchange | Vanilla rates in the exponent form per slow step: walls 0.00017 per tick per unit weight per cell; a rock edge with no room behind it at half weight toward the outdoors (deep water, C, not checked against vanilla); a door closes `1/cells` of the gap per pass (34 ticks open, 375 closed), toward the mean of its sides between rooms and at half a share toward the outdoors; unroofed cells 0.0007 per tick and a thin roof 0.00005 toward the outdoors, amplified above 100 degrees; a thick roof pulls a room above 15 down to 15; a door cell reads the mean of its sides; room temperatures clamped to -273.15 to 1000 | Walls, far-side rule, roof, deep ground, door and door cell mean all adopted (D: `Verse/RoomTempTracker.cs:22, 28, 30, 248, 251, 254-262, 272, 281, 290-296, 56`; `RimWorld/BuildingProperties.cs:256, 258, 260`; `RimWorld/Building_Door.cs:411, 470`; `Verse/GenTemperature.cs:408, 414, 427-453`). Not adopted: vent (no building), doorway as a room, the underground rate, the thick and thin natural roof split (natural roof is thick, C, **?**) | **Matches for the rules above, with differences**: the sample of far cells is taken whole each slow step (not 20 percent per pass), the door passes are their expected count, a door opens on entry (vanilla on approach), and the heater output is C. See `heat-exchange.md` | D for the rates and rules above; C for the integration and the door timing; **?** for the door def's XML intervals and the natural roof thickness |
| Open doors | Swing doors open 300 ticks after a pawn steps in and 110 while one stands in them (saved, `Building.doorHold`, version 40); a slide hatch is open while powered | Door open for 300 at approach (min), 110 close delay; powered doors (`RimWorld/Building_Door.cs:411, 470, 47`) | Partial: no opening time (the speed stat is definition data), and the approach is taken as the step onto the door | D for 300 and 110; C for the entry rule and the hatch; **?** for the opening time |
| Heater and cooler output (heat per unit) | Heater 35.62 heat units (was 14), no falloff; cooler -14, unchanged. With the vanilla rates above, a sealed 3 by 3 room with one heater reaches 21 at 0 C outdoors (threshold 35.618; 35.61 never reaches 21), and a cooler holds 21 up to about 29 C | Built heater's output is definition data; the code default energy is 12 (`RimWorld/CompProperties_TempControl.cs:7`) | **Stillmere's choice** (C): the smallest value to two decimals that reaches 21 C at 0 C. At 0 C the room cycles 16.3 to 21 C (mean 20.4) because control acts once per slow step (vanilla's swing not checked). The owner may change it (game value, no save change). See `heat-exchange.md`, "Heater output" | C, **?** |
| Room temperature across a save | Saved from version 40 (per indoor room, keyed by its first cell); before, a load reset every indoor room to the outdoor temperature | Vanilla's save of room temperatures: not checked in the decompiled tree this round | not compared; the layout is rebuilt from the saved map, so a save taken while rooms are dirty keeps the stale layout's temperatures | C |
| Wall material insulation | none (all materials equal) | none recalled | | M |
| Heater / cooler | +35.62 / -14 heat units, only below / above 21 C (the heater's 35.62 is Stillmere's choice, C; the cooler is unchanged) | ? (definition data; the code default energy is 12) | | UNVERIFIED |
| Rooms | flood fill; walls, doors and rock separate; a door splits rooms and belongs to none; indoor when it does not touch the map edge and fewer than a quarter of its cells are open (unroofed), `open * 4 < cells` | a room that is 75% roofed or less is outdoors (Verse/Room.cs:103-108, quarter rule at :105) | **Matches**. The old 700-cell rule is gone, kept only to migrate saves | D, `Verse/Room.cs:105` (see `roofs.md`) |
| Roofs and roof collapse | built roof grid (saved, version 38); designation and build job (65 work, progress 1.7 x work speed per tick); a roof needs a holder (wall, door or rock) within 6.9 cells through roofed cells; removing a holder drops the roofs it held; crush roll 15 to 30 per colonist under a falling roof | the same rules, from RoofCollapseUtility, RoofCollapseCellsFinder, JobDriver_BuildRoof, JobDriver_AffectRoof, RoofCollapserImmediate | **Partial**: no remove-roof designation, trees are not cut first, the collapse does not destroy buildings or plants, no app command yet | D for the 6.9-cell radius (`Verse/RoofCollapseUtility.cs:5`) and the quarter rule (`Verse/Room.cs:103-108`, `:105`), both rows of the reference table; D for the 65 work (`RimWorld/JobDriver_AffectRoof.cs:15`, `:43`), the 1.7 rate (`:47`) and the 15 to 30 crush roll (`Verse/RoofCollapserImmediate.cs:10`), cited to the decompiled source with no reference row (the pinned build is 1.6.9438.38202, the same as the source); C for the work speed used in place of the construction stat, the collapse steps and the holder test; the 3 by 3 block is roofed to a fixed point (C), and vanilla's single pass is not adopted; the other vanilla rules are C or not adopted for the same reason (`roofs.md`, Tags and "Corrections in this round"); C (work speed mapping; no material cost charged); UNVERIFIED (which defs hold roofs, natural rock holding a roof, the built roof's thin flag) |
| Room stats | beauty, cleanliness, wealth, impressiveness, role (9 roles); constants in `RoomRules` | impressiveness from wealth, beauty, space, cleanliness with named stages | structure matches, all numbers custom | M, UNVERIFIED (structure, from a search summary with no URL recorded); C (numbers) |

**Heat exchange round (version 40):** walls, rock, doors, roofs and open doors use the vanilla rates (see `heat-exchange.md`); door cells read the mean of their sides; room temperatures are saved. Tests: `HeatExchangeTest` (25) and `LegacyHeatMigrationTest`; `TemperatureRoomsTest` values changed (heater at 14 C, cooler at 30 C, a new heater-at-0 test). Not committed.

**Heater output round (no save change):** the heater's heat per unit goes from 14 to 35.62 (`Buildings.kt`), Stillmere's choice (C), the smallest value to two decimals that reaches 21 C at 0 C outdoors in a sealed 3 by 3 room (threshold 35.618; 35.61 settles at 20.995 and never reaches 21). The cooler (-14) is unchanged. `TemperatureRoomsTest`: the 14 C band is 19 to 25 (the old 19 to 23 cannot hold for any output that reaches 21 at 0 C), and `aHeaterCannotHoldTheSetpointAtZeroDegreesOutdoors` is renamed `aHeaterReachesTheSetpointAtZeroDegreesOutdoors`, which checks that the room reaches 21 and sits below it on average (it does not claim the room is held). The heat-doc citation for the thermostat is `Climate.kt:102` (was `:71`, a door comment). Full suite: 826 tests, 0 failures, 0 errors, 0 skipped. Open: the swing of a heated room is one pulse wide (16 to 21 C at 0 C) because control acts once per slow step; vanilla's swing is not checked (?). Not committed.

**Done in phase 4:** outdoor temperature moved into a pure `Climate` object (`StrictMath`, fixing R3 for this curve); heat now flows
between neighbouring rooms through doors, walls and rock instead of only to the outdoors; heater and cooler thermostat extracted;
room stats moved out of `Game` into `computeRoomStats` with named constants. 18 tests in `TemperatureRoomsTest`.

**Could not be verified** (no direct wiki access; see the doc): heater and cooler output (the heater's 35.62 is Stillmere's choice: see the row above), the vent rate (no vent building), the natural roof thickness, the door def's XML intervals, the day/night curve,
the impressiveness formula and thresholds, and which buildings and terrain contribute to beauty and cleanliness and by how much.
All are marked UNVERIFIED and are Stillmere's own numbers.

**Roofs (this round, `roofs.md`).** The 75 percent rule, roofless rooms, roof building and roof collapse are in `sim/` (`Roofs.kt`,
`GameMap.kt`). A room indoors now needs a roof: a walled room with no roof is outdoors, as in vanilla. Saves from version 37 and older
get roofs on each cell of the rooms that were indoor under the old rule, so no room changes status on load (`LegacyRoofMigrationTest`).
`Desig.ROOF` and `JobType.BUILD_ROOF` are new (appended, so saved job types keep their ordinals). Crush damage goes through the sim RNG.
Tests: `RoofsTest.kt` (rule, support radius, collapse, crush, building, save) and `LegacyRoofMigrationTest.kt`; the room tests in
`TemperatureRoomsTest.kt`, `GameTest.kt` and `ColonyMapSizeTest.kt` now roof their rooms or check version 38 (listed in `roofs.md`).
All 826 sim tests pass with 0 failures (23 in `RoofsTest`, 6 in `LegacyRoofMigrationTest`), confirmed by one `./gradlew :sim:test --console=plain` run (`roofs.md`, Status).

**App (this round):** the Orders menu has a Build roof tool. Tap or drag marks cells with `Desig.ROOF` through the same `designate` call as the other order tools, a marked cell reads "Marked: roof", and built roofs are drawn with a faint tint apart from natural roof (`roofs.md`, "The app"). Not built here: CI must confirm.

**Still open for roofs:** the remove-roof designation, in the sim and the app (not available); trees that block a roof are skipped, not cut first; the 3 by 3 block is roofed to a fixed point, not vanilla's single pass (not adopted); the crush does not
destroy buildings or plants; natural roof is kept when its group loses support, where vanilla drops a natural roof that is not thick (not
adopted, a decision recorded in `roofs.md`); the built roof's cost, thin or thick flag, and which defs hold a roof are `UNVERIFIED`.

**Missing:** vents, doors that are open versus closed, rain and snow on roofless rooms beyond the existing roofed checks, overhead
mountain rock as a room property beyond the natural-roof flag, sunlight beyond a day/night curve, seasonal day length, latitude-dependent
seasonal swing.

### 3.4 Health and medicine

Updated after the health pass (PR #2), which supersedes the audit's earlier findings for this system. Status key: **Matches** (agrees with a `SECONDARY` source), **Partial**, **Gap** (known difference, not fixed), **UNVERIFIED** (no vanilla figure could be confirmed, so the sim's own tuning stands).

**Nothing in this section is confirmed against RimWorld's own data.** The wiki was unreachable from the build environment (blocked by network policy), so references are web-search summaries of the wiki (`SECONDARY`) or memory (`UNVERIFIED`). Details and every value: [health.md](health.md).

**Files:** `Health.kt`, `Body.kt` (`Bodies`, `HediffKind`, `Injury`, `Hediff`), `Surgery.kt`, `Mood.kt` (`comfortTick`, drug withdrawal), `Life.kt` (`agingConditions`), `JobDrivers.kt` (`driveTend`).

Phase 5. Code in `sim/`, tests in `HealthFidelityTest.kt` (65 new tests; `./gradlew :sim:test` passes, 443 total).

| Area | Status | Notes |
|---|---|---|
| Body parts and hierarchy | Partial | 29 human parts with correct parents and vital set. Vanilla's bones, tongue and extra digits are not modelled. Finger and toe HP is 12 where I recall 8 (`UNVERIFIED`). |
| Part health | Matches | Per-part HP times health scale, with no body total. Torso, heart, lungs, kidneys, liver and brain agree with `SECONDARY`; the other parts are `UNVERIFIED`. |
| Hit locations | UNVERIFIED | Outer parts by coverage, organs only through their parent (`SECONDARY` agrees). Coverage values and the penetration rule are the author's tuning. |
| Consciousness | Partial | Pumping weight 0.2 agrees; breathing 0.2 and filtration 0.1 weights are D; the pain term is D in form, with the sim's form differing and not changed; the blood-loss term is `UNVERIFIED`. |
| Moving, manipulation | Partial | Structure is the author's. Consciousness does not multiply either directly (`SECONDARY`). |
| Sight, hearing | Matches | Mean of the eyes or ears; one eye lost halves sight. |
| Eating | Matches (changed) | Now jaw only; the stomach moved to Metabolism. |
| Breathing, pumping, filtration | Matches (changed) | Invented fudge factors removed: one lung or kidney lost now halves the capacity. Liver loss and loss of both kidneys now kill ("organ failure"). |
| Metabolism | Partial (new) | Computed from the stomach. Not yet consumed by anything: the rest-rate link is not wired. |
| Pain and pain shock | Matches (changed) | Shock threshold 0.80; The Tender frame trait lowers it by 0.50 instead of multiplying pain. The pain-to-consciousness curve is `UNVERIFIED`. |
| Downed state | Matches (changed) | Down when consciousness < 30%, moving ≤ 15%, or pain at the threshold; standing up is the exact negation. |
| Bleeding and clotting | UNVERIFIED | Death at 100% bloodLoss agrees. Rates, tended residual, clotting and the staged blood-loss hediff are unconfirmed or absent. |
| Wound infection | UNVERIFIED | Qualitatively consistent with wiki anecdotes. No figures confirmed. |
| Illness speed | Gap | The chest cough illness (vanilla flu) runs about 3.5 times faster than the wiki's figures (`SECONDARY`). Left alone; illness tuning was out of scope. |
| Scars, lost limbs | Partial | Scar chance and permanence are D (health.md, Scars). The sim's 18% chance and no-pain scar differ and are kept. Lost-limb detail is `UNVERIFIED`. |
| Implants (limb crafting research) | UNVERIFIED | Efficiencies unconfirmed. "Gaff grip" may not be a base-game item. |
| Tend quality | Matches (changed) | Doctor-stat curve, ×0.7 self-tend, ±25% luck, clamp to the medicine's cap. The no-medicine values are D (`RimWorld/TendUtility.cs:10-12`). Leaf poultice potency and cap are V (a wiki snippet). The +0.10 bed offset is `UNVERIFIED`. About double the old tend quality for skilled doctors. |
| Medicine items | Partial | The strongest item (sterile dressing, `MEDS_STERILE_DRESSING`) agrees. Leaf poultice is `UNVERIFIED`. Glitterworld medicine is missing. |
| Healing rates, beds | D for the formulas; values differ (round 22) | Kept as the author's tuning, with one change: the tended base is 0.5, not 0.55 (`Verse/Pawn_HealthTracker.cs:1158-1159`). The untended 3.2 per wound differs from vanilla's pawn-wide 8 a day, and the bed's x1.35 differs from vanilla's added +4 and the bed's XML heal (`Verse/Pawn_HealthTracker.cs:1123-1161`). Infirmary-cot healing, infection and tend bonuses are XML or unconfirmed. |
| Surgery | Partial | Amputation and prosthetic or engineered-part installation. Success ignores medicine potency and no medicine is consumed (both `SECONDARY`/`UNVERIFIED` gaps). No operations on injuries. |
| Downed enemies dying from pain shock | Gap | `SECONDARY`; not implemented. |
| Death at consciousness 0% | Gap | `SECONDARY`; not a separate rule. |
| DLC exclusion | Matches | No DLC-sourced condition or operation found in the sim (audited from memory). |

#### Follow-ups

1. Confirm the `UNVERIFIED` values against the game's data or a readable wiki. Start with finger and toe HP, coverage,
   healing rates, the scar rule and the infection numbers.
2. Wire filtration into immunity gain, and Metabolism into rest rate.
3. Apply consciousness directly to Moving and Manipulation.
4. Make surgery success depend on medicine potency, and consume medicine.
5. Re-tune illness progression against the wiki's per-day figures.
6. Add glitterworld medicine and a staged blood-loss hediff.


### 3.5 Needs, mood, thoughts, drugs, mental breaks

**Status: Partly closed** (round 13, branch `ccr-41984ede-stlmp4`). The rest and joy drains, the hunger categories, malnutrition, break timing and cooldown, the zero-rest collapse, drug tolerance and withdrawal, and the comfort, beauty, outdoors and indoors needs now follow the decompiled reference, or are marked C or UNVERIFIED. Sources and the list of rows not adopted are in `needs-mood.md`. A second review (same round) re-checked the citations at their lines. Craving is now summed over the addictions in withdrawal, so it no longer depends on hediff order. One in-bed test (`isInBed`) now serves both the outdoors need and the rest gain. **Still open:** the thought catalogue and its staging, mood smoothing and baseline, the chemical interest need, break kinds and durations, starting rest and joy, and the joy pause after a gain. **Priority 1 for the open items.**
**Files:** `NeedRules.kt` (`NeedRules`, `BreakRules`, `DrugRules`), `Mood.kt` (`moodUpdate`, `breaksTick`, `comfortTick`, `placeNeedsTick`, `restCollapseTick`, `startBreak`), `Game.kt` (`pawnTick` need drain, `starvationTick`), `JobDrivers.kt` (`driveSleep`, `applyDrug`), `Pawn.kt` (`Thought`, `addThought`, new need and tolerance fields), `Body.kt` (drug hediffs), `Jobs.kt` (`URGENT_FOOD`, `startSleepInPlace`), `SaveGame.kt` (version 32).

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| Hunger rate (adult human) | **1.6 per day** of a 0-to-1 bar (was 0.7) | **1.6 nutrition per day** | matches | D (RimWorld/Need_Food.cs:218; `decompiled-reference.md` line 187). Round 1 came from search summaries: M, UNVERIFIED, no URL recorded (`needs-mood.md`) |
| Raw plant food nutrition | 0.05 each | 0.05 each | none | V (https://rimworldwiki.com/wiki/Food) |
| Hearth stew nutrition / cost | 0.9, from 10 raw items = 0.5 nutrition | 0.9, from 0.5 nutrition of ingredients | none | V for the vanilla 0.9 meal value and the 0.5 ingredient cost (`https://rimworldwiki.com/wiki/Simple_meal`, a search result read in round 9; the page itself was not read, and the wiki version is not stated); the 10-item count is C (Stillmere recipe) |
| Rest drain | **0.95 per day awake**, by band: x0.7 tired (below 0.28), x0.3 very tired (below 0.14), x0.6 exhausted (below 0.01); none while asleep or lying down in a REST job (stage 1); a REST job still walking to its bed drains (was flat, and it also drained in REST) | 0.95 per day in the Rested band, by the same band multipliers | matches (rate); band rule adopted | D (RimWorld/Need_Rest.cs:20, 38-46, 57-59, 104-117; RimWorld/Toils_LayDown.cs:94-97 for the gain only in the lay-down toil; `needs-mood.md`) |
| Joy drain | **Per band** per 150 ticks: 0.6 per day empty and satisfied and above, 0.42 low, 0.24 very low; none in a JOY job (was 0.42 flat) | per band, as above | adopted | D (RimWorld/Need_Joy.cs:45-50, 115; :19, 23, 27) |
| Hunger categories | **Food use x1 fed, x0.5 hungry (below 0.24), x0.25 urgently hungry (below 0.12), x0 starving**; urgent eat interrupt below 0.12 (was 0.15) | the same multipliers | adopted | D (RimWorld/HungerLevelUtility.cs:28-31; Need_Food.cs:21, 23) |
| Malnutrition | **0.453 per day** gained while starving and lost **while food is above 0** (was 0.28 gained, and 2.88 per day lost above 0.15); no per-pawn factor | 0.453 per day, scaled per pawn by 0.8 to 1.2 | differs (per-pawn factor deferred) | D (RimWorld/Need_Food.cs:13, 174) |
| Mental break thresholds | **0.35, 0.20, 0.05** of a 0-1 mood (unchanged; match). Per check: a break of each level can happen only after 2000 ticks below it, at a mean time of 4, 0.8 and 0.5 days (was 0.45%, 1.2%, 3% per update with no wait). A 15000-tick cooldown follows each recovery (new). Traits unchanged (C) | Minor threshold from a stat (no base in code, so 0.35 is not decompiled); major = 4/7 and extreme = 1/7 of it, which gives 0.20 and 0.05 at 0.35. Checked every 150 ticks at MTB 4, 0.8 and 0.5 days; 2000-tick wait; 15000-tick cooldown | matches the round-1 values; timing adopted | **M, UNVERIFIED** for the values 0.35, 0.20 and 0.05: round 1 took them from search summaries of the RimWorld Wiki, with no URL recorded (`needs-mood.md` lines 233-234). **D** for the fractions and the timing (Verse.AI/MentalBreaker.cs:40, 42 for the fractions; :24-28 for MTB; :165-177, 296-306 for the 2000-tick wait; :389 for the cooldown). The minor base 0.35 is read from a stat (:44) and is not decompiled |
| Zero-rest collapse | **New**: after 1000 ticks at zero rest a colonist or prisoner may lie down where it stands; mean days 0.25, 0.125, 1/12, 0.0625 by time at zero | the same, with a life-stage curve | differs (curve not applied) | D (RimWorld/Need_Rest.cs:172) |
| Thermal thoughts (cold and hot stages) | **Stages at 10, 20, 30 degrees** below or above the comfort limit. Stage 0 (under 10) is -0.02 (was -0.05 from 3 degrees), so the mild thought is weaker; the stage 0 and stage 3 values are C | stages at 10, 20, 30; stage 0 is an active thought | matches (bands); values C | D (RimWorld/ThoughtWorker_Cold.cs:16; ThoughtWorker_Hot.cs:15) |
| Mood baseline | 0.55, smoothed 50/50 with the previous value every 250 ticks | baseline 0.5 recalled; thoughts sum to a level | model differs | M |
| Break kinds | 10 (wander, berserk, food binge, insult, tantrum, fire, catatonic, run wild, drug binge, hide) | more kinds, each tied to a severity band | partial | M |
| Distinct thoughts | about 45 | well over 100 recalled | much smaller | M |
| Needs | food, rest, joy, **comfort, beauty, outdoors, indoors** (added this round: outdoors and indoors are saved; comfort and beauty are derived each update, and comfort has no mood effect yet) | food, rest, joy, comfort, beauty, outdoors, indoors, chemical needs | chemical interest need missing; comfort mood effect missing | D (RimWorld/Need_Comfort.cs:41-61; Need_Beauty.cs:63, 67, 95; Need_Outdoors.cs:44-60, 106-140; Need_Indoors.cs:10, 56, 103) |
| Drugs | beer, rolled bundle, pale dust, white rush, surge tonic, dawn tablet, lucid draught (hidden) | also luciferium, ambrosia, penoxycyline recalled | 3 missing | M |
| Addiction | one roll per use at 1.5% to 45% by drug, times the tolerance curve (x1 at 0, x5 at 0.5, x15 at 0.8); addiction falls 0.5 per day; withdrawal at 1% (beer addiction now falls too, which it did not before) | per-drug addictiveness with tolerance; need falls by its def's fall rate (default 0.5); withdrawal at 0.01 | curve and fall adopted; per-drug values UNVERIFIED | D (RimWorld/DrugStatsUtility.cs:11-13; Need_Chemical.cs:63; NeedDef.cs:60) |
| Tolerance | **per pawn, per drug**: +0.05 per dose, -0.025 per day, capped at 1, saved from version 32. Values are Stillmere's own (`BREW_RESISTANCE` is still unused) | per-drug tolerance built up and decayed by XML values | curve adopted; build-up and decay **UNVERIFIED** | C |
| Drug policy | per colonist: none, social only, whenever bored | named, shareable policies with per-drug rules | much simpler | M |
| Food policy / outfit | per colonist: 3 and 4 fixed options | named, editable policies | much simpler | M |

**Also true:** mood is one smoothed scalar, so moods do not stack or decay like vanilla thought lists; "Lonely" and "Under fire" are
not vanilla thoughts as far as I know (**UNVERIFIED**) and look invented.

### 3.6 Skills, traits, passions, backstories

**Status: Partial** (learning, the XP curve, decay, the daily cap, the XP per work tick and per attack, and the generation of base levels and passions now follow the decompiled reference, see [skills-traits.md](skills-traits.md)). **Priority 2.**
**Files:** `Skills.kt` (`SkillRules`), `Pawn.kt` (`gainXp`, `skillIntervalTick`), `Game.kt` (`newHuman`, `pawnTick`, `hourlyTick`), `JobDrivers.kt` (`doWork`), `Ai.kt` (`fire`, melee), `Health.kt` (`tendPawn`), `Defs.kt` (`SkillType`, `Trait`), `SaveGame.kt` (version 33).

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| Skills | 12, cap 20 | 12, cap 20 | none | M |
| Learning rate, no / minor / major passion | 0.35 / 1.0 / 1.5 | 0.35 / 1.0 / 1.5 | none | D (RimWorld/SkillRecord.cs:379-381) |
| Daily learning cap | 4000 XP a day, then 0.2 | same | none | D (SkillRecord.cs:33, 37, 391) |
| Skill decay | from level 10: 0.1 to 12 XP per 200 ticks; the level drops at -1000 XP | same | none (no use check) | D (SkillRecord.cs:207-249) |
| XP per level | 1000 + 1000 x level to 9; 10000 + 2000 x (level - 9) to 19; 30000 above | same | none | D (SkillRecord.cs:47-52) |
| Passion budget | 5 + Gaussian (+/-4); major 1.5, minor 1; passions add no levels | same | none | D (Verse/PawnGenerator.cs:1857-1870) |
| Base level | uniform 0 to 4, then backstory gains x 1.0 to 1.4, the age factor and the final compression | same, for skills whose flag is the default | per-skill flags are XML (UNVERIFIED) | D (PawnGenerator.cs:1963-2016) |
| XP per work tick | mining 0.07, plant sowing and harvest 0.085, construction and deconstruction 0.25, repair 0.05, research 0.1, bills 0.1, animal gathering 0.13 | same | none for these jobs | D (JobDriver_*.cs, Toils_Recipe.cs:188) |
| Combat XP | shooting 170 (hostile) or 20 x cycle seconds per burst; melee 200 x cycle seconds per attack | same | none | D (Verb_Shoot.cs:14-16; Verb_MeleeAttack.cs:31) |
| Medicine XP per tend | 500 (animal 175) x 0.5 | 500 (175) x medicine factor, the factor being potency x 0.7 clamped to 0.5 to 1; default 0.5 (round 23) | the factor formula is D; the potencies are secondary (health.md section 4) | D (Toils_Tend.cs:68-70; Verse/ThingDef.cs:712) |
| Eager pupil / late bloomer learning | 1.4 / 0.6 | XML stat | values UNVERIFIED | UNVERIFIED |
| Traits | 42, no degrees, 1 to 3 drawn (a clash is redrawn) | around 50, with degrees (for example two levels of work speed) | degrees and commonality missing | M |
| Backstories | 33 (11 childhood, 22 adulthood) | hundreds | tiny set; titles are original | M |
| Backstory effects | skill gains (summed, x 1.0 to 1.4), a few disabled work types; the forced-trait mechanism is in place, and no backstory uses it yet | all three, with trait grants | trait lists and trait skill grants open | M |

All 42 traits have some effect in code (several only add a mood thought: meat-first, machine-minded, flame-drawn). the traits labelled Easy company, Meat-first and Inventive eye (names.md section 6.1) are not traits I recognise from the base game
(**UNVERIFIED**).

Still open: trait degrees and skill grants, trait and backstory commonality and lists (XML), passion conflicts, and the
sexuality chance (ambiguous, kept as is). Child passions before 13 (PawnGenerator.cs:1886-1904, ungated by DLC) and the kind
extra levels and ranges (PawnGenerator.cs:1998-2015) are not modelled; the first is unreachable from `newHuman`, which makes
adults only. The derived-cache caveat is in 3.17: the health caches are fixed, and the room cache and room temperatures are open.

### 3.7 Work and jobs

**Status: Partial** (structure **Matches**). **Priority 2.**
**Files:** `Jobs.kt` (`think`, `tryWork`, finders, bills), `JobDrivers.kt`, `Reservations.kt`, `Regions.kt`, `Pathfinder.kt`, `Defs.kt` (`WorkType`).

**Matches:** 18 work types in the vanilla order (firefight, patient, doctor, warden, handle animals, cook, hunt, construct, grow, mine,
plant cut, smith, tailor, art, craft, haul, clean, research); priorities 1 to 4 with 0 for off, lowest number first, then list
order; a 24-hour schedule of anything / work / joy / sleep; reservations and unreachable memory; region-based reachability.

**Divergent or missing:**
* Cleaning only targets roofed cells.
* Bills have 3 modes and a minimum skill; no ingredient search radius, no quality or hit-point range, no store-location option.
* Butchering is automatic at a carving slab rather than a bill.
* `nearestCell`, `findFood` and similar scan every item or cell each call; a risk at 250 x 250, which is now the size of a new colony.
* Study desk: tier 3 projects need the precision lab; vanilla gates by analysis array and tech-level rules (**UNVERIFIED**).

### 3.8 Construction, power, fire, zones

**Status: Partial.** **Priority 2.** Round 16 (`economy.md`) closed the construction and power rows of the reference. Fire and zones are not in that round.
**Files:** `Buildings.kt`, `Game.kt` (`placeBlueprint`, zones), `JobDrivers.kt` (`driveBuild`), `World.kt` (`powerTick`, `fireTick`, `explode`), `GameMap.kt` (`Zone`).

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| Buildings offered | 72 (76 defined, 4 legacy) | far more | partial | M |
| Wood wall | 5 wood, 160 work | wall costs 5 stuff; work **UNVERIFIED** | | M |
| Sun array | 1,700 W; output scaled by the unroofed share of its footprint (round 16; D `CompPowerPlantSolar.cs:17`) | 1,700 W recalled; roof share in source | roof share matches; rating UNVERIFIED (XML) | M |
| Log-burning engine | 1,000 W | 1,000 W recalled | rating UNVERIFIED (XML) | M |
| Breeze mill | 1,500 W x wind; wind used capped at 1.5 (round 16; D `CompPowerPlantWind.cs:32`); wind walk 0.15 to 1.35 | cap 1.5 in source; walk is noise (XML) | cap matches; walk UNVERIFIED | M |
| Battery capacity | 1,000 Wd per battery (round 16; D `CompProperties_Battery.cs:5`, code default) | 1,000 recalled | matches; battery XML may override (UNVERIFIED) | M |
| Battery self-discharge | 5 W per battery per tick, capped by stored energy (round 16; D `CompPowerBattery.cs:70`) | 5 W per tick | matches | high |
| Battery charge efficiency | Lossless | code default 0.5 (D, `CompProperties_Battery.cs:7`); XML may override (UNVERIFIED) | not adopted (D; XML UNVERIFIED) | M |
| Glow panel draw | 400 W | 2,900 recalled; no reference row (XML) | UNVERIFIED | M |
| Hotplate, picture box, grow tray | 350, 200, 70 W | same recalled | UNVERIFIED | M |
| Construction learning | 0.25 XP per tick in the work stage, after all materials are in | 0.25 per tick while the frame holds resources | **Matches** (reference row was stale) | high |
| Zone priorities | low, normal, preferred, important, critical | same five | **Matches** | M |
| Materials | wood, stone, steel, hardalloy for walls; wood or steel for doors | many stuff types by property | 4 only | C |
| Fire | intensity 0 to 1.2 per cell, spreads by flammability, rain 0.01 per step | fire size model | model differs | C |

**Missing:** roofs and roof collapse, cut stone by rock type, wall and door insulation, doors for power or vents, live-wire-free
power transmitters, multiple room-based work-table effects, snow clearing zones, named animal and pen areas (only 3 restriction
areas exist), a hit-point filter on zones.

**Open (power, round 16):** the power shortage shedding (every 20 ticks, 5% of consumers) and the battery restart reserve need a
finer power step than the 625-tick network step, so they are deferred. Also open: the breeze mill's 250-tick wind read and its
blocked wind path, the battery charge efficiency (D for the code default; the XML value is UNVERIFIED), and the sun output curve (depends on the time model in 3.1).
Building costs and power ratings are XML in vanilla and stay UNVERIFIED (see `economy.md`).

### 3.9 Plants, food, cooking, spoilage

**Status: Partial.** **Priority 2.** Round 16 (`economy.md`) closed the crop growth, harvest, rot and raw-meat rows of the reference.
**Files:** `Defs.kt` (`PlantType`, `ItemType`), `World.kt` (`plantsTick`, `spoilTick`, `deteriorationTick`), `Recipes.kt`, `JobDrivers.kt` (`driveSow`, `driveCut`, `driveEat`, `driveButcher`), `Jobs.kt` (`findGrow`).

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| Crop grow days (rice, potato, corn, strawberry, cotton, mirewort) | 5.8, 5.8, 11.5, 6.5, 5.5, 8 | same recalled | per-plant XML UNVERIFIED | M |
| Growth speed | `1 / days` per day at full factors (round 16; the 0.55 is removed, D `Plant.cs:346`); about 0.46 of the old rate on Stillmere's daylight, so rice takes about 11.6 game days, not 5.4 | `1 / growDays` times the light, fertility and temperature factors | **Matches** in formula; the daylight-as-glow mapping is C | high |
| Fertility factor | `fertility x 0.5 + 0.5` (round 16; D `PlantUtility.cs:358`); grow tray growth factor 2.0 kept as before (C), applied directly and not through the blend | same blend, sensitivity default 0.5 | per-plant sensitivity UNVERIFIED | high |
| Temperature factor | 0 at the growth limits (6 C below and 16 C above the 6 to 42 C band), ramp to 1 in the band (round 16; D `PlantUtility.cs:370-381`) | same ramp; limits 0 and 58 C by default | per-plant limits UNVERIFIED | high |
| Light factor | Ramp from glow 0.51 to 1.0, glow = daylight (lamp cells count as 1.0) (round 16; D `PlantUtility.cs:361-367`) | same ramp | daylight-as-glow mapping is C | medium |
| Rain and garden-sense growth bonuses | Removed (round 16; D `Plant.cs:289-302`: no weather or trait term) | none | **Matches** | medium |
| Corn yield / potato yield | 40 / 11 | 40 / 11 recalled | none | M |
| Rice yield | 12 | 6 recalled | 2x | M |
| Harvest yield | Zero at or below 65% growth (not harvestable); above it `count x (0.5 + 0.5 x share from 65% to 100% growth)`, randomly rounded, no skill term (round 16; D `Plant.cs:115`, `Plant.cs:917-919` and `934-936` for the zero, `Plant.cs:932` for the formula); health term 1 (C); difficulty 1 | same formula, plus health and difficulty | **Matches** | high |
| Crop harvest threshold | Crops harvestable above 65% growth (round 16; D `Plant.cs:115`); trees and regrowing plants keep the full-growth rule | harvest minimum 65% | **Matches** for crops | high |
| Sown crop starting growth | 0.0001 (round 16; D `JobDriver_PlantSow.cs:61`) | 0.0001 | **Matches** | high |
| Crop growth temperature | 6 to 42 C band (wispsilk from 10) | 6 to 42 recalled | optimal band matches | M |
| Sowing temperature gate | Strictly between the growth limits (round 16; D `PlantUtility.cs:21-33`) | same | **Matches** | high |
| Frost kill | below -2 C, 15% per slow tick | ? | C | ? |
| Spoilage (days): raw meat | 2 (round 16; D `ThingDefGenerator_Meat.cs:37`, destroyed at the rotting stage) | 2 | **Matches** | medium |
| Spoilage (days): meals, milk | 4, 4 | ? (XML) | UNVERIFIED | ? |
| Spoilage: raw plants | 40 days (strawberries 10) | ? | UNVERIFIED | ? |
| Corpse rot | 2.5 days (round 16; D `ThingDefGenerator_Corpses.cs:181`) | 2.5 days to rotting, 5 to dessicated | **Matches** to rotting; no dessicated stage (see below) | high |
| Rot temperature factor | 0 at or below 0 C, linear to 1 at 10 C (round 16; D `Verse/GenTemperature.cs:459-470`) | same | **Matches** | high |
| Raw meat poisoning | 2% per eating; no rotten-food roll for raw meat (round 16; D `ThingDefGenerator_Meat.cs:68`) | 2% | **Matches** | high |
| Other meat-category poisoning | 1% per eating; 40% for other food over 65% rot | fresh-stage rule; other food's boundary is XML | UNVERIFIED for non-meat food | C |
| Butcher meat yield | Round-random of `meat x (0.55 + 0.3 x min(cook speed, 1.5))`, no rot term (round 16; D `Verse/Pawn.cs:2962`); the cook-speed term is C | round-random of meat amount times efficiency | efficiency mapping C | medium |
| Cooking output count | Fixed output per recipe (`Recipes.kt`) | ceil(count x efficiency) | no source mapping for efficiency; left fixed | medium |

**Grass (round 18, `grass.md`):** grass ground (a terrain, fertility 1.0 UNVERIFIED) with a grass cover that grows by the shared plant rules, is harvested for hay from 65 percent growth, and regrows after a harvest. Vanilla's grass values are XML and are UNVERIFIED; the grass cover's yield is a Stillmere choice.

**Missing:** wild-plant ecology (grass spread, plants from seed, death from lack of light, plant lifespan), plant fertility mapping beyond terrain, animal feed items beyond fodder and feed
pellets, insect jelly, chocolate, and the like. Also not in this round: death from lack of light (needs a persisted counter on
each plant, so a save bump), plant lifespan (needs plant hit points), the food-choice cut-off at 88% rot and the rotting boundary
for non-meat foods (XML), and the dessicated corpse stage. The mash works (a separate bench for beer) is **not in the base game as far as I
know (UNVERIFIED)**; the reference shows a fermenting barrel and no mash bench identifier in source.

**Defect fixed (round 16):** a subsoil auger brought up ore from the cell it stood on, but ore only sits in rock and a building
could not stand on rock, so a real auger always yielded rough rock. The auger may now stand on ore rock. When it is finished,
its rock is drilled out to gravel and the ore stays in the cell, so it pulls the ore from its own cell. Regression:
`BaseContentTest.aSubsoilAugerPlacedOnOreRockYieldsItsOwnOre` (places the auger, has a colonist build it, then checks the steel).
`aSubsoilAugerIsStillRefusedOnRockWithNoOre` checks that rock without ore is still refused. The earlier test
`aPoweredSubsoilAugerBringsUpItsOre` still places its ore by hand.

### 3.10 Animals

**Status: Partial.** **Priority 3.**
**Files:** `Race.kt`, `Animals.kt`, `Life.kt` (`animalLifeDaily`), `JobDrivers.kt` (`driveTame`, `driveTrain`, `driveHunt`, `driveSlaughter`, `driveGather`).

* 38 animal species plus 3 mechanoids; vanilla has roughly 70 animals (**UNVERIFIED**).
* **Training is invented.** Vanilla trains specific skills (obedience, release, rescue, haul) in a fixed order. Stillmere teaches up to two generic "tricks" that make an animal hit harder (`MAX_TRICKS = 2`, `Pawn.trained`). Divergent; no DLC involved.
* Taming chance, wildness, herd behaviour, predator behaviour and meat/leather yields are custom numbers and unverifiable here.
* Colony animal cap of 40 in breeding is custom.
* Missing: pens and area restrictions for animals, animal bonding, wildlife migrations, nuzzling, pack animals' effect outside caravans.

### 3.11 Combat

**Status: Partly done (combat round, see `combat.md`).** **Priority 1.**
**Files:** `Ai.kt` (`fire`, `aimChance`, `coverAgainst`, `meleeSwingHits`, `shotPath`, `hostileAI`, `draftedAI`), `Combat.kt` (constants and rules), `Burning.kt` (burning pawns), `Defs.kt` (`Weapon`, `Apparel`, `DamageKind`), `Health.kt` (`dealDamage`), `World.kt` (`turretsTick`, `explode`, `fireTick`).

Done this round, each with its source in [combat.md](combat.md):

* **Accuracy by range band.** Touch, short, medium and long bands with breakpoints at 3, 12, 25 and 40 cells, blended and clamped (D, `Verse/ShootTuning.cs:5-11`; `Verse/VerbProperties.cs:652-653`). The medium and long values are a Stillmere choice (C). One stated accuracy per weapon; the four per-weapon band values are not added (combat.md, plan item 1).
* **Shooter factor.** The aim is `f ^ distance` times the band factor, floored at 0.0201, then the target size factor (body size clamped 0.5 to 2), the prone factor and the execution factor (all D). The base `f` is UNVERIFIED.
* **Two rolls for a ranged shot.** An aim roll, then a cover roll. The reference row for the cover pass is D.
* **Cover.** The eight cells around the target are scanned, with angle and distance factors. Walls are full-fill (0.75). A shot that cover stops hits the cover building (D for the weighting, C for the damage). Cover now also takes the damage, and is read again for each pellet, so a cover shot apart stops no more (C).
* **Armour.** Layers are rolled one by one, outermost first. A roll deflects below half the rating, halves below the rating, and makes a sharp hit blunt. Apparel takes 25 percent of the damage that reaches it. No cap. Penetration defaults to damage x 0.015 when a weapon sets none (all D). The rating kind is read once per hit, so a halved sharp hit leaves the later layers their sharp rating (D, `Verse/ArmorUtility.cs:21`).
* **Damage.** Ranged damage is deterministic (D). Melee damage varies from 0.8 to 1.2 (D). Explosions hit each pawn once (D).
* **Melee.** Reach 1.42 (adjacent, diagonals included, D). A non-miss roll, then a dodge roll (D for the two stages; the dodge value is C). A downed target is always hit: both rolls are skipped (D for the skips; downed standing in for immobile is C). The under-1 melee rule (raised to 1 and blunt) is adopted (D). A swing that leaves the target alive and on the map staggers it for 95 ticks (D).
* **Burning.** A flame hit that is not deflected sets the pawn alight (D, the cumulative chance over 60 ticks). A pawn on a fire above intensity 0.4 may catch fire (D, 150 ticks). A burning pawn takes the reference fire damage every 150 ticks (D, unrounded), and its job stops (D). The direct 3.5 x intensity damage to pawns in fire cells is removed.
* **Save.** Version 35 to 36 (burn size, stagger end). A version 35 fixture loads with both at zero.
* **Ranged reach.** Ends exactly at the weapon range, with no tolerance (D).

Still open:

* **Burn dynamics** (decay per check, the intensity read as fire size, pawn flammability 1.0) are Stillmere choices or UNVERIFIED.
* **Parry** is not in the decompiled source (a search of the whole tree finds no "Parry"). It is not added.
* **Not adopted:** warmup reset on target change (medium confidence, negative search); burst spacing; cooldown factors; minimum range for ranged weapons (needs a melee fallback); stray misses; the intercept and impact model; walls that stop shots only past 5 cells; blunt stun curves; EMP; shields; the fire growth, spread and rain rows of 3.8. Reasons are in [combat.md](combat.md).
* **Weapon stat blocks are still Stillmere's.** The per-weapon values, the medium and long factors, and the apparel armour values are not from the source (C or UNVERIFIED). Vanilla warmup and cooldown are in seconds (the units match, so the units are not a gap).
* **Suppression** ("a volley that hits nothing pins down the target") and the "Under fire" thought are Stillmere inventions, kept (UNVERIFIED).
* 38 weapons defined. Swing repeater and lucid draught are hidden by `Catalog.kt` as "not in the base game" or "not confirmed"; **I believe both are in Core (UNVERIFIED), so hiding them may be wrong.**
* Tests: `CombatPipelineTest` (20, three updated; `GameTest` `HealthTest.woundsBleedAndTendingHelps` one more; the version assertions in five Legacy tests; see combat.md), `CombatModelTest` (27, new; four added in the review round), `LegacyCombatMigrationTest` (4, new). Full run: 713 pass. The combat numbers are checked against the reference's formulas, not against vanilla play.

### 3.12 Social and life stages

**Status: Partial.** **Priority 3.** (A **DLC-sourced** candidate is flagged.)
**Files:** `Social.kt`, `Life.kt`, `Mood.kt`, `Pawn.kt` (`stage`, `opinion`, `spouse`, `lover`).

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| Year | 60 days | 60 days | none | V (https://rimworldwiki.com/wiki/Time) |
| Baby / child / teen / adult | 0 to 2, 3 to 12, 13 to 17, 18+ | ? | | ? |
| Growth moments | at 7, 10, 13 (passion, skill, trait) | Biotech only: growth points at Verse/Pawn_AgeTracker.cs:371, each birthday moment at :703 | **DLC-sourced: flagged, not removed.** The reference rows for its passion, skill and trait gains are not adopted. | D |
| Pregnancy | 30 days; 6% chance per day for a partnered woman aged 16 to 45 | ? | | ? |
| Old age | chronic conditions from 40 to 62; death roll from 80 | ? | | ? |
| Social interactions | 8 kinds: chitchat, insult, argument, compliment, romance, and three friendly talks (their thoughts are Candid exchange, Warm talk and Friendly ribbing) | more, including slights and marriage proposals | partial | M, UNVERIFIED |

**Missing:** relation types beyond parent, spouse, lover and opinion (siblings, cousins, ex-partners, bonded animals), weddings and
parties, social fights beyond a bruise exchange, babies' separate needs. Orientation traits gate romance (`ContentTest.orientationsGateRomance`).

### 3.13 Threats and storytellers

**Status: Partly closed (round 15, section 15).** Priority 1 stays for the open items listed there.
**Files:** `Incidents.kt` (`IncidentRegistry`, `threatPoints`, the raid cycle), `Events.kt` (`launchRaid` and all event effects), `Game.kt` (`Storyteller`, `Difficulty`, `init` timers), `Factions.kt` (`pickRaiders`). The values and their sources are in [threats.md](threats.md).

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| Raid points | (wealth points + colonist points and 0.48 x danger per tamed animal, each blended by health) x difficulty x storyteller x min(1, 0.34 + day / 34), clamped between a seeded floor (35 up to a day ceiling of 70) and 10,000 (`Incidents.kt`) | **(wealth points + pawn points) x difficulty x starting factor x adaption**; buildings count at half; wealth capped at 1,000,000; points capped at 10,000. The formula's shape and its curves are now D (RimWorld/StorytellerUtility.cs:185-188). Still open: the pawn market-value term and the adaptation term. | adaptation and pawn terms not modelled; days factor and threat scales are Stillmere's (UNVERIFIED) | D (RimWorld/StorytellerUtility.cs:185) |
| Difficulty threat scale | 0 / 0.6 / 0.9 / 1.5 / 2.2 | ? | | ? |
| First raid | day 8 to 12 | ? | | ? |
| Raid kinds | rush, tunnelers, shelling camp, lander drop, iron swarm (vanilla raid kinds: names.md section 6.3) | straight assault, sappers, breach, siege, drop pods, infestation, mech cluster | partial | M |
| Incident channels | 6 timers (raid, wanderer, pod, trader, temperature, misc) with 24 incident types. Raids of Marlowe and Orrin follow an on and off cycle (round 15); the other channels keep their timers | storyteller-specific incident cycles | model differs for the other channels | M |
| Storytellers | 3, original characters | 3 in Core | structure similar, behaviour not | M |

**Missing:** pawn market-value wealth, adaptation, population-intent weighting, siege budget, temperature gates, psychic drone levels, raid strategy kinds, friendly raids (see section 15 for what is still open)
(allied reinforcements exist only through comms), wanderer and refugee rules, the incident list beyond what is registered. "Aurora"
is registered (incident `AURORA`, log text now "sky curtain") and I cannot confirm it is a Core event (**UNVERIFIED**).

### 3.14 Research

**Status: Divergent.** **Priority 2.** Round 16 (`economy.md`) set the reference progress rate and session length. The cost scale and base costs remain Stillmere's.
**Files:** `Defs.kt` (`Research`), `Game.kt` (`researchAvailable`, `startResearch`), `JobDrivers.kt` (`driveResearch`, `researchPointsPerTick`), `Jobs.kt` (`findResearch`).

* 57 projects (README says 53).
* **Progress rate (round 16):** `0.00825` points per work tick (D `ResearchManager.cs:38`) times work speed, bench factor and keen mind, on Stillmere's point scale of 16 (C, kept so saved progress keeps its meaning). At work speed 1 a project takes its base cost divided by 0.00825 ticks, the reference formula's time.
* **Cost:** `base cost x 16`. The base costs are **UNVERIFIED**: vanilla costs are project XML and are not in the decompile. The old progress rate was 0.1 per tick, so a project now takes about 24% less time at the same cost.
* **Learning:** 0.1 XP per tick on scholarship through `gainXp`, with passion and saturation (matches D `JobDriver_Research.cs:39`; the reference row was stale).
* **Session (round 16):** 4000 ticks in Delay mode (D `JobDriver_Research.cs:48`), was 2250.
* Keen mind 1.35 and the precision lab factor 1.6 stay Stillmere values (C, UNVERIFIED; the reference has no value for keen mind, and the bench factor is XML).
* Several project names are not vanilla Core as far as I know (for example "Banquet cookery", "Alloy edging") (**UNVERIFIED**); the tree shape is original.
* Missing: the tech-level cost factor (`1 + 0.5 x (project level - researcher level)`, D `ResearchProjectDef.cs:486-499`). It needs the colony's tech level, which is set in faction XML that the decompile does not contain, and a mapping from Stillmere's three tiers. Deferred to the owner, because it would make tier 2 and 3 projects 1.5 and 2 times dearer. Techprints match (none without Royalty). Study desks' effect beyond the 1.0 and 1.6 split is XML (facility offsets).

### 3.15 World map, factions, caravans, quests

**Status: Partial.** **Priority 2.**
**Files:** `WorldMap.kt`, `Factions.kt`, `Caravans.kt`, `Battles.kt`, `Encounters.kt`, `Quests.kt`, `Trade.kt`, `Ransom.kt`.

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| World size | 60 x 40 tiles | much larger | much smaller | M |
| Hill levels | flat, small, large, impassable | flat, small, large, mountainous | **Matches** | M |
| Factions | 6: 2 tribal, 2 outlander, 2 pirate | non-hostile and hostile faction types | similar | M |
| Carry capacity: human / shagox | 35 kg / 70 kg | same recalled | none | M |
| Item mass: silver, steel, wood, component | 0.008, 0.5, 0.5, 0.6 kg | same recalled | none | M |
| Caravan speed | `1900 ticks per cost unit`, slowed up to 40% by load | ? | | ? |
| Quest kinds | 2 (clear a camp, rescue captives) | many | far fewer | M |
| Trade price | sell `0.5 + social * 0.01`, buy `1.35 - social * 0.01` of base value | trade price stats and negotiator skill | simplified | M |

**Implemented and tested:** caravans that travel, trade, fight on a temporary battle map, retreat, are taken captive, found new colonies;
faction goodwill, ransom offers, allied aid, orbital traders. The battle map is an ordinary `Game` in encounter mode, which does
reuse the weapons, wounds and AI as the README says.

**Missing:** world sites other than bandit camps, ancient ruins, item stashes, quest types, world feature names, planet coverage and seed
options, weather and biome effects on travel.

### 3.16 UI and controls

**Status: Partial.** **Priority 2.** **Read from source only; the app was not built or run.**
**Files:** `app/` (`MainActivity.kt`, `GameView.kt`, `Panels.kt`, `Dialogs.kt`, `WorldUi.kt`, `MenuActivity.kt`, `SaveUi.kt`, `Sprites.kt`, `Overview.kt`, `TutorialUi.kt`, `Updater.kt`).

* Bottom bar: Architect, Work, Schedule, Research, People, Animals, Map, World, Trade, Log, Menu.
* Touch: pan, pinch zoom, tap to inspect, draft, move, attack.
* Landscape only (`sensorLandscape`); `minSdk 26` (Android 8.0) matches the README.
* Speeds: pause, 1x, 3x, 6x.
* Policies are per-colonist buttons, not editable named policies (see 3.5).
* Zone filters, quality limit and priority are present (`Dialogs.kt:362-383`).
* Not reviewed in depth: rendering (`Sprites.kt`, `GameView.kt`), the world-map UI, the in-app updater.
* Everything is drawn procedurally; no copied art (consistent with the README).

### 3.17 Start, scenarios, save and load

**Status: Partial** (saves are strong; scenarios are simplified). **Priority 2.**
**Files:** `Game.kt` (`startNewColony`, `startSettlement`, `Scenario`), `SaveGame.kt` (format version, see names.md section 2), `SaveStore.kt`, `SaveSession.kt`, `Tutorial.kt`, `JobRestore.kt`.

* Four scenarios: salvage crew (3 colonists), quiet enclave (5), surplus start (1), bare start (1). Start gear, supplies and starting research are custom. Vanilla scenarios are data-driven and customisable (**UNVERIFIED**).
* Colonists are random on start; a crew-selection step exists (`Dialogs.kt:240`).
* Saves: versioned format (28), old versions still load (tested back to version 1), autosave with a recovery copy, named saves, quicksave, archive of settled colonies, damage detection with fallback. This is the best-tested area (`SaveFormatTest`, `SaveRestoreTest`, `SaveSessionTest`, `SaveStoreTest`).
* Win condition: ship (navigation brain, 2 engines, core furnace, cold pod) then launch. Vanilla's ship needs a different part list (**UNVERIFIED**).
* Determinism caveat for saves: R2.
* Derived caches are not saved. The health caches (`cap`, `pain`, `healthDirty`) are recomputed on load. In the live game they are
  now refreshed on every read once an input has changed, so the live and loaded games read the same values (fixed in 3.6;
  `HealthCacheTest`). The room cache (`roomDirty`) is still cleared only at the next slow tick in the live game, while a load clears
  it at once, so the two can differ for that window (**open**). Room temperatures (`roomTemp`) are not saved at all, and a load starts
  every room at the outdoor temperature (**open**; it needs a save format change, version 34 with a fixture).

## 4. README claims checked against the code

| README claim | Verdict |
|---|---|
| "33 backstories" | **True** (11 + 22). Tiny compared with vanilla. |
| "12 skills with passions" | **True.** |
| "42 traits" | **True.** |
| "work priorities for 18 job types" | **True.** |
| "24-hour schedule" | **True.** |
| "Needs for food, rest, joy and temperature" | **True**, but temperature is a health hazard rather than a need, and vanilla's comfort, beauty and outdoors needs are absent. |
| "mood built from dozens of thoughts" | **Partly true.** About 45 thoughts, summed into a smoothed scalar. |
| "Mental breaks, social interaction, romance, marriage, rivalries and family" | **True** for those; family is parent, child, spouse, lover only. |
| "Life stages: children grow up, adults form pregnancies, old age brings chronic conditions" | **True.** |
| "Per-colonist outfit and drug policies, food policies" | **Partly true.** Fixed per-colonist options, not policies. |
| "drug use with tolerance, addiction and withdrawal" | **Partly false.** Addiction and withdrawal exist. **Tolerance does not**: the hediff is defined and never used. |
| "29 human parts, with organs, hit locations, armor, penetration, bleeding, pain, consciousness, infection, scarring and lost limbs" | **True.** |
| "Doctors tend wounds with leaf poultices or sterile dressings; infirmary cots heal faster" | **True.** |
| "chest cough, boil sickness, marsh fever, heavy drowse, parasites, cold core, sunstruck, frostbite, starved frame and spoiled stomach" | **True.** |
| "65 buildings" | **Stale.** 72 are offered. |
| "Rooms ... have temperature, beauty, cleanliness and impressiveness values" | **True.** |
| "Stockpile, growing and dumping zones with item filters, quality limits and priorities" | **True.** |
| "Workbench bills for cooking, sewing, forging, shaping, stone cutting and medicine" | **Partly false.** Butchery is not a bill; it is an automatic job at a carving slab. |
| "Power grids with solar, wind, wood and battery storage. Fire spreads, rain puts it out, and lightning can start it." | **True.** |
| "Six biomes, rivers, lakes, five rock types" | **True.** |
| "six ores" | **False.** Five ores. |
| "caves" | **True from round 19** (`caves.md`): caves are generated on hill tiles, so only colonies on large hills have them (about 4 percent of starts). The README claim was re-added when caves existed. |
| "seasons, rain, fog, snow, lightning squalls, deep chills and scorching spells" | **True.** |
| "Crops that depend on temperature, light and soil; spoilage of food and corpses; items that wear outdoors" | **True.** |
| "Wildlife for hunting, taming, slaughter and breeding" | **True.** |
| "generated planet with hills, mountains, rivers, lakes, roads and six factions" | **True** (60 x 40 tiles). |
| "Caravans carry goods by weight ... Ambushes become battles ... using the same weapons, cover, wounds and AI as colony fights" | **True.** The battle map reuses the main `Game` class. |
| "Settlements can be traded with, supplied, or attacked. Bandit camps can be cleared for rewards." | **True.** |
| "Quests with acceptance, deadlines and consequences, and rescue missions" | **True**, 2 kinds. |
| "Three storytellers and five difficulty levels" | **True.** |
| "Raids that scale with colony wealth: rushes, tunnelers, shelling camps and lander drops" | **True.** |
| "Rogue packs, pest blooms, disease, corona surges, shadow passages, fume clouds, grid faults, crop mildew, falling stones, smoke pall events, crashed iron hulks, wandering colonists and peddler convoys" | **True.** |
| "A research tree of 53 projects" | **Stale.** 57. |
| "Four scenarios. Launch the ship to win. Automatic and manual saves." | **True.** |
| "Android 8.0 or later" | **True** (`minSdk 26`). |
| "The in-app updater downloads newer builds and keeps saves across updates" | **Not verified.** `Updater.kt` exists; not built or run. |
| "The `Build APK` workflow builds it on every push" | **True** (`android.yml`). |
| "contains no RimWorld code or assets ... All art is drawn procedurally" | **Consistent with what was read**; see 2.2 for names. |
| "Download the APK" link | **Stale repo name** (X2). |

## 5. Existing tests

Totals: 337 tests, 22 files. `GameTest.kt` alone holds 24 test classes and 111 tests.

| File (test classes) | Tests | Covers |
|---|---|---|
| `GameTest.kt` / PathfinderTest, WorldTest | 2, 4 | A* with walls and breaching; terrain per biome; room heat from a campfire; sunlight to a lamp; fire spread |
| ... HealthTest | 9 | bleeding, tending, healing, death by heart loss, losing legs, armour, cold core, illness course |
| ... ColonyTest | 8 | quiet days survive, beds, chopping and mining, crops, cooking bills, research, stockpile filters, save load |
| ... CombatTest, AnimalTest, TradeTest | 3, 3, 1 | turret and drafted fights; capture and recruit; hunting, taming, herds; trading |
| ... ScenarioTest, AdvancedTest, AreaTest | 3, 5, 1 | every scenario runs; storyteller and difficulty scaling; engineered-part surgery; mech scrap; area restriction |
| ... CraftingTest, VictoryTest, CaravanTest | 6, 1, 5 | stitchwork, forgework, rock splitting, mash craft, soilless growing; ship launch; caravan trade, travel, save |
| ... FactionTest, MapSizeTest, SettleTest | 6, 1, 1 | six factions, relations, peace talks, allied aid; large map; founding a colony from a caravan |
| ... LifeTest, MultiTileTest, ContentTest | 4, 2, 7 | pregnancy, growing up, old age, animal breeding; footprints; recipes and research chains, orbital traders, orientations |
| ... RulesTest, MaterialTest | 3, 8 | outdoor wear, outfit policy, lovers; wall and door materials with research gating |
| ... CaravanBattleTest | 14 | caravan fights: win, lose, retreat, animals, saving mid-battle, settlement assault |
| ... TutorialTest, RansomTest | 4, 17 | tutorial lessons; ransom offers, cooldowns, saves |
| `BacklogFixTest.kt` | 14 | regression fixes: caravans, prisoners, breaks, beds, floors, sappers, faction drift, raid spacing |
| `BaseContentTest.kt` | 16 | turrets, lobbing tube, subsoil auger, pyre house, sculpture, beer, vat mash, hidden content |
| `BodyPartsTest.kt` | 3 | fingers and toes affect grip and walking |
| `CombatPipelineTest.kt` | 20 | melee reach, range, warmup, cooldown, cover, walls, interception, skill, armour, vital parts, determinism of a fight |
| `IncidentTest.kt` | 29 | eligibility, cooldowns, channels, seeded selection, per-incident effects, save of the history |
| `ItemsTest.kt` | 10 | quality and condition travel with goods through stock, caravans, trade, saves |
| `JobAiTest.kt` | 10 | urgent hunger and rest, bed release, reservation races, unreachable memory, interrupted hauls |
| `MoodSocialTest.kt` | 14 | loneliness, friendly ribbing, arguments, compliments, spouse separation, hiding, lounge seat |
| `PerformanceEquivalenceTest.kt` | 5 | optimised searches match simple versions; same seed gives the same save bytes |
| `PerfBenchTest.kt` | 4 | benchmarks, mostly disabled |
| `QuestTest.kt` | 17 | camp and rescue quests, captives, saves |
| `RegionTest.kt` | 5 | reachability agrees with a breadth-first oracle |
| `ReservationTest.kt` | 11 | exclusive claims, multi-claims, cleanup, infirmary cots, saves |
| `SaveFormatTest.kt` | 10 | blob storage, damage and recovery, version-1 upgrade |
| `SaveRestoreTest.kt` | 11 | a loaded game continues exactly as the original; jobs and claims restored |
| `SaveSessionTest.kt` / PlayClockTest | 17 | play time counting, named saves, autosave recovery, menu flows |
| `SaveStoreTest.kt` / RngTest | 19 | RNG matches `java.util.Random`, round trips, corruption handling |
| `SuppressionTest.kt` | 5 | suppression (a Stillmere mechanic, see 3.11) |
| `TrainingTest.kt` | 4 | animal tricks (a Stillmere mechanic, see 3.10) |
| `UpdatePolicyTest.kt` | 2 | build-number parsing for updates |

**Coverage gaps:** no test for needs drain, hunger rate, mood thresholds, skill learning rate, weapon stat values, raid point
formula, temperature exchange, research cost, or any number from the tables above. No tests exist for `app/`.

## 6. DLC-sourced and flagged content

Per `CLAUDE.md`: flagged, not deleted.

| Item | Why flagged | Confidence |
|---|---|---|
| Growth moments at ages 7, 10, 13 (`Life.kt growthMoment`) | recalled as a Biotech mechanic | M, **UNVERIFIED** |
| `Incident.AURORA` | cannot confirm it is a Core event | ? |
| Recently added furniture: lounge seat, bookshelf, wardrobe, coffee table (latest commits) | cannot confirm they are Core; the commit history shows content was added item by item | ? |
| The mash works building, and the banquet cookery research | not recalled from Core | M |
| The traits labelled Easy company, Meat-first and Inventive eye | not recalled as Core traits | ? |
| `W_LEVER`, `CLEAR_MIND_TEA` (hidden) | I believe these are Core, so they may be wrongly hidden | M |
| Animal "tricks", suppression, "Lonely" thought, colony animal cap | invented, not DLC; flagged as non-vanilla (not as DLC) | C |
| `Incident.MECH_CRASH` (mech cluster, Core channel, day 25) | the reference says the vanilla cluster refuses to run unless Royalty is active (`RimWorld/IncidentWorker_MechCluster.cs:11`). Round 15 flags it as DLC-sourced and keeps it (threats.md). The owner decides whether it stays. | D (the gate), M (the content) |
| Colonist points for Biotech (age factor, colony mechs) | Biotech-gated in vanilla (`RimWorld/StorytellerUtility.cs:58-64, 178`). Not implemented, so nothing to flag in the sim; recorded here so it is not added without a decision. | D |

## 7. Top 10 gaps

1. ~~**Time base (24,000 vs 60,000 ticks per day)**~~ **Done** (`7e402c9`): 60,000 ticks per day. (3.1)
2. **Map: new colonies default to 250 x 250 (round 6), and the new-colony wizard offers the six reference sizes from 200 to 325 (round 21); grass ground and covers are in (round 18, `grass.md`); caves are generated on hill tiles (round 19, `caves.md`); ruins are generated on colony maps (round 20, `ruins.md`).** Roofs and roof collapse are in (3.3, `roofs.md`); the app has a Build roof tool but no remove-roof command yet. (3.2, 3.3, 3.9)
3. **Needs and mood model:** the drains, hunger categories, break timing, drug tolerance and the four added needs now follow the decompiled reference (round 13, `needs-mood.md`). The thought catalogue, mood smoothing and the chemical interest need are still missing. (3.5)
4. **Combat model** (3.11): band accuracy, the shooter factor, the cover pass, the layered armour roll, melee dodge and stagger, and burning are done from the reference (combat round, `combat.md`). Still open: the weapon stat values, the intercept and stray-miss model, minimum range, stun, EMP, shields, and the fire growth rows of 3.8.
5. **Threat generation:** raid points formula, caps, storyteller cycles. (3.13)
6. **Health depth:** 29 parts, simplified bleeding and immunity, limited chronic disease, surgery and medicine. (3.4)
7. ~~**Skill model:** learning rates, decay, the XP curve, the daily cap and the XP per work tick and attack.~~ **Done** (round 14, `skills-traits.md`). Open: trait degrees and skill grants, trait and backstory data, passion conflicts. (3.6)
8. **Production numbers:** research cost model, recipes, building costs, power draws (glow panel, battery), crop growth 1.8x fast; subsoil auger cannot find ore; hidden Core items. (3.8, 3.9, 3.14)
9. **Rule violations in `sim/`:** wall clock and UUID in `SaveStore`, hash-order save bytes, platform trig. Small fixes. (2.1)
10. **Committed signing key** (X1). Labels and text copied from RimWorld are resolved for the verify check (2.2).

## 8. Proposed phase order

Branch names follow `phase-<n>-<short-name>` once you approve. Phases 2 and 3 change numbers that old saves depend on, so they
need your sign-off on a save-migration approach first.

| Phase | Name | Content | Needs from you |
|---|---|---|---|
| 1 | `phase-1-hygiene` | Pin `<VERSION>`; fix R1 to R3; delete nothing; create `docs/fidelity/` with one file per system and the constant tables above; add a first fidelity test per system as numbers are verified; fix the README counts and links | the version; the naming decision (2.2); the signing-key decision (X1) |
| 2 | `phase-2-timebase` (time base; largely done, see docs/fidelity/time.md) | Move to 2,500 / 60,000 ticks and rescale every per-tick constant; save migration | approval to break or migrate old saves |
| 3 | `phase-3-needs-mood` (partly done: hunger, break thresholds; see `needs-mood.md`) | Hunger rate, rest and joy drains, add comfort, beauty, outdoors and indoors needs, vanilla break thresholds, thought catalogue, tolerance | none after phase 2 |
| 4 | `phase-4-skills-traits` | Learning rates, decay, XP curve, work-speed curves, trait degrees, backstory set | the backstory approach (original titles only) |
| 5 | `phase-5-health` (done on `main`, see 3.4) | Body depth, bleeding and immunity, chronic conditions, medicine, surgery | none |
| 6 | `phase-6-combat` | Weapon stat blocks, accuracy bands, armour roll, melee dodge and parry, burning, cover | none |
| 7 | `phase-7-map-rooms` | 250 x 250 maps, roofs and collapse, grass, caves, ruins, heat exchange; fix the subsoil auger | performance budget on phones |
| 8 | `phase-8-threats` | Raid points curves and caps, storyteller cycles, raid kinds, infestations | none |
| 9 | `phase-9-economy` | Research costs, recipes, building costs, power, crops, spoilage | none |
| 10 | `phase-10-world-ui` | World size, quests, trade, named policies and areas, UI parity | none |

Open questions blocking Phase 1: the target `<VERSION>`; whether proper names count as "text strings" (2.2, resolved for labels and text); what to do with the
committed signing key (X1); whether to hold the invented mechanics (suppression, tricks, "Lonely") until after the vanilla ones land.

## 9. What this audit did not cover

* `app/` was not compiled or run. `Sprites.kt`, `GameView.kt` and `WorldUi.kt` were not read in depth.
* `SaveGame.kt` and `SaveStore.kt` were read for structure, not line by line.
* No vanilla source was available. All vanilla comparisons are **V** (secondary summaries) or **M/UNVERIFIED**.
* Item-level costs, work amounts, weapon stats and most event numbers were not compared, because no reliable source was reachable.

## 10. Changes since the audit (2026-10-10)

Made on branch `ccr-401b8c33-djvmwb`. Sources for the new values are in `needs-mood.md`.

* **Phase 3, partly:** mental-break thresholds set to 0.35, 0.20 and 0.05 of mood. Human hunger rate set to 1.6 nutrition
  per day. Starting food in every scenario was scaled so each opening lasts as long as before. The gap report gives a URL for both new numbers, so they are **V** there
  (secondary sources). `needs-mood.md` records no URL for its sources, so there they are **M, UNVERIFIED**. The per-check chances, the trait multipliers and the scaling factors are Stillmere's own.
* **R3, fixed:** every `Math.sin`, `Math.cos`, `Math.pow`, `Math.hypot` and `atan2` in `sim/` is `StrictMath`.
* **README:** counts corrected to 72 buildings and 57 research projects. Removed "caves", changed "six ores" to five, and
  removed butchery from the bill list.
* **Tests:** 451 pass, up from 443. Eight are new, in `NeedsMoodFidelityTest`. One existing test changed: the baby
  feeding check in `LifeTest` now restocks milk and watches a full day, not half. Adults eat milk, so the old stock was gone
  before the baby needed it.
* **Not done, and why:** the version pin (no source, `CLAUDE.md` still says `<VERSION>`), the naming decision (2.2), the
  signing key (X1), the rest and joy drains and the four missing needs (no source), and drug tolerance (no source).
* **Compiler warnings:** section 1 says three, all in tests. Two more are in `Game.kt` (`Game.kt:455`, "condition always true", and
  `Game.kt:569`, "always false"). They were already there before this change, and nothing was done about them.
* **Stale in this report:** section 1's test count (337) and warning count, section 3.3 and 3.4 (health and temperature changed after the
  audit), and section 7 item 1 (done).

## 11. Names and labels on branch `ccr-41984ede-stlmp4`

Committed as `eb9784c`.

* **2.2 resolved for labels and text, with open items** (see the status line in 2.2). Mapping, rules and open items are in [names.md](names.md). Enum positions are unchanged (checked by position, names.md section 8).
* **Round 7 (review of the names area).** No old RimWorld name remains in a code literal. The labels that the review listed were already renamed, and their constants were renamed in round 7 (names.md section 5.2). Labels renamed in earlier rounds, and the round 7 renames, are listed with their old and new names in names.md sections 6.1 to 6.4, which is the only place this report refers to them. The benches that were renamed are listed in names.md section 6 and section 11.
* **Saved text (version 30 to 31).** Three backstory titles were renamed in version 31 and are translated when read for saves below version 31 (names.md sections 2, 3 and 6.5). The death causes written into the graveyard were renamed for new deaths; old graveyard entries are kept as saved and never translated (round 9; names.md section 2). In version 30 (round 7), four thought labels and one temporary event name were renamed and are translated for saves below version 30 (names.md sections 3 and 6.4).
* **Tests:** 490 pass, 0 failures, 0 errors (`./gradlew :sim:test --console=plain`).
* **Round 8 (review of round 7).** Graveyard causes were translated for saves below version 31 (reversed in round 9); three backstory titles were renamed (version 31); the V rows the review named have URLs or new tags; the old labels in this report were removed; `README.md` no longer named the AI model (restored in round 9). Tests: 499 pass, 0 failures, 0 errors. Round 10 gave every V cell a URL or changed it to M, UNVERIFIED (names.md section 10). Nothing is committed.
* **Round 9 (review of round 8).** Graveyard entries are no longer translated: an old entry is kept as saved (names.md section 2; the read site in `SaveGame.kt`). `README.md` line 12 is restored to the committed sentence that says the project was built with Claude (names.md decision 7). The three backstory titles stay renamed; the owner decides whether they should be (names.md section 9, item 10). A health test name that echoed a Stillmere label was renamed (names.md section 5.3). The hearth stew and outdoor-temperature URLs were checked by a search in round 9. The map size vanilla values are M, UNVERIFIED. Round 9 test count: see names.md section 13.
* **Not compiled:** the `app/` module (no Android SDK here). The renamed constants were applied to it by the same rule, and a grep finds no old identifier in `app/`.
* **Round 11 (verdict on round 10, and the names-fix task).** The graveyard and log-line decisions are stated in the goal (names.md section 0) and in section 2. The log lines are kept as saved, and the claim that they are not saved is withdrawn. The save code has a comment at each read site. A test checks that an old graveyard string loads unchanged. The V cells of the four fidelity docs were re-checked by script: each has its URL in the row. The social-thought test was renamed to match its label ("Friendly ribbing"). The decisions on the titles, the README, the scope, the graveyard and the log lines are for the owner to confirm (names.md section 9). Nothing is committed.
* **Round 12 (review of round 11, findings A and B).** The lower-case adult titles of T13 and T14 have keys (T20 and T21), so an old save that held either one loads the new adult title. The pirate camp texts saved in site names and pending-fight labels are translated for saves below version 28 (S01-S03). Caravan last-event notes are kept as saved, as a record of a past battle. The decisions on the camp texts and the last-event notes are for the owner to confirm (names.md items 26 and 27; section 16). Tests: 507 pass, 0 failures, 0 errors (`./gradlew :sim:test --console=plain`). Nothing is committed.
* **Round 13 (review of the needs and mood work, two findings).** (1) A REST job walking to its bed (stage 0) falls, as the reference does: the fall is skipped only in an update that took a gain, and a walk takes none. The review said every REST job was exempt. The code exempts only stage 1 (`Game.kt`, rest line), and a test already checked the walk. The reason is now in the code comment and in `needs-mood.md` row 195. A test now checks the fall on every step of a walk to the bed, and the gain only after the pawn lies down. Exempting every REST job makes both walk tests fail. (2) The break-threshold row tags 0.35, 0.20 and 0.05 as M, UNVERIFIED (round 1, search summaries, no URL recorded, as `needs-mood.md` lines 233-234 say). D is kept only for the 4/7 and 1/7 fractions and the timing. No wiki URL is left for the thresholds. Game.kt line numbers in `decompiled-reference.md` now match the current file. Tests: 551 pass, 0 failures, 0 errors (`./gradlew :sim:test --console=plain`). Save VERSION is 32 in the working tree (set by earlier uncommitted work); this round changes no saved data. Nothing is committed.
* **Round 15 (graveyard causes, log lines and caravan notes).** Renamed wording in these saved texts is translated when a save is read, below the version of each rename: the death causes below 31, the pirate names and camp labels below 28, and the log sentences, raid labels, and chronic and disease labels below 27 to 30 (names.md sections 2 and 3, rows S04, L01-L36 and G01-G09). Item, building, creature, plant, drug and research labels embedded in log lines are not translated (names.md open items 29 and 30). The save version is not bumped. `LegacyNamesMigrationTest` has 53 tests, all passing, and the full suite is 825 tests with 0 failures.
* **Round 16 (review of round 15, names.md section 20).** The round 15 text of names.md that said log lines, caravan notes or the bare pirate name are kept as saved is corrected, as are the git history facts behind the death-cause gate (`a9c032b` and `eb9784c`, with `eb9784c^` the last commit that holds the old causes, at `VERSION` 25) and the fixtures table, and it records that the fixtures are tracked at `HEAD`. The code is unchanged, except one comment in `LegacyNamesMigrationTest.kt`. `./gradlew :sim:test --console=plain`: 825 tests, 0 failures, 0 errors, 0 skipped. Nothing is committed.
* **Other docs.** Old RimWorld names in this report were replaced with references to names.md (the temperature event offsets, the food hediff row, the raid kinds and the quoted README claim). `health.md` and `README.md` use the round 7 names as well.

## 12. Decompiled reference (version 1.6.9438.38202)

Added on branch `ccr-41984ede-stlmp4`. Source: the decompiled RimWorld source at [Chillu1/RimWorldDecompiled](https://github.com/Chillu1/rimworlddecompiled), commit `2d50803`. Its README says personal use only, which the owner accepted for this work. Full table: [decompiled-reference.md](decompiled-reference.md).

Two passes checked the citations. A reader extracted each value, and a second pass re-opened the cited line. **532** citations were confirmed. Rejected citations are not listed.

| Section | System | Confirmed | Matches | Differs | Ambiguous |
|---|---|---|---|---|---|
| 3.1 | Time and determinism | 16 | 11 | 3 | 2 |
| 3.2 | Map generation | 35 | 0 | 29 | 6 |
| 3.3 | Temperature and rooms | 31 | 9 | 20 | 2 |
| 3.4 | Health and medicine | 68 | 15 | 51 | 2 |
| 3.5 | Needs, mood, thoughts, drugs, mental breaks | 44 | 4 | 34 | 6 |
| 3.6 | Skills, traits, passions, backstories | 29 | 2 | 24 | 3 |
| 3.7 | Work and jobs | 35 | 2 | 33 | 0 |
| 3.8 | Construction, power, fire, zones | 43 | 4 | 35 | 4 |
| 3.9 | Plants, food, cooking, spoilage | 26 | 2 | 21 | 3 |
| 3.10 | Animals | 36 | 2 | 27 | 7 |
| 3.11 | Combat | 50 | 3 | 45 | 2 |
| 3.12 | Social and life stages | 25 | 3 | 20 | 2 |
| 3.13 | Threats and storytellers | 27 | 1 | 21 | 5 |
| 3.14 | Research | 15 | 6 | 8 | 1 |
| 3.15 | World map, factions, caravans, quests | 36 | 6 | 27 | 3 |
| 3.17 | Start, scenarios, save and load | 16 | 5 | 5 | 6 |
| | **Total** | **532** | **75** | **403** | **54** |

What this means:

* **Matches** agree with the decompiled source. The most matches are in health (15), time (11), temperature-rooms (9), research (6).
* **Differs** is a real difference, a unit or scope difference, or a difference in what the system includes. A "differs" is not automatically a bug. For example, a slow-update interval that Stillmere keeps in a converted unit looks different in ticks but is not a value error.
* **Ambiguous** depends on runtime data, such as the starting season or tile temperatures, or on several candidate values. The reference doc marks these rows.
* Section 3 status cells are unchanged. They will move only after each system is reviewed against the table in the reference doc.

The pinned build is 1.6.9438.38202, the same as the source, so the D values in this section match it.

## 13. Skills, traits and passions (3.6), round 14

Made on branch `ccr-41984ede-stlmp4`. The plan, every constant with its source, and the rows not adopted are in [skills-traits.md](skills-traits.md).

* **Adopted from the reference (D):** learning factors 0.35, 1.0, 1.5 and the daily cap (4000, then 0.2); the XP curve; decay from level 10 every 200 ticks; the level cap at 20 and the drop at -1000 XP; the XP per work tick for mining, plants, construction, repair, research, bills, and animal gathering; shooting and melee XP per cycle second (the melee cycle includes the warm-up term, zero in the data); medicine XP per tend; the passion budget, forced passions and the order of passions; base levels (uniform 0 to 4), the backstory gains, the age factor and the final compression; the trait count with redraw on a clash, with backstory traits counted toward the target (PawnGenerator.cs:1495-1510).
* **Removed (no reference basis):** the flat per-meal and per-gear XP (40 and 60), the per-tick tending XP, the flat 220 per tend, the old 15%-chance base level, and the +2 levels for each passion.
* **Kept, marked UNVERIFIED:** the Eager pupil (1.4) and Late bloomer (0.6) learning factors (XML stats in vanilla), and the Stillmere backstory gains (design values).
* **Not adopted:** trait degrees and skill grants, trait commonality, passion conflicts, backstory trait lists (all XML data); the growth moments, which are DLC and flagged (3.12).
* **Discrepancies:** the brief said `VERSION` is 31; it is 32 at HEAD. The brief said decay applies to unused skills; the decompiled code has no use check, so it does not. The reference's cooking citation (`JobDrivers.kt:1191`) is butchering.
* **Save version 33.** One new saved field: `xpToday` (the daily counter, 12 floats per pawn), gated on version 33. Two version-32 fixtures are tested by `LegacySkillsMigrationTest`: `legacy/colony_v32.sav` (all XP zero at its save point) and `legacy/colony_v32_xp.sav` (the same colony with non-zero XP, written at version 32; the current writer reproduces the original `colony_v32.sav` byte for byte at version 32, so the layout is unchanged). Enums unchanged.
* **Tests changed, each for a stated reason:** the version assertions (32 to 33); a marksmanship test that now holds the skill level for each shot, since shots teach; a ransom test whose prisoners now get a content thought (the new traits lower some moods, and a prisoner below 0.28 tries to escape, which blocks offers; no assertion changed); the melee XP test now expects the full-cycle term.
* **Superseded in section 14:** the next bullet (the relaxed `JobAiTest` checks and the masked save test) describes the state after round 14, not now.
* **Two assertions relaxed, and one masked:** `JobAiTest` (the haul and build tests) used to require no reservation at all. The colonist now equips a bow, which holds one key, so the check now requires the held keys to be exactly the current job's key. That allows that one key. A haul reservation on another tile still fails it. A leftover on the same key as the current job would not be told apart from the job's own key; no case here shares a key. `SaveRestoreTest` (the blast-on-save test) flushes the health caches before saving. That **masks** the save and load divergence in 3.17 and does not fix it.
* **Review fixes in this round:** the citations to `Verse/GenTicks.cs:67-70` and `Verse/Verb_Shoot.cs:14-16` (were `RimWorld/`); the shooting amounts are at :14 (not :15, the cycle); the growth birthday gate is at `Verse/Pawn_AgeTracker.cs:703` (`:371` gates growth points); the recipe factor is `Verse/RecipeDef.cs:65`; the decay hash phase is in the decompile (`Verse/Gen.cs:214-217, 254-257`), and Stillmere keeps its id stagger (C); backstory traits count toward the trait target (`PawnGenerator.cs:1495-1510`), with a test; child passions before 13 (`PawnGenerator.cs:1886-1904`, ungated) and the kind extra levels and ranges (`PawnGenerator.cs:1998-2015`) are recorded as not modelled, with the unreachable path explained; the melee cycle includes the warm-up term; the Gaussian floor 1e-7 is recorded as Stillmere's guard (C); the version 33 note is in `SaveGame.kt`; the non-zero XP fixture is new.
* **Tests:** 601 pass, 0 failures, 0 errors, 0 skipped (`./gradlew :sim:test --console=plain`). New: `SkillsTraitsTest` (40, two of them for the trait count), `LegacySkillsMigrationTest` (10, four of them for the non-zero XP fixture).
* **Open:** the save caveat for derived caches (3.17); trait data (3.6); the per-skill backstory flags (UNVERIFIED), and per-medicine factors (closed in round 23, section 23); the work-speed curve (3.6 and 3.7, no reference row).
* **Nothing is committed.**

## 14. Review fixes to section 13

Made on branch `ccr-41984ede-stlmp4`, after a review of section 13. The plan, the constants and the rows not adopted are in [skills-traits.md](skills-traits.md).

* **Health caches (determinism).** The blast-on-save test had flushed the health caches before saving, which hid a difference between the live and the loaded game. The flush is removed, and the test is back to its original form. `Pawn.cap` and `Pawn.pain` now refresh on read whenever an input has changed. Every write to an input sets the mark (`Health.kt`, `Pawn.kt`, `Mood.kt`, `World.kt`, `Life.kt`). `HealthCacheTest` (3 tests) checks each read against a fresh computation, and the loaded game against the live one. Removing one of the marks makes it fail (checked).
* **Rooms (open).** The room cache is stale in the live game until the next slow tick after a building change, and room temperatures are not saved. Not fixed here; see 3.17.
* **Skills code.** `applyXp` is now a climb (the rise, with the cap) followed by a fall (the drop, with the level-0 hold). The passion budget is a purse that buys a stock of passions, and forced and unforced passions draw from it in turn. The parameter name `force` is gone. Both were checked against the previous code by a temporary test (about 3 million XP inputs, 200,000 passion seeds, the same RNG draws after each call); the test was deleted, and the results match exactly.
* **Backstory traits.** Forced backstory traits skip repeats, not clashes, as in the reference (`Verse/PawnGenerator.cs:1461-1492`; `RimWorld/TraitSet.cs:258-270`). The doc and the code comment said the opposite. Two tests added (`SkillsTraitsTest`).
* **Draw order.** The doc said the target is drawn before the backstory traits. The reference draws it after the forced traits (`Verse/PawnGenerator.cs:1496`), and the code did the same. The doc is corrected, and the row now states the count of traits held.
* **JobAiTest.** The two reservation checks are strict again. The weapons are removed from these two tests, so no equip job (which holds a key of its own) can start during the check.
* **Citations.** The 66 citations in `skills-traits.md` were opened at their lines, and their values checked. The short-name citations in the rows not adopted (`TraitUtility.cs`, `TraitDegreeData.cs`, `GrowthUtility.cs`, `PawnGenerator.cs:1429-2015`, `Toils_Recipe.cs:107`) were checked too. The `Verse/PawnGenerator.cs:1563-1595` attempt cap is checked.
* **Save format.** Unchanged. `VERSION` is 33 and nothing saved changed, so there is no bump and no new fixture. The brief said 31 at the start of the run; the tree had 33 (section 13 has 32 at HEAD).
* **Tests:** 606 pass, 0 failures, 0 errors, 0 skipped (`./gradlew :sim:test --console=plain`, from a clean test task). Section 13 had 601: `SkillsTraitsTest` now has 42, and `HealthCacheTest` (3) is new. Section 5's totals were out of date before this round and are not refreshed here.

## 15. Threats and storytellers (3.13), round 15

Made on branch `ccr-41984ede-stlmp4`. The plan, every constant with its source, and the rows not adopted are in [threats.md](threats.md). Nothing is committed.

* **Adopted (D):** the points cap of 10,000; the floor, now a seeded value from 35 up to a day ceiling (35 at day 12, 70 at day 35), drawn once per game hour and not scaled by difficulty; the wealth curve (0 to 14,000 gives 0, then 2,400 at 400,000, 3,600 at 700,000, 4,200 at 1,000,000); buildings at half value in the wealth; the colonist curve (15, 140, 200 per colonist) with the 65% health blend, which tamed animals get too (D, RimWorld/StorytellerUtility.cs:167-173 applies it to every counted pawn); the 0.08 factor for each tamed animal that is not downed; the manhunter ambush factor of 0.75, and the manhunt count's rounding and its minimum of two (D); the recent-incident factors 0.01, 0.3, 0.5, 0.7, 0.9 over the last five random incidents; the 60-interval day and the storyteller check step of 1,000 ticks, used by the raid cycle; a raid cycle window that cannot place its raids is dropped whole, not only the raids past its end (D, RimWorld/IncidentCycleUtility.cs:64-69).
* **Adopted with Stillmere's own values (C, UNVERIFIED):** the raid cycle for Marlowe (day 8, on 10, off 6 days, 3 or 4 raids, 2 days apart) and for Orrin (day 8, on 8, off 12, 2 or 4 raids, 2 days apart). Juniper keeps the timer. The mean rates match the old timers (Marlowe about 0.22 a day, Orrin about 0.15). The animal combat power is Stillmere's proxy (6 times the danger), the manhunt count uses 18 times the danger (C, UNVERIFIED), a danger floor of 0.5 and a cap of 10 (C; the cap is a scope choice), the seed mixer and salts are Stillmere's (C), and the health of colonists and animals is `healthFraction()`.
* **Kept:** the days-passed factor, the difficulty and storyteller threat scales, the hard cooldowns, the uniform channel spacing for every channel except the raid channel of Marlowe and Orrin, the hourly check, and the first raid timer (day 8 to 12).
* **Not adopted, with reasons:** the adaptation term and its counter (all curves are XML); population-intent weighting (XML curves and a population-adaptation counter); the siege budget (no camp construction); the temperature gates (needs per-tile seasonal temperature); the psychic drone levels (no levels in Stillmere); raid strategy kinds (ambiguous); the pawn market-value term (no pawn value in Stillmere); Biotech colonist factors (DLC); the animal release-trainable condition (no animal training state); the manhunt upper bound of 100 (scope); the cycle window's end, which vanilla includes by one step and Stillmere excludes (C). The mech cluster is flagged as DLC-sourced in section 6 and kept.
* **Save format: version 33 to 34.** One new field: the five most recent random incidents by name, written after the incident history and read only from version 34. A version 33 fixture (`legacy/colony_v33.sav`, with `legacy/colony_v33_expect.txt`) was made from the unchanged code before the change. `LegacyThreatsMigrationTest` checks that it loads, that its list reads as empty, and that a version 34 save keeps a list.
* **Tests changed:** six assertions that the default write is version 33 now say 34, and four test names that said "ThirtyThree" were renamed to match (`LegacyNeedsMigrationTest`, `LegacySkillsMigrationTest`, `LegacyNamesMigrationTest`). No other existing test asserted a threat value.
* **Tests:** 644 pass, 0 failures, 0 errors, 0 skipped (`./gradlew :sim:test --console=plain`). Before this round, 606.
* **Discrepancy:** the brief said `VERSION` is 31. It was 33 at the start of this run.
* **Checked by a temporary run (deleted):** raid launches over a year, five seeds: Marlowe 76.8 (expected about 78), Orrin 51.8 (about 53.5). The first version of the cycle timer lost hits that sat exactly the spacing after the last one (58 a year); the timer now measures from the time it was due.
* **Open:** adaptation, population-intent weighting, the siege budget, the temperature gates, the psychic drone levels, the raid strategy kinds, the pawn wealth term, the storyteller points-by-days curves (XML), and cycles for the other channels. Item values are not checked against vanilla market values (UNVERIFIED), so the wealth curve's scale is unconfirmed.

## 16. Production and economy (3.8, 3.9, 3.14), round 16

Made on branch `ccr-41984ede-stlmp4`. The plan, every constant with its source, and the rows not adopted are in [economy.md](economy.md). Nothing is committed.

* **Adopted (D, checked in the decompiled tree):** battery capacity 1,000 Wd (code default; the battery XML may override it, UNVERIFIED); battery self-discharge of 5 W per battery per tick, capped by stored energy; the breeze mill's 1.5 wind cap; the sun array's roof share of its footprint; crop growth of `1 / days` per day at full factors (the 0.55 is removed); the fertility blend (sensitivity 0.5); the temperature ramp around the 6 to 42 C band (limits 0 and 58 C); the light ramp from glow 0.51 to 1.0; no rain or garden-sense growth bonus; a sown crop starting at 0.0001; crop harvest above 65% growth; the harvest yield, zero at or below 65% growth and from about half to full above it, with random rounding; raw meat spoiling in 2 days and corpses in 2.5; the rot rate ramp from 0 C to 10 C; raw meat poisoning at 2%, and no rotten-food roll for raw meat; the butcher's meat without a rot term, randomly rounded; the sowing gate at the growth limits; the research point rate of 0.00825 per work tick (about 24% less time per project than before); the research session of 4000 ticks.
* **Adopted with Stillmere's own values (C or UNVERIFIED):** the research point scale of 16 (kept, so saved progress keeps its meaning); the growth limits as the optimal band plus the default distances; the daylight as glow, or a lamp's or fire's light where that is brighter, on the same ramp (`plantGlow`, not a step); the health term of 1 for plants; the cook-speed term in butchering; the research base costs (UNVERIFIED).
* **Already matched, no change:** construction learning (0.25 per tick in the work stage) and research learning (0.1 per tick through `gainXp`). Both reference rows were out of date.
* **Bug fix (not a reference value):** a subsoil auger can stand on ore rock, drills the rock out at completion, and pulls the ore from its own cell. Regression test `BaseContentTest.aSubsoilAugerPlacedOnOreRockYieldsItsOwnOre`.
* **Not adopted, with reasons (summary; each row is in `economy.md`):** power shedding and the battery restart reserve (needs a finer power step); the breeze mill's 250-tick read and blocked path; the battery efficiency of 0.5 (XML may override); death from lack of light and plant lifespan (save bump and plant hit points); the food-choice cut-off and the non-meat rot boundary (XML); cooking output count (no efficiency mapping); the tech-level cost factor and tech-level scale (faction XML; a decision for the owner); bench eligibility and facility offsets (XML); fire, zones, roofs, snow and work-table penalties (other rows of 3.8).
* **Crop speed, owner decision:** on Stillmere's daylight the adopted rate makes rice take about 11.6 game days instead of 5.4 (weather off; `aClearDayGivesAboutHalfAGrowTimeOfLightOnTheRamp` checks the day average of 0.499). The gap report's "1.8x too fast" was wrong for this schedule. Trees and other growing plants slow by the same factor. This is the largest balance change here and should be reviewed.
* **Save format: version 34 to 35.** The layout is unchanged. The meaning of saved rot changed (raw meat 4 days to 2, corpses 2.2 to 2.5), so rot read from older saves is rescaled as it is read: map items, job stacks, and the held food of an eat job (`Job.heldRot`, raw meat only, which the spoiled-stomach roll never uses). The research progress unit and the battery charge are unchanged. A version 34 fixture (`legacy/colony_v34.sav`, with `legacy/colony_v34_expect.txt`) was made from the unchanged code before the change. `LegacyEconomyMigrationTest` checks that it loads, that its rot is rescaled once, and that the next write is version 35.
* **Tests changed (old values):** seven assertions that the default write is version 34 now say 35, in `LegacyThreatsMigrationTest`, `LegacyNeedsMigrationTest`, `LegacySkillsMigrationTest` (two) and `LegacyNamesMigrationTest` (three). One test name (`aVersionThirtyOneSaveWrittenAgainIsVersionThirtyFour`) was renamed to `...IsTheCurrentVersion`. No other existing test asserted an economy value; the existing crop, research, battery, auger and spoilage tests pass unchanged.
* **Tests added:** `EconomyFidelityTest` (27, one or more per adopted rule), `LegacyEconomyMigrationTest` (9), and two auger tests in `BaseContentTest`.
* **Test changed for the new rate:** `GameTest.cropsGrowAndGetHarvested` is now `cropsSownInAGrowingZoneGrowOnTheReferenceRate`. It asserted a planted rice crop after 9 days. On the reference rate the first crops are still growing at 8 days (about 0.57 in the seed used), and a cold snap on day 9 kills immature crops through the existing frost rule. It now checks that the zone's crops are above 0.4 growth after 8 days. Nothing else in the suite changed.
* **Tests:** 682 pass, 0 failures, 0 errors, 0 skipped (`./gradlew :sim:test --console=plain`).
* **Discrepancy:** the brief said `VERSION` is 31. The committed `VERSION` is 34 (`SaveGame.kt:29` at `HEAD`), and the working tree has 35 (`SaveGame.kt:34`), the value this round sets. It is not changed in the review run.
* **Review fixes (this run, on the uncommitted working tree):**
  * **Light step.** The glow was full (1.0) whenever the cell light was above 0.5, and outdoors the cell light is the daylight, so the light factor jumped at half daylight. Rice would take 10.7 game days on that step, not 11.6. The glow is now `plantGlow` (the larger of the daylight and the cell's light). `aPlantUnderDimDaylightGrowsOnTheRampInTheWorldTick` fails under the step and passes now.
  * **Grow tray.** The sowing filter (`Jobs.kt` `findGrow`) used a literal 2 for a powered tray. It now uses `GROW_TRAY_GROWTH_FACTOR`, as the growth step already did.
  * **Harvest row.** The old tree and wild formula had a work-speed term (`0.5 + 0.5 x min(work speed, 1.6)`), so a tree at full growth gave the same count only at work speed 1.0. `economy.md` now says so.
  * **Citations.** Every D citation in `economy.md` and in the economy code comments was opened and its value checked. One was wrong: the bench default of 1.0 is `StatDef.cs:96` (read at `StatWorker.cs:1248`), and `economy.md` now cites both. One comment was also wrong: the cut-XP comment said "fully grown" at `JobDriver_PlantCut.cs:8-18`, but lines 11-13 gate on `CanYieldNow()`. The fully grown gate is Stillmere's (C), it was already in HEAD, and it is recorded as an open row in `economy.md`, not changed here.
  * **Untracked.** `economy.md`, `EconomyFidelityTest.kt`, `LegacyEconomyMigrationTest.kt`, `legacy/colony_v34.sav` and `legacy/colony_v34_expect.txt` are still untracked. This run does not commit them.
* **Open:** the power shedding and reserve; the breeze mill's 250-tick read and blocked path; battery efficiency (UNVERIFIED); death from lack of light; plant lifespan; the food-choice cut-off; the tech-level cost factor (owner decision); the cut and harvest learning gate (owner decision, see `economy.md`); the research base costs and all building costs and power ratings (XML, UNVERIFIED); the per-plant growth sensitivities and temperature limits (UNVERIFIED).


## 17. Map size and generation (3.2), round 17

Made on branch `ccr-41984ede-stlmp4`. The plan, every constant with its source, and the rows not adopted are in [map.md](map.md). Nothing is committed.

* **Adopted (D, checked in the decompiled tree):** ore blotches per 10,000 cells by hilliness: flat 4, small hills 8, large hills 11, impassable 16, default 10 (`RimWorld/GenStep_RocksFromGrid.cs:127-143`). One blotch covers round(10,000 / density) cells, and the count is the area over that, rounded (`Verse/GenStep_Scatterer.cs:266-274`). The count now scales with the map: a 250 by 250 map has 25 to 100 blotches where it had 18 fixed veins.
* **Adopted with Stillmere's own values (C or UNVERIFIED):** the kind shares 8 : 3 : 2 : 2 : 3, which are the old fixed veins kept as weights and as the battle plan (the reference's kind weights come from XML and are ambiguous); lump sizes 7, 4 and 3; cumulative rounding, so the veins always add up to the blotch count; the default density for a map with no world tile (C); the rounding tie mode, half to even, which is UNVERIFIED because the source uses an engine rounding call whose tie mode is not in the tree (it changes one value: 62 instead of 63 for the default density on 250); the mapping of `Hills.MOUNTAIN` to impassable (UNVERIFIED; mountainous would give 15, and 94 blotches on 250).
* **Rock threshold, not changed:** Stillmere's rock threshold for `Hills.MOUNTAIN` is 0.5. The reference gives 0.636 for mountainous (0.7 over 1.1, `RimWorld/MapGenTuning.cs:5,13`) and 0.583 for impassable. This is a "not size" row, so changing it is a separate change (map.md, "Hilliness mapping").
* **Battle maps keep their 18 fixed veins.** A battle map has no world tile, so the density rule is not applied to it. The rule would have given 2 blotches, which changed every fight's ore for a reason that is not map size. That change was reverted: battle maps are generated with `GameMap.FIXED_ORE_VEINS` (`Encounters.kt:15`).
* **Owner decision:** new colonies are 250 by 250, and the new-colony wizard now offers only that size (`Dialogs.kt`). It had 75, 100 (its default "Medium"), 150 and 200, so no button matched the 250 default.
* **Save format: `VERSION` 36 to 37.** The owner's goal asks for a bump with this change. No saved field changed: the width and height were written and read from version 14 on, so no field was added and no reader was gated. Version 37 is a marker for saves written after the change. The version 36 fixture `legacy/colony_v36.sav` was written by the code before the bump, and `ColonyMapSizeTest.aVersion36SaveLoadsAndANewSaveIsVersion37` checks that it still loads and that a new save is written as 37. The nine legacy tests that checked the literal 36 now compare with `SaveGame.VERSION`. Nineteen fixtures are committed: ten store 100 by 100, nine store 250 by 250 (eight older saves and the new one). The premise that every older save is 100 by 100 is false for these fixtures, and the origin of the 250 fixtures cannot be traced from the repository (open for the owner).
* **Not adopted, with reasons (summary; each row is in map.md):** the lake and river models (a map-wide field and a centre-point river, where Stillmere has a disc and a straight band); river channel widths; the elevation, fertility, rock-threshold, gravel and roof rules, which are not size and change every map; the colony start rule; the plant rules; the biome and world rules; the ambiguous kind and terrain counts (XML). The lake and river citations in `decompiled-reference.md` were corrected to the lines that hold their values (`TileMutatorWorker_Lake.cs:29, 45, 52-53, 65, 93-101` and `TileMutatorWorker_River.cs:171-173`).
* **Tests changed (no seed changed from its HEAD value).** `MapGenTest.rockTypesOreAndVeinsStayInTheirRanges` generates 250 by 250 maps, with each map's own vein plan known from its planet's hilliness. Its ore floor was a share of 3%, then 1%, which the reference density (2.6%) and the old 18 veins had set. It is now a placement check: the ore placed must be at least 90% of the planned cells (99.6% measured over 72 maps). The 15% upper bound on the share stays. Four seed-dependent tests keep their HEAD seeds and assertions, and each setup no longer depends on the layout near home: `ContentTest.dormantMechsWakeNearColonists` (seed 54: no work, and the colonist is drafted at the spot, so only the distance matters); `RulesTest.outfitPolicyKeepsArmorOffWorkers` (seed 62: no work, a colonist who can wear apparel, and its job ends when the policy changes); `JobAiTest.anInterruptedHaulDropsItsLoadAndReleasesItsReservations` (seed 209: the eat window is one interrupt cycle, `tk(10)`, not 10 steps); `LifeTest.couplesConceiveAndBabiesAreBornAndFed` (seed 31: every colonist is fed and idle, and the old milk is replaced by fresh milk). `SettleTest.caravanFoundsNewColonyAndOldOneIsArchived` (seed 22) turns off random caravan events with the new `Game.randomCaravanEvents`, which defaults to on and is not saved. The probe counts before and after are in map.md, Tests.
* **Settling founds a colony at the new size.** `Game.settle` in `Caravans.kt` copied the old map's width and height, so a colony founded from a 100 by 100 save was 100 by 100. It now uses `Game.MAP_SIZE`. The archived old colony keeps the size it stores. Test: `ColonyMapSizeTest.aColonyFoundedFromAHundredByHundredSaveIsTwoFiftyByTwoFifty`.
* **Test helpers at 250.** `GameTest.newGame`, `JobAiTest.newGame`, `CombatModelTest.arena`, `CombatPipelineTest.arena` and `SkillsTraitsTest.arena` build `Game.MAP_SIZE` colonies, as do `GameTest`'s `WorldTest` map and the rock-type loop of `MapGenTest`. Explicit-size generation tests (river, lake, determinism) and the benchmarks still pass 100, since they test the generator at a given size.
* **Tests added:** `ColonyMapSizeTest` (`everyLegacySaveKeepsTheSideItStores`, `aNewColonyRoundTripsAt250`, `aVersion36SaveLoadsAndANewSaveIsVersion37`, `aColonyFoundedFromAHundredByHundredSaveIsTwoFiftyByTwoFifty`); `MapGenTest` (`oreBlotchesFollowTheAreaAndHilliness`, `oreVeinPlanAddsUpToTheBlotchCount`, `battleMapsKeepTheFixedVeins`); two opt-in benchmarks in `PerfBenchTest` (`benchOneDayHundredByHundred`, `benchOneDayTwoFiftyByTwoFifty`).
* **Benchmark (one in-game day, 60,000 steps, five colonists, same seed and scenario; `COLONY_BENCH=1`; three runs in this sandbox, from the previous round):** 100 by 100 takes 0.048 to 0.064 ms per step, and 250 by 250 takes 0.098 to 0.102 ms per step. That is about twice the time for 6.25 times the area. Map generation takes 161 to 193 ms and 88 to 113 ms. The state hash is the same across runs. **The phone budget is unmeasured, because no phone is available here.** At 1x the limit is 16.7 ms per step.
* **Tests:** `./gradlew :sim:test --console=plain`: 722 tests in 72 suites, 0 failures, 0 errors, 0 skipped (final run, 12 min 33 s, after the last change to the code and the docs; the settle fix and its test were added in this pass).
* **App, not built:** `app/src/main/java/io/github/teamomuito/colony/Dialogs.kt` (the new-colony size row) now offers the single 250 option. `:app` was not built, as the run's rules say.
* **Not committed:** the changes are on the working tree of `ccr-41984ede-stlmp4`. `docs/fidelity/map.md` and `sim/src/test/resources/legacy/colony_v36.sav` are untracked.
* **Open:** the owner confirms `Hills.MOUNTAIN` as impassable (16) or mountainous (15) for ore, and decides the rock threshold for it (0.5 here, 0.636 in the reference); decides whether any other size is offered (the reference lists 200 to 325); traces the origin of the 250 fixtures; confirms that the version 37 bump is wanted; decides whether battle ore should follow the map-size rule; decides whether the explicit 100 by 100 generation tests should move to 250; and confirms the ore density on 250 by 250 maps (25 to 100 blotches) as the intended balance. The other 3.2 rows are not adopted, for the reasons in map.md, and each needs its own change. The river and lake models are the largest of them.

## 18. Grass terrain (3.2, 3.9), round 18

Made on branch `ccr-41984ede-stlmp4`. The plan, every constant with its source, the rows not adopted and the open items are in [grass.md](grass.md). Nothing is committed.

* **Added:** `Terrain.GRASS` (grass ground, fertility 1.0, passable, cost 1) and `PlantType.GRASS` (the grass cover: 3.5 grow days, 4 hay at full growth, 90 harvest work, regrows). Both are appended at the end of their enums. Grass ground is soil on maps outside the desert and arid biomes, where a patch noise is above 0.55; a grass cover sits on each grass cell where a cover noise is above 0.35. The noises are their own, so the generator draws nothing new from the RNG, and the trees, berries, mirewort and brambles are placed on the same cells as before.
* **Growth and harvest use the shared plant rules (D):** growth per day at one over the grow days (`RimWorld/Plant.cs:346`), the fertility factor (`PlantUtility.cs:358`), the temperature and light ramps (`PlantUtility.cs:361-381`), the default 6 to 42 C band (`PlantProperties.cs:89, 91`). A grass cover is harvestable above 65 percent growth (`Plant.cs:115`), yields `count x (0.5 + 0.5 x share)` (`Plant.cs:934-947`), and after a harvest regrows from 0.05 (Stillmere's value; the reference mechanism is `Plant.cs:661`, and its value is XML).
* **Not adopted:** the reference's fertility noise, elevation and terrain set (XML), the wild-plant density rules, spawn growth, lifespan and death from light (each with a reason in grass.md).
* **Save format: `VERSION` 38 to 39, as a marker.** No field changed. The version-38 fixture `legacy/colony_v38.sav` was written by the code before the change; `GrassTest` checks that it loads with no grass and is rewritten as 39. A save with grass round-trips cell for cell. Old builds refuse a version-39 save with the usual "Unsupported save version" error. The owner may reverse the bump.
* **Tests:** `GrassTest` (16, new); `ColonyMapSizeTest` (the literal 38 became 39; `colony_v38.sav` added to the table) and `RoofsTest` (the same literal). 766 tests in total (750 before, one of them failing on the fixture table, now fixed). The full run takes about 12 minutes, so it was run in three parts, each passing.
* **Behaviour changes to check:** a grass cover burns (default flammability 0.5, UNVERIFIED), counts as a plant for garden-sense mood and room beauty, does not block a growing zone (the crop is sown over it), and does not block a grow tray's placement (other plants still do). These are C or UNVERIFIED and are open for the owner.
* **App, not built:** `GameView.kt`, `Sprites.kt` and `Overview.kt` gained `GRASS` branches in their `Terrain` `when` expressions, which have no `else` and would not compile otherwise. The `:app` module was not built, as the run's rules say.
* **Open:** the owner confirms the version 39 marker, the grass numbers that are not in the source (fertility, grow days, yield, work, and the patch and cover shares), whether grass should yield hay at all, and whether the garden-sense and room-beauty effects should count grass.

## 19. Generated caves (3.2), round 19

Made on branch `ccr-41984ede-stlmp4`. The plan, every constant with its source, the rows not adopted and the open items are in [caves.md](caves.md). Nothing is committed.

* **Added:** caves on colony maps, carved when a map is made. A hill tile's map has caves with chance 0.5 on mountain tiles and 0.25 on large hills (`WorldMap.kt`; the base game's chances are `RimWorld.Planet/WorldGenStep_Mutators.cs` lines 12 and 14). The chance is rolled once per world tile, from a generator of its own. The carver (`Caves.kt`) cuts tunnels from rock masses of 300 cells or more after their edges are trimmed. Open tunnels start at the rock's edge and run out of it (the entrances), and closed tunnels stay inside it. The counts, widths, steps and branching are the base game's values, each cited to its file and line in `caves.md`. The heading noise is Stillmere's 2-D value noise (C), so the shape of a tunnel is not the base game's shape. Carved cells become gravel with their natural roof, the rock that stays is the cave's wall, and no ore is placed in a cave.
* **Few colonies have caves.** Only a large-hill home can have them, at a quarter of that chance. Stillmere never starts a colony on a mountain tile, so the mountain chance applies to no colony yet. In 300 seeds, 12 temperate homes (4.0 percent) have caves. This is the rule as the base game gives it, with Stillmere's home choice. It is open for the owner (open item 1).
* **Not adopted, with reasons (summary; each row is in `caves.md`):** the cave floor's water and gravel patches (they need a Perlin threshold that Stillmere's noise does not give, and the default floor is already gravel); the thin and thick roof split and the roof-group rule (the roof area); the elevation rock threshold (not a caves rule, and it changes every map); cave hives (insect content); the Anomaly, Odyssey and Ideology cave content (pocket-map exits, cavern, lake, lava, ice and insect-lair caves, cave debris, ancient turrets, undercave interest), excluded per `CLAUDE.md`.
* **Save format: unchanged, `VERSION` stays 39.** No saved field and no enum constant was added. The cave floor is the existing `GRAVEL`, and caves are plain terrain with the roof flag, which the save already holds. The world's caves flag is not saved, because the world is rebuilt from its seed on load. No fixture was needed, since nothing in the format changed.
* **Maps without caves are unchanged, checked.** A digest of colony maps (planet and temperate homes, seeds 1 to 300), battle maps and worlds (seeds 1 to 40) was taken from this tree and from commit `249d420`. Only the colony maps of the cave seeds changed: 15 planet homes and 12 temperate homes. The probe was removed.
* **Tests:** `CavesTest` (10 tests, new). No existing test asserted an old value, so none was changed. The full sim suite passes after the citation review. A single foreground `./gradlew :sim:test --console=plain` does not finish within 600 seconds (about 12 minutes), so it was run as four foreground calls, each with `--tests` filters that together name all 80 top-level test classes. The calls gave 269, 272, 134 and 101 tests, all passing: 776 tests in 76 test classes, with 0 failures, 0 errors and 0 skipped.
* **Measured:** a 90 by 90 rock mass keeps 80 to 93 percent of its rock as wall. A 250 by 250 cave map has 610 to 1,943 carved cells, of which 19 to 91 touch open ground. Map generation takes 41 to 136 ms in the test JVM. No phone was measured.
* **README:** the caves claim is back in the World section ("Caves in some hill country"), because caves now exist. Section 4 above is updated to match.
* **Open:** the owner confirms the mountain mapping (0.5, UNVERIFIED, since no colony uses it) and decides whether colonies may start on mountain tiles, which would raise the share with caves and changes the home choice and the world. The owner decides whether caves get water patches, and with which noise. The value-noise heading is a model difference (C). The base game's mutator placement rules are not adopted: the tile-validity check is at `RimWorld/TileMutatorDef.cs:172` and tests the def's own ranges and categories, whose values for the Caves definition are in XML and are UNVERIFIED, and the Caves definition's non-landmark chance is XML too (UNVERIFIED). The owner decides whether battle maps get caves.

## 20. Generated ruins (3.2), round 20

Made on branch `ccr-41984ede-stlmp4`. The plan, every constant with its source, the rows not adopted, the test notes and the open items are in [ruins.md](ruins.md). Nothing is committed.

* **Review findings fixed (three, all in `GameTest.kt`; each failed in the working tree with the ruins on the map):**
  * `MultiTileTest.footprintsBlockAndRotate` expected two buildings. Ruin walls are buildings, so the test now counts from a baseline taken before it places anything (`before + 2`, before and after the save round-trip).
  * `MaterialTest.insufficientMaterialWaitsWithoutSpendingAnything` expected no hardalloy on the map. Ruin loot holds 11 on that seed, so the test removes the map's hardalloy before it counts.
  * `AdvancedTest.mentalBreaksHappenToUnhappyColonists` expected a break for seed 6 within 40,000 ticks. A break is one roll every 250 ticks at about 0.8 percent (`BreakRules.chance`, extreme level), so one colonist in that window breaks about 7 times in 10. The ruins change the random sequence and so change the outcome of that one roll. The mood path for seed 6 was the same with and without ruins. Over seeds 6 to 17 with the same setup, 7 of 12 colonists broke with ruins and 9 of 12 without, which is the spread the chance predicts. The test now asserts that at least five of the twelve break within 40,000 ticks. A broken break mechanism gives zero, so the assertion still tests the mechanism. This changes an assertion, so it is open for the owner to confirm (below).
* **Citation audit.** Every D citation in `ruins.md` was opened at its line in the clone (`/home/user/chillu1/rimworlddecompiled`). Rows whose cited line did not hold the claim were changed to C or not adopted, with the reason. The main changes: the wall outline is C (vanilla places walls from a generated shape, not a rectangle), and the rubble chance is C (0.5 is vanilla's filth scatter chance around a tank trap, not per destroyed wall). The floor and buildables rows no longer cite `SketchResolver_FloorFill.cs:63`, which is in the open-shape path. The list is in `ruins.md`, "Round 20 changes". No row was added to `decompiled-reference.md`.
* **Code.** The only code change is a comment: `Ruins.RUBBLE_CHANCE` is now described as C. The generator and its numbers are unchanged, so the measurements in `ruins.md` still hold. The save format is unchanged and `VERSION` stays 39.
* **Tests.** `./gradlew :sim:test --console=plain` does not finish within the 600-second limit, so it ran as six foreground calls, with `--tests` filters that together name every test class. The calls ran 42, 58, 45, 171, 184 and 288 tests. The totals add to 788, the number of `@Test` methods. All passed, with 0 failures and 0 errors. The 58-test call took 578 seconds, close to the limit, so its heavy classes are in that call alone.
* **Open:** the owner confirms the mental break test's change from one seed to twelve (a test change, not a ruin change). The count per 10,000 cells (0.5 to 1.0), the loot table and the rubble choice remain C and UNVERIFIED, and the owner decides the rubble pile count (the open items in `ruins.md`).

## 21. Selectable map sizes (3.2), round 21

Made on branch `ccr-41984ede-stlmp4`. The plan, the constants and their sources, the tests and the open items are in [map.md](map.md), "Decisions" items 1 and 8. Nothing is committed.

* **Adopted (D, `RimWorld/Dialog_AdvancedGameConfig.cs:12`):** the new-colony wizard offers 200, 225, 250, 275, 300 and 325, held in `Game.SELECTABLE_MAP_SIZES` (`sim/.../Game.kt`). This closes round 17's open item on which sizes are offered. 350 and 400 are the reference's test sizes and are not offered. The default is `Game.MAP_SIZE`, 250 (C, owner's decision).
* **App, not built:** the size row of the new-colony wizard (`app/.../Dialogs.kt`, `renderSize`) now makes one button per offered side, labelled with the side (for example "200 × 200") and "(standard)" on 250, which is selected by default. Choosing a side makes a new map at that side with the same seed and biome, and resets the crew, as choosing a biome does. `:app` was not compiled: there is no Android SDK here, so CI must confirm the build.
* **Sim:** `Game.SELECTABLE_MAP_SIZES` is new. `Game.startNewColony` takes its size from the map it starts on, and its doc comment says so. The generator, the ore count and the save already take any width and height, so no generation code changed.
* **Save format: unchanged, `VERSION` stays 40.** The sizes are a game value and a UI option. A save already stores width and height on every version, so no field and no reader changed.
* **Tests (new, in `ColonyMapSizeTest`):** `theOfferedSizesAreTheSixReferenceSizesAndTheDefaultIsOneOfThem`, and `aColonyOfEachOfferedSizeStartsStepsAndRoundTrips`. The second runs for each of the six sides: a colony starts inside its map, runs two in-game hours of steps, and round-trips through a save at its own size, with the same bytes written again.
* **Docs corrected (stale before this round):** `map.md` said `VERSION` was 37 and listed 19 fixtures. The code has `VERSION` 40 and 22 fixtures (10 at 100 by 100, 12 at 250 by 250). The map-size rows of this file and of `decompiled-reference.md` said that only 250 was offered; they now say the list is offered. The `Game.kt` line cited in `decompiled-reference.md` row 614 is updated.
* **Tests run:** `./gradlew :sim:test --console=plain`, in the background with a log file, read from the JUnit XML: 828 tests in 79 suites, 0 failures, 0 errors, 0 skipped (14 min 31 s). `ColonyMapSizeTest` passes all 9 tests.
* **Not measured:** the phone cost of the larger sides. A 325 by 325 map has 1.7 times the cells of a 250 by 250 map. `PerfBenchTest` measures only 100 and 250, and no phone is available here (`map.md`, open item 8).
* **Open:** the owner confirms the six sizes and the 250 default. The version 37 marker (`map.md`, open item 5) is still open. Battle maps keep their 44 by 44 size and fixed veins, as before.

## 22. Re-check of UNVERIFIED values (round 22)

Made on branch `ccr-41984ede-stlmp4`. Nothing is committed. Scope: every value tagged UNVERIFIED in `docs/fidelity/*.md`, except `names.md`: its UNVERIFIED marks say whether a label is RimWorld wording, which is not a game value, so they are not re-checked here. Method: each value was searched in the decompiled tree (`/home/user/chillu1/rimworlddecompiled`, C# only, no XML defs). Web searches were run for values the tree cannot give, and only the search snippets were read (the wiki host does not resolve). A value is tagged D only where the tree gives it for the same mechanic, and V only with a URL.

**Counts, UNVERIFIED lines before and after the round:** health 72 to 57; temperature-rooms 17 to 9; roofs 12 to 11; ruins 8 to 7; caves 8 to 6; skills-traits 16 to 13; decompiled-reference 7 to 2; map 3 to 2. Unchanged: combat 14, grass 9, needs-mood 15, threats 12, heat-exchange 1, economy 15 (its battery row stays UNVERIFIED). A row that is still UNVERIFIED is one whose value is XML data, or whose tree evidence conflicts; the settling source is listed below.

**Sim values changed (three), all with tests updated:**

* `HealthRules.HEAL_TEND_BASE` 0.55 to 0.5. D `Verse/Pawn_HealthTracker.cs:1158-1159` (tended heal is 8 x (0.5 + quality) a day). `HealthFidelityTest` now asserts 8 x 0.5 and 8 x 1.5.
* `HealthRules.bloodRecoveryPerDay`: 0.36 when fed and 0.10 when unfed (C) become 1/3 a day for both, and the food gate is removed. D `Verse/HediffGiver_Bleeding.cs:14` (0.00033333333 per 60 ticks, with no food gate). `Health.kt` no longer passes food, and `HealthFidelityTest` asserts 1/3 with no food argument.
* `Climate.COLDEST_HOUR` 3 to 4.32. D `Verse/GenTemperature.cs:194-202` (the cosine's minimum is at day fraction 0.18, and `RimWorld/GenDate.cs:145-147` gives longitude 0 no offset). Coldest hour 04:19, warmest 16:19. The amplitude 7 (`Verse/TemperatureTuning.cs:20`) already matched. Tests updated for the new hour: `TemperatureRoomsTest` (coldest 4, warmest 16 on whole hours; swing measured at 4.32 and 16.32), and the version 39 fixture expectation `colony_v39_expect.txt`, outdoor 8.111009 to 6.3693705 at tick 16875. That change equals the diurnal difference to 1e-6, so only the expectation moved; the save file is unchanged.
* Battery capacity is not changed: it stays 1000 Wd (`BATTERY_CAPACITY_WD`, D `RimWorld/CompProperties_Battery.cs:5`, the code default). A wiki search snippet gives 600 Wd (V, https://rimworldwiki.com/wiki/Battery), but a snippet does not override a D value, and the battery XML is not in the tree, so the row stays UNVERIFIED. `EconomyFidelityTest` asserts 1000.

Save format: unchanged, `VERSION` stays 40. These are game values, not a save layout.

**Tagged D this round (value in the tree, sim unchanged, or already matching):** mental break mean days 0.5, 0.8, 4 and the 4/7 and 1/7 threshold fractions (`Verse.AI/MentalBreaker.cs:24-28, 34, 40-44`); the breathing and filtration consciousness weights 0.2 and 0.1 (`RimWorld/PawnCapacityWorker_Consciousness.cs:18-20`); tend quality constants (`RimWorld/TendUtility.cs:10-12`, no medicine 0.3 and cap 0.7, which the sim already uses); the injury pain and bleed formulas and the 90000-tick bleed stop (`Verse/Hediff_Injury.cs:137, 158-188`); the infection curves and delay (`Verse/HediffComp_Infecter.cs`, `Verse/HealthTuning.cs:74`); the permanent-injury chance (`Verse/HediffComp_GetsPermanent.cs:63-77`); the roof work 65 and rate 1.7 (`RimWorld/JobDriver_AffectRoof.cs:43, 47`); roof flags (`Verse/RoofDef.cs`); the cave water and gravel thresholds (`RimWorld/TileMutatorWorker_Caves.cs:15-21`); the hilliness bands (`RimWorld.Planet/WorldGenStep_Terrain.cs:221, 239, 257`); the settlement refusal of impassable tiles (`RimWorld.Planet/TileFinder.cs:82-85`); the curve evaluation (`Verse/SimpleCurve.cs:139-170`); the wall, roof, door and vent rates and intervals (`Verse/RoomTempTracker.cs:22, 30`, `Verse/TemperatureTuning.cs:28-36`), which the sim's `Climate.kt` already matches; the outdoor-room rule (`Verse/Room.cs:99-105`); the impressiveness inputs (`RimWorld/RoomStatWorker_Impressiveness.cs:8-23`); the heater and cooler formulas (`RimWorld/Building_Heater.cs`, `RimWorld/Building_Cooler.cs`); the skill XP calls (`RimWorld/SkillRecord.cs:37, 386`, `RimWorld/Toils_Tend.cs:69-70`); the need base 0.5 (`RimWorld/NeedDef.cs:45`); the ship part list (`RimWorld/ShipUtility.cs:18-27`); the fermenting barrel constants (`RimWorld/Building_FermentingBarrel.cs:17, 19`); the shot hit factor (`Verse/ShotReport.cs:160-172`); the cover fill (`Verse/CoverUtility.cs:104-108`); and the count rounding (`Verse/GenStep_Scatterer.cs:266-274`).

**V tags added (search snippets, URL inline; none is a Stillmere value that a snippet clearly contradicts; the battery's 600 Wd conflicts with a D value and is kept at 1000, see above):**

* Battery capacity 600 Wd per battery (https://rimworldwiki.com/wiki/Battery). Not adopted; see above.
* Herbal medicine potency 0.6 and maximum tend quality 0.7, which the sim already uses (https://rimworldwiki.com/wiki/Herbal_medicine).
* Mental break base stat 35%, giving thresholds 35%, 20% and 5% (https://rimworldwiki.com/wiki/Mental_break). The break frequencies on other mirrors conflict with the tree, and the tree's values are kept.
* Solar generator 1,700 W at full light (https://rimworldwiki.com/wiki/Solar_generator). The sim matches.
* Battery charging at 50% efficiency and 5 Wd a day self-discharge (https://rimworldwiki.com/wiki/Battery). The sim's self-discharge matches; the charge efficiency is not modelled.
* `Mathf.RoundToInt` sends an exact half to the even integer, per the legacy Unity pages (https://docs.unity3d.com/450/Documentation/ScriptReference/Mathf.RoundToInt.html, 4.5 to 2017.4). The current page was not read. The sim's `kotlin.math.round` is half to even, so `map.md` and `ruins.md` now say V.
* Heater 175 W and 21 heat per second (https://rimworldwiki.com/wiki/Heater). The sim has 200 W and 35.62 (the round recorded in `heat-exchange.md`, "Heater output"). Not changed: the heat figure is a Stillmere tuning, and the snippet does not give units that map onto it. Owner decision.
* Medicine XP factor: a wiki XP table reads 0.5 for herbal and 0.7 for industrial (V, a search snippet only, not a read page: https://rimworldwiki.com/wiki/Medicine). The sim used 0.5 for every medicine. Changed in round 23 (section 23): the factor is now potency x 0.7 clamped to 0.5 to 1 (D, Verse/ThingDef.cs:712), which gives the same two values.
* Grass base growth 2.5 days, with 4.62 days in parentheses (https://rimworldwiki.com/wiki/Grass). Neither reading is 3.5, the sim's value, so the row stays UNVERIFIED.
* Biome extremes 35 C to -25 C (https://rimworldwiki.com/wiki/Biome) conflict with the temperate forest minimum of -10 C (https://rimworldwiki.com/wiki/Temperate_forest). The row stays UNVERIFIED.
* Surgery success by skill: 10% at 0, 80% at 8, 100% at 15, 110% at 20 (https://rimworldwiki.com/wiki/Medical_Surgery_Success_Chance). A mirror of the same page gives 20% at 0 and 99% at 20. The sim's 0.5 + 0.03 x skill is kept, UNVERIFIED.
* Rusted bed heals +5 a day, and a sleeping spot +4 (https://rimworldwiki.com/wiki/Rusted_bed). A forum reply quoting the bed page says +8 for a regular bed (https://steamcommunity.com/app/294100/discussions/0/2969524118002585015, a user summary, weak). Both support an added heal, not the sim's x1.35. Not changed.

**Found different from the sim and not changed (owner decision):**

* Untended healing: vanilla heals 8 a day for the pawn, one random injury per 600 ticks, plus 4 when not standing. The sim gives 3.2 per wound. Matching it would be a 2.5x balance change, and the structures differ.
* Bed rest: vanilla adds +4 and the bed's XML heal; the sim multiplies by 1.35.
* Starvation: vanilla stops all healing at food 0 (`RimWorld/Need_Food.cs:19, 35`). The sim scales by 0.3 below 0.05.
* Consciousness pain: vanilla subtracts up to 0.4 for pain from 0.1 to 1.0 (`RimWorld/PawnCapacityWorker_Consciousness.cs:12`). The sim multiplies by 1 - 0.55 x pain.
* Bleeding: vanilla tended wounds do not bleed, and wounds stop bleeding at age 90000 ticks or more. The sim clots and leaves a 0.06 trickle when tended.
* Infection: vanilla rolls once when the wound is added and after 15000 to 45000 ticks; its curves differ from the sim's start rule.
* Scars: vanilla's chance is 0.02 times factors times a severity curve, and a permanent wound keeps its pain and a floor. The sim uses 18% for sharp wounds with no pain.
* Impressiveness and the seasonal curve: the weights and the latitude amplitude differ in form.
* Inner-part damage: `ChanceToAdditionallyDamageInnerSolidPart` 0.2 is declared, but its use is not in the tree, so the sim's 0.55 and 35% gate are not checked against vanilla.

**What would settle the rows still UNVERIFIED (XML data, not in the tree):**

* Body part coverage, hit points and child parts (health 45 to 62, 72 to 76): `Core/Defs/BodyDefs` (Body_Human).
* Injury and disease values: `painPerSeverity`, `bleedRate`, `infectionChance`, per-stage `naturalHealingFactor`, immunity: `Core/Defs/HediffDefs`.
* Power draws, the heater and cooler `energyPerSecond`, the bed's `bed_healPerDay`, the `MedicalTendQualityOffset` and `SurgerySuccessChanceFactor` of beds, and the glow panel's wattage: `Core/Defs/ThingDefs` (buildings), and the full wiki `Property:Power_Consumption` page.
* Stat base values (`ShootingAccuracyPawn`, `MedicalSurgerySuccessChance`, `MedicalTendQuality`, `MentalBreakThreshold` base, `GlobalLearningFactor` per trait, `InjuryHealingFactor`): `Core/Defs/StatDefs` and `TraitDefs`.
* Medicine potency and quality cap other than herbal: the medicine `ThingDefs`.
* Built roof flags, leaving things, filth, and the rock holder flag: `Core/Defs/TerrainDefs` (roofs) and the rock `ThingDefs`.
* Grass and crop values (grow days, fertility sensitivity, minimum light, yield, terrain fertility): `ThingDefs` (plants) and `TerrainDefs`.
* Biome temperatures and variation, including the temperate forest: `BiomeDefs`.
* Cave and ruin gates and counts: `TileMutatorDefs`, `GenStepDefs`, `ThingSetMakerDefs`.
* Raid cycles, the points curve, threat scales and floor ceiling per difficulty: `StorytellerDefs`, `DifficultyDefs`, `IncidentDefs`.
* Need base overrides, drug tolerance, addiction and chemical fall per drug: `NeedDefs` and drug `ThingDefs`.
* Room stat thresholds and beauty and cleanliness per building: `RoomStatDefs` and building `ThingDefs`.
* Research project costs: `ResearchProjectDefs`.
* Surgery outcome chances and failure damage: `RecipeDefs` and `SurgeryOutcomeEffectDefs`.
* The rounding tie rule for the current engine: the current Unity Scripting API page for `Mathf.RoundToInt` (only the legacy pages were read).

**Tests:** `./gradlew :sim:test --console=plain`, run in the background with the log at `scratchpad/sim-test.log` and read from the JUnit XML. 828 tests in 79 suites, 0 failures, 0 errors, 0 skipped (`./gradlew :sim:test --console=plain --rerun-tasks`, 11 min 23 s, after the re-check fixes listed above). Earlier in the round, two tests failed on the first run, both from the coldest-hour change, and were updated as described above.

## 23. Review fixes to section 22 (round 23)

Made on branch `ccr-41984ede-stlmp4`. Nothing is committed. A review rejected section 22 on the points below. Each one is fixed in the working tree, and each tag or comment now matches its source. One game value changes: the medicine XP factor.

**Tags and comments (no game value changed):**

* `Climate.DIURNAL_SWING` (7): tagged D, `Verse/TemperatureTuning.cs:20` (the daily amplitude). It was tagged UNVERIFIED (custom), which the cited source does not support.
* `Climate.DIURNAL_SWING_DRY` (10): tagged C, Stillmere's own value. The reference's sun cycle has one amplitude for every biome (`Verse/GenTemperature.cs:194-202`). Its temperature code does have biome terms (a constant outdoor temperature, `Verse/MapTemperature.cs:29-31, 45-47`; the vacuum factor, `Verse/GenTemperature.cs:451`), but neither is a daily swing. It had no tag.
* `GameMap.oreBlotchCount`, tie mode: V, as `map.md` says (the legacy Unity page for `Mathf.RoundToInt`, a search snippet). The comment said UNVERIFIED. The final level rounding row in `skills-traits.md` was UNVERIFIED for the same rule; it is now V with the same URL.
* `NeedRules.beautyLevel`, base 0.5: D as the code default (`RimWorld/NeedDef.cs:45`). The beauty need's XML override stays UNVERIFIED, as `needs-mood.md` says. The beauty thoughts row in `needs-mood.md` said the base was UNVERIFIED; it now says D with the XML override UNVERIFIED.
* `TimeModelTest`, `outdoorTemperatureSwingsThroughTheDay`: the comments said the curve bottoms out at 03:00 and peaks at 15:00. The test now uses 04:19 (tick 10,800) and 16:19 (tick 40,800), the minimum and maximum for `COLDEST_HOUR` 4.32 at 2,500 ticks an hour.

**Medicine XP factor (game value changed):**

* The claim that the factor is not in the decompiled source was false for the factor. `Verse/ThingDef.cs:712` gives it: the medicine's potency x 0.7, clamped to 0.5 to 1. The default without medicine, 0.5, is at `RimWorld/Toils_Tend.cs:69`. Corrected in: `skills-traits.md` (the medicine XP factor row, and the open list); this file's section 3.6 medicine XP row, which is at line 294 here (`skills-traits.md` has 209 lines, so the reviewer's line 294 was in this file); `decompiled-reference.md` (the medicine XP row, now "matches", medium, because the potencies are secondary); `health.md` (a note under the medicine table); and section 22's medicine line, marked as changed.
* Sim: `SkillRules.medicineXpFactor(potency)` and `SkillRules.tendXp(animal, potency)`. `Health.kt` passes the medicine's potency, or 0 with no medicine. Effects: sterile dressing (potency 1.0) gives 0.7, so 350 XP to a human (was 250) and 122.5 to an animal (was 87.5). Leaf poultice (potency 0.6, giving 0.42) is raised to the floor of 0.5, unchanged. No medicine gives 0.5, unchanged. The potencies are the sim's existing values (`health.md`, section 4, secondary sources).
* Tests in `SkillsTraitsTest`: the factor at potencies 0, 0.6, 1 and 2 (floor and cap); the XP for a human and an animal with and without medicine; and a tend through `tendPawn` that teaches 350 XP with sterile dressing and 250 with none.

**Found, not changed:**

* Vanilla adds a random daily offset to the outdoor temperature: a per-tile Perlin value scaled by 7 (`RimWorld.Planet/TileTemperaturesComp.cs:53-56`, used at :50 and :87, which feed the map's outdoor temperature). The sim has no such term. `decompiled-reference.md` already lists it as a missing row ("Random daily term"); `temperature-rooms.md` now marks it not modelled as well. The amplitude 7 in section 22 is the sun cycle alone.

**Save format:** unchanged, `VERSION` stays 40. These are game values, comments and tests, not a save layout.

**App:** no file under `app/` changed, and nothing under `app/` refers to the changed functions, so this round does not touch the Android build.

**Tests:** `./gradlew :sim:test --console=plain`, run in the background with the log at `scratchpad/sim-test.log` and read from the JUnit XML in `sim/build/test-results/test`. 830 tests in 79 suites, 0 failures, 0 errors, 0 skipped; BUILD SUCCESSFUL in 11 min 28 s. Section 22 reported 828. The change is `SkillsTraitsTest` going from 42 to 44 (one medicine test replaced by three), so 830 is the count the run confirms.

## 24. Review fixes to sections 22 and 23 (round 24)

Made on branch `ccr-41984ede-stlmp4`. Nothing is committed. A review rejected the work on the four points below. Each one is fixed in the working tree. No game value changes, so `VERSION` stays 40 and the save layout is unchanged.

**Source path (tag source mismatch):**

* The medicine XP factor is at `Verse/ThingDef.cs:712`. The reference tree has no `RimWorld/ThingDef.cs`. Fixed in `Skills.kt` (three comments), `SkillsTraitsTest.kt` (one comment), `health.md`, `skills-traits.md` (two places), `decompiled-reference.md` (the medicine XP row), and this file: section 3.6 at line 294 and section 22 at line 930. Section 23 at line 982 already had the right path.

**UNVERIFIED count:**

* Section 22 said `skills-traits.md` kept 16 UNVERIFIED lines. The file has 13 (16 at HEAD). The count line in section 22 now reads "16 to 13", and skills-traits has moved out of the unchanged list. The other counts in that line match the files, checked with `grep -c UNVERIFIED` at HEAD and in the working tree.

**Biome terms in the reference temperature code:**

* The claim that the reference's temperature code has no biome term was wrong. `Verse/MapTemperature.cs:29-31` and `45-47` return the biome's constant outdoor temperature in place of the outdoor and seasonal temperatures, and `Verse/GenTemperature.cs:451` applies a vacuum factor of 0.1 to room heat exchange when the room is colder. Neither is a daily swing. The sun cycle (`Verse/GenTemperature.cs:194-202`, amplitude 7 from `Verse/TemperatureTuning.cs:20`) has one amplitude for every biome, so the +10 for desert and arid stays C, with its reason now stated correctly.
* Corrected in `Climate.kt` (the `DIURNAL_SWING_DRY` comment), `temperature-rooms.md` (the day/night swing row), and the `DIURNAL_SWING_DRY` bullet in section 23 (line 975), which repeated the same claim.
* Stillmere models neither term: its six biomes set no constant outdoor temperature and have no vacuum. No behaviour changes.

**Tests:** `./gradlew :sim:test --console=plain`, run in the background with the log at `scratchpad/sim-test.log` and read from the JUnit XML in `sim/build/test-results/test`. 830 tests in 79 suites, 0 failures, 0 errors, 0 skipped; BUILD SUCCESSFUL in 11 min 12 s. The change is comments and docs only, so the count matches section 23.

**App:** no file under `app/` changed. The Android app cannot be built here, and nothing under `app/` was touched.

## 25. Review fixes to sections 22 to 24 (round 25)

Made on branch `ccr-41984ede-stlmp4`. Nothing is committed. A review rejected the re-check of the UNVERIFIED values on four points. Each one is fixed in the working tree. One game value changes: the desert and arid day/night swing. No saved field changes, so `VERSION` stays 40 (`SaveGame.kt:55`).

**Game value changed:**

* Desert and arid day/night swing: 10 C to 7 C, the same as every other biome. `Climate.DIURNAL_SWING_DRY` (C, Stillmere's own value) is removed. `Climate.diurnal` takes no biome and uses `DIURNAL_SWING` (D, `Verse/TemperatureTuning.cs:20`), the amplitude of the sun cycle at `Verse/GenTemperature.cs:194-202`, which has no biome term. Sections 22 to 24 kept the +10 as C. The same-mechanic rule takes the source's 7 for desert and arid too. The desert and arid day peak and night trough each move 3 C toward the mean, so the day-to-night range falls from 20 C to 14 C.
* `TemperatureRoomsTest`, `outdoorTemperatureFollowsDayAndNight`: desert and arid are checked at 14 C from 04:19 to 16:19 (desert was 20 C).

**Tags and comments (no game value changed):**

* `Caves.WIDTH_CURVE` (`Caves.kt:41`): the code tagged the flat ends C, while `caves.md` tagged them D. The comment now gives D: the three points at `RimWorld/MapGenCavesUtility.cs:55-60`, and the flat-and-straight evaluation at `Verse/SimpleCurve.cs:139-170`. `caves.md` also listed the width curve among its C deviations in the Results section, which contradicted its own table. That item is removed and the curve is listed as D.
* Medicine XP factor, wiki table (V): `skills-traits.md` (the medicine XP factor row) and section 22 above called the source "scraped, read with caution". The V tag allows search snippets only, so both now say "a search snippet only, not a read page". The formula stays D (`Verse/ThingDef.cs:712`, `RimWorld/Toils_Tend.cs:69`).
* Battery charge efficiency: the code default 0.5 (D, `RimWorld/CompProperties_Battery.cs:7`, applied to charge at `RimWorld/CompPowerBattery.cs:85`) is now tagged D, with the same XML caveat as the capacity row. This is recorded in `economy.md` (the bullet and the open-items entry) and in section 3.8 of this file (the table row at line 338 and the open list at line 352). The status stays "not adopted": Stillmere still stores charge losslessly. Taking the 0.5 would halve what every battery stores, which changes the power balance on every map with batteries. That is a gameplay decision for the owner and is not made here.
* `decompiled-reference.md`: the desert and arid swing row changes from "differs" to "matches", and the temperate row now reads "every biome".
* `temperature-rooms.md` and section 3.3 of this file (line 158): the day/night swing reads +/-7 C in every biome, and the tag is D.

**UNVERIFIED counts** (`grep -c UNVERIFIED`, taken at the end of this round): `economy.md` 15, `gap-report.md` 106, `skills-traits.md` 13, `caves.md` 6, `temperature-rooms.md` 9, `decompiled-reference.md` 2. These were not compared before and after each edit, so an earlier 'unchanged' claim is withdrawn. The battery and medicine lines still carry UNVERIFIED on their XML and wiki parts.

**Save format:** unchanged, `VERSION` stays 40. These are game values, comments and docs, not a save layout.

**Tests:** `./gradlew :sim:test --console=plain`, run in the background with the log at the scratchpad (`sim-test.log`) and read from the JUnit XML in `sim/build/test-results/test`. 830 tests in 79 suites, 0 failures, 0 errors, 0 skipped; BUILD SUCCESSFUL in 11 min 14 s.

**App:** no file under `app/` changed. Nothing under `app/src` refers to `DIURNAL_SWING_DRY` or to `Climate.diurnal`, so the Android build is not touched by this round.
