# Vanilla constants read from the decompiled source

Audit date: 2026-10-12. Vanilla source: the public decompiled tree `Chillu1/RimWorldDecompiled`, commit `2d50803`,
build `1.6.9438.38202` (RimWorld 1.6). Paths below are relative to that repo's root.

## How to read this file

Numbers here were read from C# source, so they are **S** (source), not recalled and not from a wiki. Paths and line numbers
point at the decompiled file that declares the value. Only numbers are recorded: no code or RimWorld text is copied.

| Tag | Meaning |
|---|---|
| **S** | Declared as a constant or literal in the decompiled C# code. Checked against the build above. |
| **S-Bio** | In code, but gated on Biotech (`ModsConfig.BiotechActive`) or otherwise DLC-era. Excluded from Core targets. |
| **XML** | Lives in RimWorld's XML `Defs`, which this repo does not contain. Cannot be checked here. |

Stillmere values are the current code in `sim/src/main/kotlin/io/github/teamomuito/colony/sim/` (paths shortened to file names).

**Repo limit:** the decompiled tree has no XML, so item costs, work amounts, weapon stats, biome lists, ore and rock
definitions, trait and backstory tables, storyteller and incident weights, and research costs are not in it.

---

## 1. Time

| Constant | Vanilla | Stillmere | Tag |
|---|---|---|---|
| Ticks per day | 60,000 (`RimWorld/GenDate.cs:9`) | 60,000 (`Defs.kt:5`) | S |
| Ticks per hour | 2,500 (`RimWorld/GenDate.cs:33`) | 2,500 (`Defs.kt:18`, via `TIME_SCALE` 2.5) | S |
| Days per season / per year | 15 / 60 (`RimWorld/GenDate.cs:29`, `:27`) | 15 / 60 | S |
| Ticks per real second at 1x | 60 (`Verse/GenTicks.cs:10`) | 60 (app) | S |

**Result: Matches.**

## 2. Map and world size

| Constant | Vanilla | Stillmere | Tag |
|---|---|---|---|
| Default colony map | 250 x 250 (`RimWorld.Planet/WorldInfo.cs:24`) | 100 x 100 (`Game.kt:695`) | S |
| Map size options | 200, 225, 250, 275, 300, 325 (`RimWorld/Dialog_AdvancedGameConfig.cs:12`) | fixed | S |
| Site default map | 200 x 200 (`RimWorld.Planet/Site.cs:37`) | battle map 44 x 44 | S |
| Caravan-meeting incident map | 100 x 100 (`RimWorld/IncidentWorker_CaravanMeeting.cs:11`) | n/a | S |
| Caravan ambush map size | 900 cells per pawn, clamped 75 to 110 per side (`RimWorld.Planet/CaravanIncidentUtility.cs:9-13`) | 44 x 44 | S |
| Planet coverage options | 30%, 50%, 100% (`RimWorld/Page_CreateWorldParams.cs:32`) | world grid 60 x 40 (`WorldMap.kt:122-123`) | S |

**Result: Divergent.** The colony map is less than half the vanilla width, and the caravan-ambush map is much smaller than vanilla's.

## 3. Temperature and rooms

| Constant | Vanilla | Stillmere | Tag |
|---|---|---|---|
| Default temperature | 21 C (`Verse/TemperatureTuning.cs`) | setpoint 21 (`Climate.kt:56`) | S |
| Daily temperature amplitude | 7 C (`Verse/TemperatureTuning.cs`) | 7 (`Climate.kt:12`) | S |
| Daily sun effect | 14 C (`Verse/TemperatureTuning.cs`) | ground 14 (`Climate.kt:68`) | S |
| Deep underground temperature | 15 C (`Verse/TemperatureTuning.cs`) | n/a | S |
| Seasonal variation curve | 3 at day 0, 4 at 0.1, 28 at year end (`Verse/TemperatureTuning.cs`) | not compared | S |
| Room equalization interval | 120 ticks (`Verse/TemperatureTuning.cs`) | per slow tick | S |
| Wall equalization factor | 0.00017 per interval, 20% of wall cells each interval (`Verse/RoomTempTracker.cs:20-22`, `:251`) | exchange 0.048 (`Climate.kt:50`) | S |
| No-roof equalization rate | 0.0007 per tick (`Verse/RoomTempTracker.cs:30`) | n/a (no roofs) | S |
| Thin-roof equalization rate | 0.00005 per tick (`Verse/RoomTempTracker.cs:28`) | n/a | S |
| Underground equalization | 0.002 per tick (`Verse/RoomTempTracker.cs:34`) | n/a | S |
| Door equalization | interval 34 open / 375 closed, rate 1 (`Verse/TemperatureTuning.cs`) | door conductance 3.0 (report) | S |
| Vent equalization rate | 14 (`Verse/TemperatureTuning.cs`) | n/a | S |
| Uses outdoor temperature | open-roof cells >= ceil(25% of room), or touches the map edge (`Verse/Room.cs:47`, `:98-108`) | own rule (report 3.3) | S |
| Psychologically outdoors | >= 300 open cells, or touches edge with >= 50% open (`Verse/Room.cs:376-390`) | n/a | S |
| Outdoors for work | > 100 open cells, or > 25% open (`Verse/Room.cs:396-400`) | n/a | S |

