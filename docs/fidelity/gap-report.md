# Fidelity gap report

Audit date: 2026-10-09. Scope: the whole repository at commit `7e01e02` plus the merge of `main`. No game code was changed
to produce this report.

**Last updated 2026-10-12**, after the decompiled-source pass. Rows tagged **S** are checked against RimWorld 1.6 source (build 1.6.9438) and cite [`source-constants.md`](source-constants.md). Sections 1 to 11 were brought up to date in that pass. Section 11 has the final verdicts.

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
| **S** | Read from the decompiled RimWorld 1.6 C# source (build 1.6.9438). Path and line are in `source-constants.md`. The strongest tag: it replaces **V** and **M** wherever it applies. |
| **S-Bio** | In code, but gated on Biotech or otherwise DLC-era. Excluded from Core targets. |
| **XML** | Stored in RimWorld's XML `Defs`, which the decompiled repo does not contain. Not verified here. |
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
| `./gradlew :sim:test` | **Passed (2026-10-12).** 451 tests, 0 failures, 0 errors, 0 skipped, in 27 test source files (about 6,600 lines). The build downloaded Gradle 8.11.1 and finished in about five minutes. |
| `./gradlew -PwithApp :app:assembleRelease` | **Not run to completion. Cannot be built here.** `ANDROID_HOME` is unset, there is no Android SDK, and Gradle could not resolve the Android Gradle Plugin `com.android.application:8.7.3` (the proxy denies `dl.google.com` with 403). Nothing in `app/` was compiled or run, so every statement about the app below is from reading source. |
| Compiler warnings | **23**: 20 in `sim/src/main` and 3 in tests. Almost all say a condition is always true or always false (see 1.1). Section 10 said 3 plus 2; that is out of date. |
| `PerfBenchTest` | 3 of its 4 tests are wrapped in `if (enabled)` and do nothing by default. They count as passes. |

### 1.1 Compiler warnings (2026-10-12)

The warnings say a condition is always true or always false. That usually means a branch never runs, or a check is redundant. Each one needs a look before fidelity work touches that code.

| File | Lines | Warning |
|---|---|---|
| `Ai.kt` | 230 | always false |
| `Api.kt` | 69 | always true |
| `Events.kt` | 37, 38, 39, 99 | always true |
| `Game.kt` | 455 | always true |
| `Game.kt` | 569 | always false |
| `Incidents.kt` | 77, 78 | always true |
| `Jobs.kt` | 822 | always false |
| `Mood.kt` | 307 | always true |
| `World.kt` | 155, 215 | always true |
| `World.kt` | 164, 298, 327, 488, 533, 557 | always false |
| `IncidentTest.kt` | 260 | always true (test) |
| `PerfBenchTest.kt` | 110, 147 | deprecated `id` (test) |

**What the tests do and do not prove.** The suite checks internal consistency and regressions: save round-trips, reservations, region reachability, determinism of fights and incidents. Some tests check Stillmere's own constants, including several from secondary sources. `NeedsMoodFidelityTest` asserts the break thresholds 0.35, 0.20 and 0.05, which are **V**. None of the tests cites the decompiled source, so a passing test still says nothing about vanilla fidelity.

## 2. Cross-cutting findings

### 2.1 Rule violations (project rules in `CLAUDE.md`)

| # | Finding | Where | Priority |
|---|---|---|---|
| R1 | **Wall clock and non-seeded randomness inside `sim/`.** `System.currentTimeMillis()` is a default argument, and `UUID.randomUUID()` names new saves. Neither changes simulation state (they are save metadata), but the rule is "never read the wall clock inside `sim/`". | `SaveStore.kt:115`, `SaveStore.kt:88` | 1 (small fix). **Fixed:** default is now 0 and save IDs count up. |
| R2 | **Hash-ordered collection written to disk.** `Game.incidentLast` is a `HashMap<Incident, Long>` keyed by an enum, and `SaveGame.kt:128` iterates it when saving. Enum `hashCode()` is identity-based, so entry order can differ between JVM runs and the same game can serialise to different bytes. `Game.kt` already warns about exactly this for `researchDone`. `PerformanceEquivalenceTest.theSameSeedGivesTheSameSaveBytes` passes only because the comparison runs in one JVM. | `Game.kt:92`, `SaveGame.kt:127-128` | 1 (small fix: `EnumMap`). **Fixed:** `EnumMap` in `Game` and the loader. |
| R3 | **Platform-dependent floating point.** `Math.sin`/`Math.cos` are allowed to differ by 1 ulp between JVM and Android runtime. They feed map generation (lake position), drop-pod landing, and the outdoor temperature curve. A seed could generate a slightly different map on a phone than in the unit tests. **Fixed.** Every `Math.sin`, `Math.cos`, `Math.pow`, `Math.hypot` and `atan2` in `sim/` is now `StrictMath` (`GameMap.kt`, `Events.kt`, `Game.kt`, `Caravans.kt`, `WorldMap.kt`). The outdoor temperature curve was already `StrictMath` in `Climate.kt`. | `sim/` (StrictMath) | 2 |

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
| X3 | **Fixed.** README counts and claims (caves, ores, butchery) were stale: it says 65 buildings and 53 research projects; the code has 72 buildable buildings (76 defined, 4 legacy) and 57 research projects. | 3 |
| X4 | Per-system files exist for time, health, needs and mood, and temperature and rooms. Skills, combat, threats, plants, research, social and the rest have none. `CLAUDE.md` asks for one per system. `docs/fidelity/source-constants.md` now records vanilla constants with their source locations. | 1 |
| X5 | 20 compiler warnings in `sim/src/main`, all "always true" or "always false" conditions (section 1.1). Some may hide code that never runs. | 2 |

## 3. Systems

### 3.1 Time and determinism

**Status: Matches for the time base, pending the save-format decision (see `docs/fidelity/time.md`). Determinism: Matches, with two rule fixes.** **Priority 1.**
**Files:** `Defs.kt` (calendar and `tk()`), `Game.kt` (`tick`, `step`, `daylight`, `outdoorTemp`, `SLOW_TICK`, `HEALTH_STRIDE`), `Noise.kt` (`Rng`), `SaveStore.kt`, `SaveGame.kt`, `app/MainActivity.kt` (real-time pace).

| Constant | Stillmere now | Vanilla | Conf |
|---|---|---|---|
| Ticks per in-game hour | 2,500 | 2,500 (`RimWorld/GenDate.cs:33`) | S |
| Ticks per day | 60,000 | 60,000 (`RimWorld/GenDate.cs:9`) | S |
| Days per season / per year | 15 / 60 | 15 / 60 (`RimWorld/GenDate.cs:27`, `:29`) | S |
| Real-time base rate at 1x | 60 ticks per second (app) | 60 (`Verse/GenTicks.cs:10`) | S |
| Real minutes per game day at 1x | about 16.7 | about 16.7 (derived from the rows above) | S (derived) |

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
| Colony map size | 100 x 100 (`Game.MAP_SIZE`) | 250 x 250 default (`RimWorld.Planet/WorldInfo.cs:24`); player options 200 to 325 in steps of 25 (`RimWorld/Dialog_AdvancedGameConfig.cs:12`) | 0.16x the area | S |
| Battle map size | 44 x 44 | site default 200 x 200 (`RimWorld.Planet/Site.cs:37`); caravan ambush maps 75 to 110 per side, scaled by pawn count (`RimWorld.Planet/CaravanIncidentUtility.cs:9-13`) | about 5% of the area of a default site | S |
| Biomes | 6 | Core has more (12 recalled, including swamps, ice sheet, extreme desert, sea ice). Biome definitions are XML and not in the repo | 6 missing | M, XML |
| Rock types | 5 (granite, marble, limestone, sandstone, slate) | 5 recalled. Rock definitions are XML | none | M, XML |
| Ore kinds | 5 (steel, silver, gold, plasteel, components) | steel, silver, gold, plasteel, jade, uranium, compacted machinery recalled. Ore definitions are XML | at least 2 missing | M, XML |
| Ore vein layout | 8 steel veins (11 cells each), 3 silver, 2 gold, 2 plasteel, 3 components (6 or 4 cells) | ? | | C |
| Soil fertility | soil 1.0, rich soil 1.4 | same recalled. Terrain definitions are XML | none | M, XML |
| Trees | 8 kinds, density by biome | many more kinds (XML) | partial | M, XML |
| Terrain types | 10 | more, with several stone floors and water types (XML) | partial | M, XML |

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
| Comfortable range | 16 to 26 C, shifted by apparel insulation | 16 to 26 C (a race stat in XML) | none | M, XML |
| Temperature granularity | per room, with heat links to neighbouring rooms and the outdoors | per room: each room has one temperature tracker, equalizing with its walls and roof (`Verse/RoomTempTracker.cs`) | **none** (a per-cell grid was not built: it would be less faithful) | S |
| Day/night swing | +/-7 C (+/-10 in desert, arid), coldest 03:00, warmest 15:00 | amplitude 7 (`Verse/TemperatureTuning.cs`, `DailyTempVariationAmplitude`). The hours and desert values were not found in the code read | base amplitude matches | S (amplitude), UNVERIFIED (hours, desert) |
| Heat wave / cold snap offset | +16 / -17 C | ? (recalled near +17 / -20) | | UNVERIFIED |
| Volcanic winter offset | -13 C for 6 days | ? | | UNVERIFIED |
| Heat exchange | `0.048 * sum(edge conductance) / cells` of the gap per slow tick; wall 1.0, door 3.0, rock 0.3 | per-room equalization: walls 0.00017 per 120-tick interval over 20% of wall cells; no roof 0.0007 per tick; thin roof 0.00005; underground 0.002; doors update every 34 ticks open or 375 closed; vents at rate 14 (`Verse/RoomTempTracker.cs:20-36`, `:215-251`; `Verse/TemperatureTuning.cs`) | model differs in detail | S |
| Wall material insulation | none (all materials equal) | walls carry insulation stats in XML (not in the repo) | | XML |
| Heater / cooler | +14 / -14 heat units, only below / above 21 C | ? | | UNVERIFIED |
| Rooms | flood fill; walls, doors and rock separate; a door splits rooms and belongs to none; indoor if it does not touch the map edge and has at most 700 cells, or a mostly natural-roofed majority | a room uses outdoor temperature when at least 25% of its cells have no roof (`Verse/Room.cs:47`, `:98-108`), and always when it touches the map edge. Psychologically outdoors at 300 or more open cells (`:376-390`). The 700-cell cap is Stillmere's own rule | **Divergent** (no roofs; the 700-cell cap and majority rule are not vanilla) | S |
| Room stats | beauty, cleanliness, wealth, impressiveness, role (9 roles); constants in `RoomRules` | 23 room role workers (`RimWorld/RoomRoleWorker_*.cs`) and 7 room stat workers (`RimWorld/RoomStatWorker_*.cs`) exist in source. Their numbers were not compared | structure matches in part; 9 roles against 23 | S (structure), C (numbers) |

