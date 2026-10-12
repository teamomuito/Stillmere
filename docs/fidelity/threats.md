# Threats and storytellers

Phase from [gap-report.md](gap-report.md), section 3.13. This round works from the decompiled reference (version
1.6.9438.38202, see [decompiled-reference.md](decompiled-reference.md), section 3.13). It closes the raid points formula
and its inputs (wealth, colonists, tamed animals), the points cap and floor, the storyteller on and off cycle for raids,
the recent-incident weighting, and the manhunter pack size (its factor, rounding and bounds). It leaves the adaptation term, the population-intent
weighting, the temperature gates, the psychic drone levels and the siege budget open, each with the reason below.

Code (`sim/src/main/kotlin/io/github/teamomuito/colony/sim/`): `Incidents.kt` (lines 294-553: the points curves,
`healthBlend` 325, `AnimalThreat` 334, `threatWealth` 337, `basePoints` 350, `clampPoints` 359, `raidPointsFloor` 366,
`threatPoints` 373, the seed mixer `mixSeed` 395 with its constants 382-410, the cycle `RaidCycle` 429, `raidCycle` 443,
`raidCycleHits` 453, `placeCycleHits` 474 with its draw count 493, `nextRaidCycleTick` 506, the recent weights 520-523,
`manhuntCount` 535 with its constants 541-553; `executeIncident` 207 and `nextChannelTime` 230 in the same file),
`Events.kt` (line 211, `manhuntCount`), `Game.kt` (line 93, `lastRandomIncidents`), `SaveGame.kt` (version 34 at line 29;
the list is written at lines 161-164 and read at lines 396-399 and 480).

Tests: `ThreatsTest.kt` (new, 32 tests), `LegacyThreatsMigrationTest.kt` (new, 6 tests, with the version 33 fixture).

## Source key

* **D, file:line**: a value or rule read from the decompiled source tree (`/home/user/chillu1/rimworlddecompiled`, read
  only). Values are restated here, not copied.
* **C**: Stillmere's own choice, where the reference gives no value, or where the vanilla value is XML data that the
  decompile does not contain. The reason is given.
* **UNVERIFIED**: a value Stillmere uses that no source here confirms.

## Discrepancies found while reading

* **Save version.** The brief says `VERSION` is 31 at the start of this run. It is 33 (`SaveGame.kt`). This round writes
  version 34. A version-33 fixture was made from the unchanged code before the format changed (`legacy/colony_v33.sav`,
  header 33, with `legacy/colony_v33_expect.txt`).
* **Vanilla values in XML.** The decompile is C# only. The difficulty threat scales, the storyteller points-by-days curves,
  the adaptation curves, the population-intent curves, the storyteller component means and on-off days, and the animal
  combat power values are all XML data. For each, Stillmere keeps its own value (C or UNVERIFIED).
* **Floor and difficulty.** The old floor was `22 x difficulty threat`, so it scaled with difficulty. Vanilla's floor is a
  seeded value between 35 and a ceiling, applied after the difficulty scale, and does not scale with it
  (`RimWorld/StorytellerUtility.cs:98-101, 188`). This round follows vanilla, so every difficulty has the same floor. Easy
  games now get at least 35 points early on, where the old floor gave about 13.
* **Cooldowns and recent weighting.** Stillmere's hard cooldowns (disease 6 days, blight and wildfire 4 days) stay, since
  vanilla's refire days are XML. The recent-weight factors are added on top, as vanilla uses both.
* **Peaceful games.** The threat scale is 0 for Peaceful, so the raw points are 0 and the points are the floor. Peaceful
  games never reach a raid or a hunt (`channelOpen`, and the friendly-only misc pass), so the floor is never read there.
* **Wealth scale.** The vanilla curves assume market values. Stillmere's item values (for example silver 1, steel 1.9,
  `Defs.kt`) are in the same range, but nothing here confirms the scale (**UNVERIFIED**). The early colonies here are well
  below the 14,000 wealth where the wealth term starts, so until they build up, raid points come from colonists alone.

