# Needs and mood

Phase 3 from [gap-report.md](gap-report.md), section 3.5. The first round set the mental-break thresholds, the human
hunger rate and the starting food. This round works from the decompiled reference (version 1.6.9438.38202, see
[decompiled-reference.md](decompiled-reference.md), section 3.5). It closes the rest and joy drains, the hunger
categories, the zero-rest collapse, the break timing, drug tolerance and withdrawal, and the comfort, beauty, outdoors
and indoors needs.

Code: `NeedRules.kt` (`NeedRules`, `DrugRules`, `BreakRules`), `Mood.kt` (`moodUpdate`, `breaksTick`, `comfortTick`,
`placeNeedsTick`, `restCollapseTick`), `Game.kt` (`pawnTick`, `starvationTick`), `JobDrivers.kt` (`driveSleep`,
`applyDrug`), `Jobs.kt` (`URGENT_FOOD`, `isInBed`, `startSleepInPlace`), `Pawn.kt` (new fields), `SaveGame.kt` (version 32).

## Source key

* **D, file:line**: a value read from the decompiled source. The file and line are the ones in
  [decompiled-reference.md](decompiled-reference.md) section 3.5 (the row number is given too). Values are restated here, not copied.
* **C**: Stillmere's own choice, where the reference gives no value or only a rule. The reason is given.
* **UNVERIFIED**: not in the decompiled source. It is XML data, or the reference does not give it.

## Constants adopted