**Done in phase 4:** outdoor temperature moved into a pure `Climate` object (`StrictMath`, fixing R3 for this curve); heat now flows
between neighbouring rooms through doors, walls and rock instead of only to the outdoors; heater and cooler thermostat extracted;
room stats moved out of `Game` into `computeRoomStats` with named constants. 18 tests in `TemperatureRoomsTest`.

**Could not be verified** (see the doc): heater and cooler output, the heat-wave and cold-snap offsets, the day/night hours,
the impressiveness formula and thresholds, and which buildings and terrain contribute to beauty and cleanliness and by how much.
Heat-exchange rates and the day/night amplitude are now source-checked (rows tagged S). The impressiveness thresholds and the building contributions to beauty and cleanliness were not compared. Stillmere's own numbers for the rest stay UNVERIFIED.

**Missing:** roofs (and so the 75% rule, roof collapse and roofless rooms), vents, doors that are open versus closed, rain and snow on
roofless rooms, overhead mountain rock as a room property beyond the natural-roof flag, sunlight beyond a day/night curve, seasonal
day length, latitude-dependent seasonal swing.

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
| Consciousness | Partial | Pumping weight 0.2 agrees; breathing and filtration weights and the pain and blood-loss terms are `UNVERIFIED`. |
| Moving, manipulation | Partial | Structure is the author's. Consciousness does not multiply either directly (`SECONDARY`). |
| Sight, hearing | Matches | Mean of the eyes or ears; one eye lost halves sight. |
| Eating | Matches (changed) | Now jaw only; the stomach moved to Metabolism. |
| Breathing, pumping, filtration | Matches (changed) | Invented fudge factors removed: one lung or kidney lost now halves the capacity. Liver loss and loss of both kidneys now kill ("organ failure"). |
| Metabolism | Partial (new) | Computed from the stomach. Not yet consumed by anything: the rest-rate link is not wired. |
| Pain and pain shock | Matches (changed) | Shock threshold 0.80; Wimp lowers it by 0.50 instead of multiplying pain. The pain-to-consciousness curve is `UNVERIFIED`. |
| Downed state | Matches (changed) | Down when consciousness < 30%, moving ≤ 15%, or pain at the threshold; standing up is the exact negation. |
| Bleeding and clotting | Gap (source) | Vanilla bleed rate is severity x injury bleed rate x race factor x part factor, zero when tended, on a solid part, or permanent. Bleeding stops by age: 90,000 ticks plus up to 90,000 by severity (`Verse/Hediff_Injury.cs`, `BleedRate` and `AgeTicksToStopBleeding`). Stillmere keeps 6% bleeding when tended and clots over 34,000 ticks untended or 12,000 tended (`HealthRules.kt:87-92`). Death at 100% blood loss agrees. Per-race and per-part bleed rates are XML. |
| Wound infection | UNVERIFIED | Qualitatively consistent with wiki anecdotes. No figures confirmed. |
| Immunity | Partial | Vanilla forced immunity is 0.65 (`Verse/ImmunityHandler.cs:19`). Immunity gain per tick is the per-day value over 60,000, with a per-disease factor from 0.8 to 1.2 (`Verse/ImmunityRecord.cs:42-47`). Per-day values are XML. Stillmere uses 0.9 per day (`HealthRules.kt:130`). |
| Illness speed | Gap | Flu runs about 3.5 times faster than the wiki's figures (`SECONDARY`). Left alone; illness tuning was out of scope. |
| Scars, lost limbs | UNVERIFIED | Scars are free and permanent (I recall vanilla scars keeping some severity). |
| Prosthetics | UNVERIFIED | Efficiencies unconfirmed. "Hook hand" may not be a base-game item. |
| Tend quality | Matches (changed) | Doctor-stat curve, ×0.7 self-tend, ±25% luck, clamp to the medicine's cap. Vanilla's no-medicine values are potency 0.3, quality cap 0.7 and no-doctor quality 0.75 (`RimWorld/TendUtility.cs:10-14`); its self-tend factor is 0.7 (`:16`), which Stillmere matches. Stillmere's no-medicine values were not found in the code read. Herbal potency (Stillmere 0.6, `Defs.kt:166`) and the +0.10 bed offset are still `UNVERIFIED`, since herbal values are XML. About double the old tend quality for skilled doctors. |
| Medicine items | Partial | Industrial agrees. Herbal is `UNVERIFIED`. Glitterworld medicine is missing. |
| Healing rates, beds | UNVERIFIED | Kept as the author's tuning. Hospital-bed healing, infection and tend bonuses are all unconfirmed. |
| Surgery | Partial | Amputation and prosthetic or bionic installation. Success ignores medicine potency and no medicine is consumed (both `SECONDARY`/`UNVERIFIED` gaps). No operations on injuries. |
| Downed enemies dying from pain shock | Gap | `SECONDARY`; not implemented. |
| Death at consciousness 0% | Gap | `SECONDARY`; not a separate rule. |
| DLC exclusion | Matches | No DLC-sourced condition or operation found in the sim (audited from memory). |

#### Follow-ups

1. Confirm the remaining `UNVERIFIED` values. Source now settles the bleed model, the no-medicine tend values, the self-tend factor and the forced immunity level. Still open, and mostly XML: finger and toe HP, coverage, healing rates, the scar rule and the infection numbers.
2. Wire filtration into immunity gain, and Metabolism into rest rate.
3. Apply consciousness directly to Moving and Manipulation.
4. Make surgery success depend on medicine potency, and consume medicine.
5. Re-tune illness progression against the wiki's per-day figures.
6. Add glitterworld medicine and a staged blood-loss hediff.


### 3.5 Needs, mood, thoughts, drugs, mental breaks

