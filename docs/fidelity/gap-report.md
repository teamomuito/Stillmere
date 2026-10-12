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
| R3 | **Platform-dependent floating point.** `Math.sin`/`Math.cos` are allowed to differ by 1 ulp between JVM and Android runtime. They feed map generation (lake position), drop-pod landing, and the outdoor temperature curve. A seed could generate a slightly different map on a phone than in the unit tests. **Fixed.** Every `Math.sin`, `Math.cos`, `Math.pow`, `Math.hypot` and `atan2` in `sim/` is now `StrictMath` (`GameMap.kt`, `Events.kt`, `Game.kt`, `Caravans.kt`, `WorldMap.kt`). The outdoor temperature curve was already `StrictMath` in `Climate.kt`. | 2 |

Everything else checked is clean: no wall-clock reads in the tick loop, one seeded `Rng` (same LCG as `java.util.Random`, state saved
and restored), and the two `shuffled(java.util.Random(...))` calls are seeded from the sim RNG or the world seed.

### 2.2 Names and text copied from RimWorld

**Status: resolved for labels and text, with open items (round 7).** Branch `ccr-41984ede-stlmp4`, working tree, not committed. The name check in verify finds no old RimWorld label in a code literal (names.md section 8). The old names live only in `docs/fidelity/names.md`, the legacy test data, and `LegacyNames.kt` as CRC-32 values (names.md section 4). Open items remain for the owner: generic labels kept on purpose (names.md section 7 and open item 5), enum identifiers that echo ordinary words (names.md open item 9), and borderline near-copy pairs (names.md open item 16). The mapping, the rules and the save handling are in [names.md](names.md).

* **Creatures, drugs, plants, materials, food, research, buildings, weapons, apparel, traits, backstory titles, thought labels, skills, hediffs and events** that RimWorld coined or uses in a distinctive form: renamed in original wording (names.md sections 6 to 6.4).
* **Scenarios and the difficulty label** that echoed a RimWorld storyteller or preset: renamed (names.md section 6).
* **Enum constants.** Constants that echoed a RimWorld label phrase were renamed in place, so each keeps its ordinal (names.md sections 5, 5.1 and 5.2). Round 7 renamed 26 identifiers (section 5.2). `app/` received the same renames but cannot be compiled here (no Android SDK).
* **Saved text:** backstory titles, thought labels, the temporary event name, one incident name and the pirate camp texts (site names and pending-fight labels) are saved as text or by name. Thought labels and the temporary event name are translated on load for saves below version 30, backstory titles for saves below version 31, and the pirate camp texts for saves below version 28 (`LegacyNames`). Graveyard entries, log lines and caravan last-event notes are kept as saved and are never translated (rounds 9, 11 and 12; names.md section 2). The old names are stored as CRC-32 values, so they are not written in code. The save version is now 31 (names.md sections 2 to 4).
* **Kept on purpose, UNVERIFIED:** ordinary words, real-world object, animal, biome, disease and weather names, and the occupations of the backstory titles. Several may also be RimWorld labels. Names.md section 7 lists them.

**Open items (see names.md section 9):**

