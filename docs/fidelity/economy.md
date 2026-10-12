# Production and economy: construction power, plants and food, research

Phase from [gap-report.md](gap-report.md), sections 3.8 (construction and power), 3.9 (plants, food, spoilage) and 3.14
(research). This round works from the decompiled reference (version 1.6.9438.38202, see
[decompiled-reference.md](decompiled-reference.md), sections 3.8, 3.9 and 3.14). It is limited to the construction-power,
plants-food and research rows, as the brief says. Fire, zones, roofs, work-table penalties and snow are other rows of
section 3.8 and are not changed here (see the not-adopted list).

## Discrepancies found while reading

* **Save version.** The brief says `VERSION` is 31 at the start of this run. The committed `VERSION` is 34
  (`SaveGame.kt:29` at `HEAD`). The working tree has 35 at `SaveGame.kt:34`, the value this round sets, and it is not changed
  again here. A version-34 fixture was made from the unchanged code before the change (`legacy/colony_v34.sav`, header 34,
  with `legacy/colony_v34_expect.txt`).
* **The light step (found in review, now fixed).** The first version of the glow took full glow (1.0) whenever the cell light
  was above 0.5. Outdoors the cell light is the daylight (`World.kt` `lightTick`), so the glow jumped from about 0.5 to full at
  half daylight. Over a clear day that gives rice 10.7 game days, not 11.6. The glow is now `plantGlow`, the larger of the
  daylight and the cell's artificial light (a lamp or a fire), so it stays on the ramp. The test
  `aPlantUnderDimDaylightGrowsOnTheRampInTheWorldTick` fails under the step and passes on the ramp.
* **Two learning rows are already matched.** The reference table's construction and research rows list the old Stillmere
  value, 0.028 XP per tick, not the reference value. The decompiled lines say 0.25 per tick for construction
  (`JobDriver_ConstructFinishFrame.cs:50`) and 0.1 per tick for research (`JobDriver_Research.cs:39`). The current code
  learns construction at 0.25 per tick, only in the work stage after every material is delivered (`JobDrivers.kt`
  `driveBuild`, stage 4), and research at 0.1 per tick through `gainXp` (`driveResearch`), which applies the passion factor
  and the saturation rule (`Pawn.kt` `gainXp`, `Skills.kt`). Both match the decompiled lines. No change.
* **Battery self-discharge citation.** The reference cites `CompPowerBattery.cs:13`. That line is only the declared constant
  (`SelfDischargingWatts = 5f`). The drain itself is in `CompTick` at `CompPowerBattery.cs:70`, which draws
  `min(5 x WattsToWattDaysPerTick, stored)` each tick (`WattsToWattDaysPerTick` is 1/60000, `CompPower.cs:20`). The value is
  the same. This round cites line 70.