**Status: Divergent, with phase 3 started** (hunger and break thresholds now match sourced values; see `needs-mood.md`). **Priority 1.**
**Files:** `Mood.kt` (`moodUpdate`, `breaksTick`, `startBreak`), `Game.kt` (`pawnTick` need drain), `JobDrivers.kt` (`driveEat`, `driveSleep`, `driveJoy`, `applyDrug`), `Pawn.kt` (`Thought`, `addThought`), `Body.kt` (drug hediffs), `Jobs.kt` (`foodScore`, `findFood`, `startJoy`).

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| Hunger rate (adult human) | **1.6 per day** of a 0-to-1 bar (was 0.7) | **1.6 nutrition per day** (`RimWorld/Need_Food.cs:11`: 2.6667e-5 per tick x 60,000) | matches | S |
| Raw plant food nutrition | 0.05 each | 0.05 each | none | V |
| Simple meal nutrition / cost | 0.9, from 10 raw items = 0.5 nutrition | 0.9, from 0.5 nutrition of ingredients | none | V |
| Rest drain | 0.95 per day awake | 0.95 per day awake (`RimWorld/Need_Rest.cs:20`: 1.5833e-5 per tick x 60,000, times 1.0 when rested, 0.7 tired, 0.3 very tired, 0.6 exhausted) | matches | S |
| Joy drain | 0.42 per day | 0.24 to 0.6 per day by joy level: 0.6 empty, satisfied, high or extreme; 0.42 low; 0.24 very low (`RimWorld/Need_Joy.cs:43-50` x 400 need intervals per day) | **Divergent** (flat against a level-dependent curve) | S |
| Mental break thresholds | **0.35, 0.20, 0.05** of a 0-1 mood (was 0.30, 0.20, 0.10); per-check chance 0.45%, 1.2%, 3% per 625-tick check | **0.35, 0.20, 0.05** (minor, major, extreme) as fractions 1, 0.5714 and 1/7 of the break stat, which is 0.35 in XML (`Verse.AI/MentalBreaker.cs:40-44`) | matches | S |
| Mood baseline | 0.55, smoothed 50/50 with the previous value every 250 ticks | the base level is `def.baseLevel`, an XML value (`RimWorld/Need_Mood.cs:31`). The 0.5 recalled earlier is not confirmed | model differs | XML |
| Break kinds | 10 (wander, berserk, food binge, insult, tantrum, fire, catatonic, run wild, drug binge, hide) | more kinds, each tied to a severity band | partial | M |
| Distinct thoughts | about 45 | thoughts are XML `ThoughtDef`s. The code has `ThoughtDefOf` and `ThoughtWorker_*` classes, but not the list | much smaller (not counted) | XML |
| Needs | food, rest, joy | food, rest, joy, **comfort, beauty, outdoors, indoors**, chemical needs (`RimWorld/Need_Comfort.cs`, `Need_Beauty.cs`, `Need_Outdoors.cs`, `Need_Indoors.cs`, `Need_Chemical.cs`) | 4+ missing | S |
| Drugs | beer, smokeleaf joint, flake, yayo, go-juice, wake-up, psychite tea (hidden) | also luciferium, ambrosia and penoxycyline. Luciferium and ambrosia are referenced in source (`Recipe_AdministerIngestible.cs`, `ThoughtDefOf.cs`), with no DLC gate in those files. Penoxycyline was not checked | 3 missing | M; S (names) |
| Addiction | one roll per use: 1.5% to 45% by drug | per-drug addictiveness with tolerance building up | model differs | M |
| Tolerance | `ALCOHOL_TOLERANCE` hediff is defined but **never used** | per-drug tolerance exists in vanilla code (`RimWorld/Need_Chemical_Any.cs` tracks interest and fascination; `RimWorld/Need_Joy.cs` has a tolerances field). Per-drug values are XML | **missing** | S |
| Drug policy | per colonist: none, social only, whenever bored | named, shareable policies with per-drug rules | much simpler | M |
| Food policy / outfit | per colonist: 3 and 4 fixed options | named, editable policies | much simpler | M |

**Also true:** mood is one smoothed scalar, so moods do not stack or decay like vanilla thought lists. "Under fire" does not appear in the C# source (a search finds nothing), so it looks invented. "Lonely" was not checked (**UNVERIFIED**).

### 3.6 Skills, traits, passions, backstories

**Status: Partial.** **Priority 2.**
**Files:** `Defs.kt` (`SkillType`, `Trait`), `Pawn.kt` (`gainXp`, `workSpeed`, `Backstories`, `Names`), `Game.kt` (`newHuman`, `conflicts`).

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| Skills | 12, cap 20 | cap 20 (`RimWorld/SkillRecord.cs:31`); the skill count is XML | none | S (cap), XML (count) |
| Learning rate, no / minor / major passion | 1.0 / 1.4 / 2.0 | **0.35 / 1.0 / 1.5** (`RimWorld/SkillRecord.cs:39-43`, `:379-381`) | no-passion learns about 2.9x too fast | S |
| Skill decay | none | **Confirmed in source.** Every 200-tick interval, XP is removed from level 10 upward: 0.1, 0.2, 0.4, 0.6, 1.0, 1.8, 2.8, 4, 6, 8 and 12 for levels 10 to 20. Great memory halves it. Perfect memory is exempt, and that trait needs Anomaly (`RimWorld/SkillRecord.cs` `Interval`; `RimWorld/Pawn_SkillTracker.cs` `SkillsTickInterval`, gated on `IsHashIntervalTick(200)`) | **missing** | S |
| XP per level | `2500 + 400 * level` | piecewise linear through 1,000 at level 0, 10,000 at level 9 and 30,000 at level 19 (`RimWorld/SkillRecord.cs:47-50`) | **Divergent**: 2,500 against 1,000 at level 0; 10,100 against 30,000 at level 19 | S |
| Work speed from skill | `0.4 + 0.075 * level` linear | per-stat curves | shape differs | M |
| Traits | 42, no degrees, 1 to 3 at random | around 50, with degrees (for example two levels of industriousness) | partial | M |
| Backstories | 33 (11 childhood, 22 adulthood) | hundreds | tiny set | M |
| Backstory effects | skill bonuses, a few disabled work types; no trait grants, no skill penalties | all three | partial | M |
| Daily learning cap | none | 4,000 XP per day at full rate, then x0.2 (`RimWorld/SkillRecord.cs:33`, `:37`, `:139`) | **missing** | S |
| Skill gain per activity | per-tick and per-action amounts: construction 0.04 per tick, research 0.07 per tick (both divided by 2.5), cooking 40 per job | per-call amounts in code, e.g. floor work learns 0.1 per tick before the passion factor (`RimWorld/JobDriver_AffectFloor.cs:40`). Amounts per activity were not compared in full | partial | S |

All 42 traits have some effect in code (several only add a mood thought: carnivore, transhumanist, pyromaniac). `Trait.SOCIABLE`,
`CARNIVORE`, `CREATIVE` are not traits I recognise from the base game (**UNVERIFIED**). Source check (2026-10-12): `RimWorld/TraitDefOf.cs` names Pyromaniac, so that trait is confirmed. `CARNIVORE` appears only in food-restriction code (`RimWorld/FoodUtility.cs`), not as a trait, so it stays unconfirmed. `SOCIABLE` and `CREATIVE` do not appear in the source, but trait definitions are XML, so that does not settle it.

### 3.7 Work and jobs

**Status: Partial** (structure **Matches**). **Priority 2.**
**Files:** `Jobs.kt` (`think`, `tryWork`, finders, bills), `JobDrivers.kt`, `Reservations.kt`, `Regions.kt`, `Pathfinder.kt`, `Defs.kt` (`WorkType`).

**Structure matches vanilla's design.** The source has the matching `WorkGiver` and `JobDriver` classes. The list of 18 work types and their order is XML (`WorkTypeDef`) and is **not verified here**. Stillmere also has priorities 1 to 4 (0 for off, lowest number first, then list order), a 24-hour schedule of anything, work, joy and sleep, reservations, unreachable memory and region-based reachability. Those were not compared rule by rule.

Source-checked job constant: floor work learns construction at 0.1 per tick before the passion factor (`RimWorld/JobDriver_AffectFloor.cs:40`). Most job durations are XML or computed per job, and were not compared.

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
| Wood wall | 5 wood, 160 work | wall costs 5 stuff; work **UNVERIFIED** (XML) | | M, XML |
| Construction speed | build work x0.5; progress 0.4 x skill speed / 2.5 per tick (`JobDrivers.kt:689`, `:707`) | global factor 1.7 (`RimWorld/GenConstruct.cs:9`); per-job work amounts are XML | not compared directly | S |
| Solar generator | 1,700 W | 1,700 W recalled (power values are XML, not in the repo) | none | XML |
| Wood-fired generator | 1,000 W | 1,000 W recalled (XML) | none | XML |
| Wind turbine | 1,500 W constant x wind 0.15 to 1.35 | ? | | ? |
| Battery capacity | 600 per battery | 1,000 recalled (XML) | -40% | XML |
| Battery self-discharge | not compared | 5 W (`RimWorld/CompPowerBattery.cs:13`) | not compared | S |
| Sun lamp draw | 400 W | 2,900 recalled (XML) | far too low, if the recall is right | XML |
| Electric stove, TV, hydroponics | 350, 200, 70 W | same recalled (XML) | none | XML |
| Zone priorities | low, normal, preferred, important, critical | same five | **Matches** | M |
| Materials | wood, stone, steel, plasteel for walls; wood or steel for doors | many stuff types by property | 4 only | C |
| Fire | intensity 0 to 1.2 per cell, spreads by flammability, rain 0.01 per step | size 0.1 to 1.75, growth 0.00055 per tick, sparks every 75 to 150 ticks, sky extinguish chance 0.04, heat 160 per interval (`RimWorld/Fire.cs:30-68`) | **Divergent** (model differs) | S |