**Result: Partial (constants) and Divergent (model).** Stillmere's day/night amplitude and setpoint match. The exchange model does
not: vanilla uses separate wall, roof, door and vent rates, and only uses outdoor temperature when at least a quarter of the room is open to the sky.

## 4. Needs

| Constant | Vanilla | Stillmere | Tag |
|---|---|---|---|
| Need update interval | every 150 ticks, 400 per day (`RimWorld/Pawn_NeedsTracker.cs:112`) | per tick | S |
| Food fall per tick | 2.6667e-5, i.e. 1.6 per day (`RimWorld/Need_Food.cs:11`) | 1.6 per day (`NeedRules.kt:9`) | S |
| Hungry threshold | 0.8 x wants-to-eat level; urgent 0.4 (`RimWorld/Need_Food.cs:21-23`) | report 3.5 | S |
| Rest gain per tick | 3.809524e-5, i.e. 2.29 per day (`RimWorld/Need_Rest.cs:18`) | not compared | S |
| Rest fall, awake | 1.5833e-5 per tick, i.e. 0.95 per day (`RimWorld/Need_Rest.cs:20`) | 0.95 per day (`Game.kt:623`) | S |
| Rest fall multipliers | tired 0.7, very tired 0.3, exhausted 0.6 (`RimWorld/Need_Rest.cs:54-62`) | none | S |
| Tired / very tired thresholds | 0.28 / 0.14 (`RimWorld/Need_Rest.cs:22-24`) | not compared | S |
| Joy fall per interval | Empty 0.0015, Very low 0.0006, Low 0.00105, Satisfied/High/Extreme 0.0015 (`RimWorld/Need_Joy.cs:43-50`) | 0.42 per day flat (`Game.kt:624`) | S |
| Joy per day at each level | 0.6 / 0.24 / 0.42 / 0.6 (derived: fall x 400) | 0.42 | S (derived) |
| Comfort thresholds | 0.1, 0.6, 0.7, 0.8, 0.9 (`RimWorld/Need_Comfort.cs:13-19`) | no comfort need | S |
| Beauty impact factor | 0.1 (`RimWorld/Need_Beauty.cs:9`) | no beauty need (room mood only) | S |
| Indoors deltas | thick roof +2, thin +1, none 0; outdoors thick 0, thin -0.25, none -0.25 (`RimWorld/Need_Indoors.cs:12-22`) | no indoors need | S |
| Outdoors deltas | indoors-thick -0.45, outdoors-thick -0.4, indoors-thin -0.32, outdoors-thin +1, indoors-none +5, outdoors-none +8 (`RimWorld/Need_Outdoors.cs:10-22`) | no outdoors need | S |
| Play fall per interval | 0.00025 (`RimWorld/Need_Play.cs:9`) | n/a | S |
| Learning need (for animals/mechs) | fall 3e-6 per tick; thresholds 0.01, 0.15, 0.3, 0.7, 0.85 (`RimWorld/Need_Learning.cs:12-22`) | n/a | S |

**Result: Partial.** Food and awake rest match. Joy does not: vanilla joy drains faster when it is already high and slower when low, while Stillmere uses one flat value. The comfort, beauty, indoors and outdoors needs are missing.

## 5. Mental breaks