* **Crop growth was not "1.8x too fast" under Stillmere's own day.** The gap report compares `1 / (days x 0.55)` with
  `1 / days` as if the crop were lit all day. Stillmere's daylight is above the old 0.2 threshold for about 59% of the day
  (`Game.kt` `daylight`), so the old code gave about `1 / days` of growth per calendar day. The 0.55 was close to the lit
  share by coincidence. The vanilla rate multiplies `1 / growDays` by a light factor that is 0 in the dark and 1 at full
  glow. On Stillmere's schedule that gives about 0.46 of the old rate (weather off, 20 C, fertility 1, rice at 5.8 days):
  5.4 game days before, 11.6 after. The old figure is 5.8 x 0.55 / 0.592, where 0.592 is the lit share above 0.2 (14.2 of 24
  hours). The new figure uses the average light factor over a clear day, 0.499, which is the ramp from 6:00 to 7:00 and from
  18:00 to 19:00 with full light between (11.98 of 24 hours' worth of light). It is checked in
  `aClearDayGivesAboutHalfAGrowTimeOfLightOnTheRamp`, which samples the day at the slow tick (average 0.4994). Trees and other
  growing plants slow by the same factor. This is the largest balance change in this round, and it follows from the
  reference, not from a tuning choice.
* **Research points keep their unit, but the rate rises.** The old cost is `baseCost x 16` and the old progress was 0.1 per
  work tick times work speed. The reference rate of 0.00825 per work tick, on the same unit scale (`16 x 0.00825 = 0.132`
  per work tick), gives 0.132 per tick over the same cost. A project therefore takes `0.1 / 0.132`, about 76% of its old time:
  about 24% less, as [gap-report.md](gap-report.md) says. The factor 16 is kept as a unit scale, so saved progress keeps its
  meaning and needs no migration.
* **Harvest below the minimum yields nothing.** A plant is harvestable only above 65% growth (`Plant.cs:115`, a strict test),
  and its yield is zero when it is not (`Plant.cs:917-919, 934-936`). An earlier version of this round took half the count
  below the minimum through a clamp. The reference does not clamp there; the zero comes from the harvestable test. Fixed:
  `harvestYieldExact` returns 0 at or below `HARVEST_MIN_GROWTH`.
* **The grow tray keeps its factor of 2.0.** Passing the tray's fertility of 2.0 through the new fertility blend would give
  1.5 (`2.0 x 0.5 + 0.5`), a change to a Stillmere rule that no reference value supports. The tray now uses
  `GROW_TRAY_GROWTH_FACTOR` (2.0) directly, so its growth is as before. Both places that read the tray's factor use the
  constant: the growth step (`World.kt` `plantsTick`) and the sowing filter (`Jobs.kt` `findGrow`). The sowing filter had a
  literal 2 until review; the value was the same.

## Source key

* **D, file:line**: a value or rule read from the decompiled source tree (`/home/user/chillu1/rimworlddecompiled`, read
  only). Values are restated here, not copied. Every D file:line in this document, and in the code comments and tests for
  these rows, was opened in that tree in the review round and its value checked at that line. A "(reference table)" tag says
  the citation came from [decompiled-reference.md](decompiled-reference.md); the line was checked as well.
* **C**: Stillmere's own choice, where the reference gives no value, or where the vanilla value is XML data. The reason is
  given.
* **UNVERIFIED**: a value Stillmere uses that no source here confirms.

## Constants adopted (construction and power)

| Constant | Old | New | Source | Note |
|---|---|---|---|---|
| Battery capacity per battery | 600 Wd (`World.kt` `powerTick`) | 1000 Wd (`BATTERY_CAPACITY_WD`) | D `RimWorld/CompProperties_Battery.cs:5` (code default) | UNVERIFIED for the battery XML def, which can override it. The gap report recalls 1,000 too. |
| Battery self-discharge | none | 5 W per battery per tick, capped by stored energy, applied each slow tick as 5 x 625 / 60000 Wd per battery (`BATTERY_SELF_DISCHARGE_W`) | D `RimWorld/CompPowerBattery.cs:70` (`CompTick`); `CompPower.cs:20` for the per-tick conversion | Stillmere's power steps once per slow tick (625 ticks), so the drain is applied per slow tick. |
| Breeze mill output cap | none (wind factor 0.15 to 1.35 used directly) | output uses `min(windFactor, 1.5)` (`BREEZE_OUTPUT_WIND_CAP`) | D `RimWorld/CompPowerPlantWind.cs:32` (`MaxUsableWindIntensity`) | Not binding while the wind walk in `worldSlowTick` is bounded at 1.35. The cap is in place for when the walk changes. |
| Sun array roof factor | none | output x (unroofed cells / footprint cells) | D `RimWorld/CompPowerPlantSolar.cs:17, 19` | An array inside a room (or under natural roof) makes nothing, as in vanilla. |

Kept as Stillmere values (C or UNVERIFIED, no reference value):

* Building costs and work amounts. The reference has no building cost rows; vanilla costs and work are XML. Kept.
* Power draws and ratings: sun array 1,700 W, breeze mill 1,500 W, log-burning engine 1,000 W, glow panel 400 W, hotplate
  350 W, picture box 200 W, grow tray 70 W (`Buildings.kt`). The reference has no rows for these (they are XML). The
  gap report recalls 2,900 W for the glow panel, which differs from the 400 W here. Neither is confirmed by source
  (**UNVERIFIED**). Kept.
* Battery charge efficiency. The reference gives the code default 0.5 (`CompProperties_Battery.cs:7`, used in
  `CompPowerBattery.cs:85`), but the battery def can override it and the gap report has no value for it. Stillmere stores
  charge losslessly. Not adopted; UNVERIFIED.
* The builder's construction speed (0.4 + 0.075 x skill, `Pawn.kt`). The reference gives the stat as XML. Kept (ambiguous).

## Constants adopted (plants and food)

| Constant | Old | New | Source | Note |
|---|---|---|---|---|
| Growth per day at full factors | `1 / (days x 0.55)` (`World.kt` `plantsTick`) | `1 / days` | D `RimWorld/Plant.cs:346` (`1 / (60000 x growDays)` per tick) | The 0.55 is removed. Grow days are Stillmere's values (`Defs.kt` `PlantType`), UNVERIFIED per plant (vanilla XML). |
| Fertility factor | `max(0.2, fertility)` | `fertility x sens + (1 - sens)` with `sens = 0.5`; a powered grow tray uses `GROW_TRAY_GROWTH_FACTOR` (2.0) instead | D `RimWorld/PlantUtility.cs:358`; default sensitivity `PlantProperties.cs:97` (reference table) | Per-plant sensitivity is XML, so the default is used for every plant (UNVERIFIED). The tray's 2.0 is a C rule, applied as a factor and not through the blend (see Discrepancies). |
| Temperature factor | 0 outside `[minTemp, maxTemp]`, 1 inside (`World.kt`) | Ramp: 0 at `minTemp - 6`, linear to 1 at `minTemp`, 1 to `maxTemp`, linear to 0 at `maxTemp + 16` | D `RimWorld/PlantUtility.cs:370-381` (the temperature factor); optimal band minimum 6 and maximum 42 at `PlantProperties.cs:89, 91`; growth minimum 0 (unset default, `:87`) and maximum 58 (`:93`) | The offsets 6 and 16 are 6 - 0 and 58 - 42, the defaults' distances to the growth limits. Applied to every Stillmere plant, per-plant limits UNVERIFIED. |
| Light factor | lit when daylight > 0.2 or cell light > 0.5 (binary) | `InverseLerp(0.51, 1.0, glow)`, a ramp, with glow = `plantGlow(daylight, cell light)`: the larger of the daylight and the cell's light (`World.kt` `plantGlow`). Cell light is the daylight, or a lamp's or fire's light where that is brighter (`lightTick`: 1 at the source, falling linearly to 0 at its radius) | D `RimWorld/PlantUtility.cs:361-367`; defaults `PlantProperties.cs:75, 77` (reference table) | Mapping daylight to glow is C: Stillmere has no latitude or season glow. A lamp's light is a ramp value, not full glow. The first version of this round stepped to full glow above half daylight (see Discrepancies). |
| Rain and garden-sense growth bonuses | x1.1 in rain, x1.25 for the garden-sense trait | removed | D `RimWorld/Plant.cs:289-302` (growth is the product of fertility, temperature and light only) | The trait keeps its other effect (the "worked with plants" thought). |
| Sown crop start growth | 0.02 | 0.0001 | D `RimWorld/JobDriver_PlantSow.cs:61` | |
| Harvest threshold (crops) | fully grown (1.0) | growth above 0.65 (`HARVEST_MIN_GROWTH`) | D `RimWorld/Plant.cs:115`; default `PlantProperties.cs:55` (reference table) | Crops only; trees and regrowing plants keep the full-growth rule. The cut and harvest learning rule (fully grown, C) is unchanged; the reference gates it differently (see Rows not adopted). |
| Harvest yield | crops: count x (0.75 + 0.25 x horticulture / 10); wild: count x (0.5 + 0.5 x min(work speed, 1.6)) x growth | 0 at or below growth 0.65 (not harvestable); above it, count x (0.5 + 0.5 x (growth - 0.65) / 0.35), at most count, x 1 (health) x 1 (difficulty), randomly rounded | D `RimWorld/Plant.cs:115` (harvestable only above the minimum), `Plant.cs:917-919` and `934-936` (zero yield), `Plant.cs:932-947` (formula); difficulty crop factor 1 at `DifficultyDef.cs:44` (reference table) | Health: Stillmere plants have no hit points, so the health term is 1 (C). Random rounding uses the sim RNG. Applies to all harvested plants. A plant at full growth gives its full count. The old tree and wild formula multiplied the count by 0.5 + 0.5 x min(work speed, 1.6), so a tree at full growth was unchanged only at work speed 1.0; at other work speeds the old count differed. The new formula has no work-speed term, as the reference has none. A plant cut at or below 65% growth gives nothing. An earlier version of this round gave half (see Discrepancies). |
| Raw meat spoil time | 4 days | 2 days (`MEAT` only) | D `RimWorld/ThingDefGenerator_Meat.cs:37` (`daysToRotStart = 2`, `rotDestroys = true`) | Other meat-category items (manflesh, chitin, eggs, milk) keep their values (C; not covered by the reference). |
| Corpse rot time | 2.2 days | 2.5 days | D `RimWorld/ThingDefGenerator_Corpses.cs:181` (`daysToRotStart = 2.5`) | Stillmere still removes a corpse at full rot; vanilla keeps it as a dessicated body (not adopted, see below). |
| Rot rate versus temperature | 0.2 to 1.0 with a steep rise above 20 C | 0 at or below 0 C, `t / 10` from 0 to 10 C, 1 from 10 C up | D `Verse/GenTemperature.cs:459-470` (the rot rate function) | Applied to the rot progress each slow tick, as before. |
| Raw meat poisoning chance | 1% for any meat-category item | 2% for `MEAT` (`FoodPoisonChanceFixedHuman`); 1% for other meat-category items | D `RimWorld/ThingDefGenerator_Meat.cs:68` | Chance is the same roll as before. |
| Raw meat rotten-food roll | 40% when eating food over 65% rotten | not applied to `MEAT` (raw meat is fresh until it is destroyed at full rot) | D `RimWorld/CompRottable.cs:151-155` and `ThingDefGenerator_Meat.cs:37-38` (rotting stage destroys the item) | Kept for other foods (C): their fresh-stage boundary is XML. |
| Butcher meat yield | `meat x eff x (1 - 0.5 x rot)`, truncated | `meat x eff`, randomly rounded (no rot term) | D `Verse/Pawn.cs:2962` (round-random of the meat amount times efficiency, no rot term; reference table) | `eff` (0.55 + 0.3 x min(cook speed, 1.5)) is kept as the efficiency analogue (C). |
| Sowing temperature gate | cell temperature above `max(6, crop min)` and below `crop max - 3` | cell temperature above `minTemp - 6` and below `maxTemp + 16` (strict) | D `RimWorld/PlantUtility.cs:21-33` (growth season) and `WorkGiver_GrowerSow.cs:63` | The limits are the same derived limits as the growth ramp. |

Kept as Stillmere values (C or UNVERIFIED):

* Frost kill below -2 C, and the death of mature crops below -3 C (`World.kt`). Stillmere rule, not in the reference table.
* Grow tray growth factor 2.0 (C, Stillmere rule). Applied as a factor, not through the fertility blend, so the tray's growth is unchanged.
* Crop grow days and terrain fertilities (`Defs.kt`, UNVERIFIED per plant; the reference gives the defaults only).
* Per-plant temperature bands (rice, potato, corn and the rest at 6 to 42 C, wispsilk at 10, sward at 3). The reference gives
  the default band only (`PlantProperties.cs:89, 91`). Kept as the optimal band; the growth limits follow from them (C).

## Constants adopted (research)

| Constant | Old | New | Source | Note |
|---|---|---|---|---|
| Points per work tick | 0.1 x work speed x bench x keen mind (`0.25 / TIME_SCALE`) | `0.00825 x 16` x work speed x bench x keen mind (`RESEARCH_BASE_RATE`, `RESEARCH_POINT_SCALE`, `researchPointsPerTick`); about 24% less time per project | D `RimWorld/ResearchManager.cs:38` (the per-work-tick constant) and `ResearchManager.cs:309` (the progress function) | The 16 is Stillmere's unit scale for points, kept from the old cost model (C). |
| Project cost | `baseCost x 16` | `baseCost x 16` (unchanged, `RESEARCH_POINT_SCALE`) | C | Base costs are Stillmere's (UNVERIFIED): vanilla costs are project XML and are not in the decompile. |
| Research session | 2250 ticks (`tk(900)`) | 4000 ticks | D `RimWorld/JobDriver_Research.cs:48` (`defaultDuration = 4000`, Delay mode) | Progress is per tick, so total time is unchanged; the session length sets how often the job ends. |

Kept as Stillmere values (C or UNVERIFIED):

* Precision lab bench factor 1.6 and study desk 1.0. The reference gives the bench factor as a stat (XML), with a code
  default of 1.0 (D `RimWorld/StatDef.cs:96`, `defaultBaseValue = 1f`, read as the base value at `RimWorld/StatWorker.cs:1248`).
  Kept (ambiguous).
* Keen mind 1.35 (`Trait.KEEN_MIND`). No reference value in the table. Kept (C, UNVERIFIED).

## Rows not adopted, with the reason

Construction and power:

* **Power shortage handling** (reference: every 20 ticks, 5% of running consumers shed at random; restart attempts every
  `max(30, 200 / waiting)` ticks). Needs a finer power step than Stillmere's 625-tick network step, and new randomness in the
  power tick. Deferred.
* **Battery reserve before restarting consumers** (reference: 5 Wd held back when batteries hold 0.1 Wd or more). Part of the
  shedding change above. Deferred.
* **Breeze mill wind update interval** (reference: every 250 ticks). Stillmere runs the power network once per slow tick, so
  the turbine would need its own timer. Deferred.
* **Breeze mill blocked wind path** (reference: each blocked cell in front removes 20%). Needs the turbine's facing
  geometry. Deferred.
* **Breeze mill wind range** (reference: base noise 0.04 to 2.0, times the weather factor, XML). The walk stays as it is.
* **Battery charge efficiency** (see above). UNVERIFIED.
* **Builder progress rate** (reference: stat and per-material factors are XML). Kept.
* **Construction learning and research learning** (already matched, see discrepancies).
* **Out of this round's scope (section 3.8)**: fire model, zone priorities and filters, roof collapse, live-wire-free
  transmitters, work-table outdoor and temperature penalties, snow clearing. Each stays as the gap report lists it.
* **Sun array output curve** (reference: sun elevation and latitude). Depends on the time-of-day model in section 3.1.

Plants and food:

* **Cut and harvest learning gate** (reference: cutting learns at 0.085 per tick when the plant has a yield and can yield now, `RimWorld/JobDriver_PlantCut.cs:11-13`; that is above 65% growth for a crop, `Plant.cs:115`). Stillmere learns only from a fully grown plant that yields (`JobDrivers.kt` `driveCut`, a C rule, the same rate of 0.085). The gate is not in this round's adopted rows. The reference's harvest class sets its rate in `Init` with no such test (`RimWorld/JobDriver_PlantHarvest.cs:11`); whether the base class gates it was not checked here. Kept; a decision for the owner, because it changes learning for crops between 65% and full growth.
* **Roofs and plant glow** (not checked against the reference this round). A roofed cell has cell light 0, but its glow is still the daylight, as it was before this round, because `plantGlow` keeps the daylight as the base. So a natural roof does not cut plant growth here. Kept as the old behaviour; open.
* **Death from lack of light** (reference: 450,000 ticks unlit, counter +2000 per long tick). Needs a persisted counter on
  every plant, so it changes the save format. Deferred to a later version bump with its own fixture.
* **Plant lifespan** (reference: 8 times grow days, then dying damage). Dying damage needs plant hit points, which Stillmere
  does not have. Deferred.
* **Wild plant regrowth and sapling spread** (reference: low confidence, the chances are declared but unused in one place).
  Kept.
* **Starting growth of spawned plants** (reference: ambiguous, map generators set it per case). Kept.
* **Fertility minimum for sowing** (reference: 0.9 default, potato XML value). Kept (any fertility above 0).
* **Food choice rot cut-off** (reference: dessicated food never eaten; rotting food still eaten; fresh food due to rot within
  30,000 ticks gets +12). Depends on the rotting boundary of non-meat foods, which is XML. Kept (88% cut-off).
* **Food poisoning for other foods** (40% over 65% rot). See the adopted table: kept for non-meat foods.
* **Cooking output count** (reference: ceil(count x efficiency), efficiency = worker stat x table stat). The efficiency
  stat has no source mapping in Stillmere, and at efficiency 1 the formula leaves the output unchanged. Kept.
* **Corpse dessicated stage and rot damage** (reference: 5 days dessicated, 2 damage per day while rotting). Stillmere
  removes a corpse at full rot instead, which the gap report already lists. Kept.
* **Outdoor deterioration of raw meat** (reference: rate 6, low confidence). Kept.
* **Mash works** (reference: no mash bench identifier in the source; the beer vessel is a fermenting barrel with 25 wort
  capacity and 360,000 base ticks, `Building_FermentingBarrel.cs:17, 19`). The gap report's claim that a mash works is not
  in the base game stays UNVERIFIED; no change.

Research:

* **Tech-level cost factor** (reference: `1 + 0.5 x (project level - researcher level)`, capped at Industrial). The colony's
  own tech level is set in the faction XML (`FactionDef.techLevel`), which the decompile does not contain, and Stillmere
  has no mapping from its three tiers to vanilla levels. Adopting a guessed level would scale tier 2 and 3 costs by 1.5x
  and 2x. Deferred to the owner's decision.
* **Tech-level scale** (eight levels against three tiers). Depends on the tech-level model above. Kept (three tiers).
* **Bench eligibility and facility offsets** (reference: a project names one bench, and linked facilities add stat offsets,
  both XML). Kept.
* **Techprints** (reference: Royalty-only, forced to 0 without Royalty). Matched: Stillmere has none.
* **Prerequisites, powered bench, completion** (reference: matched). Unchanged.

## Deep drill bug fix (not a reference value)

**Defect.** A subsoil auger brings up the ore of the cell it stands on, but ore is only set on rock cells
(`GameMap.kt` generation), and a building cannot be placed on rock. So a real auger always yields rough rock. The test
`BaseContentTest.aPoweredSubsoilAugerBringsUpItsOre` passed only because it wrote ore under a drill by hand.

**Fix.** The subsoil auger may be placed on a rock cell that holds ore. When the auger is finished, its rock cell is
mined out to gravel and the ore stays in the cell, so the drill pulls the ore from its own cell. Placement on rock without
ore is still refused, and placement on passable ground without ore still yields rough rock as before. The mining rule
mirrors the mining job (`JobDrivers.kt` `driveMine`): terrain to gravel, roof and walk state updated, and the ore not
removed. The conversion happens at completion, so a cancelled blueprint leaves the rock as it was.

Test: `BaseContentTest.aSubsoilAugerPlacedOnOreRockYieldsItsOwnOre` (new), which places the auger through `placeBlueprint`,
completes it with the real build job, and checks that the ore comes up.

## Save version and migration

Version 35 (`SaveGame.kt`). The format is unchanged. The meaning of three saved numbers changes:

* **Item rot** is saved as the fraction of an item's spoil time that has passed. The spoil time changed for raw meat (4 days
  to 2) and for corpses (2.2 days to 2.5). Items read from a version 34 or older save are converted when read:
  raw meat rot x2 (4 / 2), corpse rot x0.88 (2.2 / 2.5). Two read sites hold items: map items and job stacks, both through
  `migrateRot`.