**Missing:** roofs and roof collapse, stone blocks by rock type, wall and door insulation, doors for power or vents, conduit-free
power transmitters, multiple room-based work-table effects, snow clearing zones, named animal and pen areas (only 3 restriction
areas exist), a hit-point filter on zones.

### 3.9 Plants, food, cooking, spoilage

**Status: Partial.** **Priority 2.**
**Files:** `Defs.kt` (`PlantType`, `ItemType`), `World.kt` (`plantsTick`, `spoilTick`, `deteriorationTick`), `Recipes.kt`, `JobDrivers.kt` (`driveSow`, `driveCut`, `driveEat`), `Jobs.kt` (`findGrow`).

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| Crop grow days (rice, potato, corn, strawberry, cotton, healroot) | 5.8, 5.8, 11.5, 6.5, 5.5, 8 | same recalled. Per-plant values are XML | none | M, XML |
| Effective growth speed | `1 / (days * 0.55)` per day at fertility 1 | growth multiplies five factors: fertility, temperature, light, haze and drought (`RimWorld/Plant.cs:289-301`). Fertility is `fertility x sensitivity + (1 - sensitivity)` (`RimWorld/PlantUtility.cs:352-358`) | **Divergent**: a flat speed-up of about 1.8x with no curves | S |
| Corn yield / potato yield | 40 / 11 | 40 / 11 recalled (XML) | none | XML |
| Rice yield | 12 | 6 recalled (XML) | 2x | XML |
| Crop growth temperature | 6 to 42 C (devilstrand from 10) | defaults: minimum 0, optimal 6 to 42, maximum 58 (`RimWorld/Plant.cs:50-58`). Growth falls off between 0 and 6, and between 42 and 58. Per-plant values are XML | optimal range matches; Stillmere cuts off hard at the limits | S |
| Frost kill | below -2 C, 15% per slow tick | ? | | ? |
| Spoilage (days): meat, meals, milk | 4, 4, 4 | 4 recalled. Rot timing is XML (`TicksToRotStart`); the rot model is in `RimWorld/CompRottable.cs` | none (recalled) | XML |
| Spoilage: raw plants | 40 days (strawberries 10) | ? | | ? |
| Corpse rot | 2.2 days | ? | | ? |
| Rot temperature factor | none below 0 C, 0.2 at 0 C rising to 1.0 at 20 C | rot rate comes from `GenTemperature.RotRateAtTemperature` (`RimWorld/CompRottable.cs`). The function is in source; its numbers were not read | not compared | S (function) |
| Food poisoning | 40% if the food was over 65% rotten, 1% from any meat | fixed chance 2% for meat and 5% for corpses (`RimWorld/ThingDefGenerator_Meat.cs:68`, `ThingDefGenerator_Corpses.cs:154`), scaled by rot stage (`RimWorld/CompRottable.cs:153`). Stillmere's 1% for meat is half the vanilla value | model differs; meat chance is half | S |

**Missing:** grass and wild-plant ecology, plant fertility mapping beyond terrain, ambient light needs per crop, animal feed items
beyond hay and kibble, insect jelly, chocolate, and the like. `Brewery` (a separate bench for beer): the source has base-layout resolvers named `Brewery` (`RimWorld.BaseGen/SymbolResolver_Interior_Brewery.cs`), with no DLC gate in the file. Whether the `BREWERY` building is Core is still unconfirmed, and vanilla's beer brewing is XML.

**Defect found:** a deep drill brings up ore from the cell it stands on, but ore only exists in rock and rock cannot be built on, so
a real deep drill always yields stone chunks (`World.kt productionTick`). `BaseContentTest.aPoweredDeepDrillBringsUpItsOre` passes
because it places ore under a drill by hand.

### 3.10 Animals

**Status: Partial.** **Priority 3.**
**Files:** `Race.kt`, `Animals.kt`, `Life.kt` (`animalLifeDaily`), `JobDrivers.kt` (`driveTame`, `driveTrain`, `driveHunt`, `driveSlaughter`, `driveGather`).

* 38 animal species plus 3 mechanoids; vanilla has roughly 70 animals (**UNVERIFIED**).
* **Training differs.** Vanilla trains through `TrainableDef`s. The source refers to `Obedience`, `Release`, `Tameness`, `Comfort`, `Dig`, `Forage`, `AttackTarget` and others (`RimWorld/TrainableUtility.cs`). Stillmere teaches up to two generic "tricks" that make an animal hit harder (`MAX_TRICKS = 2`, `Pawn.trained`). Divergent; no DLC involved.
* **Taming (source, 2026-10-12):** each recruit attempt reduces resistance by 1 (`RimWorld/InteractionWorker_RecruitAttempt.cs:9`); a bonded animal gets a x4 chance (`:40`); a tamed animal gives a 1% bond chance (`:42`); tame attempts are at least 30,000 ticks apart (`RimWorld/TameUtility.cs:9`). The base recruit difficulty is XML. Stillmere's taming chance, wildness, herd and predator behaviour and yields are custom and were not compared.
* Colony animal cap of 40 in breeding is custom.
* Missing: pens and area restrictions for animals, animal bonding, wildlife migrations, nuzzling, pack animals' effect outside caravans.

### 3.11 Combat

**Status: Divergent.** **Priority 1.**
**Files:** `Ai.kt` (`fire`, `hitChance`, `shotPath`, `hostileAI`, `draftedAI`), `Defs.kt` (`Weapon`, `Apparel`, `DamageKind`), `Health.kt` (`dealDamage`), `World.kt` (`turretsTick`, `explode`, `trapsTick`).

* **Weapon stat blocks are custom.** Each weapon has one damage, one range, one accuracy, a cooldown in ticks, a warmup in ticks, burst, armour penetration and (for shotguns) pellet count. Vanilla keeps warmup, cooldown and accuracy by range band in XML (`Verse/VerbProperties.cs` has the fields; the values are not in the repo).
* **Accuracy and hit chance.** Vanilla aim chance is `accuracy` raised to the target distance, with range-band factors at 3, 12, 25 and 40 cells and a floor of 0.0201 (`Verse/ShotReport.cs:167-173`, `:64`). A downed target at 4.5 cells or more halves it, and a downed target within 3.9 cells takes a 7.5x execution factor (`:36-46`). The total then multiplies by the cover pass chance. Stillmere's hit chance, as recorded in the earlier audit (not re-read this pass), is `accuracy * (0.62 + 0.03 * skill) * quality`, scaled down by distance, sight, cover, weather and suppression. The shapes differ. **Divergent.**
* **Armour.** Vanilla rating is capped at 2.0 (`Verse/ArmorUtility.cs:9`). A roll below half of (rating minus penetration) deflects the hit. A roll below the full difference halves the damage and turns sharp into blunt. The armour also takes a quarter of the damage as wear (`:58-92`). Stillmere cuts damage by armour fraction and deflects only small hits (`Health.kt:135-146`). **Divergent.**
* **Melee.** Vanilla makes two rolls: a non-miss roll from `MeleeHitChance`, then a dodge roll from `MeleeDodgeChance`, each with lit and dark offsets (`RimWorld/Verb_MeleeAttack.cs:43-45`, `:126-194`). Damage varies from 0.8x to 1.2x (`RimWorld/Verb_MeleeAttackDamage.cs:10-12`), and there is a per-target cooldown of 50 ticks (`Verb_MeleeAttack.cs:10`). Stillmere has one hit roll and no dodge or parry. **Gap.**
* **Cover.** Vanilla cover blocks shots by angle and distance. A corner gives 0.75 (`Verse/CoverUtility.cs:8`). Angle factors are 1.0, 0.8, 0.6, 0.4 and 0.2 by band, diagonal angles count x1.75, and distance factors are 0.333 under 1.9 cells and 0.667 under 2.9 cells (`:55-99`). Stillmere counts only the best cover on the cells beside the target (`targetCover`), not along the path. **Gap.**
* **Suppression.** Vanilla has a suppression need, with thresholds of 0.7 (able to suppress) and 0.3 (critical) (`RimWorld/Need_Suppression.cs:8-10`). The earlier audit called Stillmere's suppression invented. That was wrong: the need exists in vanilla. Stillmere's rules and numbers are its own and were not compared.
* **Missing:** burning status on pawns (the source search found none), EMP and stun, fire modes, explosive types, proper turret ammo and power, infestation hives, mechanoid weapon variety, shields.
* 38 weapons defined. Psychite tea is hidden as unconfirmed in `Catalog.kt`, but the source's 0.18 back-compatibility converter maps the old `PsychiteTea` def (`Verse/BackCompatibilityConverter_0_18.cs:44`). It existed in an early Core release, so hiding it is probably wrong. The lever-action rifle does not appear in the source. Weapons are XML, so that stays unresolved.
* Tested well for internal behaviour (`CombatPipelineTest`, 20 tests), but none of its tests asserts a vanilla number.