| Constant | Vanilla | Stillmere | Tag |
|---|---|---|---|
| Check interval | every 150 ticks (`Verse.AI/MentalBreaker.cs:22`) | per tick | S |
| Threshold fractions of the break stat | extreme 1/7, major 0.5714286, minor 1.0 (`Verse.AI/MentalBreaker.cs:40-44`) | extreme 0.05, major 0.20, minor 0.35 (`NeedRules.kt:22-28`) | S |
| Base break stat | 0.35 (XML `MentalBreakThreshold`; not in repo) | 0.35 | XML |
| Mean time between checks, extreme | 0.5 days (`Verse.AI/MentalBreaker.cs:24`) | not compared | S |
| Mean time between checks, major | 0.8 days (`Verse.AI/MentalBreaker.cs:26`) | not compared | S |
| Mean time between checks, minor | 4 days (`Verse.AI/MentalBreaker.cs:28`) | not compared | S |
| Minimum ticks below threshold | 2,000 (`Verse.AI/MentalBreaker.cs:30`) | not compared | S |
| Minimum ticks since last recovery | 15,000 (`Verse.AI/MentalBreaker.cs:32`) | not compared | S |

**Result: Threshold fractions match. Frequency and timing rules are not compared yet.** Stillmere's break chances are stated as its own tuning.

## 6. Skills

| Constant | Vanilla | Stillmere | Tag |
|---|---|---|---|
| Skill tick interval | 200 ticks (`RimWorld/SkillRecord.cs:27`) | per tick | S |
| Max level | 20 (`RimWorld/SkillRecord.cs:31`) | 20 (`Pawn.kt`, `gainXp`) | S |
| Full-rate XP per day | 4,000, then factor 0.2 (`RimWorld/SkillRecord.cs:33`, `:37`, `:139`) | no saturation found | S |
| Master skill threshold | 14 (`RimWorld/SkillRecord.cs:35`) | n/a | S |
| Passion factor, none / minor / major | 0.35 / 1.0 / 1.5 (`RimWorld/SkillRecord.cs:39-43`, `:379-381`) | 1.0 / 1.4 / 2.0 (`Pawn.kt`, `gainXp`) | S |
| Fast / slow learner | XML trait offsets | 1.4 / 0.6 | XML |
| XP to level up | piecewise linear through (0, 1000), (9, 10000), (19, 30000) (`RimWorld/SkillRecord.cs:47-50`) | 2500 + 400 x level, i.e. 2,500 at 0 and 10,100 at 19 | S |
| Passion effect on XP needed | none (passion scales XP gained) | none | S |
| Skill decay | no ongoing decay found in `RimWorld/SkillRecord.cs`; only a minimum-level floor (`:401-404`) | none | S |

**Result: Divergent.** Stillmere's passion base is 1.0 where vanilla uses 0.35, and the top passion gives 2.0 where vanilla gives 1.5.
Vanilla also has a daily XP cap, which Stillmere lacks. The XP curve is the other big difference: vanilla is cheaper at the start and much more expensive at the top (30,000 vs 10,100 at level 19), so skills rise faster early in Stillmere but far slower late in vanilla.

## 7. Combat

| Constant | Vanilla | Stillmere | Tag |
|---|---|---|---|
| Aim chance | accuracy^distance, banded by distance: 3, 12, 25, 40 cells (`Verse/ShotReport.cs:167-173`) | no distance formula found | S |
| Minimum aim chance | 0.0201 (`Verse/ShotReport.cs:64`, `:173`) | n/a | S |
| Downed-target factor | 0.5 at 4.5+ cells (`Verse/ShotReport.cs:36-40`) | n/a | S |
| Execution factor | 7.5 at 3.9 cells or less against a downed target (`Verse/ShotReport.cs:42-46`) | n/a | S |
| Cover block, corner | 0.75 (`Verse/CoverUtility.cs:8`) | n/a | S |
| Cover angle factors | <=15 deg 1.0; <27 0.8; <40 0.6; <52 0.4; <65 0.2; beyond none; diagonal angles x1.75 (`Verse/CoverUtility.cs:55-99`) | n/a | S |
| Cover distance factors | under 1.9 cells x0.3333; under 2.9 x0.66666 (`Verse/CoverUtility.cs:92-96`) | n/a | S |
| Armour rating cap | 2.0 (`Verse/ArmorUtility.cs:9`) | armour as fraction (0 to 1) | S |
| Armour deflect | if roll < 0.5 x (rating - penetration), damage 0 (`Verse/ArmorUtility.cs:74-80`) | if roll < 0.5 x armour and damage small (`Health.kt:138-146`) | S |
| Armour halve | if roll < (rating - penetration), damage halved and sharp becomes blunt (`Verse/ArmorUtility.cs:81-88`) | not present | S |
| Armour wear | armour takes 25% of damage (`Verse/ArmorUtility.cs:70`) | apparel loses 10% of damage | S |
| Default armour penetration per damage | 0.015 (`Verse/VerbProperties.cs:248`) | n/a | S |
| Melee hit | two rolls: non-miss roll (MeleeHitChance stat), then dodge roll (MeleeDodgeChance stat) (`RimWorld/Verb_MeleeAttack.cs:43-45`) | no dodge roll found | S |
| Melee damage range | x0.8 to x1.2 (`RimWorld/Verb_MeleeAttackDamage.cs:10-12`) | not compared | S |
| Melee target cooldown | 50 ticks (`RimWorld/Verb_MeleeAttack.cs:10`) | n/a | S |