* **Health blend, the citation and the before state.** The review cited `RimWorld/StorytellerUtility.cs:28-34` for the health
  blend. Those lines are the fixed-wealth time curve, which Stillmere does not use. The blend is at :41 (the constant) and
  :173 (the lerp), and vanilla applies it to every pawn that counts inside the loop at :140-184 (the lerp sits in the
  `if (num3 > 0f)` branch at :167-173). Before this round neither colonists nor tamed animals were health-blended: the old
  `threatPoints` had no health term, only 17 per colonist and 6 times danger per tamed animal, both at full value. Both are
  blended now, the same way.
* **Names.** `globalPointsMin` and `recentRandomIncidents` were renamed to `raidPointsFloor` and `lastRandomIncidents`, because
  the vanilla names are `StorytellerUtility.GlobalPointsMin` and `StoryState`'s recent list (`RimWorld/StorytellerUtility.cs:98`,
  `RimWorld/StoryState.cs:142`). `INTERVALS_PER_DAY` is now `CYCLE_STEPS_PER_DAY` for the same reason (`RimWorld/Storyteller.cs:49`
  names it `IntervalsPerDay`). None of the new names appears in the decompile. Kotlin names only: the saved fields are unchanged.
* **Two combat power stand-ins.** The raid points count a tamed animal at 6 times its danger, and the manhunt pack count divides by
  18 times its danger, for the same kind of animal. Vanilla has one combat power per animal kind, and both uses read it
  (`RimWorld/StorytellerUtility.cs:153`, `RimWorld/AggressiveAnimalIncidentUtility.cs:107`). Both values are Stillmere's (C). They are
  not reconciled here: one value would change pack sizes, which is a gameplay change, so it is open.

## Constants adopted