### 3.12 Social and life stages

**Status: Partial.** **Priority 3.** (A **DLC-sourced** candidate is flagged.)
**Files:** `Social.kt`, `Life.kt`, `Mood.kt`, `Pawn.kt` (`stage`, `opinion`, `spouse`, `lover`).

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| Year | 60 days | 60 days (`RimWorld/GenDate.cs:27`) | none | S |
| Baby / child / teen / adult | 0 to 2, 3 to 12, 13 to 17, 18+ | life-stage definitions are XML | | XML |
| Growth moments | at 7, 10, 13 (passion, skill, trait) | ages 7, 10, 13 in code (`RimWorld/GrowthUtility.cs:56`). The file has no DLC gate, but the growth-tier code is Biotech-era, so the DLC status is unresolved (section 6) | ages match | S |
| Pregnancy | 30 days; 6% chance per day for a partnered woman aged 16 to 45 | gestation length is per race, in XML (`RimWorld/PawnColumnWorker_Pregnant.cs:28` multiplies `gestationPeriodDays` by 60,000). The 6% chance was not checked | | XML |
| Old age | chronic conditions from 40 to 62; death roll from 80 | ? | | ? |
| Social interactions | 8 (chitchat, deep talk, insult, kind words, joke, argument, compliment, romance) | `InteractionWorker_*` classes exist. Vanilla base weights include deep talk 0.075, insult 0.007, marriage proposal 0.4 with 0.9 acceptance, and breakup 0.02 (`RimWorld/InteractionWorker_*.cs`). Stillmere's rules are in `Social.kt` and were not compared weight by weight | partial | S |
| Opinion thresholds | friend at 20, rival at -20 (`RimWorld/Pawn_RelationsTracker.cs:47-49`); minimum romance age 16 (`:51`) | Stillmere uses its own scale: deep talk needs opinion above 25, romance above 35, arguments start below -10, romance age 18 (`Social.kt`) | different scales | S |
| Bond relations | a check every 2,500 ticks within 12 cells, 0.001 chance (`RimWorld/Pawn_RelationsTracker.cs:41-45`) | not compared | | S |

**Missing:** relation types beyond parent, spouse, lover and opinion (siblings, cousins, ex-partners, bonded animals), weddings and
parties, social fights beyond a bruise exchange, babies' separate needs. Orientation traits gate romance (`ContentTest.orientationsGateRomance`).

### 3.13 Threats and storytellers