1. **Not committed.** The change set and the untracked fixtures in `sim/src/test/resources/legacy/` (including the new `colony_v29.sav`) are not committed yet.
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
**Files:** `GameMap.kt` (`generate`, `generateFor`), `Noise.kt`, `Defs.kt` (`Terrain`, `RockType`, `Ore`, `Biome`, `PlantType`), `Game.kt` (`MAP_SIZE`, `findStartSpot`), `Animals.kt` (`populateWildlife`).

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| Colony map size | 250 x 250 for new colonies (`Game.MAP_SIZE`); saves keep the size they were written with (100 x 100 for older saves) | 250 x 250 default; 200 small, 300 large. M, UNVERIFIED: round 9 found only third-party guides about RimWorld Odyssey, which is DLC, not base game, and a forum comment | none for the default; no size choice is built | C for 250 x 250 (owner's map size decision); vanilla values M, UNVERIFIED |
| Battle map size | 44 x 44 | ? | | ? |
| Biomes | 6 | Core has more (12 recalled, including swamps, ice sheet, extreme desert, sea ice) | 6 missing | M |
| Rock types | 5 (granite, marble, limestone, sandstone, slate) | 5 | none | M |
| Ore kinds | 5 (steel, silver, gold, hardalloy, components) | steel, silver, gold, hardalloy, jade, uranium, compacted machinery recalled | at least 2 missing | M |
| Ore vein layout | 8 steel veins (11 cells each), 3 silver, 2 gold, 2 hardalloy, 3 components (6 or 4 cells) | ? | | C |
| Soil fertility | soil 1.0, rich soil 1.4 | same | none | M |
| Trees | 8 kinds, density by biome | many more kinds | partial | M |
| Terrain types | 10 | more, with several stone floors and water types | partial | M |

**What exists:** value-noise elevation/fertility/wetness, rivers (65% of seeds, or from the world tile), a lake when the world tile
borders water, mountains by an elevation threshold tuned by the world tile's hilliness, ore veins in rock, forests by biome,
berry bushes, feral mirewort, brambles, wildlife herds, a flat start area.

**Missing:**
* **Grass** (animals graze on any fertile, unroofed cell), flowers, bushes, most wild plants.
* **Caves** (mountains are solid rock; `JobDrivers.kt:444` states cave-ins are not modelled), **ancient ruins**, steam geysers, map-edge features.
* **Roofs.** Only a natural roof flag exists. There is no roof designation, no building of roofs, and no roof collapse.
* **Ore as vanilla places it:** the subsoil auger reads the ore under its own cell, but ore only exists inside rock, which cannot be built on. See 3.9 (defect).
* Rock chunks on the surface, mining yields and mine work are custom (`Ore.work` 1000 to 2200, plain rock 1000).

### 3.3 Temperature and rooms

**Status: Partial** (was Divergent). Details and sources: `docs/fidelity/temperature-rooms.md`. **Priority 1.**
**Files:** `Climate.kt` (outdoor temperature, `Thermal.step`), `GameMap.kt` (`rebuildRooms`, `buildHeatLinks`, `tempAt`), `Rooms.kt` (`computeRoomStats`, `RoomRules`), `World.kt` (`roomClimate`), `Game.kt` (`outdoorTemp`), `Mood.kt` (`comfortTick`), `Pawn.kt` (`comfyMin/Max`).

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| Comfortable range | 16 to 26 C, shifted by apparel insulation | 16 to 26 C | none | M |
| Temperature granularity | per room, with heat links to neighbouring rooms and the outdoors | per room (every cell of a room shares one temperature) | **none** (a per-cell grid was not built: it would be less faithful) | M, UNVERIFIED (search summary, no URL recorded) |
| Day/night swing | +/-7 C (+/-10 in desert, arid), coldest 03:00, warmest 15:00 | ? | | UNVERIFIED |
| Scorching spell / deep chill offset (vanilla event names: names.md section 6.3) | +16 / -17 C | ? (recalled near +17 / -20) | | UNVERIFIED |
| Smoke pall offset (vanilla event name: names.md section 6.3) | -13 C for 6 days | ? | | UNVERIFIED |
| Heat exchange | `0.048 * sum(edge conductance) / cells` of the gap per slow tick; wall 1.0, door 3.0, rock 0.3 | doors, vents and missing roof speed it up; rates not found | model differs in detail | M, UNVERIFIED (qualitative part from a search summary with no URL recorded; rates UNVERIFIED) |
| Wall material insulation | none (all materials equal) | none recalled | | M |
| Heater / cooler | +14 / -14 heat units, only below / above 21 C | ? | | UNVERIFIED |
| Rooms | flood fill; walls, doors and rock separate; a door splits rooms and belongs to none; indoor if it does not touch the map edge and has at most 700 cells, or a mostly natural-roofed majority | a room that is 75% roofed or less is outdoors | **Divergent** (no roofs) | M, UNVERIFIED (search summary, no URL recorded) |
| Room stats | beauty, cleanliness, wealth, impressiveness, role (9 roles); constants in `RoomRules` | impressiveness from wealth, beauty, space, cleanliness with named stages | structure matches, all numbers custom | M, UNVERIFIED (structure, from a search summary with no URL recorded); C (numbers) |

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
| Pain and pain shock | Matches (changed) | Shock threshold 0.80; The Tender frame trait lowers it by 0.50 instead of multiplying pain. The pain-to-consciousness curve is `UNVERIFIED`. |
| Downed state | Matches (changed) | Down when consciousness < 30%, moving ≤ 15%, or pain at the threshold; standing up is the exact negation. |
| Bleeding and clotting | UNVERIFIED | Death at 100% bloodLoss agrees. Rates, tended residual, clotting and the staged blood-loss hediff are unconfirmed or absent. |
| Wound infection | UNVERIFIED | Qualitatively consistent with wiki anecdotes. No figures confirmed. |
| Illness speed | Gap | The chest cough illness (vanilla flu) runs about 3.5 times faster than the wiki's figures (`SECONDARY`). Left alone; illness tuning was out of scope. |
| Scars, lost limbs | UNVERIFIED | Scars are free and permanent (I recall vanilla scars keeping some severity). |
| Implants (limb crafting research) | UNVERIFIED | Efficiencies unconfirmed. "Gaff grip" may not be a base-game item. |
| Tend quality | Matches (changed) | Doctor-stat curve, ×0.7 self-tend, ±25% luck, clamp to the medicine's cap. Leaf poultice potency, the leaf poultice cap, the no-medicine values and the +0.10 bed offset are `UNVERIFIED`. About double the old tend quality for skilled doctors. |
| Medicine items | Partial | The strongest item (sterile dressing, `MEDS_INDUSTRIAL`) agrees. Leaf poultice is `UNVERIFIED`. Glitterworld medicine is missing. |
| Healing rates, beds | UNVERIFIED | Kept as the author's tuning. Infirmary-cot healing, infection and tend bonuses are all unconfirmed. |
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

**Status: Divergent, with phase 3 started** (hunger and break thresholds now match sourced values; see `needs-mood.md`). **Priority 1.**
**Files:** `Mood.kt` (`moodUpdate`, `breaksTick`, `startBreak`), `Game.kt` (`pawnTick` need drain), `JobDrivers.kt` (`driveEat`, `driveSleep`, `driveJoy`, `applyDrug`), `Pawn.kt` (`Thought`, `addThought`), `Body.kt` (drug hediffs), `Jobs.kt` (`foodScore`, `findFood`, `startJoy`).

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| Hunger rate (adult human) | **1.6 per day** of a 0-to-1 bar (was 0.7) | **1.6 nutrition per day** | matches | V (https://rimworldwiki.com/wiki/Hunger_rate) |
| Raw plant food nutrition | 0.05 each | 0.05 each | none | V (https://rimworldwiki.com/wiki/Food) |
| Hearth stew nutrition / cost | 0.9, from 10 raw items = 0.5 nutrition | 0.9, from 0.5 nutrition of ingredients | none | V for the vanilla 0.9 meal value and the 0.5 ingredient cost (`https://rimworldwiki.com/wiki/Simple_meal`, a search result read in round 9; the page itself was not read, and the wiki version is not stated); the 10-item count is C (Stillmere recipe) |
| Rest drain | 0.95 per day awake | ? | | ? |
| Joy drain | 0.42 per day | ? | | ? |
| Mental break thresholds | **0.35, 0.20, 0.05** of a 0-1 mood (was 0.30, 0.20, 0.10); per-check chance 0.45%, 1.2%, 3% (unchanged, UNVERIFIED) | **0.35, 0.20, 0.05** (minor, major, extreme) | matches | V (https://rimworldwiki.com/wiki/Mental_Break_Threshold) |
| Mood baseline | 0.55, smoothed 50/50 with the previous value every 250 ticks | baseline 0.5 recalled; thoughts sum to a level | model differs | M |
| Break kinds | 10 (wander, berserk, food binge, insult, tantrum, fire, catatonic, run wild, drug binge, hide) | more kinds, each tied to a severity band | partial | M |
| Distinct thoughts | about 45 | well over 100 recalled | much smaller | M |
| Needs | food, rest, joy | food, rest, joy, **comfort, beauty, outdoors, indoors**, chemical needs | 4+ missing | M |
| Drugs | beer, rolled bundle, pale dust, white rush, surge tonic, dawn tablet, lucid draught (hidden) | also luciferium, ambrosia, penoxycyline recalled | 3 missing | M |
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
| Learning rate, no / minor / major passion | 1.0 / 1.4 / 2.0 | **0.35 / 1.0 / 1.5** | no-passion learns about 3x too fast | V (https://rimworldwiki.com/wiki/Passion) |
| Skill decay | none | **XP drains above level 10, faster at higher levels** | **missing** | V (https://rimworldwiki.com/wiki/Skill) |
| XP per level | `2500 + 400 * level` | ? | | ? |
| Work speed from skill | `0.4 + 0.075 * level` linear | per-stat curves | shape differs | M |
| Traits | 42, no degrees, 1 to 3 at random | around 50, with degrees (for example two levels of work speed) | partial | M |
| Backstories | 33 (11 childhood, 22 adulthood) | hundreds | tiny set | M |
| Backstory effects | skill bonuses, a few disabled work types; no trait grants, no skill penalties | all three | partial | M |

All 42 traits have some effect in code (several only add a mood thought: meat-first, machine-minded, flame-drawn). the traits labelled Easy company, Meat-first and Inventive eye (names.md section 6.1) are not traits I recognise from the base game
(**UNVERIFIED**).

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

**Status: Partial.** **Priority 2.**
**Files:** `Buildings.kt`, `Game.kt` (`placeBlueprint`, zones), `JobDrivers.kt` (`driveBuild`), `World.kt` (`powerTick`, `fireTick`, `explode`), `GameMap.kt` (`Zone`).

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| Buildings offered | 72 (76 defined, 4 legacy) | far more | partial | M |
| Wood wall | 5 wood, 160 work | wall costs 5 stuff; work **UNVERIFIED** | | M |
| Sun array | 1,700 W | 1,700 W recalled | none | M |
| Log-burning engine | 1,000 W | 1,000 W recalled | none | M |
| Breeze mill | 1,500 W constant x wind 0.15 to 1.35 | ? | | ? |
| Battery capacity | 600 per battery | 1,000 recalled | -40% | M |
| Glow panel draw | 400 W | 2,900 recalled | far too low | M |
| Hotplate, picture box, grow tray | 350, 200, 70 W | same recalled | none | M |
| Zone priorities | low, normal, preferred, important, critical | same five | **Matches** | M |
| Materials | wood, stone, steel, hardalloy for walls; wood or steel for doors | many stuff types by property | 4 only | C |
| Fire | intensity 0 to 1.2 per cell, spreads by flammability, rain 0.01 per step | fire size model | model differs | C |

**Missing:** roofs and roof collapse, cut stone by rock type, wall and door insulation, doors for power or vents, live-wire-free
power transmitters, multiple room-based work-table effects, snow clearing zones, named animal and pen areas (only 3 restriction
areas exist), a hit-point filter on zones.

### 3.9 Plants, food, cooking, spoilage

**Status: Partial.** **Priority 2.**
**Files:** `Defs.kt` (`PlantType`, `ItemType`), `World.kt` (`plantsTick`, `spoilTick`, `deteriorationTick`), `Recipes.kt`, `JobDrivers.kt` (`driveSow`, `driveCut`, `driveEat`), `Jobs.kt` (`findGrow`).

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| Crop grow days (rice, potato, corn, strawberry, cotton, mirewort) | 5.8, 5.8, 11.5, 6.5, 5.5, 8 | same recalled | none | M |
| Effective growth speed | `1 / (days * 0.55)` per day at fertility 1 | takes the full grow days | crops grow about 1.8x too fast | C |
| Corn yield / potato yield | 40 / 11 | 40 / 11 recalled | none | M |
| Rice yield | 12 | 6 recalled | 2x | M |
| Crop growth temperature | 6 to 42 C (wispsilk from 10) | 6 to 42 recalled | none | M |
| Frost kill | below -2 C, 15% per slow tick | ? | | ? |
| Spoilage (days): meat, meals, milk | 4, 4, 4 | 4 recalled | none | M |
| Spoilage: raw plants | 40 days (strawberries 10) | ? | | ? |
| Corpse rot | 2.2 days | ? | | ? |
| Rot temperature factor | none below 0 C, 0.2 at 0 C rising to 1.0 at 20 C | ? | | ? |
| Spoiled stomach (vanilla name: names.md section 6.3) | 40% if the food was over 65% rotten, 1% from any meat | depends on cleanliness and cook skill recalled | model differs | M |

**Missing:** grass and wild-plant ecology, plant fertility mapping beyond terrain, ambient light needs per crop, animal feed items
beyond fodder and feed pellets, insect jelly, chocolate, and the like. The mash works (a separate bench for beer) is **not in the base game as far as I
know (UNVERIFIED)**; vanilla uses a fermenting vessel fed with wort.

**Defect found:** a subsoil auger brings up ore from the cell it stands on, but ore only exists in rock and rock cannot be built on, so
a real subsoil auger always yields rough rocks (`World.kt productionTick`). `BaseContentTest.aPoweredSubsoilAugerBringsUpItsOre` passes
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
* 38 weapons defined. Swing repeater and lucid draught are hidden by `Catalog.kt` as "not in the base game" or "not confirmed"; **I believe both are in Core (UNVERIFIED), so hiding them may be wrong.**
* Tested well for internal behaviour (`CombatPipelineTest`, 20 tests), but against no vanilla numbers.

### 3.12 Social and life stages

**Status: Partial.** **Priority 3.** (A **DLC-sourced** candidate is flagged.)
**Files:** `Social.kt`, `Life.kt`, `Mood.kt`, `Pawn.kt` (`stage`, `opinion`, `spouse`, `lover`).

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| Year | 60 days | 60 days | none | V (https://rimworldwiki.com/wiki/Time) |
| Baby / child / teen / adult | 0 to 2, 3 to 12, 13 to 17, 18+ | ? | | ? |
| Growth moments | at 7, 10, 13 (passion, skill, trait) | recalled as a Biotech mechanic | **DLC-sourced candidate. UNVERIFIED. Flagged, not removed.** | M |
| Pregnancy | 30 days; 6% chance per day for a partnered woman aged 16 to 45 | ? | | ? |
| Old age | chronic conditions from 40 to 62; death roll from 80 | ? | | ? |
| Social interactions | 8 kinds: chitchat, insult, argument, compliment, romance, and three friendly talks (their thoughts are Candid exchange, Warm talk and Friendly ribbing) | more, including slights and marriage proposals | partial | M, UNVERIFIED |

**Missing:** relation types beyond parent, spouse, lover and opinion (siblings, cousins, ex-partners, bonded animals), weddings and
parties, social fights beyond a bruise exchange, babies' separate needs. Orientation traits gate romance (`ContentTest.orientationsGateRomance`).

### 3.13 Threats and storytellers

**Status: Divergent.** **Priority 1.**
**Files:** `Incidents.kt` (`IncidentRegistry`, `threatPoints`), `Events.kt` (`launchRaid` and all event effects), `Game.kt` (`Storyteller`, `Difficulty`, `init` timers), `Factions.kt` (`pickRaiders`).

| Constant | Stillmere | Vanilla | Diff | Conf |
|---|---|---|---|---|
| Raid points | `(17 * colonists + wealth / 95 + 6 * animal danger) * difficulty * storyteller * min(1, 0.34 + day / 34)`, floor `22 * difficulty` | **(wealth points + pawn points) x difficulty x starting factor x adaption**; buildings count at half; wealth capped at 1,000,000; points capped at 10,000 | different shape, no caps, no half-weight for buildings | V (https://rimworldwiki.com/wiki/Raid_points) |
| Difficulty threat scale | 0 / 0.6 / 0.9 / 1.5 / 2.2 | ? | | ? |
| First raid | day 8 to 12 | ? | | ? |
| Raid kinds | rush, tunnelers, shelling camp, lander drop, iron swarm (vanilla raid kinds: names.md section 6.3) | straight assault, sappers, breach, siege, drop pods, infestation, mech cluster | partial | M |
| Incident channels | 6 timers (raid, wanderer, pod, trader, temperature, misc) with 24 incident types | storyteller-specific incident cycles | model differs | M |
| Storytellers | 3, original characters | 3 in Core | structure similar, behaviour not | M |

**Missing:** wealth and pawn point curves with caps, per-storyteller cycles, raid strategies beyond the five kinds, friendly raids
(allied reinforcements exist only through comms), wanderer and refugee rules, the incident list beyond what is registered. "Aurora"
is registered (incident `AURORA`, log text now "sky curtain") and I cannot confirm it is a Core event (**UNVERIFIED**).

### 3.14 Research

**Status: Divergent.** **Priority 2.**
**Files:** `Defs.kt` (`Research`), `Game.kt` (`researchAvailable`, `startResearch`), `JobDrivers.kt` (`driveResearch`), `Jobs.kt` (`findResearch`).

* 57 projects (README says 53).
* **Cost model is invented:** `cost = baseCost * 16`, progress per tick = `workSpeed * 0.25 * bench multiplier (1.0 or 1.6) * trait`. Vanilla assigns each project a fixed point cost and a research-speed stat; I cannot compare without that data (**UNVERIFIED**).
* Several project names are not vanilla Core as far as I know (for example "Banquet cookery", "Alloy edging") (**UNVERIFIED**); the tree shape is original.
* Missing: tech levels, techprints, prerequisite rules beyond the listed dependencies, study desks' effect on speed beyond the 1.0/1.6 split.

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
| "caves" | **False.** No caves are generated. |
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

## 7. Top 10 gaps

1. ~~**Time base (24,000 vs 60,000 ticks per day)**~~ **Done** (`7e402c9`): 60,000 ticks per day. (3.1)
2. **Map: new colonies are 250 x 250 (round 6); no roofs, grass, caves or ruins**, rooms that count as indoors without a roof. (3.2, 3.3)
3. **Needs and mood model:** hunger and break thresholds now match the sourced values. Four needs, drug tolerance and the scalar mood are still missing. (3.5)
4. **Combat model is custom** end to end: weapon stats, accuracy, armour, melee, cover, no burning. (3.11)
5. **Threat generation:** raid points formula, caps, storyteller cycles. (3.13)
6. **Health depth:** 29 parts, simplified bleeding and immunity, limited chronic disease, surgery and medicine. (3.4)
7. **Skill model:** learning rates 1.0/1.4/2.0 against 0.35/1.0/1.5, no decay, thin backstories, no trait degrees. (3.6)
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

Not committed (working tree only).

* **2.2 resolved for labels and text, with open items** (see the status line in 2.2). Mapping, rules and open items are in [names.md](names.md). Enum positions are unchanged (checked by position, names.md section 8).
* **Round 7 (review of the names area).** No old RimWorld name remains in a code literal. The labels that the review listed were already renamed, and their constants were renamed in round 7 (names.md section 5.2). Labels renamed in earlier rounds, and the round 7 renames, are listed with their old and new names in names.md sections 6.1 to 6.4, which is the only place this report refers to them. The benches that were renamed are listed in names.md section 6 and section 11.
* **Saved text (version 30 to 31).** Three backstory titles were renamed in version 31 and are translated when read for saves below version 31 (names.md sections 2, 3 and 6.5). The death causes written into the graveyard were renamed for new deaths; old graveyard entries are kept as saved and never translated (round 9; names.md section 2). In version 30 (round 7), four thought labels and one temporary event name were renamed and are translated for saves below version 30 (names.md sections 3 and 6.4).
* **Tests:** 490 pass, 0 failures, 0 errors (`./gradlew :sim:test --console=plain`).
* **Round 8 (review of round 7).** Graveyard causes were translated for saves below version 31 (reversed in round 9); three backstory titles were renamed (version 31); the V rows the review named have URLs or new tags; the old labels in this report were removed; `README.md` no longer named the AI model (restored in round 9). Tests: 499 pass, 0 failures, 0 errors. Round 10 gave every V cell a URL or changed it to M, UNVERIFIED (names.md section 10). Nothing is committed.
* **Round 9 (review of round 8).** Graveyard entries are no longer translated: an old entry is kept as saved (names.md section 2; the read site in `SaveGame.kt`). `README.md` line 12 is restored to the committed sentence that says the project was built with Claude (names.md decision 7). The three backstory titles stay renamed; the owner decides whether they should be (names.md section 9, item 10). A health test name that echoed a Stillmere label was renamed (names.md section 5.3). The hearth stew and outdoor-temperature URLs were checked by a search in round 9. The map size vanilla values are M, UNVERIFIED. Round 9 test count: see names.md section 13.
* **Not compiled:** the `app/` module (no Android SDK here). The renamed constants were applied to it by the same rule, and a grep finds no old identifier in `app/`.
* **Round 11 (verdict on round 10, and the names-fix task).** The graveyard and log-line decisions are stated in the goal (names.md section 0) and in section 2. The log lines are kept as saved, and the claim that they are not saved is withdrawn. The save code has a comment at each read site. A test checks that an old graveyard string loads unchanged. The V cells of the four fidelity docs were re-checked by script: each has its URL in the row. The social-thought test was renamed to match its label ("Friendly ribbing"). The decisions on the titles, the README, the scope, the graveyard and the log lines are for the owner to confirm (names.md section 9). Nothing is committed.
* **Round 12 (review of round 11, findings A and B).** The lower-case adult titles of T13 and T14 have keys (T20 and T21), so an old save that held either one loads the new adult title. The pirate camp texts saved in site names and pending-fight labels are translated for saves below version 28 (S01-S03). Caravan last-event notes are kept as saved, as a record of a past battle. The decisions on the camp texts and the last-event notes are for the owner to confirm (names.md items 26 and 27; section 16). Tests: 507 pass, 0 failures, 0 errors (`./gradlew :sim:test --console=plain`). Nothing is committed.
* **Other docs.** Old RimWorld names in this report were replaced with references to names.md (the temperature event offsets, the food hediff row, the raid kinds and the quoted README claim). `health.md` and `README.md` use the round 7 names as well.
