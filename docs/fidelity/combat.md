# Combat model (gap report 3.11)

Phase from [gap-report.md](gap-report.md), section 3.11. This round works from the combat rows of the decompiled reference
(version 1.6.9438.38202, see [decompiled-reference.md](decompiled-reference.md), section 3.11). The brief asks for accuracy by
range band, an armour roll with deflection and penetration, melee dodge, burning from incendiary hits and fire, and cover that
blocks or reduces hit chance. Per-weapon band accuracy is not done (plan item 1); the other parts are.

## Tags

* **D** (adopted from the decompiled reference). The value or rule comes from a row of the verified table in
  `decompiled-reference.md`. Every D here gives the file and line of the source, checked by opening that line.
* **C** (project choice). A value Stillmere chose, or a mapping from a vanilla rule to a Stillmere field. Not taken from the source.
* **UNVERIFIED**. A value that vanilla gets from XML or a stat definition that the decompiled C# does not contain. The number is a
  placeholder, kept until it can be checked.

Vanilla base values for stats such as shooting accuracy, melee hit and dodge chance, and armour ratings are XML in the game, not
in the C# source. The decompiled reference says so for armour (`Verse/ArmorUtility.cs:58`, "values are XML"). Every such value
here is therefore UNVERIFIED or C, never D.

## Starting point (read before changing)

* `Ai.kt` `fire`: ranged reach `d > range + 1`; one accuracy per weapon scaled by `(0.62 + 0.03 skill)`, a distance falloff
  `1.15 - d / (1.35 range)`, sight, the best neighbour cover, traits, fog, suppression and body size `(0.55 + 0.45 size)`; floor
  0.05, cap 0.95. Damage `x uniform(0.85, 1.15)` per shot. Melee reach `2.1`, one hit roll, no dodge.
* `Health.kt` `dealDamage`: armour is the sum of all covering apparel (capped at 0.9); a hit is deflected when `roll < armour / 2`
  and the rest is small; otherwise damage is multiplied by `(1 - armour)`. Apparel loses 8 or 10 percent of the damage.
* `World.kt` `fireTick`: every fire cell does `3.5 x intensity` to every pawn on it every 50 ticks. `explode` hits each pawn twice
  (BLAST `damage x fall` and `0.5 x damage x fall`).
* Save `VERSION` is 35 (`SaveGame.kt:34`). Pawn burning state is new, so it needs a version bump (see "Save format" below).

## Plan

1. **Equipment accuracy by range band.** One stated accuracy per weapon (`Weapon.accuracy`) serves the touch and short bands. The
   medium and long bands are that value times 0.9 and 0.7 (C). The reference has four accuracy stats per equipment, one per band,
   each defaulting to 1.0 (`Verse/VerbProperties.cs:160-166`, row 430). Per-weapon band values are not added: the vanilla values
   are XML, and per-weapon numbers here would be invented. The blend across the breakpoints 3, 12, 25 and 40 cells, and the clamp
   to 0.01 to 1, follow the reference (`Verse/VerbProperties.cs:652-653`).
2. **Shooter factor.** The aim falls by a factor `f` for each cell: `f ^ distance` (`Verse/ShotReport.cs:168`). `f` comes from
   marksmanship (C base). The equipment band factor multiplies the result, and the product is floored at 0.0201
   (`Verse/ShootTuning.cs:17`). Stillmere's skill term moves into `f`. The reference applies this factor only when the verb can go
   wild (`Verse/ShotReport.cs:90-93`); Stillmere applies it to every pawn shot (C). The per-pawn band stats
   (`Verse/ShotReport.cs:169-172`) are not applied and are treated as 1.0 (UNVERIFIED, XML).
3. **Aim chance after the shooter factor.** In the reference's order (`Verse/ShotReport.cs:58-74`): a downed target within 3.9
   cells takes a x7.5 execution factor (`Verse/ShotReport.cs:50-53`); the product is floored at 0.0201; the target size factor
   applies (body size clamped to 0.5 to 2, `Verse/ShotReport.cs:129-135`); then a downed target at 4.5 cells or more takes x0.5
   (`Verse/ShotReport.cs:38-41`). The aim is clamped to 0 to 1 (C; the reference clamps the total hit chance, not the aim, at
   `Verse/ShotReport.cs:78`). The weather, trait, suppression, sight and manipulation factors are Stillmere's (C; see the table).
   "Downed" stands in for the reference's "not standing" posture (C).