**Result: Divergent throughout.** Vanilla's accuracy, cover and two-roll melee system is not in Stillmere. Its armour rules have the same shape (deflect, then reduce) but use a different rating scale and reduction formula.

## 8. Threats and raids

| Constant | Vanilla | Stillmere | Tag |
|---|---|---|---|
| Wealth to points curve | 0 to 14,000 gives 0; 400,000 gives 2,400; 700,000 gives 3,600; 1,000,000 gives 4,200 (`RimWorld/StorytellerUtility.cs:18-24`) | wealth / 95 (`Incidents.kt:278`) | S |
| Points per free colonist | 15 up to 10,000 wealth, 140 at 400,000, 200 at 1,000,000 (`RimWorld/StorytellerUtility.cs:43-49`) | 17 per colonist (`Incidents.kt:278`) | S |
| Colonist age factor | 3 years 0, 13 years 0.5, 18 years 1 (`RimWorld/StorytellerUtility.cs:51-56`) | none | S-Bio (applied only when Biotech is active) |
| Tame animal points | 0.08 x combat power (`RimWorld/StorytellerUtility.cs:37`) | animals power term (`Incidents.kt:278`) | S |
| Caravan wealth/animal factor | 0.7 (`RimWorld/StorytellerUtility.cs:76-78`) | n/a | S |
| Points clamp | min floor 35; max 10,000 (`RimWorld/StorytellerUtility.cs:12-14`) | min 22 x difficulty (`Incidents.kt:281`) | S |
| Final formula | sum x random factor x adaptation (lerped by difficulty) x difficulty threat scale x points-by-days curve, clamped (`RimWorld/StorytellerUtility.cs:186-190`) | sum x difficulty x storyteller | S |
| Storyteller, incident weights, raid kinds | XML | custom | XML |

**Result: Divergent.** Stillmere's wealth term is roughly 1.75x vanilla at 400,000 wealth and roughly 2.5x at 1,000,000 (10,526 vs 4,200 in points before the per-colonist term).
Vanilla also scales with pawn age in Biotech, and applies a days-passed curve and a clamp. Stillmere has none of these.

## 9. Health and medicine

| Constant | Vanilla | Stillmere | Tag |
|---|---|---|---|
| Injury impact on summary health | severity / (75 x health scale) (`Verse/Hediff_Injury.cs:124`) | not compared | S |
| Bleed rate | severity x injury bleed rate x race factor x part factor (`Verse/Hediff_Injury.cs:142-176`) | raw x (1 - tend quality) x 0.06 when tended (`HealthRules.kt:91-92`) | S |
| Tended bleed | 0: a tended injury does not bleed (`Verse/Hediff_Injury.cs`, `BleedRate`) | residual 0.06 | S |
| Bleed stops by age | 90,000 ticks, plus up to 90,000 by severity (InverseLerp 1 to 30) (`Verse/Hediff_Injury.cs:179-184`) | clot 34,000 untended, 12,000 tended (`HealthRules.kt:88-89`) | S |
| Immunity gain per tick | per-day value / 60,000 (`Verse/ImmunityRecord.cs:45-47`); per-day value XML | 0.9 per day (`HealthRules.kt:130`) | S for formula, XML for value |
| Disease variance | per-instance factor 0.8 to 1.2 (`Verse/ImmunityRecord.cs:42`) | not compared | S |
| Forced immunity from a hediff | 0.65 (`Verse/ImmunityHandler.cs:19`) | n/a | S |
| Immunity scale | lerp 1 to 0 over 0 to 0.6 (`Verse/ImmunityHandler.cs:74`) | n/a | S |
| Heart attack tend chance factor | 0.65; severity reduction 0.3; check interval 5,000 ticks (`RimWorld/Hediff_HeartAttack.cs:9-13`) | n/a | S |
| Blood transfusion | 0.35 bloodloss healed per pack (`RimWorld/Recipe_BloodTransfusion.cs:10`) | n/a | S |