| Constant | Value | Was | Source | Row |
|---|---|---|---|---|
| Rest fall, awake, Rested band | 0.95 per day | 0.95 per day | D, RimWorld/Need_Rest.cs:20 | 194 |
| Rest fall multiplier, Tired (rest below 0.28) | 0.7 | 1.0 | D, RimWorld/Need_Rest.cs:57-59 | 194 |
| Rest fall multiplier, Very tired (below 0.14) | 0.3 | 1.0 | D, RimWorld/Need_Rest.cs:57-59 | 194 |
| Rest fall multiplier, Exhausted (below 0.01) | 0.6 | 1.0 | D, RimWorld/Need_Rest.cs:57-59 | 194 |
| Rest band thresholds for the fall | 0.28, 0.14, 0.01 | not used for the fall (mood used 0.3, 0.1) | D, RimWorld/Need_Rest.cs:38, 42, 46 (band edges; the same values are constants at :22 and :24) | 196 |
| Rest fall while asleep, or lying down in a REST job (stage 1) | none. A REST job at stage 0 (walking to its bed) still falls | the REST job drained 0.95 per day | D, RimWorld/Need_Rest.cs:104-117 (a resting pawn gains, otherwise it falls; the per-interval gain is at :110, the per-tick constant at :18); RimWorld/Toils_LayDown.cs:94-97 (the gain is taken only in the lay-down toil, and only while asleep). Why the walk falls: the reference skips the fall only in an update that took a gain, and a walking pawn takes none, so it falls like an awake pawn. Stillmere's `driveSleep` returns at stage 0 before any gain, and `aPawnWalkingToABedOnARestJobLosesRestOnEveryStepUntilItLiesDown` checks the fall on every step of a walk. Known divergence, kept: Stillmere's REST stage 1 has no asleep flag, so a pawn lying on a REST job always gains. The reference keeps a pawn awake when it cannot fall asleep (RimWorld/Toils_LayDown.cs:61-67, with the check at :63), and that pawn falls. Not changed here | 195 |
| Natural wake level in bed | rest 1.0 | 0.985 | D, RimWorld/Need_Rest.cs:28; RimWorld/RestUtility.cs:607 | 198 |
| Wake level outside sleep hours | rest 0.82 | 0.82 | C: no decompiled value; kept | 198 |
| Joy fall per 150 ticks, empty (below 0.01) | 0.0015 (0.6 per day) | 0.42 per day, flat | D, RimWorld/Need_Joy.cs:45-50 (the values; the switch header is at :43), :115 (the subtraction) | 201 |
| Joy fall per 150 ticks, very low (below 0.15) | 0.0006 (0.24 per day) | 0.42 per day, flat | D, RimWorld/Need_Joy.cs:45-50 (the values; the switch header is at :43), :115 (the subtraction) | 201 |
| Joy fall per 150 ticks, low (below 0.3) | 0.00105 (0.42 per day) | 0.42 per day, flat | D, RimWorld/Need_Joy.cs:45-50 (the values; the switch header is at :43), :115 (the subtraction) | 201 |
| Joy fall per 150 ticks, satisfied and above | 0.0015 (0.6 per day) | 0.42 per day, flat | D, RimWorld/Need_Joy.cs:45-50 (the values; the switch header is at :43), :115 (the subtraction) | 201 |
| Joy band thresholds for the fall | 0.01, 0.15, 0.3 | not used for the fall (mood used 0.1, 0.3) | D, RimWorld/Need_Joy.cs:19, 23, 27 (0.01, 0.15, 0.3; the bands go on at :31 and :35) | 202 |
| Joy drain paused during a JOY job | yes | yes | Stillmere's own: the gains in JOY jobs are not changed here | 204 |
| Hunger multiplier, Fed (food 0.24 and up) | 1.0 | 1.0 | D, RimWorld/HungerLevelUtility.cs:28-31 | 188 |
| Hunger multiplier, Hungry (below 0.24) | 0.5 | 1.0 | D, RimWorld/HungerLevelUtility.cs:28-31 | 188 |
| Hunger multiplier, Urgently hungry (below 0.12) | 0.25 | 1.0 | D, RimWorld/HungerLevelUtility.cs:28-31 | 188 |
| Hunger multiplier, Starving (food 0) | 0 | 1.0 | D, RimWorld/HungerLevelUtility.cs:28-31 | 188 |
| Hungry and urgently hungry thresholds | 0.24 (0.8 x 0.3), 0.12 (0.4 x 0.3) | eat-wanted 0.3 only | D, RimWorld/Need_Food.cs:23 (0.8), :21 (0.4); the category checks are at :33, :37 and :41. The 0.3 base is the omnivore value at Verse/RaceProperties.cs:298. RimWorld/JobGiver_GetFood.cs:44 only compares food with that value (Stillmere treats humans as omnivores; the diet mapping is not checked here) | 190 |
| Urgent eat interrupt | food below 0.12 | below 0.15 | D, RimWorld/Need_Food.cs:21, 37 (the 0.4 factor and the urgently hungry band) | 190 |
| Malnutrition gain while starving | 0.453 per day (nominal) | 0.28 per day | D, RimWorld/Need_Food.cs:13 | 191 |
| Malnutrition recovery while not starving | 0.453 per day, whenever food is above 0 | 0.03 per 625 ticks while food above 0.15 (about 2.88 per day) | D, RimWorld/Need_Food.cs:176 (the recovery call; the else branch that selects it is at :174) | 192 |
| Malnutrition per-pawn factor (0.8 to 1.2) | not applied (factor 1.0) | none | Deferred: needs a seeded value per pawn, saved (see Not adopted) | 191 |
| Chemical need fall per day (addiction severity) | 0.5 per day | 0.45 per day | D, RimWorld/NeedDef.cs:60 (code default); RimWorld/Need_Chemical.cs:63. Per-drug values are XML: **UNVERIFIED** per drug | 217 |
| Withdrawal level (craving starts) | 0.01 or below | 0 or below | D, RimWorld/Need_Chemical.cs:8 (the desire threshold) | 217 |
| Craving per addiction in withdrawal | 0.4 for each addiction in withdrawal, summed and capped at 1. One craving hediff, kept while any addiction is in withdrawal | 0.4 added once. A later addiction out of withdrawal could remove it, so the result depended on hediff order | C: Stillmere's own value. The reference's withdrawal is a stage of each addiction hediff, not a summed craving | 217 |
| Addiction chance multiplier by tolerance | 1 at 0; 5 at 0.5; 15 at 0.8; linear between; flat above 0.8. Applied only above 0 | none | D, RimWorld/DrugStatsUtility.cs:11-13 (the curve points; :9 is its header), applied at :39-41 only above zero tolerance | 219 |
| Addiction chance, base per drug | unchanged: beer 3%, hushmoss 1.5%, lucid 10%, pale dust 45%, white rush 30%, surge tonic 6% | same | C: Stillmere's own (UNVERIFIED against XML) | 220 |
| Minimum tolerance to addict | none (0) | none | C: the decompiled minimum is XML | 220 |
| Drug tolerance build-up per dose | +0.05, capped at 1 | none (`BREW_RESISTANCE` is defined but unused) | C, **UNVERIFIED** (the build-up is XML) | 219 |
| Drug tolerance fall per day | 0.025 | none | C, **UNVERIFIED** (the decay is XML) | 219 |
| Drugs that build tolerance (`DrugRules.ADDICTIVE`, used at JobDrivers.kt:408) | beer, joints, lucid draught, pale dust, white rush, surge tonic. Dawn tablets are not in it: `applyDrug` returns for them before any dose, so they neither build tolerance nor roll for addiction | none: no tolerance; every drug went through the same addiction roll | C, **UNVERIFIED**. The reference reads addictiveness per drug from XML (RimWorld/CompDrug.cs:29 uses the drug's addictiveness and its minimum tolerance), so this list is Stillmere's own classification | 219, 220 |
| Beauty need level | clamp(0.5 + 0.1 x room beauty) | not modelled | Formula D, RimWorld/Need_Beauty.cs:95. The base 0.5 is the code default (D, `RimWorld/NeedDef.cs:45`, `baseLevel`); the beauty need's XML override is not in the tree (**UNVERIFIED** for that), and reference row 213 is ambiguous (code at NeedRules.kt:79) | 213 |
| Beauty thoughts (ugly at level 0.35 or below, pretty above 0.65) | room beauty -1.5 and +1.5 (follows from the base 0.5: D as the code default, the XML override UNVERIFIED; see the row above) | -0.6 and +1.6 | Bands D, RimWorld/Need_Beauty.cs:63 (0.65, pretty) and :67 (0.35, ugly); the level formula is at :95. Thought values kept at -0.04 and +0.06 (C) | 213 |
| Comfort need level (instant, from the seat under the pawn) | the seat's comfort while the pawn sits, lies or idles on it, else 0 | not modelled | Bands D, RimWorld/Need_Comfort.cs:41, 45, 49, 53, 57 (0.1, 0.6, 0.7, 0.8, 0.9). Smoothing is XML, so the level is taken directly (C). No mood effect: the thought values are not in the decompile | 214 |
| Jobs that put the pawn on the seat under it (`SEAT_JOBS`, Mood.kt) | sleep, rest, joy, social, eat, wait. In any other job the seat comfort is 0 | not modelled | C: Stillmere's own set. The reference's level counts only when the seat was used within the last 15 ticks (RimWorld/Need_Comfort.cs:29 window, :31 value). Stillmere keeps no per-tick use record, so the job type stands in for using the seat | 214 |
| Outdoors need, per 150 ticks | outdoors without roof +0.02; outdoors under rock roof -0.001 (floor 0.2); indoors under roof -0.001125 (floor 0); falls x0.2 in bed | not modelled | D, RimWorld/Need_Outdoors.cs:106-140 (floor at :106, deltas and bed factor at :110-131) | 215 |
| Indoors need, per 150 ticks | indoors under roof +0.005; outdoors under roof 0; outdoors without roof -0.000625 (no floor) | not modelled | D, RimWorld/Need_Indoors.cs:103-105 (the delta, the 0.0025 scale, and no floor) | 216 |
| Outdoors and indoors start | 1.0 | not modelled | D, RimWorld/Need_Outdoors.cs:95 (CurLevel = 1f; the method header is at :93); RimWorld/Need_Indoors.cs:88 (the header is at :86) | 215, 216 |
| In bed (the outdoors fall x0.2, and the bed rest rate) | A pawn given a bed that it has reached: a SLEEP or REST job at stage 1 with amount 1, on a built sleeping building. A collapsed pawn on a bed cell is not in bed, and neither is a downed pawn (it has no job). Before: the outdoors need used the SLEEP job and the building, and the rest gain used amount 1 alone | the outdoors fall x0.2 when in bed; the bed rest rate for a pawn in bed | D, RimWorld/RestUtility.cs:496-537 (InBed needs a bed the pawn occupies and a bed posture; the job and the amount approximate that), RimWorld/Need_Outdoors.cs:127-130 (x0.2 for a fall in bed) | 215 |
| Outdoors thoughts (new labels) | at 0.4 or below -0.05 "Cooped up"; at 0.2 or below -0.10 "Trapped inside"; at 0.05 or below -0.15 "Sealed in" | none | Bands D (0.4, 0.2, 0.05; RimWorld/Need_Outdoors.cs:44-60, thresholds at :86-90). Values **UNVERIFIED** (C) | 215 |
| Indoors thought (new label) | at 0.2 or below -0.04 "Worn by the weather" | none | Band D (0.2; RimWorld/Need_Indoors.cs:10, 56). Value **UNVERIFIED** (C). The 0.4 band was dropped: an outdoors-working colony reaches it in about 2.4 days with no roofs | 216 |
| Cold stages by difference from the comfort minimum | 0 to 10: stage 0 "Cold" -0.02; 10 to 20: "Very cold" -0.14; 20 to 30: "Freezing" -0.25; 30 and up: "Freezing" -0.25 | 3 / 9 / 20 with -0.05, -0.14, -0.25 | Bands D, RimWorld/ThoughtWorker_Cold.cs:16 (stage 0 is an active thought there). The stage 0 value and the stage 3 value are C (the XML offset is not in the decompile) | 209 |
| Hot stages | 0 to 10: "Hot" -0.02; 10 to 20: "Very hot" -0.14; 20 to 30: "Scorching" -0.25; 30 and up -0.25 | 3 / 7 / 15 | Bands D, RimWorld/ThoughtWorker_Hot.cs:15 (the cold file has the same bands at :16; row 209). Values as cold, C | 209 |
| Mental break minimum time below a threshold | 2000 ticks before that level can break | none (any check below the threshold) | D, Verse.AI/MentalBreaker.cs:165-177, 296-306 | 225 |
| Mental break mean days (extreme / major / minor) | 0.5 / 0.8 / 4 days, as a rate per tick | 0.45%, 1.2%, 3% per 625-tick update | D, Verse.AI/MentalBreaker.cs:24, 26, 28 (the checks that use them are at :298, :302 and :306) | 224 |
| Break cooldown after recovery | 15000 ticks awake | none | D, Verse.AI/MentalBreaker.cs:389 | 225 |
| Break thresholds | 0.35, 0.20, 0.05 (extreme and major are fractions of minor) | same | Fractions D, Verse.AI/MentalBreaker.cs:40 (1/7) and :42 (0.5714286), matches (row 222). Minor 0.35 is **ambiguous** (row 223): the decompile reads it from a stat at :44 and has no base value in code, so 0.35 is Stillmere's own (C) | 222, 223 |
| Break check timing | the counter steps by the 625-tick slow update, so the 2000-tick wait is 2500 ticks in practice | per slow update | C: the mood update runs every 625 ticks. The 150-tick checks are folded into one step at the same rate. Stillmere's trait multipliers (unmoved, steel nerves, short fuse) are kept | 224, 225 |
| Involuntary sleep (collapse) at zero rest | after more than 1000 ticks at zero rest: mean days 0.25 (below 15000 ticks at zero), 0.125 (below 30000), 1/12 (below 45000), 0.0625 (45000 or more) | none | D, RimWorld/Need_Rest.cs:172 (more than 1000 ticks), :174 (the mean days), :181 (the check every 150 ticks); zero is rest below 0.0001 at :119. Life-stage MTB curve is XML, not applied. Colonists and prisoners only; not drafted pawns; they lie down where they stand | 199 |
| Hunger category rule for animals | the same food categories as humans | none | UNVERIFIED for animals: the reference's rule is on the food need, which every pawn has, but no animal-specific value was checked | 188 |

Break thresholds, hunger rate and starting food from the first round are kept (see the sections below).

## Not adopted

Each row below has a reason. Where a value is in the decompiled source but Stillmere keeps its own, the reason says why.

| Row | Item | Reason |
|---|---|---|
| 187 | Hunger rate 1.6 per day | Already matches. |
| 189 | Eat-when-below 0.3 | Already matches. |
| 190 (moods) | Hungry, very hungry and starving thoughts at 0.3, 0.15, 0.03 | The thought labels are saved text (names.md), so the thresholds stay until the owner decides on relabelling. |
| 193 | Starting food 0.8 | Already matches. |
| 195 | Rest gain (bed and ground) | Ambiguous: the reference gain depends on each bed's rest-effectiveness stat, which Stillmere has no equivalent of. Kept as it is. |
| 196 | Tired and exhausted thoughts at 0.28, 0.14, 0.01 | Same reason as 190: "Tired" and "Exhausted" are saved labels, and the reference's stages do not map one-to-one onto them. |
| 197 | Sleep-seeking and falling-asleep limits (JobGiver_GetRest) | Stillmere's sleep choice is its own schedule logic, not the decompiled job priority. It belongs with the job AI (3.7). |
| 200 | Starting rest between 0.9 and 1.0 | A random start needs a draw per new colonist, which changes every seeded colony. Deferred. |
| 201 (detail) | Joy drain paused for 15 ticks after a gain | The effect is about 0.00015 per gain. It needs a last-gain tick at every gain site. Not done. |
| 201 (detail) | Joy drain halved while forming a caravan | Stillmere has no forming-caravan state. |
| 201 (detail) | Joy drain scaled by a stat | Stillmere has no such stat; the factor is 1.0. |
| 203 | Joy tolerance per activity kind | Joy gains are not changed in this round. |
| 204 | Joy gain rate from buildings | Ambiguous, low confidence in the reference. |
| 205 | Mood baseline (0.55 in Stillmere, 0.5 code default) | The model differs: the reference sums thought offsets into a base level. Not changed. |
| 206 | Mood smoothing toward the instant mood | The smoothing rates are XML (per-hour rise and fall). Not changed. |
| 207 | Stacking of thoughts of one kind | Stillmere's `addThought` already keeps one thought per label, which is the reference's default stack limit of 1. No Stillmere thought stacks. |
| 208 | Catharsis timing (at the start of a run-wild break) and value | Value and duration are XML. Not changed. |
| 210 | Pain thought bands | Belongs with pain in 3.4 (health). Medium confidence. |
| 211 | "In the dark" timing (240 ticks since last lit) | Timing rule, medium confidence; the light model is 3.3. |
| 212 | Cramped room (size curve, sampled cell count) | Ambiguous. |
| 218 | Chemical interest need (drug desire trait) | A new per-pawn saved need with trait-driven gains. Not in this round. |
| 220 | Repeat use adds an offset to addiction (Stillmere resets to 1) | The per-dose offset is XML. Stillmere's reset to 1 is kept. |
| 221, 226 | Break kind weights and binge weights | Commonality is XML. Stillmere keeps its uniform choice. |
| 227 | Break durations (recovery MTB with a 500-tick minimum) | Stillmere's windows are kept. Changing them changes every break's length. |
| 228 | Catatonic break as a hediff | Stillmere keeps its downing. |
| 222, 223 | Break thresholds | Already match (the reference reads the minor base 0.35 from a stat; Stillmere uses 0.35). |
| n/a | Per-pawn malnutrition factor (0.8 to 1.2) | Needs a seeded value per pawn, saved. Nominal 1.0 is used. |
| n/a | Involuntary sleep life-stage curve | XML. Not applied. |
| n/a | RestFallFactor, RestFallRateFactor, JoyFallRateFactor, RestRateMultiplier | Stillmere has no such stats; the factor is 1.0. |
| n/a | "Prefers indoors" and "prefers outdoors" traits | No such trait in Stillmere, so the outdoors and indoors needs are never disabled. |
| n/a | Thin roofs | Stillmere has one roof kind (natural rock or built roof), so every roof counts as thick. |
| n/a | Comfort and beauty smoothing (seeker rise and fall per hour) | XML. The levels are taken directly each slow update. |
| n/a | Mood thoughts for the comfort need | The thought values are not in the decompile. The level is computed and exposed (`Pawn.comfortNeed`) but has no mood effect yet. |

## Fixed on the way

* **Beer addiction never decayed.** The decay loop matched addictions by the name suffix `_ADDICTION`, and the beer addiction
  is named `BREW_DEPENDENCE`, so beer addiction stayed at full severity. The loop now uses an explicit list of the six
  addiction kinds (`DrugRules.ADDICTIONS`). Beer addicts now go into withdrawal like the others. This is a behaviour change.
* **Craving depended on hediff order.** An addiction out of withdrawal removed the craving that another addiction in
  withdrawal had just added. The cravings are now summed over all addictions first, and the keep-or-remove rule is applied
  once (see the second review below).

## Review fixes (citations and names)

A review checked the citations in this file against the decompiled source. The corrections:

* The mental-break source is `Verse.AI/MentalBreaker.cs`, not `RimWorld/MentalBreaker.cs`. The intensity block is at lines 165-177.
* The minor break threshold 0.35 is **ambiguous** (reference row 223), not D. Only the major and extreme fractions (1/7 and 0.5714286) are D, at :40 and :42.
* The rest multipliers 0.7, 0.3 and 0.6 are at `Need_Rest.cs:57-59`. The band edges are at :38, :42 and :46. The rule that a resting pawn gains and does not fall is at :104-117; line 18 is the gain constant.
* The hunger factors 0.4 and 0.8 are at `Need_Food.cs:21` and :23. Line 33 is the category switch. The four multipliers are at `HungerLevelUtility.cs:28-31`.
* The outdoors bands are at `Need_Outdoors.cs:44-60` and their thresholds at :86-90. The indoors band 0.2 is at `Need_Indoors.cs:10, 56`.
* The beauty bands 0.65 and 0.35 are at `Need_Beauty.cs:63` and :67. Line 95 holds only the level formula. The beauty base 0.5 is the code default (D, `RimWorld/NeedDef.cs:45`); the beauty need's XML override is **UNVERIFIED**.
* The hot bands are at `ThoughtWorker_Hot.cs:15`. The cold file has the same bands at :16, and the hot values are not in it.
* `ALCOHOL_TOLERANCE` no longer exists. The hediff is `BREW_RESISTANCE` (`Body.kt:130`), which is still unused.
* The comfort band names are now `BARE`, `MODEST`, `COSY`, `SNUG`, `PLUSH` and `OPULENT`. The old names echoed the decompiled comfort category identifiers. The enum is not saved, so no save format changes.

A second review of this section and the code, in the same round:

* The 0.3 eat-wanted base behind the 0.24 and 0.12 thresholds is the omnivore value at `Verse/RaceProperties.cs:298`. The cite that used to be given, `RimWorld/JobGiver_GetFood.cs:44`, only compares food with that value. Row 190 now cites both.
* The 0.453 malnutrition recovery is applied at `RimWorld/Need_Food.cs:176`. Line 174 is the else branch that selects it. Row 192 now cites 176.
* Five Kotlin names matched decompiled identifiers, so they are renamed. The meaning, the order in which they are saved and the save version are unchanged:
  * `Pawn.ticksBelowExtreme`, `ticksBelowMajor` and `ticksBelowMinor` are now `moodBelowExtremeTicks`, `moodBelowMajorTicks` and `moodBelowMinorTicks`. They matched `Verse.AI/MentalBreaker.cs:16, 18, 20`.
  * The `ticksAtZero` parameter of `NeedRules.collapseMtbDays` is now `zeroRestTicks`. It matched `RimWorld/Need_Rest.cs:14`.
  * `BreakRules.mtbDays` is now `BreakRules.meanDaysBetweenBreaks`. It matched `RimWorld/StorytellerCompProperties_SingleMTB.cs:7`.
* `DrugRules.ADDICTIVE` and `SEAT_JOBS` were missing from the constants table. Both are now there (the drug list and the seat jobs), each with its source.

## Review fixes (second review)

A second review found the citations and two logic faults below. Each cite was opened at its line and the value checked there.

* **Citations corrected.**
  * Joy thresholds 0.01, 0.15 and 0.3 are at `Need_Joy.cs:19, 23, 27`. Line 19 alone shows only 0.01.
  * The joy fall values are at :45-50. The switch header at :43 shows none of them. The subtraction is at :115.
  * The outdoors and indoors start of 1.0 is `Need_Outdoors.cs:95` and `Need_Indoors.cs:88`. The headers at :93 and :86 only
    set it up.
  * The comfort bands are at `Need_Comfort.cs:41, 45, 49, 53, 57`. Line 41 alone shows only 0.1.
  * The tolerance curve points are at `DrugStatsUtility.cs:11-13`. Line 9 is its header.
  * The collapse mean days are at `Need_Rest.cs:174`, and the check every 150 ticks is at :181.
  * The mental-break checks are at `MentalBreaker.cs:298, 302, 306`. Line 298 is the extreme check only.
  * The indoors delta, its 0.0025 scale and the lack of a floor are at `Need_Indoors.cs:103-105`.
  * Rows 201, 202, 214, 215 and 216 of [decompiled-reference.md](decompiled-reference.md) carried the same faults. They
    are corrected to match.
* **Craving no longer depends on hediff order.** `comfortTick` added the craving when one addiction went into withdrawal,
  and removed it when another addiction was out of withdrawal. The result depended on which hediff came first. Now the
  cravings of all addictions in withdrawal are summed (0.4 each, capped at 1), and the keep-or-remove rule is applied once.
  For one addiction the craving is still 0.4.
* **One in-bed test.** The outdoors need used a SLEEP job on a sleeping building (`Mood.kt`). The rest gain used
  `amount == 1` (`JobDrivers.kt`). A collapse on a bed cell therefore gave the outdoors bed factor while the pawn was not
  in bed. Both now call `Game.isInBed` (`Jobs.kt`). It is a SLEEP or REST job at stage 1 with amount 1, on a built sleeping
  building. Three things change in play:
  * a collapsed pawn on a bed cell no longer gets the bed factor;
  * a resting pawn in a bed now gets the bed factor on the outdoors need, as it already got the bed rest gain;
  * a pawn whose bed has gone gets the ground rest rate, not the bed rate with comfort 0.
* **Tests added (7).** In `NeedsMoodFidelityTest.kt`: two addictions in withdrawal give the same craving (0.8) in both
  orders; one in withdrawal and one out give 0.4 in both orders; no addiction in withdrawal gives none. A pawn given a bed
  falls at a fifth of a collapsed pawn's rate. A downed pawn on a bed cell gets no bed factor, with or without a collapse
  job. A collapsed pawn rests at the ground rate on a bed. A pawn given a bed rests at the bed rate.

No saved layout changes. The craving severity is written as before. Its value changes only when more than one addiction is
in withdrawal, or when an addiction out of withdrawal came before one in it. VERSION stays 32.

## Save format (version 31 to 32)

Version 32 adds, at the end of each pawn record and only for version 32 and later: the outdoors need, the indoors need,
the zero-rest counter, the three time-below counters, the break cooldown, and drug tolerance (a count, then each drug's
ordinal and value, in drug order). Saves before 32 read these as their defaults: needs at 1.0, counters at 0, no tolerance.
Nothing saved before changed its layout.

Fixture: `colony_v31.sav` with `colony_v31_expect.txt`. It was written by the version-31 code before this change, by a
temporary generator (`Game(4242L)`, `startNewColony(QUIET_ENCLAVE)`, 2000 base ticks). The generator was removed after use.

Not saved: the comfort level, which is recomputed each slow update.

## Tests

* `NeedsMoodFidelityTest.kt`: the constants and each rule above, with the source named in each test. Game-level checks for
  the collapse, the resting counter, beer addiction withdrawal and tolerance decay. Added in the second review: the craving
  in either hediff order, and the in-bed rule (a collapsed or downed pawn on a bed, a pawn given a bed, and the rest rates).
* `LegacyNeedsMigrationTest.kt`: `colony_v31.sav` still loads, its new fields read as defaults, version 32 keeps tolerance and
  needs across a save, and a version-31 write leaves the new fields out.

Existing tests changed because they asserted an old value:

* `NeedsMoodFidelityTest.kt`: the break-chance tests. The chance is now per check, from the mean times, so the test
  names are kept but they check the new rule (`noBreakAboveTheMinorThreshold`, `breakChanceRisesAsMoodFalls`). The
  hunger, threshold and meal tests are unchanged.
* `LegacyNamesMigrationTest.kt`: three assertions that a fresh save is the current version, 31 changed to 32, and the
  comment at the top that says so.

All 541 sim tests passed after the first round (`./gradlew :sim:test --console=plain`). After the second review, 548 pass.
The count was 507 at the last report.

## Checks in the simulation

A temporary diagnostic (removed) ran the five-colonist start for three days on seeds 4242 and 113:

* Food reached 0.01 to 0.07 by the end of day 3 for every colonist. The first round recorded the same food run-out on day 3
  for this start, so this is not a new shortage. The urgent band now slows the fall, so food stays above zero a little longer.
* Nobody collapsed (the minimum rest was 0.26), so the collapse rule does not fire in ordinary play.
* Five and one mental breaks came in the two runs. At the end of day 3, mood was below the extreme threshold (0.05) for 2 of
  the 5 colonists on seed 4242 and for 4 of the 5 on seed 113. The main cause in both was the starvation thought (-0.3).
* The indoors need fell to 0.25 for every colonist, because there are no roofs yet. That is why the indoors thought was
  kept only at its lowest band.
* Outdoors thoughts were not present, since the outdoors need stayed at 1.0 for colonists in the open.

## Round 1 (kept for the record)

Round 1 set the break thresholds to 0.35, 0.20 and 0.05, and the human hunger rate to 1.6 nutrition per day. Both are kept.
Its hunger-rate source was secondary (search summary of the RimWorld Wiki "Saturation" article, **M, UNVERIFIED**
in this file, as no URL was recorded). The break thresholds are now checked: the code gives 4/7 and 1/7 of the stat (D, `Verse.AI/MentalBreaker.cs:34, 40-44`), and a wiki snippet gives the base stat as 35% (V, https://rimworldwiki.com/wiki/Mental_break). This round's sources are the decompiled values above.

Round 1 scaled each scenario's starting food so the opening lasted as long as before the rate change. The colonists also start
with 0.8 food each, which does not scale, so the factor keeps `(starting bars + stock) / (colonists × rate)` the same:

| Scenario | Colonists | Stock before (nutrition) | Stock now (nutrition) | Factor |
|---|---|---|---|---|
| Salvage crew | 3 | 30 sealed rations (27) | 72 sealed rations (64.8) | 2.40 |
| Quiet enclave | 5 | 60 trail cake (3.0), 40 rice (2.0) | 199 trail cake (10.0), 132 rice (6.6) | 3.31 |
| Surplus start | 1 | 20 sealed rations (18) | 47 sealed rations (42.3) | 2.34 |
| Bare start | 1 | 6 sealed rations (5.4) | 15 sealed rations (13.5) | 2.48 |

**Checked in round 1.** On the five-colonist start (seeds 113 and 61), food ran out on day 3 both before and after scaling. With
seed 61, the first death came on day 6 both before and after. With seed 113 nobody died by day 6 either way. Before scaling,
the new rate alone ran food out on day 2 and killed a colonist on day 5 (seed 113). Those numbers came from a temporary
diagnostic test, since removed.

## Open items

1. Per-drug fall rates, addictiveness and tolerance build-up come from XML and are **UNVERIFIED**. The per-drug values here are
   Stillmere's own.
2. The thought values for outdoors and indoors, and the stage 0 and stage 3 cold and hot values, are **UNVERIFIED**.
3. Not done: the chemical interest need, the malnutrition per-pawn factor, the random starting rest and joy, the mood smoothing
   rates, the joy pause after a gain, and mood effects for comfort (see Not adopted).
4. The app module does not show the new needs. `app/` was not built here (no Android SDK).
5. The hunger categories apply to animals too; no animal-specific value was checked (see the table).
6. The sleep-seeking thresholds (JobGiver_GetRest) are not changed. They belong with the job AI (3.7).
7. A downed patient lying in a bed has no job (`downPawn` ends it), so it is not in bed. The reference counts a bed occupant
   in bed posture (`RimWorld/RestUtility.cs:496-537`), so a downed patient would count there. Not changed.