| Constant | Value | Was | Source | Reference item |
|---|---|---|---|---|
| Points cap | 10,000 | none | D, RimWorld/StorytellerUtility.cs:14 (constant), :188 (clamp). Code: `POINTS_CAP`, `clampPoints` | Raid points: upper cap |
| Floor range minimum | 35 | 22 x difficulty threat | D, RimWorld/StorytellerUtility.cs:12 (constant), :98-101. Code: `POINTS_FLOOR_MIN` | Raid points: floor |
| Floor ceiling default | 70, for every difficulty | none | D, RimWorld/Difficulty.cs:18 (default), RimWorld/DifficultyDef.cs:99 (default). Per-difficulty presets are XML, so every Stillmere difficulty uses 70 (**UNVERIFIED** per preset). | Raid points: floor ceiling curve |
| Floor ceiling curve | 35 at day 12 or before, 70 at day 35 or after, linear between | none | D, RimWorld/Difficulty.cs:458-462. Code: `pointsFloorCeilingCurve` | Raid points: floor ceiling curve |
| Floor value | seeded from the game seed and the game hour, between 35 and the ceiling | none | D, RimWorld/StorytellerUtility.cs:100 (`RangeSeeded` with ticks / 2500). The seed mix is Stillmere's (**C**). The draw does not use the main RNG. Code: `raidPointsFloor`. | Raid points: floor |
| Wealth: buildings weight | 0.5 | 1.0 | D, RimWorld/StorytellerUtility.cs:16; Verse/Map.cs:422 (`WealthItems + WealthBuildings * 0.5 + WealthPawns`). Code: `threatWealth`. The map's own `wealth()` (used by mood) is unchanged. | Wealth input: buildings weight |
| Wealth to points curve | 0 to 14,000 gives 0; 400,000 gives 2,400; 700,000 gives 3,600; 1,000,000 gives 4,200; flat outside | wealth / 95 | D, RimWorld/StorytellerUtility.cs:18-25 (linear between points, flat outside: Verse/SimpleCurve.cs:139-170). Code: `wealthPointsCurve` | Wealth to colony points curve |
| Colonist points per colonist | 15 up to 10,000; 140 at 400,000; 200 at 1,000,000; flat outside | 17 flat | D, RimWorld/StorytellerUtility.cs:43-49. Code: `colonistPointsCurve` | Colonist points: base per colonist |
| Health blend, colonists and tamed animals | 65% toward a health-scaled value: each counted pawn's points x (0.35 + 0.65 x health) | none | D, RimWorld/StorytellerUtility.cs:41 (constant 0.65), :167-173 (applied to every pawn that counts, inside the loop at :140-184; the lerp is at :173). The health value is **C**: `healthFraction()` (Pawn.kt:227), the closest thing to vanilla's summary-health percent. Code: `HEALTH_BLEND`, `healthBlend`. | Colonist points: health modifiers; animal points: health |
| Tamed animal points per combat power | 0.08 per unit, for each tamed animal that is not downed, then blended by health as colonists are (row above) | 6 x danger, no factor | D, RimWorld/StorytellerUtility.cs:37 (constant), :151-153 (tamed, not downed; use), :167-173 (the health blend). The combat power is **C**: 6 x danger, the value the old formula used. Vanilla's combat power is XML, and Stillmere has no such field. So an animal gives 0.48 x danger points at full health. Vanilla counts only animals that the training system accepts for release (:151); see the rows not adopted. Code: `ANIMAL_POINTS_PER_COMBAT_POWER`, `ANIMAL_COMBAT_POWER_PER_DANGER`, `AnimalThreat`. | Raid points: total formula (animal term) |
| Manhunter ambush points factor | 0.75 | 1.0 | D, RimWorld/IncidentWorker_Ambush_ManhunterPack.cs:9 (constant), :40-43 (applied before the animal is chosen and the group made). Code: `MANHUNTER_POINTS_FACTOR`, `manhuntCount` | Manhunter pack size |
| Manhunter count: rounding | Rounded to the nearest whole animal, after dividing the ambush points by the combat power stand-in (next rows) | truncated (`toInt`) | D, RimWorld/AggressiveAnimalIncidentUtility.cs:107 (`RoundToInt` of points over combat power), RimWorld/IncidentWorker_Ambush_ManhunterPack.cs:28 (the count is made from the adjusted points). Kotlin's `round` rounds an exact half to the even whole number. Vanilla's rounding at an exact half is Unity's `RoundToInt`, which is not in the decompile, so the tie rule is **UNVERIFIED**. Code: `manhuntCount`. | Manhunter pack size |
| Manhunter count: minimum | 2 | 2 (the same clamp, written in place) | D, RimWorld/AggressiveAnimalIncidentUtility.cs:12 (`MinAnimalCount`), held by the clamp at :107. Code: `MANHUNTER_MIN_COUNT`. | Manhunter pack size |
| Manhunter count: combat power stand-in | 18 x danger | 18 x danger (no change) | **C, UNVERIFIED**. Vanilla's combat power is one value per animal kind, in XML, and is read at RimWorld/AggressiveAnimalIncidentUtility.cs:107. Stillmere's stand-in is the value the count has always used. It is not the points term's 6 x danger for the same animal (see Discrepancies). Code: `MANHUNTER_COMBAT_POWER_PER_DANGER`. | Manhunter pack size |
| Manhunter count: danger floor | danger at least 0.5 | 0.5 (no change) | **C**. Vanilla has no danger rating. The floor stops an animal of almost no danger from dividing the points by almost nothing. Code: `MANHUNTER_DANGER_FLOOR`. | Manhunter pack size |
| Manhunter count: maximum | 10 | 10 (no change) | **C, scope**. Vanilla holds the count between 2 and 100 (RimWorld/AggressiveAnimalIncidentUtility.cs:14, 107). Stillmere keeps 10 because each animal is a simulated pawn; see the rows not adopted. Code: `MANHUNTER_MAX_COUNT`. | Manhunter pack size |
| Seed mixer for seeded draws | `mixSeed`: start value 0x2545F4914F6CDD1D. For each part, xor it in, then two rounds of xor-shift and multiply (multipliers 0x3F58476D1CE4E5B9 and 0x14D049BB133111EB; shifts 30, 27 and 31). | none | **C**. Stillmere's own mix. It has the form of the 64-bit finaliser in common use, with the top bit of each multiplier cleared (a Kotlin `Long` literal cannot hold that bit); the comparison and the shifts are from memory (**UNVERIFIED**). Vanilla's seeded draw seeds the game RNG with the hour alone (RimWorld/StorytellerUtility.cs:100; Verse/Rand.cs:227-233, 445-449). So the draws here are not vanilla's sequence, and the constants are not reproduced from it. Code: `MIX_START`, `MIX_MULTIPLIER_1`, `MIX_MULTIPLIER_2`, `mixSeed`. | Raid points: floor; storyteller on and off cycles |
| Seed salts | 0x5E1A_0001 for the raid floor; 0x5E1A_0002 for the raid cycles (each cycle also mixes in its index) | none | **C**. Any two distinct values would do. Each salt keeps its draws apart from the other's and from the game seed alone. Code: `POINTS_FLOOR_SALT`, `RAID_CYCLE_SALT`. | Raid points: floor; storyteller on and off cycles |
| Recent incident weights | 0.01, 0.3, 0.5, 0.7, 0.9 for the most recent to the fifth most recent random incident. A repeat in the list multiplies each factor. | none | D, RimWorld/QuestTuning.cs:14; RimWorld/StoryState.cs:140-147 (newest at index 0, five kept); RimWorld/StorytellerComp.cs:121-129 (factor per matching index). Stillmere counts every incident; vanilla skips only Special and quest incidents, and Stillmere has none. Code: `RECENT_INCIDENT_WEIGHTS`, `recentWeight`. | Recent-incident weighting |
| Recent incident list | five entries, newest first, saved from version 34 by incident name | none | D, RimWorld/StoryState.cs:142-146 (the list and its length) | Recent-incident weighting |
| Intervals per day | 60 of 1,000 ticks, so a day is 60,000 ticks | 60,000 ticks per day | D, RimWorld/Storyteller.cs:49 (`IntervalsPerDay`), :51 (`CheckInterval` 1000). Code: `CYCLE_STEPS_PER_DAY`, `TICKS_PER_INTERVAL` | Storyteller on/off cycles |
| Cycle counts: raids per on window | a rounded random count drawn from a range | none | D, RimWorld/IncidentCycleUtility.cs:60; Verse/GenMath.cs:62-65 (rounding) | Storyteller on/off cycles |
| Cycle placement | raids at random intervals inside the on window, sorted, and pushed later until each is at least the spacing after the one before. The first raid of a cycle is spaced from the last raid of the cycle before. Up to 101 draws. If none fits, the whole window is dropped: no raid of it is kept, not even one that fell inside the window. | none | D, RimWorld/IncidentCycleUtility.cs:52-110 (the placement and spacing rule), :64-69 (the hit list is cleared before the give-up, so the window is dropped whole; the error is logged), :65 (101 draws). The arithmetic is written out here. Vanilla's bump also adds the list index, which is not reproduced (**C**). At these spacings (120 steps, against at most four hits) that term cannot move a hit past the spacing rule, so the result is the same. | Storyteller on/off cycles |
| Raid cycle, Marlowe | first cycle at day 8; on 10 days, off 6 days; 3 or 4 raids per on window; at least 2 days apart | timer of 3 to 6 days with chaos 0.2, mean 4.5 days | **C, UNVERIFIED**. Mean 3.5 raids per 16 days, one every 4.6 days. The vanilla values are XML. | Storyteller on/off cycles |
| Raid cycle, Orrin | first cycle at day 8; on 8 days, off 12 days; 2 to 4 raids per on window; at least 2 days apart | timer of 3 to 6 days at pace 1.5, mean 6.75 days | **C, UNVERIFIED**. Mean 3 raids per 20 days, one every 6.7 days. The vanilla values are XML. | Storyteller on/off cycles |
| Raid cycle, Juniper | none: the raid timer keeps the uniform chaos spacing | none | **C**. Juniper is "wild and random", so it keeps the timer. | Storyteller on/off cycles |
| Spacing after a raid | the next cycle raid is the first cycle hit at least the spacing after the time the timer was due | none | **C**. Measured from the due time, not from the hourly check, which can be up to an hour later and would skip a hit that sits exactly at the spacing. The first raid still uses the existing timer (day 8 to 12). | Storyteller on/off cycles |