**Result: Divergent in model, Partial in formulas.** Stillmere's tended-bleed rule is not the vanilla one: vanilla stops bleeding completely
when tended, while Stillmere keeps 6%. Vanilla bleeding also runs for a set number of ticks by severity, while Stillmere clots over time.

## 10. Plants and crops

| Constant | Vanilla | Stillmere | Tag |
|---|---|---|---|
| Base growth at sowing | 0.0001 (`RimWorld/Plant.cs:38`) | not compared | S |
| Base growth percent | 0.15 (`RimWorld/Plant.cs:36`) | n/a | S |
| Growth factors | fertility x temperature x light x haze x drought (`RimWorld/Plant.cs:301`) | fertility (floor 0.2) and a light gate (`World.kt:378-382`) | S |
| Fertility factor | fertility x sensitivity + (1 - sensitivity) (`RimWorld/PlantUtility.cs:352-358`) | fertility, floored at 0.2 | S |
| Light factor | inverse lerp between min and optimal glow (`RimWorld/PlantUtility.cs:361-368`) | binary gate: light > 0.2 or > 0.5 | S |
| Temperature factor | inverse lerp up to optimal minimum; flat to optimal maximum; lerp down to max (`RimWorld/PlantUtility.cs:370-`) | hard cut at min and max temperature | S |
| Default temperatures | min 0, optimal 6 to 42, max 58 (`RimWorld/Plant.cs:50-58`) | per plant type | S |
| Death without light | 450,000 ticks (`RimWorld/Plant.cs:46`) | n/a | S |
| Vacuum damage | 1 per tick (`RimWorld/Plant.cs:70`) | n/a | S |
| Rot "never" sentinel | 72,000,000 ticks (`RimWorld/CompRottable.cs:212`, `:217`) | not compared | S |
| Rot damage interval | per 60,000 ticks (`RimWorld/CompRottable.cs:117`) | not compared | S |

**Result: Divergent.** Stillmere grows crops at 1/(0.55 x grow days) per tick, which is about 1.8x vanilla's
base speed before the vanilla factors. It also has a light gate and a hard temperature cut where vanilla uses curves.

## 11. Fire

| Constant | Vanilla | Stillmere | Tag |
|---|---|---|---|
| Minimum fire size | 0.1 (`RimWorld/Fire.cs:30`) | intensity starts at 0.3 (`World.kt:30`) | S |
| Maximum fire size | 1.75 (`RimWorld/Fire.cs:40`) | intensity cap 1.2 (`World.kt:36`) | S |
| Base growth per tick | 0.00055 (`RimWorld/Fire.cs:48`) | +0.002 x flammability per update (`World.kt:59`) | S |
| Spark interval | 150 ticks at size 1, down by 40 per size step, floor 75 (`RimWorld/Fire.cs:34-38`, `:88-93`) | none | S |
| Sky extinguish chance | 0.04 base (`RimWorld/Fire.cs:54`) | rain -0.01 and snow -0.004 per update (`World.kt:57-58`) | S |
| Heat per size per interval | 160 (`RimWorld/Fire.cs:60`) | n/a | S |
| Ignite movables at size | 0.4 (`RimWorld/Fire.cs:46`) | n/a | S |
| Burn-out floor | 7,500 ticks (`RimWorld/Fire.cs:42`) | n/a | S |

**Result: Divergent.** Stillmere's fire is a single intensity value. Vanilla has a size with growth, sparks, heat and snow clearing.

## 12. Construction and power

| Constant | Vanilla | Stillmere | Tag |
|---|---|---|---|
| Construction global speed factor | 1.7 (`RimWorld/GenConstruct.cs:9`) | build work x0.5, progress 0.4 x skill speed per tick (`JobDrivers.kt:689`, `:707`) | S |
| Battery self-discharge | 5 W (`RimWorld/CompPowerBattery.cs:13`) | not compared | S |
| Power net restart interval | 30 to 200 ticks (`RimWorld/PowerNet.cs:28-30`) | n/a | S |
| Power net shutdown interval | 20 ticks, minimum fraction 0.05 (`RimWorld/PowerNet.cs:34-36`) | n/a | S |
| Minimum stored energy to turn on | 5 (`RimWorld/PowerNet.cs:38`) | n/a | S |
| Solar output at night | 0 (`RimWorld/CompPowerPlantSolar.cs:9`) | not compared | S |
| Solar generator wattage, build cost, research cost | XML | 1700 W, 100 steel, 3 components, 800 research (`Buildings.kt:72`, `Defs.kt:381`) | XML |