4. **Two rolls for each pellet.** First the aim roll (`aim chance`). A pellet that is aimed well then faces the cover pass. The
   reference also rolls the aim first (`Verse/Verb_LaunchProjectile.cs:153`) and then the cover (`Verse/Verb_LaunchProjectile.cs:167`).
   Stillmere has no stray misses (not adopted, see below).
5. **Cover pass.** The eight cells around the target are scanned (`Verse/CoverUtility.cs:18-25`). Each neighbour's block chance is
   its base, times an angle factor (the angle between the neighbour and the shooter, seen from the target), times a distance factor
   from the shooter. Overall block is one minus the product of (one minus each block) (`Verse/CoverUtility.cs:42`). Full-fill
   buildings (walls) have base 0.75 (`Verse/CoverUtility.cs:106`); others use their cover value (sandbags 0.55, kept). The cover is
   read again for each pellet, so a cover that one pellet shot apart stops no more pellets or bursts (C; the reference computes the
   cover in the shot report of each launch, `Verse/Verb_LaunchProjectile.cs:150-151`). A pellet that passes the aim roll and fails
   the block roll is absorbed by a cover building chosen by weight (`Verse/Verb_LaunchProjectile.cs:167-178`;
   `Verse/ShotReport.cs:249-256`).
6. **Armour roll.** Covering apparel is applied layer by layer, outermost first (the slot order is C; the reference walks its worn
   list from the end, `Verse/ArmorUtility.cs:25-42`), then natural armour (for mechanoids only, C; the reference applies the pawn's
   own armour to every pawn, `Verse/ArmorUtility.cs:44-45`). The rating kind is fixed when the hit arrives: sharp for the sharp kinds
   and BLAST, blunt for the rest. It is read once and used for every layer and for natural armour, even after a halved sharp hit has
   become blunt for the wound (`Verse/ArmorUtility.cs:21`; the BLAST mapping is C). Each layer takes 25 percent of the damage that
   reaches it (`Verse/ArmorUtility.cs:70`). With rating `r` (after penetration, floored at 0) and a uniform roll `u`:
   `u < r/2` deflects (damage 0; `Verse/ArmorUtility.cs:11, 75-77`); `r/2 <= u < r` halves the damage, and a sharp kind becomes BRUISE
   for the wound (`Verse/ArmorUtility.cs:81-86`); otherwise nothing changes. A layer with rating 0 is not rolled, which gives the
   same outcome as the reference's roll. No cap on armour (`Verse/ArmorUtility.cs:9`).
7. **Penetration default.** A weapon without a hand-set penetration uses damage x 0.015 (`Verse/ProjectileProperties.cs:181` for
   ranged, `Verse/VerbProperties.cs:379` for melee). For ranged, the damage is the damage per pellet; for melee, the weapon damage.
   Hand-set values stay (C).
8. **Damage.** Ranged damage is deterministic: the weapon damage per pellet times the weapon damage multiplier (quality, and
   training for animals). The reference rounds the projectile damage to an integer (`Verse/ProjectileProperties.cs:145`), which is
   not adopted (see the table). Melee damage is the weapon damage times uniform(0.8, 1.2) (`RimWorld/Verb_MeleeAttackDamage.cs:22`).
   A melee hit under 1 is raised to 1 and becomes blunt (`RimWorld/Verb_MeleeAttackDamage.cs:23, 26-37`). The reference's rule sits in
   the branch for a pawn caster, and every Stillmere attacker is a Pawn, animals included (`Verse/Verb.cs:125`), so `meleeFloor`
   applies it to all of them.