## Rows not adopted, with the reason

| Reference row | Decision and reason |
|---|---|
| Raid points: total formula, the days-passed factor | **Partly adopted.** The wealth, colonist, animal and cap terms are adopted. The days-passed factor, `min(1, 0.34 + day / 34)`, is kept. It is Stillmere's own curve; vanilla's is the storyteller's `pointsFactorFromDaysPassed` (XML, `RimWorld/StorytellerDef.cs:35`). **UNVERIFIED**, C. |
| Raid points: difficulty and storyteller threat scales | Kept. Vanilla's `threatScale` defaults to 1 (`RimWorld/DifficultyDef.cs:20`) and the presets are XML. Stillmere's 0, 0.6, 0.9, 1.5 and 2.2, and Orrin's 0.6, are its own (**UNVERIFIED**, C). |
| Raid points: random factor (exactly 1 on a home map) | Not needed. A home map's random factor is exactly 1 (`Verse/Map.cs:442`), so the home-map points have none. The per-incident storyteller factor is a separate row. |
| Raid points: adaptation term (row 539) and counter growth and cap (row 540) | **Open.** The term needs the adaptation counter and the storyteller's curves for growth, points by adaptation days and colonist losses. All are XML (`RimWorld/StorytellerDef.cs:37-49`, `RimWorld/StoryWatcher_Adaptation.cs:14-81`). Only the 30,000-tick update, the half-growth rule and the cap of 100 days (`StorytellerDef.cs:39`) are in the decompile. Inventing the curves would be guessing, so this is left open. |
| Wealth input: pawn wealth term (`WealthPawns`) | **Not modelled.** Vanilla adds each colonist's market value (`RimWorld/WealthWatcher.cs:139-148`). Stillmere pawns have no market value, and vanilla's race values are XML. The wealth input is lower than vanilla by the pawns' value. Open. |
| Wealth input: fixed-wealth mode | Not modelled. The flag is off by default (`Verse/Map.cs:418`, `RimWorld/Difficulty.cs:97`) and Stillmere has no such difficulty. |
| Wealth input: off-home maps | Not applicable: the points are read on the home map only. Caravans have their own formula (`Caravans.kt:303`). |
| Colonist points: age factor and colony-mech factor (Biotech) | **Not adopted, DLC.** Biotech-gated (`RimWorld/StorytellerUtility.cs:178`, :58-64). Excluded by `CLAUDE.md`. |
| Colonist points: cryptosleep (x0.3) and colony slaves (x0.75) | **Not applicable.** Stillmere has no cryptosleep casket and no slaves (no matches in `sim/`). |
| Incident check cadence | Kept hourly. Vanilla checks every 1,000 ticks (`RimWorld/Storyteller.cs:51, 216`). The raid cycle is placed on the 1,000-tick intervals, and a raid fires at the first hourly check after its hit. |
| Channel spacing: rate-based timers vs uniform with chaos | **Kept for the other channels.** Vanilla's rate timers take their mean days from XML (`RimWorld/StorytellerComp_CategoryMTB.cs:17`). Stillmere's means and uniform spacing are its own, and changing their shape without the XML values would invent them. The raid channel uses cycles for two storytellers (above). |
| Storyteller on/off cycles: accept fraction | **Not modelled.** It is a product of curves over days, threat points and progress score, all XML (`RimWorld/StorytellerComp_OnOffCycle.cs:12-24`). Every cycle raid is kept. |
| Storyteller on/off cycles: other channels | **Not applied.** Only the raid channel has a cycle; the others keep their timers. Open. |
| Random-main: per-incident points factor (0.5 to 1.5) | **Not adopted.** Ambiguous: it applies only where a storyteller uses the random-main component with a points range, which is XML (`RimWorld/StorytellerComp_RandomMain.cs:55`). Stillmere has no such component. Open. |
| Random-main: forced threat-big after a gap | **Not adopted.** Stillmere has no threat-big category. The gap is checked at `RimWorld/StorytellerComp_RandomMain.cs:43`, and its default is 99,999 days (`RimWorld/StorytellerCompProperties_RandomMain.cs:12`), so it is effectively off unless XML sets it. |
| Cycle window end: inclusive in vanilla | Vanilla accepts a window's last hit when it sits exactly at the window's end: `RimWorld/IncidentCycleUtility.cs:77` passes start + length as the limit, and :109 accepts a last value at or below it. Stillmere's last hit is before the end, so a raid never sits in the off window. The difference is one step (1,000 ticks) per window. **Kept (C)**: the on window is the span its raids are drawn in, and the schedule tests read "raids only in on windows". |
| Population-intent weighting | **Open.** The chance curve (0.05, 0.3, 0.45; `RimWorld/StorytellerComp.cs:14-19`) and the effect factors (0.4, 0, -0.4; :86-93) are in code. The intent itself is the storyteller's population curve and a population-adaptation counter, both XML and absent here. Stillmere's incidents have no population flag. |
| Animal-join weight | **Not adopted.** Ambiguous: the size factor applies only with Ideology and a ranching issue (the size curve at `RimWorld/IncidentWorker_FarmAnimalsWanderIn.cs:15-17`; the Ideology and ranching-issue gate at :25). Stillmere's flat 1.2 is kept (C). |
| Shelling camp (siege) points budget | **Not adopted.** The budget (points times 0.2 to 0.3, floor 60; `RimWorld/RaidStrategyWorker_Siege.cs:12-15`) pays for camp blueprints. Stillmere's shelling camp is a position 30 tiles away and builds nothing, so the budget has nothing to pay for. Open with the camp work. |
| Raid kinds mapping | **Not adopted.** Ambiguous: vanilla chooses the raid strategy from several strategy workers. Stillmere's kinds (rush, tunnel, shelling, drop, iron swarm) are its own. Open. |
| Mech cluster incident (Stillmere MECH_CRASH) | **Flagged as DLC-sourced and kept.** The reference says the cluster needs Royalty (`RimWorld/IncidentWorker_MechCluster.cs:11`). Per `CLAUDE.md` it is flagged in `gap-report.md` section 6 and not deleted without the owner's decision. |
| Heat wave and cold snap temperature gates | **Open.** The gates are seasonal temperatures: 20 and above for heat (`RimWorld/IncidentWorker_HeatWave.cs:18`), above 0 and below 15 for cold (`RimWorld/IncidentWorker_ColdSnap.cs:18-20`). Stillmere's gate is a season and biome rule. It needs a per-tile seasonal temperature, which is outside this round. |
| Psychic wave drone severity | **Open.** Vanilla's level depends on points: below 800 low, below 2,000 medium, otherwise high (`RimWorld/IncidentWorker_PsychicDrone.cs:8-10, 28`). Stillmere's drone has one strength, and adding levels would invent their thought values. |
| First raid timing (classic intro) | **Not adopted.** The intro is a storyteller component (XML, `RimWorld/StorytellerComp_ClassicIntro.cs:44`) and its use is ambiguous. Stillmere's first raid stays at day 8 plus 0 to 4 days. |
| Animal points: release-trainable condition | Vanilla adds a tamed animal only if its training accepts "release" (`RimWorld/StorytellerUtility.cs:151`). Stillmere has no animal training state, so every tamed animal that is not downed counts. Which animals vanilla would leave out is **UNVERIFIED** here. Open. |
| Manhunter count: upper bound of 100 | Vanilla holds the count between 2 and 100 (`RimWorld/AggressiveAnimalIncidentUtility.cs:14, 107`). Stillmere keeps 10 (C). Each animal in a pack is a simulated pawn, so the bound is a scope decision for the sim's pawn limits, not a formula or unit difference that the reference alone can settle. Not adopted for scope. Open with the pawn limits. |
| Time base: ticks per hour and per day | Matches (2,500 and 60,000). No change. |

