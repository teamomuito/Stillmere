# Skills, traits and passions

Phase from [gap-report.md](gap-report.md), section 3.6. This round works from the decompiled reference (version
1.6.9438.38202, see [decompiled-reference.md](decompiled-reference.md), section 3.6). It closes the learning rates, the
passion multipliers, the XP curve, the decay of skills at level 10 and above, the daily learning cap, the XP awarded per
work tick and per attack, the generation of base levels, passions and the trait count. It leaves the trait degrees, the
trait skill grants, the trait and backstory commonality data, and the growth moments, which are DLC-sourced (see below).

Code: `Skills.kt` (new: `PiecewiseCurve`, `SkillRules`; `applyXp` is a climb then a fall, and the passion budget buys a stock of
passions), `Pawn.kt` (`gainXp`, `addXp`, `skillIntervalTick`, `xpToday`, `hasSkills`, `skillDisabled`; `cap` and `pain` are a
cache of the health inputs, refreshed on read), `Game.kt` (`newHuman`, `drawTraits`, `pawnTick`, `hourlyTick`), `JobDrivers.kt`
(`doWork` and the work drivers), `Ai.kt` (`fire`, `fullCycleTicks`, `shootingXp`, the melee award), `Health.kt` (`tendPawn`,
`refreshHealthCache`, and the health marks), `Mood.kt`, `World.kt` and `Life.kt` (health marks only), `SaveGame.kt` (version 33).
`Life.kt` (growth moments, DLC) is otherwise unchanged.

## Source key

* **D, file:line**: a value read from the decompiled source tree (`/home/user/chillu1/rimworlddecompiled`, read only).
  Values are restated here, not copied.
* **C**: Stillmere's own choice, where the reference gives no value or only a rule. The reason is given.
* **UNVERIFIED**: not in the decompiled source. It is XML data, or the reference does not give it.

## Discrepancies found while reading

* **Save version.** The brief says `VERSION` is 31 at the start of this run. At HEAD it is 32 (`SaveGame.kt:23`). This
  round writes version 33. The version-32 fixture was written by the unchanged code before the format changed
  (`legacy/colony_v32.sav`, header 32, `legacy/colony_v32_expect.txt`).
* **"Decay for unused skills".** The brief says decay applies to unused skills. The decompiled interval has no use check:
  every skill at level 10 or above loses XP every 200 ticks, whether or not it was used
  (`RimWorld/SkillRecord.cs:207-249`). This round follows the code and adds no use condition.
* **Cooking citation.** The reference's bills row cites `JobDrivers.kt:1191` for cooking. That line is the butchering
  work at a carving slab (`driveButcher`, Galley skill). Meals are cooked through `driveBill` (`JobDrivers.kt:837`),
  which this round covers. Butchering is not a reference row and is unchanged.
* **Learning-rate base.** The learning factor is multiplied by the pawn's `GlobalLearningFactor` stat
  (`RimWorld/SkillRecord.cs:386`). Its base value and the trait modifiers are XML data, so the base is taken as 1
  (**UNVERIFIED**).
* **Backstory traits and clashes.** An earlier version of this doc and of `Game.drawTraits` said backstory traits skip clashes,
  as in the reference. The reference's forced-trait block checks disallowed traits and repeats, not clashes
  (`Verse/PawnGenerator.cs:1461-1492`), and `GainTrait` adds with no conflict check (`RimWorld/TraitSet.cs:258-270`). Backstory
  traits now skip repeats only, and both clashing traits are kept.
* **Draw order.** An earlier version of row "Backstory traits count toward the trait target" said the target is drawn before the
  backstory traits. The reference draws it after the forced traits (`Verse/PawnGenerator.cs:1496`), and the code always did the
  same. Only the doc was wrong.

## Constants adopted