9. **Melee.** Reach 1.42 cells, adjacent including diagonals (`Verse/ShootTuning.cs:13`). Two rolls, in order: the non-miss roll
   (`RimWorld/Verb_MeleeAttack.cs:43`) and the dodge roll (`RimWorld/Verb_MeleeAttack.cs:45`). Both are skipped for a downed target,
   which stands in for the reference's immobile target (C): the reference skips the non-miss roll and the dodge for an immobile
   target (`RimWorld/Verb_MeleeAttack.cs:126-134, 159-168`), so a downed target is always hit. The dodge chance is
   `min(0.45, 0.04 x close combat skill)` (C; the reference's dodge stat is XML). The old target-skill term was
   `max(0.55, 1 - 0.04 x skill)`, which equals `1 - min(0.45, 0.04 x skill)`, so the attacker's skill effect is the same. The old
   0.95 cap applied to the whole chance after that term. The new cap applies to the attacker's chance before the dodge, so the two
   differ where the attacker's chance is over 0.95 or under 0.05 (C; above 0.95 the new chance is lower). Parry: see the not-adopted
   list. A swing that leaves the target alive and on the map sets a 95-tick stagger on the target, hit or miss
   (`RimWorld/Verb_MeleeAttack.cs:96-98`). "On the map" is `t in pawns`, which stands for the reference's Spawned check at line 96.
10. **Burning.** A pawn catches fire from (a) a flame hit (kind BURN) that the armour does not deflect: the cumulative chance over
    60 ticks (`Verse/DamageWorker_Flame.cs:21`; `RimWorld/FireUtility.cs:94`), with a size of uniform 0.15 to 0.25
    (`Verse/DamageWorker_Flame.cs:23`); and (b) standing in a fire cell with intensity above 0.4: the cumulative chance over 150
    ticks (`RimWorld/Fire.cs:267`), with a size of 0.2 x intensity (`RimWorld/Fire.cs:269`). The chance per second comes from
    flammability through the reference curve (`RimWorld/FireUtility.cs:9-15`). Catching fire sets the burn size and stops the
    pawn's job (`RimWorld/FireUtility.cs:122`). A burning pawn takes `clamp(0.0125 + 0.0036 x size, 0.0125, 0.05) x 150` damage every
    150 ticks, unrounded (`RimWorld/Fire.cs:348`), and one worn apparel item takes the same damage (`RimWorld/Fire.cs:360-363`).
    The burn size drops by 0.15 at each check, and by 0.3 in rain (C). The direct 3.5 x intensity damage to pawns in fire cells is
    removed (C).
11. **Explosions.** Each pawn in the radius takes one hit, damage x `1 - d / (radius + 0.5)`, not two. The reference hits each thing
    once per explosion (`Verse/DamageWorker.cs:302`). An incendiary blast is one BURN hit, so a flame hit can set the pawn alight.
12. **Turrets.** A turret shot deals the weapon damage with no variance and the penetration default (`World.kt`, `turretsTick`).
    Its accuracy stays flat, and distance and cover are not applied (not adopted, row 460).

## Constants

| Constant | Value | Source | Tag |
|---|---|---|---|
| Accuracy band breakpoints | 3, 12, 25, 40 cells | `Verse/ShootTuning.cs:5-11`; `Verse/VerbProperties.cs:652` (row 431) | D |
| Blend across the breakpoints | linear between the band values | `Verse/VerbProperties.cs:652` (row 430) | D |
| Equipment factor clamp | 0.01 to 1 | `Verse/VerbProperties.cs:653`; minimum also `Verse/ShootTuning.cs:15` | D |
| Band values, touch and short | the stated accuracy (`Weapon.accuracy`) | the reference's per-equipment band stats are XML, default 1.0 (`Verse/VerbProperties.cs:160-166`) | C |
| Band values, medium and long | accuracy x 0.9; accuracy x 0.7 | none | C |
| Minimum aim chance | 0.0201 | `Verse/ShootTuning.cs:17` (row 431); `Verse/ShotReport.cs:64-66, 173` | D |
| Shooter factor is `f ^ distance` | exponent is distance in cells | `Verse/ShotReport.cs:168` (row 460) | D |
| Shooter base `f` | `0.96 + 0.002 x marksmanship`, capped at 1 (`Combat.kt` `shooterAccuracy`) | the stat is read at `Verse/ShotReport.cs:167` and is XML | C; base UNVERIFIED |
| Pawn band stats (touch, short, medium, long) | not applied (treated as 1.0) | `Verse/ShotReport.cs:169-172` (XML stats) | UNVERIFIED |
| Shooter factor only for verbs that can go wild | not modelled: every pawn shot takes it | `Verse/ShotReport.cs:90-93` | C (difference) |
| Target size factor | body size clamped to 0.5 to 2 | `Verse/ShotReport.cs:129-135` (row 448) | D |
| Prone target at 4.5 cells or more | x 0.5 (`Ai.kt` `aimChance`) | `Verse/ShotReport.cs:38-41`; `Verse/ShootTuning.cs:19-21` (row 449) | D |
| Execution, downed target at 3.9 cells or less | x 7.5 (`Ai.kt` `aimChance`) | `Verse/ShotReport.cs:50-53`; `Verse/ShootTuning.cs:23-25` (row 449) | D |
| Steady aim (trait) | x 1.12 (`Ai.kt:73`) | none | C |
| Fog | x 0.85 (`Ai.kt:74`) | the reference's weather multiplier is XML (`Verse/ShotReport.cs:119`) | C; value UNVERIFIED |
| Quick trigger (trait) | x 0.92 (`Ai.kt:75`) | none | C |
| Suppressed shooter | x 0.7 (`Ai.kt:76`) | none | C |
| Sight factor floor | sight capacity at least 0.2 (`Ai.kt:77`) | none | C |
| Manipulation factor floor | manipulation at least 0.35 (`Ai.kt:78`), not for animals | none | C |
| Suppression of a target | a volley that hits nothing: 1 hour, mood -0.04 (`Ai.kt:164-165`) | none | C |
| Aim chance clamp | 0 to 1 (`Ai.kt` `aimChance`) | `Verse/ShotReport.cs:78` clamps the total, not the aim | C |
| Cover: 8 neighbours scanned | the 8 cells adjacent to the target | `Verse/CoverUtility.cs:18-25` (row 455) | D |
| Cover angle factors | 1.0, 0.8, 0.6, 0.4, 0.2 under 15, 27, 40, 52, 65 degrees; none at 65 or more | `Verse/CoverUtility.cs:63-88` (row 456) | D |
| Cover diagonal angle | x 1.75 | `Verse/CoverUtility.cs:61` | D |
| Cover distance factor | x 0.3333 under 1.9 cells; x 0.66666 under 2.9 cells | `Verse/CoverUtility.cs:89-97` (lines 92 and 96) | D |
| Overall block | 1 - product of (1 - block) | `Verse/CoverUtility.cs:42` (row 456) | D |
| Full-fill base block | 0.75 | `Verse/CoverUtility.cs:106` (row 457) | D |
| Non-full cover base | the building's cover value; sandbags 0.55 (kept) | `Verse/CoverUtility.cs:108` (the fill percent); `BuildDef.SANDBAGS.cover` is existing | C; vanilla fill percent UNVERIFIED |
| Cover absorbs a shot by weight | a cover building picked with weight equal to its block chance | `Verse/Verb_LaunchProjectile.cs:167-178` (row 458); `Verse/ShotReport.cs:249-256` | D |
| Cover read again per pellet | a cover shot apart stops no more pellets | none (see plan item 5) | C |
| Damage a cover building takes | the pellet's damage | none | C |
| Ranged reach | `d <= range`, no +1 | `Verse/Verb.cs:810` (row 436) | D |
| Ranged damage | per pellet: weapon damage divided by pellets, times the damage multiplier; no variance | `RimWorld/Bullet.cs:23` (the bullet applies its damage); `Verse/ProjectileProperties.cs:145` shows the rounding that is not adopted (row 438) | D (no variance); rounding differs |
| Melee damage variance | x uniform(0.8, 1.2) | `RimWorld/Verb_MeleeAttackDamage.cs:22` (row 439) | D |
| Melee damage under 1 | raised to 1 and made blunt | `RimWorld/Verb_MeleeAttackDamage.cs:23, 26-37`; caster is a Pawn, `Verse/Verb.cs:125` | D (rule); C (all attackers are Pawns) |
| Penetration default (projectile) | damage per pellet x 0.015 | `Verse/ProjectileProperties.cs:181` (row 440) | D |
| Penetration default (melee) | weapon damage x 0.015 | `Verse/VerbProperties.cs:379` (row 465) | D |
| Armour rating after penetration | max(rating - penetration, 0) | `Verse/ArmorUtility.cs:73` (row 441) | D |
| Armour rating kind | sharp for CUT, STAB, BULLET, SCRATCH, BITE and BLAST; blunt for the rest; read once per hit | `Verse/ArmorUtility.cs:21` (row 442) | D (read once); C (the kind mapping, BLAST as sharp) |
| Blunt conversion of a halved hit | a sharp kind becomes BRUISE for the wound; BLAST and BURN keep their kind | `Verse/ArmorUtility.cs:84-86` (the conversion); kind mapping C | D (rule); C (mapping) |
| Armour roll | u < r/2 deflects; r/2 <= u < r halves; the random rounding of the halved damage (line 83) is not adopted | `Verse/ArmorUtility.cs:74-88` (row 442) | D |
| Apparel share of the damage | 25 percent of the damage that reaches the layer | `Verse/ArmorUtility.cs:70` (row 444); the rounding at line 71 is not adopted | D |
| Armour cap | none | `Verse/ArmorUtility.cs:9` (row 443; the declared 2 is not used in the file) | D |
| Layer order | covering layers outermost first, then natural armour | `Verse/ArmorUtility.cs:25-45` (the reference walks the worn list from its end); slot mapping C | C (mapping) |
| Apparel rating (quality and hit points) | kept: quality x (0.5 + 0.5 hp fraction) | existing; the reference row 446 is ambiguous | C |
| Apparel rating values | kept from Stillmere (for example, Brigandine 0.75 sharp, 0.35 blunt) | none | UNVERIFIED |
| Natural armour | mechanoids only; blunt at 0.6 of sharp (`Pawn.kt` `naturalArmor`) | the reference applies the pawn's armour to every pawn, `Verse/ArmorUtility.cs:44-45` (row 445) | C; 0.6 UNVERIFIED |
| Armour for fire and burn hits | the BURN kind goes through the armour at the blunt rating (`Health.kt`) | the reference returns the damage unchanged for a damage kind with no armour category, `Verse/ArmorUtility.cs:17-19`; the category of flame is XML | C; UNVERIFIED |
| Quality factor clamp (melee and ranged) | the weapon damage multiplier clamped to 0.8 to 1.15 for the hit chance (`Ai.kt:95`) | none | C |
| Melee reach | 1.42 cells, adjacent including diagonals | `Verse/ShootTuning.cs:13` (row 463) | D |
| Melee non-miss roll | accuracy x (0.62 + 0.03 skill) x quality clamp x trait 1.12 (`Ai.kt:96`, STREET_SCRAPPER) x manipulation, clamped to 0.05 to 0.95 (`Ai.kt:97-98`) | the stage: `RimWorld/Verb_MeleeAttack.cs:43`; the stat is `Verb_MeleeAttack.cs:136` (XML) | D (stage); C (formula and values) |
| Melee dodge roll | target's dodge chance `min(0.45, 0.04 x close combat skill)`; none for a downed target | the stage: `RimWorld/Verb_MeleeAttack.cs:45`; the stat and skips: `Verb_MeleeAttack.cs:159-177` | D (stage); C (value); UNVERIFIED (stat) |
| Melee rolls skipped for a downed target | both rolls skipped; the target is always hit | `RimWorld/Verb_MeleeAttack.cs:126-134, 159-168` (immobile targets) | D (rule); C (downed stands in for immobile) |
| Melee stagger | 95 ticks, set after each swing that leaves the target alive and on the map, hit or miss | `RimWorld/Verb_MeleeAttack.cs:96-98` (row 462) | D |
| Animal melee size factor | x (0.7 + 0.3 x body size) (`Ai.kt:180`, existing) | none | C |
| Cooldown factors (quick trigger, steady aim, manipulation) | x 0.85 and x 1.25; manipulation clamped to 0.4 to 1; floor tk(8) ranged, tk(10) melee (`Ai.kt:169, 185`, existing) | cycle seconds `Verse/VerbProperties.cs:579-581`; the cooldown factors are XML (row 435) | C; not changed this round |
| Burn chance curve per second | 0 at 0; 0.07 at 0.1; 1 at 0.3; 1 above | `RimWorld/FireUtility.cs:9-15` (row 469) | D |
| Cumulative catch chance | 1 - (1 - p)^(ticks / 60) | `RimWorld/FireUtility.cs:97-109` (formula at 107-108) | D |
| Flame hit catch window | 60 ticks | `Verse/DamageWorker_Flame.cs:21`; `RimWorld/FireUtility.cs:94` | D |
| Flame hit needs a hit that is not deflected | yes | `Verse/DamageWorker_Flame.cs:21` (`!damageResult.deflected`) | D |
| Flame attach size | uniform 0.15 to 0.25 | `Verse/DamageWorker_Flame.cs:23` (row 469) | D |
| Cell catch gate and window | fire size above 0.4; 150 ticks | `RimWorld/Fire.cs:267` (row 469) | D |
| Burn size on a cell catch | 0.2 x fire size | `RimWorld/Fire.cs:269` (row 469) | D |
| Pawn flammability | 1.0 for humans and animals; 0 for mechanoids | none (XML stat); the mechanoid choice is C | UNVERIFIED (1.0); C (0) |
| Fire damage per 150 ticks | clamp(0.0125 + 0.0036 x size, 0.0125, 0.05) x 150, unrounded | `RimWorld/Fire.cs:348` (row 470); the rounding and the minimum of 1 (lines 348-352) are not adopted | D; rounding differs |
| Burn damage to apparel | one random worn item takes the same damage | `RimWorld/Fire.cs:360-363` (row 470) | D |
| Burning pawn and armour | fire damage is not reduced by armour (`Burning.kt` `burnTick`) | the reference's fire category is XML | C |
| Stop jobs on catching fire | yes | `RimWorld/FireUtility.cs:122` (row 469, lines 111-126) | D |
| Fire size used for a burning pawn | the cell fire's intensity is read as fire size | none | C |
| Burn decay per 150-tick check | 0.15; in rain 0.3 | none | C; dynamics UNVERIFIED |
| Incendiary blast | one BURN hit per pawn | `Verse/DamageWorker.cs:302` (once per thing per explosion, row 478) | D (once per thing); C (BURN kind) |
| Explosion falloff | 1 - d / (radius + 0.5) | existing; the reference's flag is XML (`Verse/Explosion.cs:219-227`, row 477, ambiguous) | C |

## Save format

Pawn burn size and the melee stagger end are saved. Both are new in version 36. Each is written at the end of the pawn record and
read only when the version is 36 or higher. The version 35 fixture `colony_v35.sav` loads with both at zero. Older saves are
unchanged. Numbers that are not saved need no migration. The rules changed in this round (rating kind, cover per pellet, melee
floor, stagger guard, cover distance literals) are not saved, so the version stays 36.

## Rows not adopted, with reasons

| Row (3.11) | Reason |
|---|---|
| 433 Warmup reset on target change | The reference finds no reset in the warmup code (`Verse/Verb.cs:357` is the warmup stance; the search found no target-switch reset), but the row is medium confidence and reports only a negative search. The existing test `aimingAtANewTargetLosesTheWarmupBuiltUpOnTheOld` pins the reset. Kept until a second check. |
| 434 Burst spacing (15 ticks) | Stillmere resolves a burst in one call. Spacing needs per-tick burst state; `Pawn.burstLeft` is saved but unused. Next round. |
| 435 Cooldown factors (quick trigger, steady aim, manipulation) | The cooldown seconds are XML; the trait and manipulation factors are Stillmere's. Not changed. |
| 437 Minimum ranged distance (1.421) | A ranged pawn next to a target would stall. Adoption needs a melee fallback for ranged weapons, which Stillmere lacks. Next round. |
| 441 Armour penetration (matches) | Already the same rule. |
| 445 Natural body armour for every pawn | The values are XML. Only mechanoids have natural armour in Stillmere, kept. |
| 446 Quality and hit points scale armour | The reference row is ambiguous. Kept. |
| 450 Melee skill gain per swing (200 x cycle seconds) | The skill XP model is in `skills-traits.md` (round 14) and belongs to 3.6. Not changed here. |
| 451 Wild miss (stray hits) | The miss-distance curve needs the forced-miss radius, not in the verified table. Next round. |
| 452 Walls stop shots only beyond 5 cells | Stillmere's rule is that any sight-blocking building on the line stops the shot. The existing test `aWallOnTheLineStopsTheShotEntirely` pins it. The intercept and impact model is a larger change. Next round. |
| 453 Pawns on the line intercept by chance | Needs the intercept chances in `Verse/Projectile.cs` and `Verse/ShootTuning.cs:75`, not in the verified table. Next round. |
| 454 Impact-cell hits | Same as 453. Next round. |
| 459 Mortar and forced-miss spread | Mortar is outside this round. |
| 460 Turret accuracy, distance and cover | Turret accuracy stays flat. Turret damage and penetration follow the pawn rules (deterministic, penetration default). |
| 466 Blunt stun | The stun curves are not in the verified table. The stun amount formula is recorded in the reference, but the curve values are not. Next round. |
| 467 EMP | No EMP damage type in Stillmere. Out of this round. |
| 468 Energy shields | No shield model in Stillmere. Out of this round. |
| 471 to 475 Fire growth, spread, start chance, rain, lifetime | These are the fire model rows of 3.8 (fire size, spread, the 150-tick growth). They need a fire-size model that Stillmere does not have (intensity only). Next round, with 3.8. |
| 476 Fire-fighting verbs | Pawn fire-fighting is 3.8. Next round. |
| 477 Explosion damage falloff (20 percent at the edge) | The flag is per projectile in XML and the reference marks it ambiguous. Kept as `1 - d / (radius + 0.5)`. |
| 479 Trap spring chances | Traps are not in this round's list. Next round. |
| Parry | The decompiled source has no parry roll. A search of the whole tree for "Parry" finds no file. The dodge roll is the only defence in the melee model, so no parry is added. |
| 464 Melee cooldown multipliers | Tool cooldown seconds and the multipliers are XML. Not changed. |
| Rounding of ranged damage (`Verse/ProjectileProperties.cs:145`) | Stillmere's damage is a float across the sim. Rounding a shotgun pellet of 2.25 gives 2, a change in units that needs its own round. Next round. |
| Rounding of halved hits and apparel damage (`Verse/ArmorUtility.cs:71, 83`) | Same units reason as the ranged rounding. Next round. |
| Rounding of fire damage and the minimum of 1 (`RimWorld/Fire.cs:348-352`) | Same units reason. The minimum never applies here: the smallest burn check is 1.875. |
| Stagger after an explosion wound (`Verse/DamageWorker.cs:330-333`) | The explosion path does not return a wound result per pawn, so no stagger follows an explosion. Next round. |
| Dodge skipped in a busy non-melee stance, and surprise attacks (`RimWorld/Verb_MeleeAttack.cs:128-131, 161-164, 173-176`) | Stillmere has no stance model and no surprise attack. Not adopted. |

## Changes

* Code: `sim/.../Combat.kt` (constants and pure rules; the cover distance factors now 0.3333 and 0.66666 as the source writes
  them; `meleeFloor`; citations corrected), `sim/.../Ai.kt` (`fire`: cover read again for each pellet; the stagger only for a target
  on the map; the melee floor; citation comments), `sim/.../Health.kt` (`dealDamage`: the rating kind read once per hit), and the
  earlier changes: `sim/.../Burning.kt` (catch fire, burn check), `sim/.../Ai.kt` (`aimChance`, `coverAgainst`, the melee swing,
  reach), `Health.kt` (layers, deflection, flame ignition), `Pawn.kt` (`naturalArmor`; `armorFor` kept as a display summary for the
  Android character panel, which the hit roll does not use), `Defs.kt` (`Weapon.armorPen` default is now "not set"), `World.kt`
  (cell fire catch, one explosion hit per pawn, turret damage and penetration), `Game.kt` (burn check and stagger in the pawn step),
  `SaveGame.kt` (version 36).
* Save `VERSION` 35 to 36 (burn size, stagger end). A version 35 fixture (`colony_v35.sav`, with its expectation file) was
  written from the version 35 code before the format change and is loaded by `LegacyCombatMigrationTest`. This round does not
  change the version.
* The fixture and the new test files are uncommitted, as the brief asks.

### Tests changed, with the reason

| Test | Change | Reason |
|---|---|---|
| `CombatPipelineTest.coverOnTheLineOfFireMakesShotsMissMore` | The sandbags are put back before any shot that finds them gone | Cover now takes the damage of the shots it stops. Without this the sandbags broke in the first few shots and the test measured an open line. The block chance is what the test checks. |
| `CombatPipelineTest.aBetterShooterHitsMoreOften` | Compares the hit rate per shot (per mille), not the raw hit count | The target dies after a few hits, and rifle damage is now deterministic, so the raw count depends on how fast the target falls. |
| `CombatPipelineTest.armorReducesDamageAndPenetrationReducesArmor` | Compares the mean of 60 hits on fresh pawns | The armour roll is made per hit (`ArmorUtility.cs:58-88`), so one hit can pass unchanged. |
| `GameTest` `HealthTest.woundsBleedAndTendingHelps` | The starting clothes are removed before the cut | The test is about bleeding and tending. A per-hit roll could deflect the cut. |
| `CombatModelTest.aSandbagBehindTheTargetBlocksItsShareAndASideCoverDoesNot` | The near-cover expectation is 0.55 x 0.3333 instead of 0.55 / 3 | The source writes the factor as 0.3333 (`Verse/CoverUtility.cs:92`), and Stillmere now uses that value. |
| Version assertions in `LegacyEconomyMigrationTest`, `LegacyNamesMigrationTest` (3), `LegacyNeedsMigrationTest`, `LegacySkillsMigrationTest` (2), `LegacyThreatsMigrationTest` | `35` becomes `36` | The current save version is 36. Each assertion checks that a save written now is the current version. |

### New tests

* `CombatModelTest` (27): band blend and clamp, shooter factor, penetration default, armour layer roll, cover geometry and
  combination, flammability curve and cumulative chances, burn damage formula, ranged and melee reach, downed targets, dodge,
  stagger, layer order, one explosion hit per pawn, cover taking damage, flame ignition, burn checks and decay, cell ignition,
  save and load of burn and stagger, and seed reproducibility with burning. This round adds four: the melee floor (under 1 raised
  and made blunt); a swing at a pawn that is off the map does not stagger it; a halved sharp hit is still rolled against the sharp
  rating by the next layer (quarter hits in 400 seeded trials, at least 20 required: 40 with the sharp rating as adopted, and 5
  on the old blunt rating; the rating values predict about 39 and 10); a cover shot apart by one pellet stops no more pellets
  (10 seeds).
* `LegacyCombatMigrationTest` (4): the version 35 fixture loads, reads zero burn and stagger, and is written again as version 36.

## Open (not done this round)

* Burn dynamics: the decay of 0.15 per check (rain 0.3) and the fire size mapping (intensity read as fire size) are Stillmere
  choices. The reference's attached-fire growth and decay are not read, so a burning pawn may burn out too soon.
* Pawn flammability 1.0 is UNVERIFIED. With it, any flame hit and any fire above intensity 0.4 sets a pawn alight.
* Band values for medium and long accuracy (0.9 and 0.7) and the shooter base 0.96 are C. The vanilla values are XML. The per-weapon
  band values (four per weapon in the reference) are not added.
* Dodge values (0.04 per skill point, cap 0.45) are C.
* The rows listed as not adopted above need a second round: the intercept and impact model, the forced-miss radius and
  stray shots, the minimum range for ranged weapons (needs a melee fallback), the stun curves, EMP and shields, the rounding of
  damage units, the explosion-wound stagger, and the fire model rows of 3.8 (growth, spread, start, rain, lifetime).