## Schedule of a cycle raid

For Marlowe and Orrin the raid timer is set, after each raid, to the first cycle hit at least the spacing after the time the
timer was due (`nextChannelTime`). The first raid still uses the existing timer (day 8 to 12, unchanged). A raid that is due
while another is active waits and fires once the channel reopens, as before. The cycle is a pure function of the game seed
and the cycle index (a hash of the seed, a salt and the index), so it does not use the main RNG and nothing about it is
saved: the next hit time is in `nextRaid`, as before. Only the cycles that can affect the wanted hit are walked:
`nextRaidCycleTick` starts one cycle before the target when the off window is at least the spacing (true for both
storytellers), which the test `theNextHitIsTheFirstHitOfTheWholeWalk` checks against the full walk.

## Save format: version 34

One new saved field: `lastRandomIncidents`, the five most recent random incidents by name, newest first. Written right
after the incident history, gated on version 34. Earlier saves read an empty list. Enums are unchanged (names only).

This round changes no saved field. The health blend, the manhunt count and the seed mixer are computed from the current state
and are not saved, so `VERSION` stays 34 and no new fixture is made.

## Tests

**New:** `ThreatsTest` (32): the points curves at the reference values; the floor curve; the cap and the clamp; the health
and animal terms (a tamed animal is blended by health as a colonist is); buildings at half and items at full in threat wealth; the manhunter count (rounded, not truncated, and held between 2 and 10); the floor (35 until day 12,
one value per hour, the main RNG unmoved, the same for a seed, changing from hour to hour); threat points between floor and
cap; the cycle set-up per storyteller; raids only in on windows and from day 8; per-window counts; a window that cannot fit
its raids keeps none of them, not even one inside it; the spacing; the seed
determinism; the long-run rate (Marlowe 3.5 per 16 days, Orrin 3 per 20, within 0.01 a day over 40 seeds); the cycle
schedule (the next hit; Juniper's chaos timer; a due raid moving onto the cycle; every hit raided at the first hourly check
after it; the walk shortcut); the recent weights (lowering, the list of five, repeats multiplying); and a seeded check
that a recent incident is picked far less often (500+ picks plain, at most a fifth as many after the recent weight).
`LegacyThreatsMigrationTest` (6): the version 33 fixture loads its colonists, clock, history and timers; its recent list is
empty; a version 34 save keeps a list; a version 33 write leaves the list out.