| Constant | Value | Was | Source | Reference item |
|---|---|---|---|---|
| Learning factor, no passion | 0.35 | 1.0 | D, RimWorld/SkillRecord.cs:379 (constant at :39) | Learning rate, no passion |
| Learning factor, minor passion | 1.0 | 1.4 | D, RimWorld/SkillRecord.cs:380 (constant at :41) | Learning rate, minor passion |
| Learning factor, major passion | 1.5 | 2.0 | D, RimWorld/SkillRecord.cs:381 (constant at :43) | Learning rate, major passion |
| Order of learning factors | passion, then trait factors, then the daily cap | the same order | D, RimWorld/SkillRecord.cs:371-397 (passion switch :377-383; global factor :386; saturation :391-393) | Learning rate |
| Eager pupil factor | 1.4 | 1.4 | C, **UNVERIFIED** (XML global learning factor). Kept. | Trait skill modifiers |
| Late bloomer (slow study) factor | 0.6 | 0.6 | C, **UNVERIFIED** (XML global learning factor). Kept. | Trait skill modifiers |
| Daily full-rate XP threshold | 4000 | none | D, RimWorld/SkillRecord.cs:33 (constant), :139 (`LearningSaturatedToday`) | Daily learning cap |
| Daily factor above the threshold | 0.2 | none | D, RimWorld/SkillRecord.cs:37 (constant), :391-393 | Daily learning cap |
| Daily counter | per skill and per pawn, saved from version 33, reset at each midnight (hour 0) | none | D, RimWorld/SkillRecord.cs:19 (field), :204 (saved), :270 (added to on non-direct XP); RimWorld/Pawn_SkillTracker.cs:103-112 (reset at hour 0). Stillmere resets at the hour-0 boundary for all pawns, in battle maps too. Vanilla resets at the first 200-tick interval of hour 0, so a gain in the first 200 ticks of a day counts to that day here. | Daily learning cap |
| Decay interval | 200 ticks, staggered by pawn id: a pawn's decay runs when `(tick + id) % 200 == 0` | none | D, RimWorld/SkillRecord.cs:27 (`IntervalTicks`); RimWorld/Pawn_SkillTracker.cs:101 (interval test `IsHashIntervalTick(200, delta)`, which is `abs((tick + hash) % 200) < delta`, Verse/Gen.cs:249-252). The hash is in the decompile: `HashOffset` (Verse/Gen.cs:214-217) and `HashOffsetTicks` for a thing (Verse/Gen.cs:254-257). Stillmere uses its raw id in place of the hashed `thingIDNumber` (**C**). Both offsets are arbitrary per pawn, so the rate is the same and only the phase of each pawn differs. The hash is not adopted, since Stillmere's ids are its own numbering. | Skill decay above level 10 |
| Decay per interval, levels 10 to 20 | 0.1, 0.2, 0.4, 0.6, 1.0, 1.8, 2.8, 4.0, 6.0, 8.0, 12.0 XP | none | D, RimWorld/SkillRecord.cs:211-246 | Skill decay above level 10 |
| Decay halved by the Great memory trait | not implemented | none | D, RimWorld/SkillRecord.cs:211 (`GreatMemory`). Stillmere has no such trait. | Skill decay |
| Decay skipped by the perfect memory trait | not applicable | none | D, RimWorld/SkillRecord.cs:209 (Anomaly DLC only, excluded) | Skill decay |
| XP per level (curve) | level 0 to 9: 1000 + 1000 x level; level 9 to 19: 10000 + 2000 x (level - 9); 30000 from level 19 up | 2500 + 400 x level | D, RimWorld/SkillRecord.cs:47-52 (points 0:1000, 9:10000, 19:30000; flat beyond the last point, linear between) and :251-254 | XP needed per level |
| Level cap and XP clamp at 20 | level 20 cannot rise; XP is held at the requirement minus 1 | the level stopped at 20 with XP kept growing | D, RimWorld/SkillRecord.cs:272-274, :292-297 | XP needed per level |
| Level drop | when XP reaches -1000 or lower the level falls one; the XP refill is the requirement of the new (lower) level; level 0 clamps XP to 0 | none | D, RimWorld/SkillRecord.cs:299-309 | Skill decay |
| No learning below level 0 for a negative amount | yes | none | D, RimWorld/SkillRecord.cs:258 | Skill decay |
| Totally disabled skill cannot learn or decay | yes: a skill whose work types are all incapable | none | D, RimWorld/SkillRecord.cs:258 (early return). Stillmere mapping: skill to work types from `WorkType.skill()` (Defs.kt:77); a skill with no work type is never disabled (**C**) | Skill decay |
| Skill trackers exist only for humanlike pawns | humans only: animals and mechanoids gain no XP and do not decay | animals gained XP | D, RimWorld/PawnComponentsUtility.cs:77-81 (the humanlike test at :77, the tracker at :81) | Shooting, melee, decay |
| Base skill level (default flag) | uniform integer 0 to 4 for every skill | 15% chance 0 to 2, otherwise 1 to 8 | D, RimWorld/SkillDef.cs:15 (default `usuallyDefinedInBackstories` true); Verse/PawnGenerator.cs:1963 (`RangeInclusive(0, 4)`). Per-skill overrides are XML data (**UNVERIFIED**); none is set here. | Initial skill base level |
| Backstory skill gain | amount x uniform 1.0 to 1.4, added to the base, summed over the backstories | larger of the base and (amount + random -1 to +2) | D, Verse/PawnGenerator.cs:1964-1972 (`Rand.Range(1f, 1.4f)` at :1970). The amounts are Stillmere's own values, not vanilla data (**C**, kept). | Backstory skill bonus mechanism |
| Age factor, random part | uniform from 1 up to a curve of age (0 at 0, 0.7 at 10, 1.0 at 35, 1.6 at 60); below 1 the range is 1 | none | D, Verse/PawnGenerator.cs:83-89 (curve), :1995 (`Rand.Range(1f, ...)`), Verse/Rand.cs:180-185 (a maximum at or below the minimum returns the minimum) | Age and final level adjustments |
| Age factor, young | 0.2 at age 3 rising to 1.0 at 18; 1.0 from 18 up | none | D, Verse/PawnGenerator.cs:91-95 (curve), :1996 | Age and final level adjustments |
| Final level compression | 0 to 0, 10 to 10, 20 to 16, 27 to 20; flat at 20 beyond 27; linear between | none | D, Verse/PawnGenerator.cs:97-103 (curve), :1997 | Age and final level adjustments |
| Final level rounding and clamp | round half to even, clamp 0 to 20 | none | D, Verse/PawnGenerator.cs:2016 (clamp). The half-to-even rule of `Mathf.RoundToInt` is Unity runtime behaviour, not in the decompile (**UNVERIFIED**). | Age and final level adjustments |
| Passion budget | 5 + Gaussian clamped to +/-4; a unit of budget buys a minor passion; a major passion costs 1.5 and is taken half the time when the budget is at least 1.5 | 1 or 2 rolls, 30% major, each adds 2 levels | D, Verse/PawnGenerator.cs:1857-1870 (budget and loop; the coin is `Rand.Bool`, Verse/Rand.cs:37) | Passion at generation: count and budget |
| Gaussian draw | standard normal from two uniform draws (Box-Muller form) | none | D, Verse/Rand.cs:137-142. Implemented with `StrictMath` log and sine on the sim RNG. | Passion at generation |
| Gaussian guard | the first uniform draw is floored at `GAUSSIAN_FLOOR = 1e-7` before the log, so the log is finite (`Skills.kt`, `SkillRules.gaussian`) | none | **C**: Stillmere's own guard. The reference draw at Verse/Rand.cs:139 has no floor. The floor changes only a first draw below 1e-7, which is rare; an exact zero, which would give an infinite log, is the case it exists for. | Passion at generation |
| Passions are not levels | passions add no skill levels | +2 levels for each passion | D, Verse/PawnGenerator.cs:1857-1954 (the passion code never changes a level) | Passion at generation |
| Passion order | highest base level first (stable on ties), skipping totally disabled skills | 1 or 2 random skills | D, Verse/PawnGenerator.cs:1911-1940 (sort, skip, create) | Passion at generation |
| Trait-forced passion | for each skill a trait forces (STREET_SCRAPPER forces Close combat, Stillmere's own pairing): major if the budget has one left, else minor even with no budget left | Close combat set to major, no budget | D, Verse/PawnGenerator.cs:1871-1885 (forced loop, one call per trait), :1942-1954 (`CreatePassion`, force branch). Which trait forces which skill is XML data; the pairing is Stillmere's (**C**). | Trait-forced passions |
| Passion overwrite | a later assignment replaces an earlier passion, and each assignment uses budget | none | D, Verse/PawnGenerator.cs:1942-1954 (the assignment overwrites `record.passion`) | Passion at generation |
| Backstory forced traits | a backstory's trait list is added before the random draws, skipping repeats (no clash check); the list counts toward the target | list ignored | D, Verse/PawnGenerator.cs:1461-1492 (a forced trait is added unless disallowed, already held or prohibited; Stillmere has no kinds, so only the held check applies). RimWorld/TraitSet.cs:258-270 (`GainTrait` adds with no conflict check and refuses a repeat). No Stillmere backstory has a trait list yet, so nothing changes (**C**: the lists are not in the decompile). | Backstory forced traits |
| Backstory traits count toward the trait target | forced traits are added first; then the target (1 to 3) is drawn, and the traits held count toward it, so a pawn with three backstory traits draws none | no backstory traits: 1 to 3 random draws | D, Verse/PawnGenerator.cs:1461-1492 (forced traits first), :1495-1496 (the count of traits held, then the target), :1506-1510 (the loop stops once the count reaches the target). No effect yet, since no Stillmere backstory has traits (`Game.drawTraits`, tested). | Traits per pawn |
| Trait count target | target 1 to 3; draws until the target is reached, with at most target + 500 attempts; a clashing draw is redrawn | 1 to 3 draws; clashes dropped | D, Verse/PawnGenerator.cs:53 (`TraitsCountRange` 1 to 3), :1563-1595 (draw loop and attempt cap). Commonality weights are XML data (see below). | Traits per pawn |
| XP per tick, mining | 0.07 per tick (flat, not scaled by work speed) | 0.07 legacy per tick (0.028 per tick) | D, RimWorld/JobDriver_Mine.cs:56 (`0.07f * delta`) | XP per work tick, mining |
| XP per tick, plant sowing | 0.085 per tick | 0.07 legacy | D, RimWorld/JobDriver_PlantSow.cs:52 | XP per work tick, plant sowing, harvest and cutting |
| XP per tick, harvest and cut | 0.085 per tick while the plant yields and is fully grown; otherwise 0 | 0.07 legacy | D, RimWorld/JobDriver_PlantHarvest.cs:11; RimWorld/JobDriver_PlantCut.cs:8-18 (the zero case). Stillmere has no harvestable flag: taken as a yielding plant at growth 1 (**C**). | XP per work tick, plant sowing, harvest and cutting |
| XP per tick, construction (frame finish) | 0.25 per tick for the build stage and for deconstruction | 0.07 legacy | D, RimWorld/JobDriver_ConstructFinishFrame.cs:50; RimWorld/JobDriver_Deconstruct.cs:39. The build stage stands for finishing a frame (**C**): vanilla's non-finish build XP is not in the reference. The rate applies whichever skill the building uses (Architecture or Artistry). | XP per work tick, construction, deconstruction and repair |
| XP per tick, repair | 0.05 per tick | 0.04 legacy | D, RimWorld/JobDriver_Repair.cs:49 | XP per work tick, construction, deconstruction and repair |
| XP per tick, research | 0.1 per tick | 0.07 legacy | D, RimWorld/JobDriver_Research.cs:39 | XP per work tick, research |
| XP per tick, bills (crafting and cooking) | 0.1 x recipe learning factor (1 here) per work tick | 0.07 legacy per tick | D, Verse.AI/Toils_Recipe.cs:188 (0.1 times the learning factor); the factor is `workSkillLearnFactor`, which defaults to 1 (Verse/RecipeDef.cs:65). Stillmere recipes have no factor, so 1. (Verse/RecipeMakerProperties.cs:33 is `workSkillLearnPerTick`, a different field, not the factor.) Vanilla awards finished recipes once at completion and unfinished ones per tick (Toils_Recipe.cs:107); Stillmere awards per tick, which differs only when a job is interrupted. | XP per work tick, bills |
| Flat per-meal and per-gear XP | removed | 40 per hearth stew or roasted platter; 60 per gear item | The reference finds no flat per-product award (only the 0.1 recipe rate at Verse.AI/Toils_Recipe.cs:188) | XP per work tick, bills |
| XP per tick, gathering animal resources | 0.13 per tick | 0.07 legacy | D, RimWorld/JobDriver_GatherAnimalBodyResources.cs:45 | XP per work tick, gathering animal resources |
| Fishing XP | not in Stillmere | none | D, RimWorld/JobDriver_Fish.cs:35 (0.025 per call). Stillmere has no fishing job. | XP per work tick, gathering animal resources |
| Medicine XP per tend (human) | 500 x medicine factor | 220 flat | D, RimWorld/Toils_Tend.cs:68 (500 for humans), :70 (`Learn`) | Medicine XP per tend (ambiguous row) |
| Medicine XP per tend (animal) | 175 x medicine factor | 220 flat | D, RimWorld/Toils_Tend.cs:68 (175 for animals) | Medicine XP per tend (ambiguous row) |
| Medicine XP factor | 0.5 for every medicine | none | D, RimWorld/Toils_Tend.cs:69 (`?? 0.5f` without medicine). Per-medicine factors are XML data (`MedicineTendXpGainFactor`, not in the decompile), so every medicine uses the default: **UNVERIFIED** for herbal and industrial medicine. | Medicine XP per tend (ambiguous row) |
| Per-tick tending XP | removed | 0.07 legacy per tick while tending | D, RimWorld/Toils_Tend.cs:70 is the only `Learn` in the tend toil; vanilla gives tending XP only at completion | XP per work tick, medicine |
| Shooting XP | at each burst, for a standing target that is not a colony mechanoid: 170 if hostile to the shooter, else 20, times the full cycle in seconds | 4 x burst per shot | D, Verse/Verb_Shoot.cs:14-16. The "not downed" and "not colony mechanoid" test is at :12. The hostile test and the amounts (170 or 20) are both at :14, the full cycle time at :15, and the learn at :16 (at warm-up completion). Full cycle: warm-up + cooldown + (burst - 1) x burst spacing (D, Verse/VerbProperties.cs:579-581). Seconds are ticks / 60 (D, Verse/GenTicks.cs:67-70). Stillmere's burst shots are spaced tk(2), the spacing its shot list uses (**C**). The "standing" test is "not downed" (Stillmere has no posture). | XP per shot (shooting) |
| Melee XP | per attack that is not against a downed pawn or a colony mechanoid: 200 x the full cycle in seconds (warm-up + cooldown, as for shooting) | 4 per attack | D, RimWorld/Verb_MeleeAttack.cs:31 (the learn, before the hit roll), :200-208 (immobile target: not standing; Stillmere uses downed as the approximation, **C**). The full cycle is from Verse/VerbProperties.cs:579-581 (warm-up, cooldown, burst spacing). Melee weapons carry no warm-up in Stillmere's data and fire one shot, so the cycle is the cooldown and the value is unchanged. | XP per melee attack |
| Hostility for shooting XP | hostile when the factions differ and either pawn is hostile | none | C: Stillmere's own rule standing in for `HostileTo`, which is faction data (**UNVERIFIED** for wild animals) | XP per shot |

## Rows not adopted

| Reference item | Stillmere now | Why not adopted |
|---|---|---|
| Growth moment passion and skill gains (`GrowthUtility.cs:43`) | One random skill: +1 passion step and +2 levels at 7, 10 and 13 (`Life.kt:51-55`) | **DLC-sourced.** Growth points are gated by `ModsConfig.BiotechActive` at `Verse/Pawn_AgeTracker.cs:371`, and each birthday moment by the same flag at `Verse/Pawn_AgeTracker.cs:703` (`TryChildGrowthMoment`). Flagged in the gap report (3.12) and kept, not removed, as the rules require. |
| Growth moment trait gains (`GrowthUtility.cs:46`) | One trait at age 13 (`Life.kt:56-58`) | **DLC-sourced**, as above. Flagged, kept. |
| Growth moment ages 7, 10, 13 (`GrowthUtility.cs:56`) | Matches | Already matches; the moments are Biotech-only. Flagged, kept. |
| Trait skill-level grants (`TraitUtility.cs:8-23`, `PawnGenerator.cs:1981-1993`) | None | The mechanism is in the decompile, but the amounts are per-degree XML (`TraitDegreeData.cs:65`). Inventing amounts would not be fidelity. Open. |
| Trait degrees (`PawnGenerator.cs:1429-1436`, `TraitDegreeData.cs:25-31`) | One level per trait | Each trait's degrees, offsets and skill grants are XML data. Open. |
| Trait selection weighting by commonality (`PawnGenerator.cs:1569`) | Uniform over all traits | Commonality weights are XML data. Open. |
| Sexuality trait chance (`PawnGenerator.cs:1541-1561`, ambiguous) | 16% chance, a pick of four entries with bisexual listed twice | The commonality weights and the gate at :1526 are XML data or Biotech. Kept as is and marked **UNVERIFIED**. |
| Passion conflicts with traits (`RimWorld/TraitDef.cs:129`, used at `PawnGenerator.cs:1911-1940`) | None | The passion-conflict lists are XML data. Nothing is skipped. Open. |
| Child passions: growth-birthday steps before age 13 (`PawnGenerator.cs:1886-1904`) | None. The `adult` flag skips the passion pass for under-13s, a placeholder that newHuman never reaches | Each growth birthday from 3 to the age, a random 0 to 3 passion steps on a random skill. The loop is **not** gated by DLC (only the lines after :1905 are). | **Not modelled.** `newHuman` makes adults only (19 to 55, `Game.kt:186`), so this path is unreachable from it. Open, for when children are generated. |
| Kind extra skill levels (`PawnGenerator.cs:1998-2001`) | None | Added to a level above 0 for the pawn kind (`extraSkillLevels`). The value is kind XML data. | **Not modelled.** Stillmere has no pawn kinds, so the value is 0 and nothing is added (**UNVERIFIED** for the human colonist kind). |
| Kind skill ranges (`PawnGenerator.cs:2002-2015`) | None | A kind may give a min-max range for a skill. A level outside it is re-rolled inside it. Range data is XML. | **Not modelled.** Stillmere has no pawn kinds, so no range applies. Open. |
| Work speed from skill (not a reference row in 3.6) | `0.4 + 0.075 x level` | Left for the work-speed area (3.7). Not changed. |
| Surgery XP per tick (`Surgery.kt:118`) | 0.08 legacy per tick | Not a reference row in 3.6. Not changed, **UNVERIFIED**. |
| Flat husbandry awards for taming, training and milking (`JobDrivers.kt`, `Life.kt:192`) | 150, 60, 60, 20 | Not a reference row in 3.6. Not changed, **UNVERIFIED**. |
| Recruiting XP (`JobDrivers.kt:988`) | 120 flat | Not a reference row in 3.6. Not changed, **UNVERIFIED**. |
| Butchering XP (`JobDrivers.kt:1193`) | Galley, 0.07 legacy per tick | Not a reference row (the cooking citation points here, see discrepancies). Not changed, **UNVERIFIED**. |
| Direct learning (`RimWorld/SkillRecord.cs:256`) | No direct award | Every Stillmere gain is non-direct, so the direct path is not needed. |
| GreatMemory halving (`SkillRecord.cs:211`) | Not implemented | Stillmere has no such trait. |

## Save format

Version 33 adds the per-skill daily counter (`xpToday`, 12 floats per pawn), written after the version-32 block and read
only when the version is 33 or later. A version-32 save reads the counters as zero, which is the value the code had at a
day's start. Enums are unchanged: no constant was reordered. Writing a version-31 save again now gives version 33
(`LegacyNeedsMigrationTest`).

Two version-32 fixtures are read by `LegacySkillsMigrationTest`:
* `legacy/colony_v32.sav` (temporary generator: `Game(4242L)`, `startNewColony(QUIET_ENCLAVE)`, 20000 steps, before the
  change). Every XP value in it is zero at the save point. The test checks the skills, XP, passions and traits as they
  were, and that the counter reads as zero.
* `legacy/colony_v32_xp.sav`: the same colony with XP on each skill a colonist can learn. A temporary generator loaded
  `colony_v32.sav`, called `gainXp` on every skill, and wrote the result with `SaveGame.write(g, 32)`. That write is a
  version-32 save. The current writer reproduces `colony_v32.sav` byte for byte at version 32 (checked by the generator),
  so the version-32 layout is unchanged by version 33. The test checks that every XP value and level reads back, and that
  every colonist has XP. This is the non-zero XP check that the first fixture cannot make.

## Tests

New:
* `SkillsTraitsTest.kt` (42 tests): the XP curve, level-up and drop, the cap at 20, learning factors and trait factors,
  the daily cap, decay and its phase, the passion budget, forced passions, the base levels, backstory gains and the final
  compression, the trait count and conflicts, backstory traits (a clashing pair is kept, a repeat is added once, and the
  backstory traits count toward the target), shooting and melee XP, medicine XP, the daily reset, and the decay tick.
* `LegacySkillsMigrationTest.kt` (10 tests): the two version-32 fixtures, the counter, and the version-33 round trip.
* `HealthCacheTest.kt` (3 tests): every read of `cap` and `pain` equals a fresh computation from the same inputs, checked
  at every tick of a colony run with blasts, a hediff and toxic fallout, and a loaded game reads the same values. A bruise
  that heals without bleeding is checked at every tick. A hit is read at once, before any health tick. Removing the mark
  at the end of `healthTick` makes the bruise test fail (checked).

Checked while working, not kept: a temporary test compared the rewritten `applyXp` with the previous code on about 3 million
random and edge inputs, and the rewritten passion code with the previous code on 200,000 seeds, including the random state
after each call. They matched exactly. That test file was deleted.

Updated (the old value or the old trajectory no longer holds; each is a minimal change, and its comment says why):
* `LegacyNeedsMigrationTest.aVersionThirtyOneSaveWrittenAgainIsVersionThirtyThree`: version 32 to 33.
* `LegacyNamesMigrationTest` (three assertions of the written version): 32 to 33.
* `CombatPipelineTest.aBetterShooterHitsMoreOften`: the marksmanship level is held for every shot, since shots now teach.
  The test measures the hit chance at a fixed skill.
* `JobAiTest.aHaulEndsWhenItsItemVanishesBeforePickup` and `aBuildEndsWhenItsBlueprintIsRemoved`: the strict check (no
  reservation is held) is back. The round-14 version allowed the current job's key, so a leftover on that key could not be
  seen. These tests now remove the weapons from the map first, so no equip job can start during the check. An equip job
  holds a key of its own (`Jobs.kt`, the weapon's tile), so it is the only legitimate holder that would otherwise appear.
* `GameTest.kt` (`RansomTest.severalPrisonersHaveSeparateOffers`): prisoners with a poor mood try to escape at 5% an hour,
  which blocks offers. The two prisoners get a content thought, so the test is about the offers. No assertion is changed.
* `SaveRestoreTest.aBlastOnScreenAtSaveTimeKeepsItsDamageButIsNotReapplied`: back to its original form. The round-14 flush of
  the health caches before the save is removed, because the caches are now correct at every read (see the caveat below).
* `SkillsTraitsTest.aMeleeSwingLearnsCloseCombatForItsFullCycleInSeconds`: the expected cycle now includes the warm-up term
  (zero for melee weapons), following the full-cycle formula.

## Derived caches: health fixed, rooms open

**Health (fixed).** The save keeps no health cache. Before this round, the live game kept the capacities (`cap`) and pain
stale until the next health stride, while a loaded game recomputed them at once. In the blast test, two colonists had
`healthDirty` set and a live capacity of 1.0 against the true 0.97 to 0.99 at the save, so a builder whose work speed
depends on capacity diverged by a fraction of a unit. The test flushed the health caches before the save, which hid this.

The fix is in the cache, not the test. `Pawn.cap` and `Pawn.pain` are read through the cache, which is refreshed on read
whenever `healthDirty` is set (`refreshHealthCache`, `Health.kt`). The cache is then a function of its inputs only:
injuries, hediffs, blood loss, implants and pregnancy. Each write to an input now sets the mark: `applyWound`, and
`addHediff` (also when it raises an existing hediff); the end of `healthTick`, where healing and infection change severity
in place; the toxin, addiction and fallout severities (`Mood.kt`, `World.kt`); the growth-moment trait and the end of a
pregnancy (`Life.kt`). The flush in `SaveRestoreTest` is removed. `HealthCacheTest` checks every read against a fresh
computation at every tick, and removing the mark at the end of `healthTick` makes it fail. No saved field changed, so the
format is still version 33.

**Rooms (open).** `roomDirty` is set by building changes and cleared at the next slow tick (`World.kt:223`). A load clears
it at once (`SaveGame.kt:545`). Between a change and the slow tick, the room reads fall back to "no room" (`homeRoom`,
`roomIndoorAt`, `tempAt`), so the live game and a loaded one can differ in that window. A probe placed a wall 300 ticks into
a colony, saved, and ran both games for 3000 ticks: the saved bytes were identical, so the window did not show there. The
difference in state is real, though, and it is not fixed here.

**Room temperatures (open; state, not a cache).** `roomTemp` is not written to the save. A load rebuilds the rooms with no
earlier temperatures to carry over, so every room starts at the outdoor temperature (`GameMap.rebuildRooms`, called from
`SaveGame.kt:545`). A loaded game can therefore differ from the live one in room climate. Fixing this needs a format change
(save the room temperatures, version 34, with a fixture). That is outside this round.

## Open

* Trait degrees and skill grants, trait commonality, passion conflicts, backstory forced traits (data).
* Growth moments are DLC; their flagging stays in the gap report (3.12).
* Child passions before age 13 (`PawnGenerator.cs:1886-1904`) and the kind extra levels and ranges (`PawnGenerator.cs:1998-2015`) are not modelled. See the rows above.
* Per-skill `usuallyDefinedInBackstories` overrides (Scholarship may be one in vanilla; **UNVERIFIED**).
* Per-medicine XP factors (**UNVERIFIED**).
* Room cache window and the unsaved room temperatures (see the caveat above; open).
