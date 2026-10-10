# Health and medicine: fidelity to vanilla RimWorld

Scope: the base game only. Biotech, Royalty, Ideology and Anomaly conditions and operations are excluded (see
[DLC audit](#dlc-audit)). Code: `sim/.../HealthRules.kt` (formulas), `Health.kt` (tick), `Body.kt` (parts),
`Surgery.kt` (operations). Tests: `HealthFidelityTest.kt`.

## How to read this document

**No value in this document is confirmed against RimWorld's own data.** The wiki hosts (`rimworldwiki.com`, the
Fandom wiki) are blocked by this environment's network policy, and the game files are not available. Each row
carries one of two statuses:

| Status | Meaning |
|---|---|
| `SECONDARY` | Stated in a web-search summary of the RimWorld wiki. I did not read the wiki page itself, summaries can be wrong, and the wiki warns some of its own pages are outdated. Treat as likely, not confirmed. |
| `UNVERIFIED` | Recalled from memory, or the sim's own tuning with no vanilla figure found. Do not rely on it. |

"Sim" columns are what the code does today. A `SECONDARY` row whose sim value matches is marked ✔; one that
differs is marked ✘ with the reason it was left alone, if it was.

The sim's README says its balance numbers are the author's own tuning. Where a vanilla figure is `UNVERIFIED`,
the sim keeps its existing number and the row says so. Only `SECONDARY`-backed rules were changed to match.

### Corrections to the task's premises

- **Part health is not derived from a body total.** As far as I can tell, vanilla gives every body part its own
  hit points, multiplied by the race's health scale. The sim does the same (`HealthRules.partMax`). There is no
  total to divide up. `UNVERIFIED`: whether vanilla rounds the result up to a whole number; the sim does not round.
- **`CLAUDE.md` does not exist** in the repository, so there were no repository instructions to follow. The README's
  module split (`sim/` is plain Kotlin, tests need no Android SDK) was followed instead.
- **`docs/fidelity/gap-report.md` did not exist**, so it was created with a "Health and medicine" section.

---

## 1. Body model

### Human body parts

HP is per part at health scale 1. Coverage is the share of outer hits the part receives (`PartDef.coverage`).
Pinned by `BodyModelTest.humanPartHealthMatchesTheDocumentedTable`.

| Part | Parent | Vital | Reference HP | Status | Sim HP | Sim coverage |
|---|---|---|---|---|---|---|
| torso | none | yes | 40 | `SECONDARY` | 40 ✔ | 0.40 |
| neck | none | yes | 25 | `UNVERIFIED` | 25 | 0.03 |
| head | none | yes | 25 | `UNVERIFIED` | 25 | 0.07 |
| arm (each) | none | no | 30 | `UNVERIFIED` | 30 | 0.07 |
| hand (each) | none | no | 20 | `UNVERIFIED` | 20 | 0.02 |
| leg (each) | none | no | 30 | `UNVERIFIED` | 30 | 0.10 |
| foot (each) | none | no | 25 | `UNVERIFIED` | 25 | 0.02 |
| fingers (each hand) | none | no | 8 | `UNVERIFIED` | **12** | 0.015 |
| toes (each foot) | none | no | 8 | `UNVERIFIED` | **12** | 0.015 |
| heart | torso | yes | 15 | `SECONDARY` | 15 ✔ | none |
| lung (each) | torso | no | 15 | `SECONDARY` | 15 ✔ | none |
| kidney (each) | torso | no | 15 | `SECONDARY` | 15 ✔ | none |
| liver | torso | no | 20 | `SECONDARY` | 20 ✔ | none |
| stomach | torso | no | 20 | `UNVERIFIED` | 20 | none |
| brain | head | yes | 10 | `SECONDARY` | 10 ✔ | none |
| eye (each) | head | no | 10 | `UNVERIFIED` | 10 | none |
| ear (each) | head | no | 12 | `UNVERIFIED` | 12 | none |
| nose | head | no | 10 | `UNVERIFIED` | 10 | none |
| jaw | head | no | 20 | `UNVERIFIED` | 20 | none |

Notes:

- **Fingers and toes**: I recall 8 HP in vanilla but could not confirm it. The sim's 12 was left alone. Fingers and
  toes are appended last in `Bodies.HUMAN` so saved injuries keep their part indexes; any new part must be appended too.
- **Vital parts.** The sim's `PartDef.vital` is torso, neck, head, heart and brain. `SECONDARY`: losing the torso,
  heart, brain, liver, both lungs or both kidneys is fatal. The liver, lungs and kidneys are not "vital parts" in the sim.
  They kill through capacity failure instead (see [Death](#downed-dying-and-death)).
- **Vanilla's skeleton is not modelled.** I recall vanilla having ribcage, sternum, spine, skull, pelvis, clavicle and
  limb bones, plus a tongue and more digits (`UNVERIFIED`). The sim collapses these into the parts above. This is
  recorded as a gap, not a bug.
- **Coverage.** `SECONDARY` sources disagree with each other on the torso's target chance (15% in one table, 40.5% in
  another), and give organ coverages (heart 2.0%, lung 2.5%, kidney 1.7%, liver 2.5%, brain 0.864%) without saying
  what they are relative to. All sim coverage values are therefore `UNVERIFIED`. The sim's outer coverages sum to
  0.98, and the picker normalises by the total.
- **Other bodies.** `QUAD`, `MECH` and `BUG` in `Body.kt` are the author's simplifications; no vanilla figures were
  sought. `UNVERIFIED`.

### Hit locations and damage distribution

| Rule | Sim | Status |
|---|---|---|
| A hit picks an outer part with probability proportional to its coverage | `Game.pickOuterPart`, weights = `PartDef.coverage` | rule `UNVERIFIED`; test pins the sim's weights (`hitsLandOnOuterPartsInProportionToCoverage`) |
| Internal parts can only be hit through their parent | Organs have coverage 0 and are reached only by penetration | `SECONDARY` ✔ |
| Sharp or blast damage can pass into a child organ | Chance 0.55 if damage ≥ 35% of the outer part's HP; the organ takes 55% of the damage; child picked uniformly | `UNVERIFIED` (author's tuning; vanilla picks children by coverage) |
| Damage beyond a part's HP destroys it | `applyWound` → `destroyPart`; children go with it | rule `UNVERIFIED` |
| Armor | Deflection roll and reduction by cover bit (`dealDamage`) | `UNVERIFIED`; armor is out of scope |

Tests: `sharpHitsCanReachOrgansThroughTheirParent`, `destroyingAParentTakesItsOrgansWithIt`.

### Organs and their capacities

| Part | Capacity it carries |
|---|---|
| brain | Consciousness |
| eyes | Sight |
| ears | Hearing |
| jaw | Eating, Talking |
| lungs | Breathing |
| heart | Blood pumping |
| kidneys, liver | Blood filtration |
| stomach | Metabolism (`SECONDARY`: the stomach is the source) |
| legs, feet, toes | Moving |
| arms, hands, fingers | Manipulation |

---

## 2. Capacities

Part efficiency (`HealthRules.partEfficiency`): `1 − damage / max HP`, clamped to 0..1, times the implant's
efficiency if the part is prosthetic. A missing part is 0. Scars do not count as damage. `UNVERIFIED` as to vanilla.

Role efficiency (`HealthRules.mean`): the mean of the efficiencies of the parts sharing a role. Missing parts count
as 0, so losing one of two lungs, kidneys, eyes or ears halves that role (`SECONDARY` for lungs, kidneys and eyes).
A body with no part in a role is not limited by it (value 1).

| Capacity | Formula in the sim (`HealthRules`) | Vanilla reference | Status |
|---|---|---|---|
| **Consciousness** | `brain × (1 − 0.55·pain) × bloodLoss(>0.3: 1 − 1.4·(bloodLoss−0.3)) × (1 − 0.2·(1−pumping)) × (1 − 0.2·(1−breathing)) × (1 − 0.1·(1−filtration))`, then hediff multipliers; anesthetic forces 0 | Brain-driven; blood pumping has 20% importance with no allowed defect and 100% max | pumping weight 0.2 `SECONDARY` ✔; breathing 0.2 and filtration 0.1 weights `UNVERIFIED`; the pain and blood-loss terms `UNVERIFIED` |
| **Moving** | `legs × (0.75 + 0.25·feet) × (0.9 + 0.1·toes)`, × hediffs, × (1 − 0.2·pain); forced to 0 with no working legs | One missing of two legs: half | structure `UNVERIFIED`. Consciousness, breathing and pumping also feed Moving in vanilla (`SECONDARY`); the sim does not apply them directly, only through the downed rule ✘ |
| **Manipulation** | `hands × (0.6 + 0.4·fingers) × 0.7 + arms × 0.3`, × hediffs, × (1 − 0.2·pain); 1 if the body has no hands | Depends on arms, hands, fingers (and consciousness) | roles `SECONDARY`, weights `UNVERIFIED`; consciousness factor not applied ✘ |
| **Sight** | mean of eyes, × hediffs | One eye lost: −50% | `SECONDARY` ✔ |
| **Hearing** | mean of ears, × hediffs | Damaged or missing ears reduce it | `SECONDARY` ✔ (direction only) |
| **Eating** | jaw, × hediffs. **Changed**: no longer includes the stomach | Jaw-driven; the stomach belongs to Metabolism | stomach assignment `SECONDARY`; the 15% floor and the manipulation weight 0.3 in the search results belong to the eating-speed stat, not the capacity, so they are not modelled `UNVERIFIED` |
| **Breathing** | mean of lungs. **Changed**: removed an invented ×1.4 fudge | One lung lost: −50%; both lost: fatal | `SECONDARY` ✔ |
| **Blood pumping** | heart | Heart | role `SECONDARY` ✔ |
| **Blood filtration** | `min(mean kidneys, liver)`. **Changed**: removed invented ×1.6 and ×1.2 fudges | One kidney lost: −50%; liver or both kidneys lost: fatal | kidney halving and fatality `SECONDARY` ✔; `min` as the way to combine kidneys and liver `UNVERIFIED` |
| **Metabolism** | mean of stomach. **New** (`Cap.METABOLISM`) | Stomach source; also feeds the rest-rate multiplier at weight 0.3 | source `SECONDARY`; the name is disputed in the sources (Metabolism or Digestion); the rest-rate link is **not wired** ✘ |
| Talking | jaw | n/a | `UNVERIFIED` (not requested; kept) |

Tests, one per formula: the `CapacityFormulaTest` class (`consciousnessCombinesBrainPainBloodAndOrgans`,
`movingIsLegsWithFeetAndToesAsSmallerShares`, `manipulationIsHandsFingersAndArms`, `sightIsTheMeanOfTheEyes`,
`hearingIsTheMeanOfTheEars`, `eatingIsTheJawAloneAndTheStomachBelongsToMetabolism`,
`breathingIsTheMeanOfTheLungsAndLosingBothIsFatal`, `bloodPumpingIsTheHeartAndLosingItIsFatal`,
`bloodFiltrationIsKidneysAndLiverWhicheverIsWorse`, `metabolismIsTheStomach`, `talkingIsTheJaw`).

Known gaps: filtration should also scale immunity gain speed (`SECONDARY`), and Metabolism should feed rest rate;
neither is wired. Consciousness should multiply Moving and Manipulation directly (`SECONDARY`).

---

## 3. Injury and condition

### Pain

| Rule | Sim | Vanilla reference | Status |
|---|---|---|---|
| Pain from a wound | `severity × kind.pain × 0.014 / max(0.5, √healthScale)` | per-severity factor, softened by body size | `UNVERIFIED` (the sim scales by √ and uses per-kind factors; I recall a flat per-severity figure and a linear scale) |
| Scars and missing parts hurt | no | n/a | `UNVERIFIED` |
| Illness and drug pain | `hediff.kind.pain × severity` | n/a | `UNVERIFIED` |
| Pain → consciousness | linear, `1 − 0.55·pain` | stepped by pain stage | `UNVERIFIED`: the stage thresholds were not found |
| Pain shock threshold | **0.80** (changed from 0.85) | default 80%; effective bounds 30%–95% | `SECONDARY` ✔ |
| Wimp | threshold **−0.50** (changed from a ×1.4 pain multiplier) → 0.30 | Wimp −50% | `SECONDARY`; I read it as percentage points, in line with the other listed offsets. If vanilla means relative, the answer is 0.40 |
| Tough | pain ×0.7, damage ×0.75 | n/a | `UNVERIFIED`, unchanged |
| Pain-shock modifiers from masks and veils | not modelled | War mask +10%, veil +5%, ritual mask +15% | `SECONDARY`; excluded as DLC-sourced items |

### Bleeding, clotting and blood loss

| Rule | Sim | Status |
|---|---|---|
| Bleed rate of a wound | `kind.bleed × severity × 1.35e-6` per tick (×1.7 inside organs; bruise and crush ×0.1) | `UNVERIFIED` |
| Tended wounds | `bleed × (1 − tendQuality) × 0.06` (`HealthRules.bleedRate`) | `UNVERIFIED`; I recall tended wounds not bleeding at all |
| Clotting | wound bleed shrinks per step by `dt/34000` untended, `dt/12000` tended, never more than half per step | `UNVERIFIED` |
| Blood loss accrues | `bloodLoss += Σbleed × dt` | `UNVERIFIED` |
| Blood recovery | 0.36/day if food > 0.2, else 0.10/day, when nothing bleeds | `UNVERIFIED` |
| Death from blood loss | at 1.0 | `SECONDARY` ✔ (lethal severity 100%) |
| Blood loss stages | labels minor, moderate, severe, extreme; thresholds not found | labels `SECONDARY`, thresholds `UNVERIFIED`; the sim has no staged hediff, only the consciousness term above 0.3 |

Tests: `bleedingIsStoppedByTendingAndFadesByClotting`, `lostBloodComesBackSlowerWhenHungry`,
`woundsMakeBlood_OneHundredPercentBloodLossKills`.

### Infection

| Rule | Sim | Status |
|---|---|---|
| Start | each tick, for an untended (or poorly tended, quality < 0.3) wound of severity > 2 whose damage type can infect: chance `kind.infect × 1.54 / day`, ×1.5 outdoors, ×0.3 in a hospital bed, ×`(1 − q) × 0.5` if tended; initial infection 0.04 | `UNVERIFIED` |
| Progression | infection grows `1.1/day × (1 − 0.8·q)`; immunity grows `0.9/day × (1 + 0.6·q + 0.3 if resting)`; when immunity exceeds 1.1× infection the infection is pushed down | `UNVERIFIED` |
| Treatment | tending subtracts `0.35 × q` from the infection and raises `q`-dependent growth suppression and immunity | `UNVERIFIED` |
| Death | infection ≥ 1.0 | `UNVERIFIED` |
| Qualitative checks | herbal-quality tending (0.4–0.5) with a normal bed usually beats an infection; excellent care wins by more than 2:1; hunger slows immunity | `SECONDARY` (anecdotes on the wiki; the sim agrees in direction. A hungry pawn heals slower but its immunity is not slowed ✘) |
| Illnesses | flu `0.9/day` severity, `0.8/day` immunity | the wiki figure is ≈0.249/day severity and ≈0.239/day immunity at 100% quality (`SECONDARY`); the sim is roughly 3.5× faster ✘, left alone because illness tuning is outside this task's wound scope |

Tests: `infectionStartsRarerWithCareAndInTheHospital`, `infectionGrowthFightsImmunityAndGoodCareTipsTheBalance`,
`tendingAnInfectedWoundKnocksItBack`, `infectionKillsAt100Percent`.

### Scars, lost limbs and prosthetics

| Rule | Sim | Status |
|---|---|---|
| Healed sharp wounds can leave a scar | 18% chance for sharp damage kinds; bruises never scar | `UNVERIFIED` |
| A scar's effect | permanent, no pain, no damage to the part | `UNVERIFIED`; I recall vanilla scars keeping a little severity |
| Destroyed part | becomes a "missing" record, permanent; stump bleeds, tended stumps bleed little | rule `UNVERIFIED` |
| Prosthetics | `Implant`: peg leg 0.55, hook hand 0.5, bionic arm/leg 1.3, bionic eye 1.25, bionic ear 1.2, bionic heart 1.3 | efficiencies `UNVERIFIED`; whether "hook hand" is a base-game item is `UNVERIFIED` |

Tests: `sharpWoundsSometimesLeaveAScarThatDoesNotWeakenThePart`, `bruisesNeverScar`, `amputatingALimbRemovesItAndLeavesATendedStump`,
`aPegLegRestoresPartOfWhatAmputationTook`, `bionicPartsBeatNaturalOnes`.

### Downed, dying and death

| State | Condition | Status |
|---|---|---|
| Unconscious (downed) | consciousness **< 30%** | `SECONDARY` ✔ |
| Incapacitated (downed) | moving **≤ 15%** (changed from < 12%) | `SECONDARY` ✔ |
| Pain shock (downed) | pain ≥ threshold (see Pain) | `SECONDARY` ✔ |
| Standing back up | none of the three holds. **Changed**: the old rule used a looser set of thresholds, so a pawn stayed down until recovering past them | the symmetric rule is `UNVERIFIED`; the sim also keeps catatonic and carried exceptions |
| Death: vital part destroyed | torso, neck, head, heart, brain | torso/heart/brain `SECONDARY`; neck and head `UNVERIFIED` |
| Death: lungs | both lost | `SECONDARY` ✔ |
| Death: filtration | liver lost, or both kidneys lost. **New** (cause "organ failure") | `SECONDARY` ✔ |
| Death: blood loss | ≥ 1.0 | `SECONDARY` ✔ |
| Death: infection or lethal illness | severity ≥ 1.0 | `UNVERIFIED` |
| Death at consciousness 0% | not a separate rule | `SECONDARY` ✘; reachable only through the causes above |
| Downed enemies may die at once from pain shock | not modelled | `SECONDARY` ✘ |

Tests: `downedWhenUnconsciousIncapacitatedOrInShock`, `aPawnGetsBackUpAsSoonAsNothingKeepsThemDown`,
`painShockThresholdIs80PercentAndAWimpGoesDownAt30`, `losingTheLiverIsFatal`, and the arc test below.

**Arc test** (`InjuryToRecoveryTest`, fixed seed 5801): a colonist's legs are crushed to 88% and the torso cut. They
are downed on the next health tick (moving ≤ 15%); a doctor of skill 8 tends them with industrial medicine; they stand
up on their own, never go back down, and after 15 days every wound is healed, blood loss is zero and moving exceeds
0.95. `theSameSeedGivesTheSameRecovery` reruns it and requires identical results.

---

## 4. Medicine

### Medicine items

| Item | Potency | Max tend quality | Status |
|---|---|---|---|
| Industrial medicine (`MEDS_INDUSTRIAL`) | 1.0 | 1.0 | `SECONDARY` ✔ |
| Herbal medicine (`MEDS_HERBAL`) | 0.6 | 0.7 | `UNVERIFIED` (potency was the sim's existing value; the cap is new) |
| No medicine | 0.3 | 0.7 | `UNVERIFIED` (new; was a 0.45 factor) |
| Raw healroot | not a medicine (potency 0); a caravan doctor that carries it is treated as using no medicine | | `UNVERIFIED` |
| Glitterworld (ultratech) medicine | not in the sim | 1.6 / 1.3 | `SECONDARY`; base-game item, recorded as a gap |

### Tend quality

`HealthRules.tendQuality`, applied by `Game.tendQuality`:

```
q = skillStat(level) × (0.6 + 0.4·manipulation) × potency
if hospital bed: q += 0.10
if self-tending: q *= 0.7
q *= 1 + (2·roll − 1) × 0.25        // roll is the game's seeded rng.float()
q = clamp(q, 0, maxTendQuality of the medicine)
```

| Element | Value | Status |
|---|---|---|
| Overall shape: doctor stat × potency + bed offset, ×0.7 self-tend, ±25% random, clamped to the medicine's cap | as above | `SECONDARY`; whether the random factor multiplies or adds is uncertain even on the wiki. The sim multiplies |
| Doctor skill stat | level 0 → 0.20, 6 → 0.80, 10 → 1.10, 18 → 1.50, 20 → 1.55 | `SECONDARY`; levels between anchors are linearly interpolated, which is `UNVERIFIED` |
| Self-tend factor | 0.7 | `SECONDARY` ✔ |
| Random spread | ±25% | `SECONDARY` ✔ |
| Hospital bed offset | +0.10 | only hospital beds have an offset: `SECONDARY`; the 0.10 magnitude is `UNVERIFIED` |
| Manipulation factor `0.6 + 0.4·manip` | | `UNVERIFIED`, kept from the old code |
| Doctor skill also affects tend speed and surgery success | | `SECONDARY`; the sim applies skill to tend quality and surgery success, not tend speed |
| Default doctor (nobody tending) | skill 2 | `UNVERIFIED` |

Because the old formula was the author's tuning (about 0.37 for a skill-6 doctor with industrial medicine), this
change roughly doubles tend quality for skilled doctors. That is a deliberate gameplay shift toward the wiki's
table; no existing test depended on the old values.

Tests: `doctorSkillFollowsTheDocumentedCurve`, `tendQualityIsSkillTimesPotency`, `aHospitalBedAddsAFlatBonus`,
`tendingYourselfIsWorse`, `luckSwingsQualityByAQuarterEitherWay`, `clumsyHandsTendWorse`, `medicineCapsHowGoodATendCanBe`,
`medicineItemsCarryTheirPotencyAndCap`, `herbalIsWorseThanIndustrialAndBothBeatBareHands`,
`tendQualityIsDeterministicForAFixedSeed`, `aBetterDoctorTendsBetterOnAPatient`, `selfTendingUsesTheSelfPenalty`,
`tendingMarksEveryWoundAndIllnessTended`, `aHigherQualityTendReplacesALowerOneButNotViceVersa`.

### Healing

| Rule | Sim (`HealthRules.healPerDay`) | Status |
|---|---|---|
| Untended wound | 3.2 HP/day | `UNVERIFIED` (I recall a single natural rate that tending does not change) |
| Tended wound | `8 × (0.55 + q)` HP/day | `UNVERIFIED` |
| Resting in a bed | ×1.35 | `UNVERIFIED` |
| Hospital bed | ×1.15 (stacks with bed rest), infection start ×0.3, tend offset +0.10 | `UNVERIFIED` |
| Bruises | ×1.5 | `UNVERIFIED` |
| Burns | ×0.7 | `UNVERIFIED` |
| Starving (food < 0.05) | ×0.3 | `UNVERIFIED` |
| A wound is gone at severity ≤ 0.3 (or becomes a scar) | | `UNVERIFIED` |

Tests, one per rule: `untendedWoundsHealAtTheBaseRate`, `tendedWoundsHealFasterWithBetterTending`,
`restingInABedSpeedsHealing`, `aHospitalBedSpeedsHealingFurther`, `bruisesHealFasterAndBurnsSlower`,
`hungerSlowsHealing`, `smallWoundsAreGoneBelowTheHealedSeverity`, `bedRestAndHospitalStackOnAPawn`.

### Surgery

Operations in the sim: **amputate** a limb, hand, foot, ear, nose, finger or toe; **install** a prosthetic on a missing
part (non-bionic), or a bionic on any matching part. Prosthetics are gated by research (`Prosthetics`, `Bionics`).

| Rule | Sim | Status |
|---|---|---|
| Success chance | `0.5 + 0.03·skill (+0.1 hospital, +0.3 amputation)`, clamped 0.30–0.98; needs skill ≥ 3 | `UNVERIFIED`. The wiki says medicine potency and doctor skill both scale surgery success (`SECONDARY`); the sim ignores potency ✘ |
| Medicine consumed | none | `UNVERIFIED`; I recall surgery consuming a medicine item ✘ |
| Failure | two cuts of 6 on the part | `UNVERIFIED` |
| Operations on injuries (treating or removing a wound) | none | `UNVERIFIED`; recorded as a gap |
| Organ removal, transplant, and similar | none | out of the requested scope |

Tests: `SurgeryRuleTest` (`amputatingALimbRemovesItAndLeavesATendedStump`, `aPegLegRestoresPartOfWhatAmputationTook`,
`bionicPartsBeatNaturalOnes`, `surgeryOffersOnlyBaseGameOperationsAndNeedsResearchForImplants`,
`noOperationIsOfferedForAnimalsOrMachines`).

---

## DLC audit

Checked from memory, so `UNVERIFIED` as a whole, but every item below is a base-game condition as far as I know.

- Illnesses: flu, plague, malaria, sleeping sickness, food poisoning, gut worms, muscle parasites.
- Environment: hypothermia, heatstroke, malnutrition, toxic buildup, blood loss, frostbite.
- Chronic: bad back, arthritis, cataract, hearing loss, dementia.
- Drugs and withdrawals: alcohol, smokeleaf, psychite family (flake, yayo, go-juice, wake-up).
- Operations: amputation, peg leg, hook hand (**base-game status unconfirmed**), bionic arm, leg, eye, ear and heart.
- Excluded and absent from the sim: pain-shock modifiers from masks and veils (Ideology and Royalty items),
  and everything from Biotech, Anomaly and Royalty (psylink, genes, xenogerms, bioferrite, and the like).

## Test map

| Requirement | Tests |
|---|---|
| One test per capacity formula | `CapacityFormulaTest` (13 tests: one per each of the 11 formulas, plus a pawn-level consciousness check and the liver rule) |
| One test per healing rule | `HealingRuleTest`, `TendingRuleTest`, `InfectionRuleTest`, `InjuryConditionTest` (bleeding) |
| Injured → downed → recovered, fixed seed | `InjuryToRecoveryTest.anInjuredPawnIsDownedThenTendedThenRecovers`, `theSameSeedGivesTheSameRecovery` |
| Body hierarchy, vital parts, hit locations | `BodyModelTest` |
| Surgery, prosthetics | `SurgeryRuleTest` |