* **Held food rot** (`Job.heldRot`, the rot of the food an eat job holds) is saved the same way and is converted when read
  from a version 34 or older save (`heldRotFromOld`). The eat job's food type, saved just before the rot, decides the rescale:
  raw meat x2 (4 / 2), any other food unchanged, since no other food's spoil time changed. The conversion changes no outcome,
  because the spoiled-stomach roll never applies to raw meat. It keeps the saved number's meaning the same in every place the
  rot is stored.
* **Research progress** is saved in the unit-scaled points. The unit is unchanged (`baseCost x 16`), so it needs no
  migration.
* **Battery charge** is saved in Wd and is still valid under the larger capacity. No migration.
* **Plant growth** is saved as a fraction. The meaning is unchanged (only the rate changed). No migration.
* **Cell light and glow** are not saved. `lightTick` recomputes the cell light each slow tick, so the glow change (`plantGlow`)
  needs no migration and no save bump.

Fixture: `sim/src/test/resources/legacy/colony_v34.sav` and `colony_v34_expect.txt`, made by a temporary generator from the
version 34 code before this change. Test: `LegacyEconomyMigrationTest` (new). It loads the fixture, checks the rot conversion,
and checks that the version is 35 on the next write. The fixture holds no held food, so the held-food conversion is tested
with streams the current writer produces at version 34 (the layout is the same at 34 and 35).