**Result: Partial.** The mechanism is comparable. The individual building costs and wattages are XML and cannot be checked here.

## 13. Research

| Constant | Vanilla | Stillmere | Tag |
|---|---|---|---|
| Research points per work tick | 0.00825 (`RimWorld/ResearchManager.cs:38`) | 0.25 x work speed / 2.5 per tick (`JobDrivers.kt:866`) | S |
| Research points per full pawn-day at speed 1 | 495 (derived: 0.00825 x 60,000) | 6,000 (derived: 0.25 / 2.5 x 60,000) | S (derived) |
| Intellectual XP per techprint | 2,000 (`RimWorld/ResearchManager.cs:40`) | n/a | S |
| Difficulty research speed | XML difficulty `researchSpeedFactor` (`RimWorld/ResearchManager.cs:317`) | threat only | S |
| Tech-level cost factor | `CostFactor(techLevel)` (`RimWorld/ResearchManager.cs:320`) | none | S |
| Project costs and prerequisites | XML | custom list, 300 to 1,800 (`Defs.kt:362-414`) | XML |

**Result: Rate difference.** Stillmere produces about 12 times as many research points per pawn-day as vanilla's base rate.
Whether that matches overall depends on vanilla project costs, which are XML and cannot be checked here. The 12x gap is the first thing to confirm.

## 14. Life stages, growth and social

| Constant | Vanilla | Stillmere | Tag |
|---|---|---|---|
| Growth moment ages | 7, 10, 13 (`RimWorld/GrowthUtility.cs:56`) | 7, 10, 13 (`Life.kt:44`) | S, verify DLC gating |
| Gestation period | per race, XML (`RimWorld/PawnColumnWorker_Pregnant.cs:28`) | 30 days (`Life.kt:8`) | XML |
| Minimum lovin' age | 16 (`RimWorld/Pawn_RelationsTracker.cs:51`) | romance requires age 18 (`Social.kt:25`) | S |
| Friend opinion threshold | 20 (`RimWorld/Pawn_RelationsTracker.cs:47`) | not compared (Stillmere uses 25, 35, 60 on its own scale) | S |
| Rival opinion threshold | -20 (`RimWorld/Pawn_RelationsTracker.cs:49`) | arguments start below -10 (`Social.kt:30`), not a direct rival rule | S |
| Bond relation check | every 2,500 ticks, 0.001 chance, within 12 cells (`RimWorld/Pawn_RelationsTracker.cs:41-45`) | n/a | S |
| Marriage proposal acceptance | 0.9 base (`RimWorld/InteractionWorker_MarriageProposal.cs:13`) | not compared | S |
| Breakup base chance | 0.02 (`RimWorld/InteractionWorker_Breakup.cs:11`) | not compared | S |
| Taming interval | 30,000 ticks minimum (`RimWorld/TameUtility.cs:9`) | not compared | S |

**Result: Mixed.** Growth ages match. Opinion thresholds are on different scales. Gestation is XML.

## 15. Caravans and world travel

| Constant | Vanilla | Stillmere | Tag |
|---|---|---|---|
| Default ticks per move | 3,300 (`RimWorld.Planet/CaravanTicksPerMoveUtility.cs:40`) | not compared | S |
| Cells per world tile | 340 (`RimWorld.Planet/CaravanTicksPerMoveUtility.cs:38`) | n/a | S |
| Move speed factor at zero mass | 2 (`RimWorld.Planet/CaravanTicksPerMoveUtility.cs:42`) | n/a | S |
| Ticks per cell from the human move-speed stat | `RimWorld.Planet/CaravanTicksPerMoveUtility.cs:159-160` | n/a | S |
| Caravan rest and food cache | 60 and 3,000 ticks (`RimWorld.Planet/Caravan.cs:53-55`) | n/a | S |

**Result: Not compared in detail.** The constants are sourced, but Stillmere's world model is a 60 x 40 grid, so its travel timing needs a separate pass.

---

## Not in the source (XML only)

Biome lists and weights, ore and rock definitions, plant growth days, animal and human race stats, trait tables, backstory tables,
storyteller weights and incident frequencies, item and recipe costs, work amounts, weapon stats, research costs, quest definitions,
faction tables, scenario definitions, and the base values of most stats. Each of these needs the XML `Defs` from the game's data
folder, which this repo does not contain.
