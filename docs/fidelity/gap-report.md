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
| **V** | Reported by a RimWorld Wiki page, as summarised by a web search on 2026-10-09. This is a secondary source: the wiki host does not resolve from this environment, so no page was read directly. Re-check against the pages when direct access exists. |
| **M** | Recalled from memory. **UNVERIFIED.** Do not implement from this without a source. |
| **C** | Custom to Stillmere. No vanilla counterpart is known. |
| **?** | Could not determine. **UNVERIFIED.** |

**Priority** (1 to 3): 1 = affects the core loop, breaks a project rule, or blocks later phases; 2 = important but can wait
for its phase; 3 = long tail or polish.

Wiki pages the **V** figures came from: [Ticks](https://rimworldwiki.com/wiki/Ticks), [Time](https://rimworldwiki.com/wiki/Time),
[Mental break threshold](https://rimworldwiki.com/wiki/Mental_Break_Threshold), [Hunger rate](https://rimworldwiki.com/wiki/Hunger_rate),
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
| R3 | **Platform-dependent floating point.** `Math.sin`/`Math.cos` are allowed to differ by 1 ulp between JVM and Android runtime. They feed map generation (lake position), drop-pod landing, and the outdoor temperature curve. A seed could generate a slightly different map on a phone than in the unit tests. **UNVERIFIED in practice**; `StrictMath` removes the doubt. | `GameMap.kt:485-486`, `Events.kt:139-140`, `Game.kt:171` | 2 |

Everything else checked is clean: no wall-clock reads in the tick loop, one seeded `Rng` (same LCG as `java.util.Random`, state saved
and restored), and the two `shuffled(java.util.Random(...))` calls are seeded from the sim RNG or the world seed.

### 2.2 Names and text copied from RimWorld (decision needed)

`CLAUDE.md` says "never copy RimWorld text strings". The code uses many RimWorld proper names verbatim. I have **not** changed any of
them. Whether the rule covers names or only prose is your call; the list is here so the call can be made once.

* **Scenarios** (`Game.kt:26-31`): "Crashlanded", "The lost tribe", "Rich explorer", "Naked brutality".
* **Difficulty** (`Game.kt:22-24`): "Losing is fun".
* **Creatures** (`Race.kt`): Muffalo, Thrumbo, Boomrat, Boomalope, Megascarab, Spelopede, Megaspider, Scyther, Lancer, Centipede, Warg.
* **Items and research** (`Defs.kt`): Go-juice, Yayo, Flake, Wake-up, Psychite, Smokeleaf, Healroot, Devilstrand, Plasteel, Doomsday rocket launcher, Packaged survival meal.
* **Backstory titles** (`Pawn.kt:322-358`), possible overlap, **UNVERIFIED**: "Vatgrown soldier", "Pit fighter", "Urchin", "Tribal child", "Space-born tinker".
* Already original: the three storytellers (Marlowe, Juniper, Orrin), faction and settlement names, all prose messages.

### 2.3 Other repository findings

| # | Finding | Priority |
|---|---|---|
| X1 | **Release signing key and its password are committed** in plain text (`app/release.jks`, `app/build.gradle.kts:22-28`). The comment says this is deliberate so any build can update an installed copy. Anyone can sign an APK that installs over yours. Needs your decision. | 2 |
| X2 | README download and release links point at `teamomuito/rimworld` (the old repo name), as does a comment in `.github/workflows/android.yml`. GitHub redirects renamed repos, but the links are stale. | 3 |
| X3 | README counts are stale: it says 65 buildings and 53 research projects; the code has 72 buildable buildings (76 defined, 4 legacy) and 57 research projects. | 3 |
| X4 | `docs/fidelity/<system>.md` files, which `CLAUDE.md` requires for every implemented constant, **do not exist yet.** Only this report does. | 1 |

## 3. Systems

### 3.1 Time and determinism

**Status: Matches for the time base, pending the save-format decision (see `docs/fidelity/time.md`). Determinism: Matches, with two rule fixes.** **Priority 1.**
**Files:** `Defs.kt` (calendar and `tk()`), `Game.kt` (`tick`, `step`, `daylight`, `outdoorTemp`, `SLOW_TICK`, `HEALTH_STRIDE`), `Noise.kt` (`Rng`), `SaveStore.kt`, `SaveGame.kt`, `app/MainActivity.kt` (real-time pace).

| Constant | Stillmere now | Vanilla | Conf |
|---|---|---|---|
| Ticks per in-game hour | 2,500 | 2,500 | V |
| Ticks per day | 60,000 | 60,000 | V |
| Days per season / per year | 15 / 60 | 15 / 60 | V |
| Real-time base rate at 1x | 60 ticks per second (app) | 60 | V |
| Real minutes per game day at 1x | about 16.7 | about 16.7 | V (derived) |

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
**Files:** `GameMap.kt` (`generate`, `generateFor`), `Noise.kt`, `Defs.kt` (`Terrain`, `RockType`, `Ore`, `Biome`, `PlantType`), `Game.kt` (`MAP_SIZE`, `findStartSpot`), `Animals.kt` (`populateWildlife`).

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| Colony map size | 100 x 100 (`Game.MAP_SIZE`) | 250 x 250 default; 200 small, 300 large | 0.16x the area | V (third-party guides) |
| Battle map size | 44 x 44 | ? | | ? |
| Biomes | 6 | Core has more (12 recalled, including swamps, ice sheet, extreme desert, sea ice) | 6 missing | M |
| Rock types | 5 (granite, marble, limestone, sandstone, slate) | 5 | none | M |
| Ore kinds | 5 (steel, silver, gold, plasteel, components) | steel, silver, gold, plasteel, jade, uranium, compacted machinery recalled | at least 2 missing | M |
| Ore vein layout | 8 steel veins (11 cells each), 3 silver, 2 gold, 2 plasteel, 3 components (6 or 4 cells) | ? | | C |
| Soil fertility | soil 1.0, rich soil 1.4 | same | none | M |
| Trees | 8 kinds, density by biome | many more kinds | partial | M |
| Terrain types | 10 | more, with several stone floors and water types | partial | M |

**What exists:** value-noise elevation/fertility/wetness, rivers (65% of seeds, or from the world tile), a lake when the world tile
borders water, mountains by an elevation threshold tuned by the world tile's hilliness, ore veins in rock, forests by biome,
berry bushes, wild healroot, brambles, wildlife herds, a flat start area.

**Missing:**
* **Grass** (animals graze on any fertile, unroofed cell), flowers, bushes, most wild plants.
* **Caves** (mountains are solid rock; `JobDrivers.kt:444` states cave-ins are not modelled), **ancient ruins**, steam geysers, map-edge features.
* **Roofs.** Only a natural roof flag exists. There is no roof designation, no building of roofs, and no roof collapse.
* **Ore as vanilla places it:** the deep drill reads the ore under its own cell, but ore only exists inside rock, which cannot be built on. See 3.9 (defect).
* Rock chunks on the surface, mining yields and mine work are custom (`Ore.work` 1000 to 2200, plain rock 1000).

### 3.3 Temperature and rooms

**Status: Partial** (was Divergent). Details and sources: `docs/fidelity/temperature-rooms.md`. **Priority 1.**
**Files:** `Climate.kt` (outdoor temperature, `Thermal.step`), `GameMap.kt` (`rebuildRooms`, `buildHeatLinks`, `tempAt`), `Rooms.kt` (`computeRoomStats`, `RoomRules`), `World.kt` (`roomClimate`), `Game.kt` (`outdoorTemp`), `Mood.kt` (`comfortTick`), `Pawn.kt` (`comfyMin/Max`).

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| Comfortable range | 16 to 26 C, shifted by apparel insulation | 16 to 26 C | none | M |
| Temperature granularity | per room, with heat links to neighbouring rooms and the outdoors | per room (every cell of a room shares one temperature) | **none** (a per-cell grid was not built: it would be less faithful) | V |
| Day/night swing | +/-7 C (+/-10 in desert, arid), coldest 03:00, warmest 15:00 | ? | | UNVERIFIED |
| Heat wave / cold snap offset | +16 / -17 C | ? (recalled near +17 / -20) | | UNVERIFIED |
| Volcanic winter offset | -13 C for 6 days | ? | | UNVERIFIED |
| Heat exchange | `0.048 * sum(edge conductance) / cells` of the gap per slow tick; wall 1.0, door 3.0, rock 0.3 | doors, vents and missing roof speed it up; rates not found | model differs in detail | V for the qualitative part, rates UNVERIFIED |
| Wall material insulation | none (all materials equal) | none recalled | | M |
| Heater / cooler | +14 / -14 heat units, only below / above 21 C | ? | | UNVERIFIED |
| Rooms | flood fill; walls, doors and rock separate; a door splits rooms and belongs to none; indoor if it does not touch the map edge and has at most 700 cells, or a mostly natural-roofed majority | a room that is 75% roofed or less is outdoors | **Divergent** (no roofs) | V |
| Room stats | beauty, cleanliness, wealth, impressiveness, role (9 roles); constants in `RoomRules` | impressiveness from wealth, beauty, space, cleanliness with named stages | structure matches, all numbers custom | V (structure), C (numbers) |

**Done in phase 4:** outdoor temperature moved into a pure `Climate` object (`StrictMath`, fixing R3 for this curve); heat now flows
between neighbouring rooms through doors, walls and rock instead of only to the outdoors; heater and cooler thermostat extracted;
room stats moved out of `Game` into `computeRoomStats` with named constants. 18 tests in `TemperatureRoomsTest`.

**Could not be verified** (no direct wiki access; see the doc): heater and cooler output, heat-exchange rates, the day/night curve,
the impressiveness formula and thresholds, and which buildings and terrain contribute to beauty and cleanliness and by how much.
All are marked UNVERIFIED and are Stillmere's own numbers.

**Missing:** roofs (and so the 75% rule, roof collapse and roofless rooms), vents, doors that are open versus closed, rain and snow on
roofless rooms, overhead mountain rock as a room property beyond the natural-roof flag, sunlight beyond a day/night curve, seasonal
day length, latitude-dependent seasonal swing.

### 3.4 Health and medicine

**Status: Partial.** **Priority 1.**
**Files:** `Health.kt`, `Body.kt` (`Bodies`, `HediffKind`, `Injury`, `Hediff`), `Surgery.kt`, `Mood.kt` (`comfortTick`, drug withdrawal), `Life.kt` (`agingConditions`), `JobDrivers.kt` (`driveTend`).

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| Human body parts | 29 (11 outer, 7 torso organs, 7 head parts, 4 finger/toe groups) | about 70 recalled (individual fingers, toes, ribs, spine, shoulders) | much coarser | M |
| Part HP (torso, head, arm, leg, heart) | 40, 25, 30, 30, 15 | ? | | ? |
| Healing rate | 3.2 severity per day untended, `8 * (0.55 + quality)` tended | ? | | C |
| Bleed rate | `kind.bleed * severity * 1.35e-6` per tick (inner parts x1.7) | ? | | C |
| Medicine potency | herbal 0.6, industrial 1.0 | herbal 0.6, industrial 1.0, glitterworld 1.6 recalled | glitterworld missing | M |
| Tend quality | `(0.18 + 0.032 * skill) * (0.55 + 0.45 * potency) * jitter` | formula uses skill, potency, bed and light | shape differs | M |
| Death rules | vital part destroyed, blood loss at 1.0, infection at 1.0, lethal hediff at 1.0 | blood loss, vital parts, consciousness, lethal severity | broadly similar | M |
| Downed rule | consciousness below 0.3, moving below 0.12, or pain at 0.85 | consciousness below 0.3 | partly | M |

**Implemented and tested:** wounds with bleeding, tending, scars, wound infection, missing parts, capacities (consciousness, moving,
manipulation, sight, hearing, talking, eating, breathing, pumping, filtration), 7 illnesses (flu, plague, malaria, sleeping sickness,
gut worms, muscle parasites, food poisoning), 4 environment hazards plus toxic buildup, 5 chronic conditions, anaesthesia, 2 prosthetics,
5 bionics, drugs' highs and withdrawal.

**Missing or simplified:**
* Chronic conditions: only bad back, arthritis, cataract, hearing loss, dementia. No asthma, frailty, heart disease, cancer, and so on (**UNVERIFIED** vanilla list).
* Surgery: only amputation and implant installation. No organ removal or harvesting, no success chance, no sterile-environment effect on infection beyond a flat 0.3x in hospital beds.
* No glitterworld medicine, no medicine-quality-by-skill table, no rest-in-bed tend bonus beyond a flat multiplier.
* Illness model is one immunity-versus-severity race; vanilla illnesses have staged hediffs with different effects per stage (**UNVERIFIED**).
* No "on fire" status: burning is damage from standing in a fire cell (`fireTick`).

### 3.5 Needs, mood, thoughts, drugs, mental breaks

**Status: Divergent.** **Priority 1.**
**Files:** `Mood.kt` (`moodUpdate`, `breaksTick`, `startBreak`), `Game.kt` (`pawnTick` need drain), `JobDrivers.kt` (`driveEat`, `driveSleep`, `driveJoy`, `applyDrug`), `Pawn.kt` (`Thought`, `addThought`), `Body.kt` (drug hediffs), `Jobs.kt` (`foodScore`, `findFood`, `startJoy`).

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| Hunger rate (adult human) | 0.7 per day of a 0-to-1 bar | **1.6 nutrition per day** | about 0.44x | V |
| Raw plant food nutrition | 0.05 each | 0.05 each | none | V |
| Simple meal nutrition / cost | 0.9, from 10 raw items = 0.5 nutrition | 0.9, from 0.5 nutrition of ingredients | none | V |
| Rest drain | 0.95 per day awake | ? | | ? |
| Joy drain | 0.42 per day | ? | | ? |
| Mental break thresholds | 0.30, 0.20, 0.10 of a 0-1 mood; per-check chance 0.45%, 1.2%, 3% | **0.35, 0.20, 0.05** (minor, major, extreme) | minor threshold 5 points too low, extreme 5 points too high | V |
| Mood baseline | 0.55, smoothed 50/50 with the previous value every 250 ticks | baseline 0.5 recalled; thoughts sum to a level | model differs | M |
| Break kinds | 10 (wander, berserk, food binge, insult, tantrum, fire, catatonic, run wild, drug binge, hide) | more kinds, each tied to a severity band | partial | M |
| Distinct thoughts | about 45 | well over 100 recalled | much smaller | M |
| Needs | food, rest, joy | food, rest, joy, **comfort, beauty, outdoors, indoors**, chemical needs | 4+ missing | M |
| Drugs | beer, smokeleaf joint, flake, yayo, go-juice, wake-up, psychite tea (hidden) | also luciferium, ambrosia, penoxycyline recalled | 3 missing | M |
| Addiction | one roll per use: 1.5% to 45% by drug | per-drug addictiveness with tolerance building up | model differs | M |
| Tolerance | `ALCOHOL_TOLERANCE` hediff is defined but **never used** | per-drug tolerance | **missing** | C |
| Drug policy | per colonist: none, social only, whenever bored | named, shareable policies with per-drug rules | much simpler | M |
| Food policy / outfit | per colonist: 3 and 4 fixed options | named, editable policies | much simpler | M |

**Also true:** mood is one smoothed scalar, so moods do not stack or decay like vanilla thought lists; "Lonely" and "Under fire" are
not vanilla thoughts as far as I know (**UNVERIFIED**) and look invented.

### 3.6 Skills, traits, passions, backstories

**Status: Partial.** **Priority 2.**
**Files:** `Defs.kt` (`SkillType`, `Trait`), `Pawn.kt` (`gainXp`, `workSpeed`, `Backstories`, `Names`), `Game.kt` (`newHuman`, `conflicts`).

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| Skills | 12, cap 20 | 12, cap 20 | none | M |
| Learning rate, no / minor / major passion | 1.0 / 1.4 / 2.0 | **0.35 / 1.0 / 1.5** | no-passion learns about 3x too fast | V |
| Skill decay | none | **XP drains above level 10, faster at higher levels** | **missing** | V |
| XP per level | `2500 + 400 * level` | ? | | ? |
| Work speed from skill | `0.4 + 0.075 * level` linear | per-stat curves | shape differs | M |
| Traits | 42, no degrees, 1 to 3 at random | around 50, with degrees (for example two levels of industriousness) | partial | M |
| Backstories | 33 (11 childhood, 22 adulthood) | hundreds | tiny set | M |
| Backstory effects | skill bonuses, a few disabled work types; no trait grants, no skill penalties | all three | partial | M |

All 42 traits have some effect in code (several only add a mood thought: carnivore, transhumanist, pyromaniac). `Trait.SOCIABLE`,
`CARNIVORE`, `CREATIVE` are not traits I recognise from the base game (**UNVERIFIED**).

### 3.7 Work and jobs

**Status: Partial** (structure **Matches**). **Priority 2.**
**Files:** `Jobs.kt` (`think`, `tryWork`, finders, bills), `JobDrivers.kt`, `Reservations.kt`, `Regions.kt`, `Pathfinder.kt`, `Defs.kt` (`WorkType`).

**Matches:** 18 work types in the vanilla order (firefight, patient, doctor, warden, handle animals, cook, hunt, construct, grow, mine,
plant cut, smith, tailor, art, craft, haul, clean, research); priorities 1 to 4 with 0 for off, lowest number first, then list
order; a 24-hour schedule of anything / work / joy / sleep; reservations and unreachable memory; region-based reachability.

**Divergent or missing:**
* Cleaning only targets roofed cells.
* Bills have 3 modes and a minimum skill; no ingredient search radius, no quality or hit-point range, no store-location option.
* Butchering is automatic at a butcher table rather than a bill.
* `nearestCell`, `findFood` and similar scan every item or cell each call; fine at 100 x 100, a risk at 250 x 250.
* Research bench: tier 3 projects need the hi-tech bench; vanilla gates by multi-analyzer and tech-level rules (**UNVERIFIED**).

### 3.8 Construction, power, fire, zones

**Status: Partial.** **Priority 2.**
**Files:** `Buildings.kt`, `Game.kt` (`placeBlueprint`, zones), `JobDrivers.kt` (`driveBuild`), `World.kt` (`powerTick`, `fireTick`, `explode`), `GameMap.kt` (`Zone`).

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| Buildings offered | 72 (76 defined, 4 legacy) | far more | partial | M |
| Wood wall | 5 wood, 160 work | wall costs 5 stuff; work **UNVERIFIED** | | M |
| Solar generator | 1,700 W | 1,700 W recalled | none | M |
| Wood-fired generator | 1,000 W | 1,000 W recalled | none | M |
| Wind turbine | 1,500 W constant x wind 0.15 to 1.35 | ? | | ? |
| Battery capacity | 600 per battery | 1,000 recalled | -40% | M |
| Sun lamp draw | 400 W | 2,900 recalled | far too low | M |
| Electric stove, TV, hydroponics | 350, 200, 70 W | same recalled | none | M |
| Zone priorities | low, normal, preferred, important, critical | same five | **Matches** | M |
| Materials | wood, stone, steel, plasteel for walls; wood or steel for doors | many stuff types by property | 4 only | C |
| Fire | intensity 0 to 1.2 per cell, spreads by flammability, rain 0.01 per step | fire size model | model differs | C |

**Missing:** roofs and roof collapse, stone blocks by rock type, wall and door insulation, doors for power or vents, conduit-free
power transmitters, multiple room-based work-table effects, snow clearing zones, named animal and pen areas (only 3 restriction
areas exist), a hit-point filter on zones.

### 3.9 Plants, food, cooking, spoilage

**Status: Partial.** **Priority 2.**
**Files:** `Defs.kt` (`PlantType`, `ItemType`), `World.kt` (`plantsTick`, `spoilTick`, `deteriorationTick`), `Recipes.kt`, `JobDrivers.kt` (`driveSow`, `driveCut`, `driveEat`), `Jobs.kt` (`findGrow`).

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| Crop grow days (rice, potato, corn, strawberry, cotton, healroot) | 5.8, 5.8, 11.5, 6.5, 5.5, 8 | same recalled | none | M |
| Effective growth speed | `1 / (days * 0.55)` per day at fertility 1 | takes the full grow days | crops grow about 1.8x too fast | C |
| Corn yield / potato yield | 40 / 11 | 40 / 11 recalled | none | M |
| Rice yield | 12 | 6 recalled | 2x | M |
| Crop growth temperature | 6 to 42 C (devilstrand from 10) | 6 to 42 recalled | none | M |
| Frost kill | below -2 C, 15% per slow tick | ? | | ? |
| Spoilage (days): meat, meals, milk | 4, 4, 4 | 4 recalled | none | M |
| Spoilage: raw plants | 40 days (strawberries 10) | ? | | ? |
| Corpse rot | 2.2 days | ? | | ? |
| Rot temperature factor | none below 0 C, 0.2 at 0 C rising to 1.0 at 20 C | ? | | ? |
| Food poisoning | 40% if the food was over 65% rotten, 1% from any meat | depends on cleanliness and cook skill recalled | model differs | M |

**Missing:** grass and wild-plant ecology, plant fertility mapping beyond terrain, ambient light needs per crop, animal feed items
beyond hay and kibble, insect jelly, chocolate, and the like. `Brewery` (a separate bench for beer) is **not in the base game as far as I
know (UNVERIFIED)**; vanilla uses a fermenting barrel fed with wort.

**Defect found:** a deep drill brings up ore from the cell it stands on, but ore only exists in rock and rock cannot be built on, so
a real deep drill always yields stone chunks (`World.kt productionTick`). `BaseContentTest.aPoweredDeepDrillBringsUpItsOre` passes
because it places ore under a drill by hand.

### 3.10 Animals

**Status: Partial.** **Priority 3.**
**Files:** `Race.kt`, `Animals.kt`, `Life.kt` (`animalLifeDaily`), `JobDrivers.kt` (`driveTame`, `driveTrain`, `driveHunt`, `driveSlaughter`, `driveGather`).

* 38 animal species plus 3 mechanoids; vanilla has roughly 70 animals (**UNVERIFIED**).
* **Training is invented.** Vanilla trains specific skills (obedience, release, rescue, haul) in a fixed order. Stillmere teaches up to two generic "tricks" that make an animal hit harder (`MAX_TRICKS = 2`, `Pawn.trained`). Divergent; no DLC involved.
* Taming chance, wildness, herd behaviour, predator behaviour and meat/leather yields are custom numbers and unverifiable here.
* Colony animal cap of 40 in breeding is custom.
* Missing: pens and area restrictions for animals, animal bonding, wildlife migrations, nuzzling, pack animals' effect outside caravans.

### 3.11 Combat

**Status: Divergent.** **Priority 1.**
**Files:** `Ai.kt` (`fire`, `hitChance`, `shotPath`, `hostileAI`, `draftedAI`), `Defs.kt` (`Weapon`, `Apparel`, `DamageKind`), `Health.kt` (`dealDamage`), `World.kt` (`turretsTick`, `explode`, `trapsTick`).

* **Weapon stat blocks are custom.** Each weapon has one damage, one range, one accuracy, a cooldown in ticks, a warmup in ticks, burst, armour penetration and (for shotguns) pellet count. Vanilla uses warmup and cooldown in seconds and accuracy that varies across touch, short, medium and long range (**UNVERIFIED**).
* **Hit chance** is `accuracy * (0.62 + 0.03 * skill) * quality`, scaled down with distance, sight, cover, weather and suppression. Vanilla's formula differs (**UNVERIFIED**).
* **Armour** multiplies damage down and sometimes deflects outright. Vanilla rolls armour against a random value and halves or negates damage (**UNVERIFIED**).
* **Melee:** a single hit roll, no dodge or parry, no melee verb choice.
* **Cover** only counts the best cover on the cells beside the target (`targetCover`), not along the path.
* **Suppression** ("a volley that hits nothing pins down the target") and the "Under fire" thought are Stillmere inventions, not vanilla Core mechanics as far as I know (**UNVERIFIED**).
* **Missing:** burning status on pawns, EMP and stun, fire modes, explosive types, proper turret ammo and power, infestation hives, mechanoid weapon variety, shields.
* 38 weapons defined. Lever-action rifle and psychite tea are hidden by `Catalog.kt` as "not in the base game" or "not confirmed"; **I believe both are in Core (UNVERIFIED), so hiding them may be wrong.**
* Tested well for internal behaviour (`CombatPipelineTest`, 20 tests), but against no vanilla numbers.

### 3.12 Social and life stages

**Status: Partial.** **Priority 3.** (A **DLC-sourced** candidate is flagged.)
**Files:** `Social.kt`, `Life.kt`, `Mood.kt`, `Pawn.kt` (`stage`, `opinion`, `spouse`, `lover`).

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| Year | 60 days | 60 days | none | V |
| Baby / child / teen / adult | 0 to 2, 3 to 12, 13 to 17, 18+ | ? | | ? |
| Growth moments | at 7, 10, 13 (passion, skill, trait) | recalled as a Biotech mechanic | **DLC-sourced candidate. UNVERIFIED. Flagged, not removed.** | M |
| Pregnancy | 30 days; 6% chance per day for a partnered woman aged 16 to 45 | ? | | ? |
| Old age | chronic conditions from 40 to 62; death roll from 80 | ? | | ? |
| Social interactions | 8 (chitchat, deep talk, insult, kind words, joke, argument, compliment, romance) | more, including slights and marriage proposals | partial | M |

**Missing:** relation types beyond parent, spouse, lover and opinion (siblings, cousins, ex-partners, bonded animals), weddings and
parties, social fights beyond a bruise exchange, babies' separate needs. Orientation traits gate romance (`ContentTest.orientationsGateRomance`).

### 3.13 Threats and storytellers

**Status: Divergent.** **Priority 1.**
**Files:** `Incidents.kt` (`IncidentRegistry`, `threatPoints`), `Events.kt` (`launchRaid` and all event effects), `Game.kt` (`Storyteller`, `Difficulty`, `init` timers), `Factions.kt` (`pickRaiders`).

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| Raid points | `(17 * colonists + wealth / 95 + 6 * animal danger) * difficulty * storyteller * min(1, 0.34 + day / 34)`, floor `22 * difficulty` | **(wealth points + pawn points) x difficulty x starting factor x adaption**; buildings count at half; wealth capped at 1,000,000; points capped at 10,000 | different shape, no caps, no half-weight for buildings | V |
| Difficulty threat scale | 0 / 0.6 / 0.9 / 1.5 / 2.2 | ? | | ? |
| First raid | day 8 to 12 | ? | | ? |
| Raid kinds | assault, sappers, siege (mortar camp), drop pods, mechanoid cluster | straight assault, sappers, breach, siege, drop pods, infestation, mech cluster | partial | M |
| Incident channels | 6 timers (raid, wanderer, pod, trader, temperature, misc) with 24 incident types | storyteller-specific incident cycles | model differs | M |
| Storytellers | 3, original characters | 3 in Core | structure similar, behaviour not | M |

**Missing:** wealth and pawn point curves with caps, per-storyteller cycles, raid strategies beyond the five kinds, friendly raids
(allied reinforcements exist only through comms), wanderer and refugee rules, the incident list beyond what is registered. "Aurora"
is registered and I cannot confirm it is a Core event (**UNVERIFIED**).

### 3.14 Research

**Status: Divergent.** **Priority 2.**
**Files:** `Defs.kt` (`Research`), `Game.kt` (`researchAvailable`, `startResearch`), `JobDrivers.kt` (`driveResearch`), `Jobs.kt` (`findResearch`).

* 57 projects (README says 53).
* **Cost model is invented:** `cost = baseCost * 16`, progress per tick = `workSpeed * 0.25 * bench multiplier (1.0 or 1.6) * trait`. Vanilla assigns each project a fixed point cost and a research-speed stat; I cannot compare without that data (**UNVERIFIED**).
* Several project names are not vanilla Core as far as I know (for example "Lavish meals", "Plasteel blades") (**UNVERIFIED**); the tree shape is original.
* Missing: tech levels, techprints, prerequisite rules beyond the listed dependencies, research benches' effect on speed beyond the 1.0/1.6 split.

### 3.15 World map, factions, caravans, quests

**Status: Partial.** **Priority 2.**
**Files:** `WorldMap.kt`, `Factions.kt`, `Caravans.kt`, `Battles.kt`, `Encounters.kt`, `Quests.kt`, `Trade.kt`, `Ransom.kt`.

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| World size | 60 x 40 tiles | much larger | much smaller | M |
| Hill levels | flat, small, large, impassable | flat, small, large, mountainous | **Matches** | M |
| Factions | 6: 2 tribal, 2 outlander, 2 pirate | non-hostile and hostile faction types | similar | M |
| Carry capacity: human / muffalo | 35 kg / 70 kg | same recalled | none | M |
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
**Files:** `Game.kt` (`startNewColony`, `startSettlement`, `Scenario`), `SaveGame.kt` (format version 24), `SaveStore.kt`, `SaveSession.kt`, `Tutorial.kt`, `JobRestore.kt`.

* Four scenarios: crashlanded (3 colonists), lost tribe (5), rich explorer (1), naked brutality (1). Start gear, supplies and starting research are custom. Vanilla scenarios are data-driven and customisable (**UNVERIFIED**).
* Colonists are random on start; a crew-selection step exists (`Dialogs.kt:240`).
* Saves: versioned format (24), old versions still load (tested back to version 1), autosave with a recovery copy, named saves, quicksave, archive of settled colonies, damage detection with fallback. This is the best-tested area (`SaveFormatTest`, `SaveRestoreTest`, `SaveSessionTest`, `SaveStoreTest`).
* Win condition: ship (computer core, 2 engines, reactor, cryptosleep casket) then launch. Vanilla's ship needs a different part list (**UNVERIFIED**).
* Determinism caveat for saves: R2.

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
| "Doctors tend wounds with herbal or industrial medicine; hospital beds heal faster" | **True.** |
| "flu, plague, malaria, sleeping sickness, parasites, hypothermia, heatstroke, frostbite, malnutrition and food poisoning" | **True.** |
| "65 buildings" | **Stale.** 72 are offered. |
| "Rooms ... have temperature, beauty, cleanliness and impressiveness values" | **True.** |
| "Stockpile, growing and dumping zones with item filters, quality limits and priorities" | **True.** |
| "Workbench bills for cooking, butchery, tailoring, smithing, machining, stonecutting and medicine" | **Partly false.** Butchery is not a bill; it is an automatic job at a butcher table. |
| "Power grids with solar, wind, wood and battery storage. Fire spreads, rain puts it out, and lightning can start it." | **True.** |
| "Six biomes, rivers, lakes, five rock types" | **True.** |
| "six ores" | **False.** Five ores. |
| "caves" | **False.** No caves are generated. |
| "seasons, rain, fog, snow, thunderstorms, cold snaps and heat waves" | **True.** |
| "Crops that depend on temperature, light and soil; spoilage of food and corpses; items that wear outdoors" | **True.** |
| "Wildlife for hunting, taming, slaughter and breeding" | **True.** |
| "generated planet with hills, mountains, rivers, lakes, roads and six factions" | **True** (60 x 40 tiles). |
| "Caravans carry goods by weight ... Ambushes become battles ... using the same weapons, cover, wounds and AI as colony fights" | **True.** The battle map reuses the main `Game` class. |
| "Settlements can be traded with, supplied, or attacked. Bandit camps can be cleared for rewards." | **True.** |
| "Quests with acceptance, deadlines and consequences, and rescue missions" | **True**, 2 kinds. |
| "Three storytellers and five difficulty levels" | **True.** |
| "Raids that scale with colony wealth: assaults, sappers, sieges and drop pods" | **True.** |
| "Manhunter packs, insect infestations, disease, solar flares, eclipses, toxic fallout, short circuits, blight, meteorites, volcanic winter, crashed mechanoid ships, wandering colonists and trade caravans" | **True.** |
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
| `GameTest.kt` / PathfinderTest, WorldTest | 2, 4 | A* with walls and breaching; terrain per biome; room heat from a campfire; solar power to a lamp; fire spread |
| ... HealthTest | 9 | bleeding, tending, healing, death by heart loss, losing legs, armour, hypothermia, illness course |
| ... ColonyTest | 8 | quiet days survive, beds, chopping and mining, crops, cooking bills, research, stockpile filters, save load |
| ... CombatTest, AnimalTest, TradeTest | 3, 3, 1 | turret and drafted fights; capture and recruit; hunting, taming, herds; trading |
| ... ScenarioTest, AdvancedTest, AreaTest | 3, 5, 1 | every scenario runs; storyteller and difficulty scaling; bionic surgery; mech scrap; area restriction |
| ... CraftingTest, VictoryTest, CaravanTest | 6, 1, 5 | tailoring, smithing, stonecutting, brewing, hydroponics; ship launch; caravan trade, travel, save |
| ... FactionTest, MapSizeTest, SettleTest | 6, 1, 1 | six factions, relations, peace talks, allied aid; large map; founding a colony from a caravan |
| ... LifeTest, MultiTileTest, ContentTest | 4, 2, 7 | pregnancy, growing up, old age, animal breeding; footprints; recipes and research chains, orbital traders, orientations |
| ... RulesTest, MaterialTest | 3, 8 | outdoor wear, outfit policy, lovers; wall and door materials with research gating |
| ... CaravanBattleTest | 14 | caravan fights: win, lose, retreat, animals, saving mid-battle, settlement assault |
| ... TutorialTest, RansomTest | 4, 17 | tutorial lessons; ransom offers, cooldowns, saves |
| `BacklogFixTest.kt` | 14 | regression fixes: caravans, prisoners, breaks, beds, floors, sappers, faction drift, raid spacing |
| `BaseContentTest.kt` | 16 | turrets, mortar, deep drill, crematorium, sculpture, brewing, nutrient paste, hidden content |
| `BodyPartsTest.kt` | 3 | fingers and toes affect grip and walking |
| `CombatPipelineTest.kt` | 20 | melee reach, range, warmup, cooldown, cover, walls, interception, skill, armour, vital parts, determinism of a fight |
| `IncidentTest.kt` | 29 | eligibility, cooldowns, channels, seeded selection, per-incident effects, save of the history |
| `ItemsTest.kt` | 10 | quality and condition travel with goods through stock, caravans, trade, saves |
| `JobAiTest.kt` | 10 | urgent hunger and rest, bed release, reservation races, unreachable memory, interrupted hauls |
| `MoodSocialTest.kt` | 14 | loneliness, jokes, arguments, compliments, spouse separation, hiding, sofa |
| `PerformanceEquivalenceTest.kt` | 5 | optimised searches match simple versions; same seed gives the same save bytes |
| `PerfBenchTest.kt` | 4 | benchmarks, mostly disabled |
| `QuestTest.kt` | 17 | camp and rescue quests, captives, saves |
| `RegionTest.kt` | 5 | reachability agrees with a breadth-first oracle |
| `ReservationTest.kt` | 11 | exclusive claims, multi-claims, cleanup, hospital beds, saves |
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
| Recently added furniture: sofa, bookshelf, wardrobe, coffee table (latest commits) | cannot confirm they are Core; the commit history shows content was added item by item | ? |
| `BREWERY` building, `LAVISH_COOKING` research | not recalled from Core | M |
| `Trait.SOCIABLE`, `CARNIVORE`, `CREATIVE` | not recalled as Core traits | ? |
| `W_LEVER`, `PSYCHITE_TEA` (hidden) | I believe these are Core, so they may be wrongly hidden | M |
| Animal "tricks", suppression, "Lonely" thought, colony animal cap | invented, not DLC; flagged as non-vanilla (not as DLC) | C |

## 7. Top 10 gaps

1. **Time base (24,000 vs 60,000 ticks per day)** and per-tick constants that do not translate. Divergent, save-breaking. (3.1)
2. **Map: 100 x 100, no roofs, grass, caves or ruins**, rooms that count as indoors without a roof. (3.2, 3.3)
3. **Needs and mood model:** hunger 0.44x vanilla, break thresholds 0.30/0.20/0.10 against 0.35/0.20/0.05, four needs missing, scalar mood. (3.5)
4. **Combat model is custom** end to end: weapon stats, accuracy, armour, melee, cover, no burning. (3.11)
5. **Threat generation:** raid points formula, caps, storyteller cycles. (3.13)
6. **Health depth:** 29 parts, simplified bleeding and immunity, limited chronic disease, surgery and medicine. (3.4)
7. **Skill model:** learning rates 1.0/1.4/2.0 against 0.35/1.0/1.5, no decay, thin backstories, no trait degrees. (3.6)
8. **Production numbers:** research cost model, recipes, building costs, power draws (sun lamp, battery), crop growth 1.8x fast; deep drill cannot find ore; hidden Core items. (3.8, 3.9, 3.14)
9. **Rule violations in `sim/`:** wall clock and UUID in `SaveStore`, hash-order save bytes, platform trig. Small fixes. (2.1)
10. **Names and strings copied from RimWorld**, plus a committed signing key. Needs your decision before more content is added. (2.2, X1)

## 8. Proposed phase order

Branch names follow `phase-<n>-<short-name>` once you approve. Phases 2 and 3 change numbers that old saves depend on, so they
need your sign-off on a save-migration approach first.

| Phase | Name | Content | Needs from you |
|---|---|---|---|
| 1 | `phase-1-hygiene` | Pin `<VERSION>`; fix R1 to R3; delete nothing; create `docs/fidelity/` with one file per system and the constant tables above; add a first fidelity test per system as numbers are verified; fix the README counts and links | the version; the naming decision (2.2); the signing-key decision (X1) |
| 2 | `phase-2-timebase` (time base; largely done, see docs/fidelity/time.md) | Move to 2,500 / 60,000 ticks and rescale every per-tick constant; save migration | approval to break or migrate old saves |
| 3 | `phase-3-needs-mood` | Hunger rate, rest and joy drains, add comfort, beauty, outdoors and indoors needs, vanilla break thresholds, thought catalogue, tolerance | none after phase 2 |
| 4 | `phase-4-skills-traits` | Learning rates, decay, XP curve, work-speed curves, trait degrees, backstory set | the backstory approach (original titles only) |
| 5 | `phase-5-health` | Body depth, bleeding and immunity, chronic conditions, medicine, surgery | none |
| 6 | `phase-6-combat` | Weapon stat blocks, accuracy bands, armour roll, melee dodge and parry, burning, cover | none |
| 7 | `phase-7-map-rooms` | 250 x 250 maps, roofs and collapse, grass, caves, ruins, heat exchange; fix the deep drill | performance budget on phones |
| 8 | `phase-8-threats` | Raid points curves and caps, storyteller cycles, raid kinds, infestations | none |
| 9 | `phase-9-economy` | Research costs, recipes, building costs, power, crops, spoilage | none |
| 10 | `phase-10-world-ui` | World size, quests, trade, named policies and areas, UI parity | none |

Open questions blocking Phase 1: the target `<VERSION>`; whether proper names count as "text strings" (2.2); what to do with the
committed signing key (X1); whether to hold the invented mechanics (suppression, tricks, "Lonely") until after the vanilla ones land.

## 9. What this audit did not cover

* `app/` was not compiled or run. `Sprites.kt`, `GameView.kt` and `WorldUi.kt` were not read in depth.
* `SaveGame.kt` and `SaveStore.kt` were read for structure, not line by line.
* No vanilla source was available. All vanilla comparisons are **V** (secondary summaries) or **M/UNVERIFIED**.
* Item-level costs, work amounts, weapon stats and most event numbers were not compared, because no reliable source was reachable.