## Tests

* New: `EconomyFidelityTest.kt` (27 tests: battery drain and capacity, breeze cap, sun array roof share, the growth, the grow
  tray factor, fertility, temperature, light (the cell glow as the brighter of daylight and cell light, a world tick at dim
  daylight that grows on the ramp, and the clear-day average), harvest (zero at or below the minimum), rot and sowing rules,
  research points and session, and the raw meat constants), `LegacyEconomyMigrationTest.kt` (9 tests, with the version 34
  fixture and three held-food
  tests), and two auger tests in `BaseContentTest.kt`
  (`aSubsoilAugerPlacedOnOreRockYieldsItsOwnOre` and `aSubsoilAugerIsStillRefusedOnRockWithNoOre`).
* Updated (assert the old current version 34, now 35): `LegacyThreatsMigrationTest.kt` (1), `LegacyNeedsMigrationTest.kt` (1),
  `LegacySkillsMigrationTest.kt` (2), `LegacyNamesMigrationTest.kt` (3). One test name changed:
  `LegacyNeedsMigrationTest.aVersionThirtyOneSaveWrittenAgainIsVersionThirtyFour` is now `...IsTheCurrentVersion`.
* No other existing test asserted an economy value. The crop, research, battery, auger and spoilage tests in `GameTest.kt`
  and `BaseContentTest.kt` pass unchanged.