**Status: Divergent.** **Priority 1.**
**Files:** `Incidents.kt` (`IncidentRegistry`, `threatPoints`), `Events.kt` (`launchRaid` and all event effects), `Game.kt` (`Storyteller`, `Difficulty`, `init` timers), `Factions.kt` (`pickRaiders`).

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| Raid points | `(17 * colonists + wealth / 95 + 6 * animal danger) * difficulty * storyteller * min(1, 0.34 + day / 34)`, floor `22 * difficulty` | the wealth term is a curve (0 up to 14,000 wealth, 2,400 at 400,000, 4,200 at 1,000,000) plus per-colonist points (15 to 200 by wealth; close to Stillmere's 17 at low wealth), animals at 0.08 x combat power, buildings weighted at 0.5 (`BuildingWealthFactor`, `:16`; the use was not traced), all multiplied by random, adaptation, difficulty and a days-passed curve, then clamped between 35 and 10,000 (`RimWorld/StorytellerUtility.cs:12-24`, `:43-49`, `:186-190`). The wealth curve stops rising at 1,000,000 because curves clamp past their last point | **Divergent**: Stillmere's wealth term is about 1.75x vanilla at 400,000 and 2.5x at 1,000,000 | S |
| Difficulty threat scale | 0 / 0.6 / 0.9 / 1.5 / 2.2 | difficulty values are XML | | XML |
| First raid | day 8 to 12 | storyteller and incident timing are XML | | XML |
| Raid kinds | assault, sappers, siege (mortar camp), drop pods, mechanoid cluster | straight assault, sappers, breach, siege, drop pods, infestation, mech cluster (defined in XML) | partial | M, XML |
| Incident channels | 6 timers (raid, wanderer, pod, trader, temperature, misc) with 24 incident types | storyteller-specific incident cycles | model differs | M |
| Storytellers | 3, original characters | 3 in Core; personalities and weights are XML | structure similar, behaviour not | XML |

**Missing:** the days-passed curve (vanilla's `pointsFactorFromDaysPassed`, XML), the colonist-age factor (Biotech only), per-storyteller cycles, raid strategies beyond the five kinds, friendly raids
(allied reinforcements exist only through comms), wanderer and refugee rules, the incident list beyond what is registered. "Aurora"
is registered and I cannot confirm it is a Core event (**UNVERIFIED**).

### 3.14 Research

**Status: Divergent.** **Priority 2.**
**Files:** `Defs.kt` (`Research`), `Game.kt` (`researchAvailable`, `startResearch`), `JobDrivers.kt` (`driveResearch`), `Jobs.kt` (`findResearch`).

* 57 projects (the README now says 57; see section 4).
* **Cost model differs.** Stillmere: `cost = baseCost * 16`; progress per tick = `workSpeed * 0.25 * bench multiplier (1.0 or 1.6) * trait`, with the rate divided by 2.5 for the new tick length (`JobDrivers.kt:866`). Vanilla: a fixed cost per project (XML) and a progress rate of 0.00825 per work tick, before the difficulty research speed and the tech-level cost factor (`RimWorld/ResearchManager.cs:38`, `:317-320`). At speed 1, a Stillmere pawn-day gives about 6,000 points against vanilla's 495, about 12 times more. A 300-cost project is 4,800 Stillmere points, about 0.8 pawn-days. Vanilla costs are XML and are needed for a final comparison.
* Several project names are not vanilla Core as far as I know (for example "Lavish meals", "Plasteel blades") (**UNVERIFIED**); the tree shape is original.
* Missing: tech levels, techprints, prerequisite rules beyond the listed dependencies, research benches' effect on speed beyond the 1.0/1.6 split.

### 3.15 World map, factions, caravans, quests

**Status: Partial.** **Priority 2.**
**Files:** `WorldMap.kt`, `Factions.kt`, `Caravans.kt`, `Battles.kt`, `Encounters.kt`, `Quests.kt`, `Trade.kt`, `Ransom.kt`.

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| World size | 60 x 40 tiles | planet coverage options of 30%, 50% and 100% (`RimWorld/Page_CreateWorldParams.cs:32`). The tile count follows from coverage in the generator and was not counted | much smaller (tile count not computed) | S (options), M (size) |
| Hill levels | flat, small, large, impassable | flat, small, large, mountainous | **Matches** | M |
| Factions | 6: 2 tribal, 2 outlander, 2 pirate | faction types are XML | similar | XML |
| Carry capacity: human / muffalo | 35 kg / 70 kg | capacity is a stat in XML | none (recalled) | XML |
| Item mass: silver, steel, wood, component | 0.008, 0.5, 0.5, 0.6 kg | item mass is XML | none (recalled) | XML |
| Caravan speed | `1900 ticks per cost unit` (about 4,750 in the new tick length), slowed up to 40% by load | vanilla: 3,300 ticks per move by default (`RimWorld.Planet/CaravanTicksPerMoveUtility.cs:40`), 340 cells per tile (`:38`), with speed from the human move-speed stat (`:159-160`). Stillmere's unit is a per-step cost, so the two are not directly comparable | needs a travel test | S |
| Quest kinds | 2 (clear a camp, rescue captives) | quest scripts are XML; the code has `RimWorld.QuestGen` nodes | far fewer | M, XML |
| Trade price | sell `0.5 + social * 0.01`, buy `1.35 - social * 0.01` of base value | global factors 1.4 to buy and 0.6 to sell, with minimums 0.5 to buy and 0.01 to sell (`RimWorld/TradeUtility.cs:14-20`). The social effect was not read | buy close (1.35 against 1.4); sell lower (0.5 against 0.6) | S |

**Implemented and tested:** caravans that travel, trade, fight on a temporary battle map, retreat, are taken captive, found new colonies;
faction goodwill, ransom offers, allied aid, orbital traders. The battle map is an ordinary `Game` in encounter mode, which does
reuse the weapons, wounds and AI as the README says.

**Missing:** world sites other than bandit camps, ancient ruins, item stashes, quest types, world feature names, planet coverage options (30, 50 and 100%) and seed
options, weather and biome effects on travel.

### 3.16 UI and controls

**Status: Partial.** **Priority 2.** **Read from source only; the app was not built or run.**
**Files:** `app/` (`MainActivity.kt`, `GameView.kt`, `Panels.kt`, `Dialogs.kt`, `WorldUi.kt`, `MenuActivity.kt`, `SaveUi.kt`, `Sprites.kt`, `Overview.kt`, `TutorialUi.kt`, `Updater.kt`).

* Bottom bar: Architect, Work, Schedule, Research, People, Animals, Map, World, Trade, Log, Menu.
* **Vanilla main tabs** (source: 15 `RimWorld/MainTabWindow_*.cs` windows): Animals, Architect, Assign (work), Factions, History (log), Ideos, Inspect, Mechs, Menu, Pawn table (people), Quests, Research, Schedule, Wildlife and Work. Ideos and Mechs are DLC-era. Stillmere has no Quests or Factions tab. Its Trade, Map and World tabs are not vanilla main tabs.
* Touch: pan, pinch zoom, tap to inspect, draft, move, attack.
* Landscape only (`sensorLandscape`); `minSdk 26` (Android 8.0) matches the README.
* Speeds: pause, 1x, 3x, 6x. Vanilla (`Verse/TickManager.cs:88-104`): 1x, 3x and 6x, with 6x becoming 12x when nothing is happening and 18x with no map, and an ultra-fast speed of 150x. Stillmere matches 1x, 3x and 6x, but has no ultra-fast speed and none of the 12x and 18x cases.
* Policies are per-colonist buttons, not editable named policies (see 3.5).
* Zone filters, quality limit and priority are present (`Dialogs.kt:362-383`).
* Not reviewed in depth: rendering (`Sprites.kt`, `GameView.kt`), the world-map UI, the in-app updater.
* Everything is drawn procedurally; no copied art (consistent with the README).

### 3.17 Start, scenarios, save and load

**Status: Partial** (saves are strong; scenarios are simplified). **Priority 2.**
**Files:** `Game.kt` (`startNewColony`, `startSettlement`, `Scenario`), `SaveGame.kt` (format version 25), `SaveStore.kt`, `SaveSession.kt`, `Tutorial.kt`, `JobRestore.kt`.

* Four scenarios: crashlanded (3 colonists), lost tribe (5), rich explorer (1), naked brutality (1). Start gear, supplies and starting research are custom. The vanilla default for starting colonists is 3 (`RimWorld/Dialog_ChooseNewWanderers.cs:63`), which matches the crashlanded start. Vanilla scenarios are XML (`ScenarioDef`), so the other counts and gear are not verified.
* Colonists are random on start; a crew-selection step exists (`Dialogs.kt:240`).
* Saves: versioned format (25), old versions still load (tested back to version 1), autosave with a recovery copy, named saves, quicksave, archive of settled colonies, damage detection with fallback. This is the best-tested area (`SaveFormatTest`, `SaveRestoreTest`, `SaveSessionTest`, `SaveStoreTest`).
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
| "65 buildings" | **Fixed.** README now says 72 (section 10). |
| "Rooms ... have temperature, beauty, cleanliness and impressiveness values" | **True.** |
| "Stockpile, growing and dumping zones with item filters, quality limits and priorities" | **True.** |
| "Workbench bills for cooking, butchery, tailoring, smithing, machining, stonecutting and medicine" | **Fixed.** README now says butchery is an automatic job at a butcher table. |
| "Power grids with solar, wind, wood and battery storage. Fire spreads, rain puts it out, and lightning can start it." | **True.** |
| "Six biomes, rivers, lakes, five rock types" | **True.** |
| "six ores" | **Fixed.** README now says five. |
| "caves" | **Fixed.** Removed from README. Caves are still not generated. |
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
| "A research tree of 53 projects" | **Fixed.** README now says 57. |
| "Four scenarios. Launch the ship to win. Automatic and manual saves." | **True.** |
| "Android 8.0 or later" | **True** (`minSdk 26`). |
| "The in-app updater downloads newer builds and keeps saves across updates" | **Not verified.** `Updater.kt` exists; not built or run. |
| "The `Build APK` workflow builds it on every push" | **True** (`android.yml`). |
| "contains no RimWorld code or assets ... All art is drawn procedurally" | **Consistent with what was read**; see 2.2 for names. |
| "Download the APK" link | **Stale repo name** (X2). |

## 5. Existing tests

**Totals (2026-10-12):** 451 tests in 27 test files. `GameTest.kt` alone holds 111. Per-file counts below are `@Test` annotations, and they add up to the 451 cases the build reported.

| File | Tests | Covers |
|---|---|---|
| `GameTest.kt` | 111 | Several classes: pathfinding, terrain, room heat, solar power, fire spread, health, colony, combat, animals, trade, scenarios, crafting, caravans, factions, life stages, rules, materials, tutorial, ransom |
| `HealthFidelityTest.kt` | 65 | Health and medicine formulas (phase 5) |
| `IncidentTest.kt` | 29 | Eligibility, cooldowns, channels, seeded selection, per-incident effects, save of the history |
| `BaseContentTest.kt` | 16 | Turrets, mortar, deep drill, crematorium, sculpture, brewing, nutrient paste, hidden content |
| `CombatPipelineTest.kt` | 20 | Melee reach, range, warmup, cooldown, cover, walls, interception, skill, armour, vital parts, determinism of a fight |
| `TemperatureRoomsTest.kt` | 18 | Heat exchange between rooms, room stats (phase 4) |
| `QuestTest.kt` | 17 | Camp and rescue quests, captives, saves |
| `SaveSessionTest.kt` | 17 | Play-time counting, named saves, autosave recovery, menu flows |
| `SaveStoreTest.kt` | 19 | RNG matches `java.util.Random`; round trips; corruption handling (`RngTest` class) |
| `BacklogFixTest.kt` | 14 | Regression fixes: caravans, prisoners, breaks, beds, floors, sappers, faction drift, raid spacing |
| `MoodSocialTest.kt` | 14 | Loneliness, jokes, arguments, compliments, spouse separation, hiding, sofa |
| `ReservationTest.kt` | 11 | Exclusive claims, multi-claims, cleanup, hospital beds, saves |
| `SaveRestoreTest.kt` | 11 | A loaded game continues exactly as the original; jobs and claims restored |
| `TimeModelTest.kt` | 11 | Time base and tick conversion |
| `ItemsTest.kt` | 10 | Quality and condition through stock, caravans, trade, saves |
| `JobAiTest.kt` | 10 | Urgent hunger and rest, bed release, reservation races, unreachable memory, interrupted hauls |
| `SaveFormatTest.kt` | 10 | Blob storage, damage and recovery, version-1 upgrade |
| `MapGenTest.kt` | 9 | Map generation |
| `NeedsMoodFidelityTest.kt` | 8 | Needs and mood numbers (phase 3). Asserts the break thresholds 0.35, 0.20 and 0.05, which are V-sourced |
| `RegionTest.kt` | 5 | Reachability agrees with a breadth-first oracle |
| `PerformanceEquivalenceTest.kt` | 5 | Optimised searches match simple versions; same seed gives the same save bytes |
| `SuppressionTest.kt` | 5 | Suppression (a Stillmere design; vanilla has a suppression need too, see 3.11) |
| `BodyPartsTest.kt` | 3 | Fingers and toes affect grip and walking |
| `LegacySaveMigrationTest.kt` | 3 | Loads a real pre-change save |
| `TrainingTest.kt` | 4 | Animal tricks (a Stillmere mechanic, see 3.10) |
| `PerfBenchTest.kt` | 4 | Benchmarks, mostly disabled |
| `UpdatePolicyTest.kt` | 2 | Build-number parsing for updates |

**Coverage gaps (updated 2026-10-12).** The 2026-10-09 list is partly out of date: `NeedsMoodFidelityTest` and `TemperatureRoomsTest` now test some of those numbers. Still untested: the joy curve, skill decay, the raid-point formula, weapon stats, armour and cover formulas, research cost, plant growth factors, and any number in `source-constants.md`. No tests exist for `app/`. The tests that assert numbers use Stillmere's own values or V-sourced ones, not the decompiled source.

## 6. DLC-sourced and flagged content

Per `CLAUDE.md`: flagged, not deleted. Source checks are from 2026-10-12. Where the source has no DLC gate in the file read, that is noted. Defs are XML, so a missing gate in code does not settle the origin.

| Item | Why flagged | Source check (2026-10-12) | Status |
|---|---|---|---|
| Growth moments at ages 7, 10, 13 (`Life.kt growthMoment`) | recalled as a Biotech mechanic | `RimWorld/GrowthUtility.cs:56` sets the ages; the growth-tier code is Biotech-era. No `ModsConfig` gate in the file read | **Still flagged** (likely Biotech) |
| `Incident.AURORA` | cannot confirm it is Core | `IncidentWorker_Aurora.cs`, `GameCondition_Aurora.cs`, `ThoughtWorker_Aurora.cs`, `JobDriver_Skygaze.cs` exist. No `ModsConfig` gate in those files; the condition def is XML | **Unresolved**; not gated in the code read |
| Recently added furniture: sofa, bookshelf, wardrobe, coffee table | cannot confirm they are Core | furniture definitions are XML; not checked | unchanged (?) |
| `BREWERY` building | not recalled from Core | `RimWorld.BaseGen/SymbolResolver_Interior_Brewery.cs` is a base-layout resolver with no DLC gate. The building def is XML | **Unresolved** |
| `LAVISH_COOKING` research | not recalled from Core | no match in source; research defs are XML | Unresolved |
| `Trait.SOCIABLE` | not recalled as Core | no match in source; traits are XML | Unresolved |
| `Trait.CARNIVORE` | not recalled as Core | `Carnivore` appears only in food-restriction code (`RimWorld/FoodUtility.cs`), not as a `TraitDef` | Unresolved; the trait name is not confirmed |
| `Trait.CREATIVE` | not recalled as Core | no match in source | Unresolved |
| `W_LEVER` (lever-action rifle) | believed Core | no match in source; weapons are XML | Unresolved; believed Core, not confirmed |
| `PSYCHITE_TEA` (hidden) | believed Core | `Verse/BackCompatibilityConverter_0_18.cs:44` maps the old `PsychiteTea` def, so the item existed in an early Core release | **Probably Core; hiding it is probably wrong** (not changed) |
| Ambrosia and luciferium (not in Stillmere) | recalled | `IncidentWorker_AmbrosiaSprout.cs` and `Recipe_AdministerIngestible.cs` reference them; neither file has a DLC gate | Unresolved; do not add until the defs are checked |
| Animal "tricks", "Lonely" thought, colony animal cap | "invented, not DLC" | Suppression is a vanilla need in source, so it is not invented. Vanilla trainables are XML-driven, so the tricks are not vanilla in form. "Lonely" and the cap were not checked | Partly corrected; see 3.5, 3.10, 3.11 |

Biotech-gated code found in source: the colonist age factor in raid points (`RimWorld/StorytellerUtility.cs`, `ModsConfig.BiotechActive`). It is excluded from Core.

## 7. Top 10 gaps

1. ~~**Time base (24,000 vs 60,000 ticks per day)**~~ **Done** (`7e402c9`): 60,000 ticks per day. Source confirms it (3.1, S).
2. **Map: 100 x 100 against 250 x 250 default (S), no roofs, grass, caves or ruins.** Roofs and the outdoor-temperature rule are the largest structural gap. (3.2, 3.3)
3. **Needs and mood:** food and rest match source. Joy drain is flat where vanilla varies by level. Comfort, beauty, indoors and outdoors needs are missing. Break thresholds match, and break frequency is close per day (minor about 1.7x vanilla). (3.5)
4. **Combat is custom:** accuracy, cover, armour and melee all follow different rules from vanilla source. Vanilla also has a suppression need. (3.11)
5. **Raid points:** Stillmere's wealth term is 1.75x to 2.5x vanilla at 400,000 to 1,000,000 wealth. (3.13)
6. **Skills:** passion base 1.0 against 0.35; XP curve differs (2,500 against 1,000 at level 0); no decay (vanilla decays from level 10); no daily cap. (3.6)
7. **Health:** tended bleeding keeps 6% against vanilla's 0; clotting model differs; immunity figures are XML. (3.4)
8. **Production numbers:** research rate about 12x vanilla per pawn-day; fire is a single intensity; plant growth lacks vanilla's curves; meat food poisoning is 1% against 2%; the deep drill cannot find ore. (3.8, 3.9, 3.14)
9. **Compiler warnings:** 20 "always true or false" conditions in `sim/src/main` (section 1.1). Rule violations R1 to R3 are fixed.
10. **Names and strings copied from RimWorld, plus a committed signing key.** Needs your decision before more content is added. (2.2, X1)

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
| 7 | `phase-7-map-rooms` | 250 x 250 maps, roofs and collapse, grass, caves, ruins, heat exchange; fix the deep drill | performance budget on phones |
| 8 | `phase-8-threats` | Raid points curves and caps, storyteller cycles, raid kinds, infestations | none |
| 9 | `phase-9-economy` | Research costs, recipes, building costs, power, crops, spoilage | none |
| 10 | `phase-10-world-ui` | World size, quests, trade, named policies and areas, UI parity | none |

**Source notes (2026-10-12).** Phase 3 (needs) now has concrete targets from source: the joy curve by level, the four missing needs and their thresholds, and the break-frequency gap. Phase 4 (skills) can use the vanilla XP curve, passion factors, daily cap and decay table directly. Phase 6 (combat) can be built from the formulas in 3.11. Phase 9 (economy) should confirm the research cost before changing the 12x rate.

Open questions blocking Phase 1: the target `<VERSION>`; whether proper names count as "text strings" (2.2); what to do with the
committed signing key (X1); whether to hold the invented mechanics (tricks, "Lonely"; suppression is a vanilla need, see 3.11) until after the vanilla ones land.

## 9. What this audit did not cover

* `app/` was not compiled or run. `Sprites.kt`, `GameView.kt` and `WorldUi.kt` were not read in depth.
* `SaveGame.kt` and `SaveStore.kt` were read for structure, not line by line. The save format has no vanilla counterpart to compare.
* The decompiled repo has no XML `Defs`. Anything that lives there stays **V**, **M** or **XML** (see the tag key). That covers item and building costs, work amounts, weapon stats, biome, ore and tree tables, trait and backstory tables, storyteller weights, incident frequencies, research costs, quest scripts, faction tables, scenario definitions and most stats.
* Not compared, even with source: storyteller and incident weights, the social part of trade prices, faction goodwill rules, travel timing on the world map, the medicine and surgery success formula, cooking and food poisoning beyond the meat chance, UI rendering, and the in-app updater.
* DLC names were checked only where section 6 lists them. Other DLC-era names were not checked.
* Section 11 is the final verdict table for the systems covered.

## 10. Changes since the audit (2026-10-10)

Made on branch `ccr-401b8c33-djvmwb`. Sources for the new values are in `needs-mood.md`.

* **Phase 3, partly:** mental-break thresholds set to 0.35, 0.20 and 0.05 of mood. Human hunger rate set to 1.6 nutrition per day. Starting food in every scenario was scaled so each opening lasts as long as before. Both new numbers were **V** (secondary sources) and are now also **S** (source; see section 3.5). The per-check chances, the trait multipliers and the scaling factors are Stillmere's own.
* **R3, fixed:** every `Math.sin`, `Math.cos`, `Math.pow`, `Math.hypot` and `atan2` in `sim/` is `StrictMath`.
* **README:** counts corrected to 72 buildings and 57 research projects. Removed "caves", changed "six ores" to five, and removed butchery from the bill list.
* **Tests:** 451 pass, up from 443. Eight are new, in `NeedsMoodFidelityTest`. One existing test changed: the baby feeding check in `LifeTest` now restocks milk and watches a full day, not half. Adults eat milk, so the old stock was gone before the baby needed it.
* **Not done, and why:** the version pin (`CLAUDE.md` still says `<VERSION>`; the decompiled source is build 1.6.9438, a candidate), the naming decision (2.2), the signing key (X1), the rest and joy drains (source now available, section 3.5, not changed), the four missing needs (source now available, not implemented), and drug tolerance (not implemented).
* **Compiler warnings:** section 1 said three, all in tests, plus two in `Game.kt`. As of 2026-10-12 there are 23: 20 in `sim/src/main` and 3 in tests (section 1.1).
* **Corrected 2026-10-12:** the section 1 and 5 counts, the warning count, section 3 tags (sources in `source-constants.md`), sections 4, 6, 7 and 9, and section 3.17's save version (25, not 24).
* **Source pass, 2026-10-12:** every section now has source checks where the decompiled 1.6 source covers them. See section 11.

## 11. Source-checked coverage pass (2026-10-12)

**Source:** the public decompiled tree `Chillu1/RimWorldDecompiled`, commit `2d50803`, build `1.6.9438.38202`. Every vanilla number cited here is in [`source-constants.md`](source-constants.md), with its file and line in that repo. The decompiled repo has no XML, so XML-only values stay unverified. Stillmere values are from the code on this branch. No code or RimWorld text was copied into Stillmere; only numbers and source locations.

This section is the final state of the audit for the systems it covers. It replaces the first pass of 2026-10-12.

### 11.1 Verdicts

| # | Area | Report section | Verdict | Main reason |
|---|---|---|---|---|
| 1 | Time base | 3.1 | Matches | Day, hour, season and year lengths all match |
| 2 | Map and world size | 3.2, 3.15 | Divergent | Colony map 100 against 250 default; ambush maps far smaller; planet options 30, 50 and 100% |
| 3 | Temperature and rooms | 3.3 | Partial | Daily swing 7 and setpoint 21 match; room equalization and the outdoor rule differ; no roofs |
| 4 | Health | 3.4 | Divergent | Tended bleeding keeps 6% against vanilla's 0; clotting timing differs |
| 5 | Needs | 3.5 | Partial | Food and awake rest match; joy is flat where vanilla varies by level; four needs missing |
| 6 | Mood and breaks | 3.5 | Partial | Threshold fractions match; per-day break rates are close; the thought list is XML |
| 7 | Skills | 3.6 | Divergent | Passion base, XP curve, daily cap and decay all differ or are missing |
| 8 | Work and jobs | 3.7 | Not comparable | Work types and amounts are XML; structure only |
| 9 | Construction, power, fire | 3.8 | Partial | Construction and battery mechanics comparable; fire model differs; building values are XML |
| 10 | Plants and food | 3.9 | Divergent | Growth model differs; meat food poisoning is 2% in vanilla against 1% |
| 11 | Animals | 3.10 | Not comparable | Taming and training partly sourced; species and chances are XML |
| 12 | Social and life stages | 3.12 | Partial | Year, growth ages and bond checks match; opinion scale differs; gestation is XML |
| 13 | Threats and raids | 3.13 | Divergent | Wealth term 1.75x to 2.5x vanilla at high wealth; caps and days-passed curve missing |
| 14 | Research | 3.14 | Divergent | Rate about 12x vanilla per pawn-day; project costs are XML |
| 15 | Combat | 3.11 | Divergent | Accuracy, cover, armour and melee all differ; vanilla also has suppression |
| 16 | World and caravans | 3.15 | Divergent | Map size differs; travel timing not directly comparable |
| 17 | Trade | 3.15 | Partial | Buy price close (1.35 against 1.4); sell lower (0.5 against 0.6) |
| 18 | Quests and factions | 3.15 | Not comparable | Quest scripts and faction tables are XML |
| 19 | UI | 3.16 | Partial | Speeds 1x, 3x and 6x match; no ultra-fast; Quests and Factions tabs missing |
| 20 | Scenarios and saves | 3.17 | Not comparable | Scenarios are XML; starting colonists match (3); no save-format counterpart |

**Total:** 1 matches, 7 partial, 8 divergent, 4 not comparable from this repo.

### 11.2 Biggest differences

1. **Colony map:** 100 x 100 against vanilla's 250 x 250 default (`RimWorld.Planet/WorldInfo.cs:24`).
2. **Research rate:** about 12 times vanilla's points per pawn-day. Project costs decide the final answer.
3. **Skills:** passion base 1.0 against 0.35; XP curve 2,500 against 1,000 at level 0 and 10,100 against 30,000 at level 19; no decay from level 10; no daily cap.
4. **Raid points:** the wealth term is 1.75x vanilla at 400,000 and 2.5x at 1,000,000.
5. **Combat:** accuracy, cover, armour and melee all follow different rules from vanilla source.
6. **Joy drain:** flat 0.42 per day against vanilla's 0.24 to 0.6 by level.
7. **Tended bleeding:** 6% residual against vanilla's 0.
8. **Fire:** a single intensity against vanilla's size model.
9. **Plant growth:** a flat speed-up and hard cut-offs against vanilla's curves.
10. **Missing needs:** comfort, beauty, indoors and outdoors.
11. **Meat food poisoning:** 1% against vanilla's 2%.

### 11.3 Confirmed by source

* Ticks per day, hour, season and year (`RimWorld/GenDate.cs`).
* Food fall of 1.6 per day, and awake rest fall of 0.95 per day.
* Mental-break threshold fractions (1, 0.5714 and 1/7 of the break stat).
* Diurnal temperature amplitude 7, and setpoint 21.
* Growth moment ages 7, 10 and 13 (DLC status still open).
* A year of 60 days, and 3 starting colonists by default.
* Trade's buy and sell factors are close to vanilla's, but not the same.

### 11.4 Mental-break frequency

Expected breaks per day while mood is below the threshold:

| Band | Stillmere | Vanilla | Ratio |
|---|---|---|---|
| Minor | 0.43 | 0.25 | 1.7x |
| Major | 1.15 | 1.25 | 0.9x |
| Extreme | 2.9 | 2.0 | 1.4x |

Stillmere checks once per 625 ticks (96 checks a day), at 0.45%, 1.2% and 3%. Vanilla checks every 150 ticks (400 a day), with mean times of 4, 0.8 and 0.5 days (`Verse.AI/MentalBreaker.cs:22-44`, `:247-306`). Vanilla also waits for 2,000 ticks below the threshold and 15,000 ticks since the last recovery. The code read for Stillmere did not show those two delays. The frequencies are close, so the difference lies mainly in the missing delay and recovery spacing.

### 11.5 Corrections to the earlier audit text

* **Suppression** is a vanilla need (`RimWorld/Need_Suppression.cs`). Calling it an invention was wrong.
* **Training:** vanilla's trainables include Obedience, Release, Tameness, Comfort, Dig, Forage and AttackTarget, among others. The earlier list (obedience, release, rescue, haul) was not right.
* **Skill decay** exists in vanilla, from level 10. The earlier note said so; the row now carries the numbers.
* **Psychite tea** is probably Core: the source keeps its old def name. Hiding it is likely a mistake.
* **Work types:** the "matches the vanilla order" claim cannot be checked, because the list is XML.
* **Tests:** section 1's "no test asserts a vanilla number" is out of date; see section 5.
* **Compiler warnings:** 23, not 3 plus 2 (section 1.1).
* **Save format:** version 25. Section 3.17 said 24.

### 11.6 What the source cannot settle

* **XML-only:** item and building costs, work amounts, weapon stats, biome, ore and tree tables, trait and backstory tables, storyteller and incident weights and frequencies, research costs, quest scripts, faction tables, scenario definitions, most race and stat values (for example the base mental-break stat of 0.35, gestation periods and carry capacity).
* **XML names:** Sociable, Creative, the lever-action rifle, lavish cooking, and whether a brewery building is Core.
* **Unresolved DLC origin:** Ambrosia, luciferium, the Aurora condition and the Brewery layout. No DLC gate was found in the files read, but the defs are XML.

### 11.7 Recommended order (updated)

1. **Phase 4 (skills):** can now be specified from source: XP curve, passion factors, daily cap and decay. Old saves store skill XP, so this needs a migration. Save version 25 already has migration code for ticks, so a skill migration would be new work.
2. **Phase 6 (combat):** can be rebuilt on the formulas in 3.11. Weapon values still need the XML.
3. **Phase 3 (needs):** joy drain by level, and the four missing needs. Break frequency is close to vanilla, but the delay and recovery spacing are missing.
4. **Phase 9 (economy):** confirm the research cost against vanilla's XML before changing the 12x rate.
5. **Phase 7 (map):** 250 x 250 is confirmed as the default. Roofs are the largest structural gap.

### 11.8 Open items

* **Version pin:** the decompiled source is build 1.6.9438. Pinning it is your decision; `CLAUDE.md` still says `<VERSION>`.
* **Git identity:** the repo config is `Claude <noreply@anthropic.com>`. Commits use the teamomuito identity from `CLAUDE.md`, passed on the command line. The repo config has not been changed.
* **Compiler warnings:** 20 in `sim/src/main` to review (section 1.1).
* **Psychite tea:** consider un-hiding it. Not changed.
* **README download links** still point at `teamomuito/rimworld` (X2).
* **Signing key** is still committed (X1).
* **Per-system docs** (X4): time, health, needs-mood and temperature-rooms exist. Skills, combat, threats, plants, research and social do not.

### 11.9 What changed in this pass

* `docs/fidelity/gap-report.md`: sections 1, 2.3, 3, 4, 5, 6, 7, 8, 9, 10 and 11 updated.
* `docs/fidelity/source-constants.md`: the skill decay row corrected, and section 16 added for the constants found in this pass.
* No simulation code changed. `./gradlew :sim:test` ran and passed: 451 tests.