**Changed, each for a stated reason:**
* `LegacyNamesMigrationTest` (3 assertions), `LegacyNeedsMigrationTest` (1), `LegacySkillsMigrationTest` (2): the default
  write is now version 34, so each assertion of "the current version" changed from 33 to 34. The test names that said
  "ThirtyThree" or "ThirtyFour" were renamed to match: `aVersionThirtyOneSaveWrittenAgainIsVersionThirtyFour`,
  `aVersionThirtyTwoSaveWrittenAgainIsVersionThirtyFour`, `theDailyCounterSurvivesASaveAtVersionThirtyFour`,
  `theXpSurvivesASaveAtVersionThirtyFour`. The doc comment of `LegacyNeedsMigrationTest` says the current version.
* No threat value is asserted by any other existing test. `IncidentTest` (`aClosedChannelKeepsItsTimerUntilItOpens`,
  `thePeacefulRaidChannelNeverFires`) still passes: they set the raid timer by hand, and the cycle changes only what the
  timer is set to after a raid fires.

**Results:** `./gradlew :sim:test --console=plain`: 644 tests, 0 failures, 0 errors, 0 skipped. The JUnit reports give
`ThreatsTest` 32 and `LegacyThreatsMigrationTest` 6. The baseline before this round was 606. Compile warnings, all of them
older than this round: `Incidents.kt` lines 79-80 (null checks), `Game.kt` lines 467 and 581 (the same two lines at HEAD, 465
and 579), and `IncidentTest.kt` line 260.