* One existing test changed for the new rate: `GameTest.cropsGrowAndGetHarvested` is now
  `cropsSownInAGrowingZoneGrowOnTheReferenceRate`. The old test asserted a planted rice crop after 9 game days. Rice takes about
  11.6 game days of light here, so the first crops are still growing at 8 days (about 0.57 in that seed), and the cold snap on
  day 9 kills immature crops by the existing frost rule (`World.kt` `plantsTick`, 15% a slow tick below -2 C). The test now
  checks growth after 8 days: a crop is above 0.4. The old assertion held on the old rate, which ripened rice in about 5.4 days,
  before the cold snap. It fails on the reference rate, which is why it was changed.
* Full run: 682 tests, 0 failures, 0 errors, 0 skipped (`./gradlew :sim:test --console=plain`).

## Code

`sim/src/main/kotlin/io/github/teamomuito/colony/sim/`: `World.kt` (`powerTick`, `lightTick` (the cell light the plants read),
`plantsTick`, `plantGlow`, `spoilTick`, `productionTick`), `Defs.kt` (`Research.cost`, `PlantType`, constants), `Buildings.kt` (no change to costs or powers),
`JobDrivers.kt` (`driveCut`, `driveSow`, `driveResearch`, `driveButcher`, the poisoning roll in `driveEat`, the auger
completion in `driveBuild`), `Jobs.kt` (`findGrow`, the sowing filter), `GameMap.kt` (`Plant.mature`), `Game.kt`
(`canBuildCell`, `placeBlueprint`), `SaveGame.kt` (version 35, the rot migration and the held-food rot). The grow tray's factor is `GROW_TRAY_GROWTH_FACTOR` in `Defs.kt`.