## Checks run (not tests)

A temporary test drove the hourly channel loop for a year, with raids cleared each hour so only the schedule counts, and
was deleted afterwards:

* Raid launches per year, five seeds: Marlowe 76.8 (expected about 78 from the cycle means); Orrin 51.8 (expected about 53.5).
  Before the due-time fix Marlowe gave 58: the hourly check skipped hits that sat exactly the spacing after the last one.
  Juniper is not comparable, because its count includes its 3% hourly misc roll.
* Raid points for a five-colonist quiet enclave with wealth 1,274 (wealth points 0): day 8 38.8 (floor 35), day 12 46.8,
  day 20 62.7, day 35 and later 67.5 (floor below the raw points). The old formula gave about 57 at day 8 and about 89 from
  day 35 for the same colony (computed by hand from the old formula).

## Open after this round

Adaptation (the term and its counter), population-intent weighting, the siege budget (needs camp construction), the
temperature gates (needs per-tile seasonal temperature), the psychic drone levels, the raid strategy kinds, the pawn wealth
term (pawn market values), the storyteller's points-by-days curve (XML), and the cycles for the other channels. The mech
cluster is flagged as DLC-sourced in `gap-report.md` section 6. Also open: the two animal combat power stand-ins (6 and 18 times
danger, see Discrepancies), and the release-trainable condition for tamed animals.
