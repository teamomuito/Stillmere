# Generated caves

Plan, constants and rows for caves on colony maps. Gap-report section 3.2 holds the audit. The vanilla values come from the
decompiled source at version 1.6.9438.38202 (tag **D**), read from the clone at `/home/user/chillu1/rimworlddecompiled` (commit
`2d50803`). The pinned build is 1.6.9438.38202, the same as the source, so the D values match it (see `decompiled-reference.md`).

The reference's section 3.2 has no caves row. The cave constants below are cited to the decompiled files directly (a D tag with
file and line is enough without a table row). Each D citation was opened at its line in the clone and the value checked there. A
claim that no cited line supports is marked C or not adopted, with its reason. The map rows that touch caves (rock threshold,
natural roofs, rock types) are listed in the "Rows not adopted" table with their reasons.

## Plan

1. **World.** When the world is generated, each tile gets a caves flag once its hilliness is final. Large hills get a 25 percent
   chance and mountain tiles a 50 percent chance, as the base game's mutator does for its mountainous and large-hill tiles. The
   draws come from a generator of their own, seeded from the world seed, so the world's own random sequence is unchanged. The flag
   is not saved: the world is rebuilt from its seed on load, as it already is.
2. **Colony map.** `GameMap.generate` carves caves when the tile it is given (the colony's home, or a settled tile through
   `World.localTerrain`) has the flag. Carving runs after the start pad is found and before the sides are connected, ore is placed
   and plants are placed. Rock inside a cave becomes gravel (passable) and keeps its natural roof. Rock that stays is the cave's
   wall. Open tunnels run out to the surface, so each one is an entrance.
3. **Carver.** A rock mass of at least 300 cells (after its edges are trimmed) gets open tunnels (to the surface) and closed
   tunnels (sealed inside the rock). The counts, widths, step sizes and branching are the base game's values, listed below. The
   carver's random draws come from a generator of their own (seeded from the map seed), and its heading comes from a value noise
   of its own. The map's own generator is not used, so a map without caves is identical to the map before this change.
4. **Battle maps** have no world tile, so they get no caves. Their terrain is the same as before.
5. **Ore and plants.** Stillmere's ore veins grow only through rock cells, and carving runs before ore, so a cave gets no ore. The base game skips carved cells during map setup (`GenStep_ScatterLumpsMineable.cs:108`).
   Plants follow the new terrain as they always do.
6. **Save format: unchanged, `VERSION` stays 39.** No saved field is added and no enum constant is added (the cave floor is the
   existing `Terrain.GRAVEL`). Caves are written as terrain and as the roof flag, which the save already holds. A map written
   before this change loads as it was. No fixture is needed for a format that does not change.

## Constants

Tags: **D** = decompiled source (file and line in the table); **C** = Stillmere's own choice; **?** = not confirmed, marked UNVERIFIED.
Repository prefix: the file paths are under `RimWorld/`, `Verse/` or `RimWorld.Planet/` in the decompiled tree.

| Constant | Value | Source | Where in sim/ | Status |
|---|---|---|---|---|
| Rock group minimum for any tunnel | 300 cells | D `RimWorld/MapGenCavesUtility.cs:48`, checked at `:88` | `Caves.MIN_ROCKS_FOR_TUNNELS` | adopted |
| Open tunnels per 10,000 rock cells | 5.8 | D `RimWorld/MapGenCavesUtility.cs:43` | `Caves.OPEN_TUNNELS_PER_10K` | adopted |
| Open tunnels per group: jitter and cap | jitter 0.9 to 1.1 (`:99`); cap 3 (`:45`, `:100`); count then drawn from 1 to the cap (`:103`) | D as cited | `Carver.digOpenTunnels` | adopted |
| Closed tunnels per 10,000 rock cells | 2.5 | D `RimWorld/MapGenCavesUtility.cs:44` | `Caves.CLOSED_TUNNELS_PER_10K` | adopted |
| Closed tunnels per group: cap | cap 1 (`:46`, `:139`); count then drawn from 0 to the cap (`:142`) | D as cited | `Carver.digClosedTunnels` | adopted |
| Tunnel width curve (rock count to width) | (100, 2), (300, 4), (3000, 5.5): 2 at 100 rock cells, 4 at 300, 5.5 at 3000 | D `RimWorld/MapGenCavesUtility.cs:55-60` (curve), evaluated at `:105` and `:144` | `Caves.WIDTH_CURVE` | the three points are adopted. C: the straight-line steps between the points and the flat ends outside them are Stillmere's choice; the base game's evaluation between and beyond the points is not cited here (UNVERIFIED) |
| Tunnel width draw | from 0.8 to 1.0 times the curve (open `:126`, closed `:159`) | D as cited for the bounds | `Carver.digOpenTunnels`, `Carver.digClosedTunnels` | the bounds are adopted. The spread within them is Stillmere's draw (C; the base game's distribution is not cited here) |
| Width lost per cell along a tunnel | 0.034 | D `RimWorld/MapGenCavesUtility.cs:49`, and the caves worker's value at `RimWorld/TileMutatorWorker_Caves.cs:25` | `Caves.WIDTH_LOSS_PER_CELL` | adopted |
| Narrowest tunnel | 1.4 | D `RimWorld/MapGenCavesUtility.cs:50`, and `RimWorld/TileMutatorWorker_Caves.cs:27` | `Caves.MIN_TUNNEL_WIDTH` | adopted |
| Tunnel width used for carving | max(width, 1) | D `RimWorld/MapGenCavesUtility.cs:252` (the width-noise amplitude is 0, `:53`, so the noise term is 0) | `Carver.dig` | adopted; the width noise is not used (amplitude 0) |
| Branch chance per tunnel (per side) | 0.02 | D `RimWorld/TileMutatorWorker_Caves.cs:23` (the caves mutator's value). The default of 0.1 is at `RimWorld/MapGenCavesUtility.cs:51` and is overridden at `RimWorld/TileMutatorWorker_Caves.cs:52` | `Caves.BRANCH_CHANCE` | adopted |
| Branching starts after this many cells | 10 | D `RimWorld/TileMutatorWorker_Caves.cs:13` and `:53` | `Caves.BRANCH_AFTER_CELLS` | adopted |
| Branch needs width above | 1.4 + 0.4 (min width plus the largest branch offset) | D `RimWorld/MapGenCavesUtility.cs:239` | `Carver.dig` | adopted |
| Branch width offset | from 0.2 to 0.4 (`:54`), drawn at the branch (`:243`, `:248`) | D as cited for the bounds | `Carver.dig` | the bounds are adopted. The spread within them is Stillmere's draw (C; the base game's distribution is not cited here) |
| Branch heading turn | 40 to 90 degrees to one side, or -90 to -40 to the other (`:243`, `:248`) | D as cited | `Carver.digBranch` | adopted |
| Branch heading tries | 6 random headings, keep the longest open ray (`:289-298`) | D `RimWorld/MapGenCavesUtility.cs:289` | `Carver.digBranch` | adopted |
| Branch ray length limit and minimum | ray up to 50 (`:292`); the branch is dug only if the ray is at least 18 (`:299`) | D as cited | `Carver.digBranch` | adopted |
| Heading change per step | noise times 8 degrees (`:47`, `:272`) | D `RimWorld/MapGenCavesUtility.cs:47` and `:272` | `Carver.dig` | adopted |
| Heading noise frequency and scales | 0.00205 per unit (`RimWorld/TileMutatorWorker_Caves.cs:11`); the noise is sampled at travelled x 60, start x 200 and start z 200 (`RimWorld/MapGenCavesUtility.cs:272`) | D as cited, for the frequency and the three scale values | `Carver.turn` | the frequency and the scale values are adopted. How they enter the noise is C: Stillmere's noise takes two inputs, so the travelled term carries the start row (start z x 200) and the second input is the start column (start x x 200) |
| Heading noise model | Stillmere's 2-D value noise in [0, 1], mapped to [-1, 1] | C. The base game uses Perlin noise (`RimWorld/TileMutatorWorker_Caves.cs:36`), sampled with three coordinates (`RimWorld/MapGenCavesUtility.cs:272`); Stillmere has no Perlin | `Carver.turn` | the shape of a tunnel is not the base game's shape |
| Step length | 0.5 cells per move, until the cell changes (`:258-261`) | D `RimWorld/MapGenCavesUtility.cs:258-261` | `Carver.dig` | adopted |
| Side cell carved on each step | the cell at the old x and the new z, if it is rock (`:265-270`). On a step that changes only z this is the new cell; on a step that changes only x it is the old cell | D `RimWorld/MapGenCavesUtility.cs:265-270` | `Carver.dig` | adopted |
| Edge cell for an open tunnel | not within 3 of the map edge (`:173`), not already carved, with a cardinal neighbour outside the group; 10 tries, the one farthest from a cave wins, ties go to the longer open ray (`:112-125`) | D as cited | `Carver.edgeCell`, `Carver.digOpenTunnels` | adopted |
| Open tunnel: distance to a cave | search radius 40 (`:115`) | D `RimWorld/MapGenCavesUtility.cs:115` | `Carver.gapToCarved` | adopted |
| Open tunnel direction | 8 rays of up to 40 cells (`:197-204`); each heading scores its own ray plus half of each neighbouring ray (`:206`); ties broken at random | D `RimWorld/MapGenCavesUtility.cs:197-206` | `Carver.bestHeading` | adopted |
| Closed tunnel start | 7 random rock cells, the one farthest from open space or a cave wins, search radius 30, open space counts (`:149-158`) | D `RimWorld/MapGenCavesUtility.cs:149`, `:152` | `Carver.digClosedTunnels` | adopted |
| Closed tunnel heading | from 0 to 360 degrees (`:160`) | D as cited for the bounds | `Carver.digClosedTunnels` | the bounds are adopted. The spread within them is Stillmere's draw (C; the base game's distribution is not cited here) |
| Closed tunnel stops at open space | a circle of radius width/2 + 1.5 (`:229`) must hold only rock that is in the group and not yet carved, or cells this tunnel has carved | D `RimWorld/MapGenCavesUtility.cs:227-238` | `Carver.closedWouldBreakOut` | adopted |
| Open tunnel and rock group edge trim | morphological open with radius 6 (`:133`) | D `RimWorld/MapGenCavesUtility.cs:133`; the morphology is `Verse/GenMorphology.cs:72-136` (erode keeps cells whose in-group distance to the edge is at least 6, dilate adds cells within 6; both measure distance by the flood's cardinal neighbour steps, `Verse/FloodFiller.cs:117-136`, where the step loop is at `:128-130`) | `Carver.opened` | adopted |
| Small pieces dropped after the trim | a piece is dropped if it has fewer than 300 cells or fewer than 5 percent of the group (`:391`) | D `RimWorld/MapGenCavesUtility.cs:374-401`, threshold at `:391` | `Carver.dropSmallPieces` | adopted |
| Caves chance on a mountainous tile | 0.5 | D `RimWorld.Planet/WorldGenStep_Mutators.cs:12`, applied at `:57` | `World.generate` (caves roll), `World.CAVE_CHANCE_MOUNTAIN` | adopted for the chance; see the mapping row below |
| Caves chance on a large-hills tile | 0.25 | D `RimWorld.Planet/WorldGenStep_Mutators.cs:14`, applied at `:57` | `World.CAVE_CHANCE_LARGE` | adopted |
| Caves mutator is base game | no DLC gate on the caves definition | D `RimWorld/TileMutatorDefOf.cs:12` (no DLC attribute above it) | (scope check) | confirmed |
| Stillmere mountain tile maps to the mountainous rule | MOUNTAIN uses 0.5 | C. The base game gives impassable tiles no caves (`WorldGenStep_Mutators.cs:57` checks only mountainous). Stillmere's MOUNTAIN is labelled "Impassable mountains" (`WorldMap.kt:10`), and treating it as the base game's mountainous band is a choice. The reference's "Hilliness of world tiles" row cites `WorldGenStep_Terrain.cs:221`, which holds the 0.235 threshold and the water test (elevation at or below 0), not the lower bands, so the band thresholds cannot be matched to a cited line here. No colony stands on one: the home search skips mountain tiles (`WorldMap.kt:221`) and a mountain tile that is still chosen becomes large hills (`WorldMap.kt:235`); settling checks `World.passable`, which excludes mountains (`Caravans.kt:485`, `WorldMap.kt:64`) | `World.generate` | C, **UNVERIFIED** mapping; no colony map uses it yet. Owner to confirm (open item 1) |
| Chance on a tile is per tile, drawn once | one draw per tile whose hilliness has a chance (short-circuit, as the base game's test) | D `RimWorld.Planet/WorldGenStep_Mutators.cs:57` (the draw runs only when the hilliness matches) | `World.generate` | adopted |
| Rock predicate for carving | rock cell (Stillmere's `ROCK` terrain) | C. The base game uses elevation above 0.7 (`RimWorld/TileMutatorWorker_Caves.cs:90`) on its own scaled elevation. Stillmere's rock is set by its hilliness thresholds, which the reference gives as scaled values (decompiled-reference.md, row "Rock threshold by hilliness", status "differs") | `Caves.carve` (rock mask) | C |
| Floor of a cave | gravel (`Terrain.GRAVEL`, fertility 0, passable; `Defs.kt:71`) | C. The base game's gravel band is elevation from 0.55 to below 0.61 (`RimWorld/MapGenUtility.cs:649`); the base game's floor above that band is not cited here. Caves in the base game also get gravel and water patches (see the not-adopted table). Stillmere has no rock floor terrain; gravel is its passable non-fertile ground | `GameMap.generate` | C; no new enum constant |
| Natural roof of a cave cell | the natural roof flag (`natRoof`) stays set, because the cell was rock and carving changes only its terrain | D `RimWorld/GenStep_RocksFromGrid.cs:67-86`: the roof is set from elevation for every cell above 0.7, whether or not caves carved it, and rock objects are skipped only where caves > 0 (`:74`). Stillmere's roof is a flag, so the flag is kept. The thin and thick thresholds (0.728 and 0.798, `:58-63`) are not used; see the table | `GameMap.generate` | adopted for the outcome (a cave has a roof); the thin and thick split is not adopted |
| Entrance | an open tunnel's last carved cell next to a cell outside the group | D `RimWorld/MapGenCavesUtility.cs:263` (the test that the next cell is in the group) and `:280-281` (the stop when it is not) | `Carver.dig` | adopted. An entrance is not guaranteed to be open ground: the cell past it can be rock that the trim left out of the group (a consequence of the cited lines, with no separate citation) |
| Caves make no ore | Stillmere's ore veins grow only through rock cells, and carving runs before ore, so carved cells get none (C) | D `RimWorld/GenStep_ScatterLumpsMineable.cs:108` (the base game skips cells that the caves grid marks, while the map is being set up) | `GameMap.generate` (ore after carving) | adopted for the outcome (no ore in a cave); the mechanism is C |
| Battle map: no caves | no world tile, so no flag | C | `Encounters.kt:15` (the call does not pass `local`, so it is null) | kept |
| Caves per map size | the counts scale with the rock group's size (per 10,000 cells), not with a fixed number | D as the open and closed rows above | `Caves.carve` | adopted; the 250 by 250 map size is unchanged |
| Random draws | a dedicated generator for the carver and another for the world's caves flag; the map's own generator and the world's generator are not used | C (design, so a map without caves is unchanged) | `Caves.carve`, `World.generate` | C |

## Rows not adopted

Each row lists the reason. "Map rows" are the 3.2 rows in `decompiled-reference.md` that touch caves.

| Source | Status | Reason |
|---|---|---|
| Cave floor water and gravel patches (`RimWorld/TileMutatorWorker_Caves.cs:15-21`, `:57-78`: water above 0.93 on a Perlin at 0.08, gravel above 0.55 on a Perlin at 0.16) | not adopted | The gravel patch has no effect, because the default floor is already gravel (C). The water patch needs a Perlin threshold that Stillmere's value noise does not give, so its share would be a guess (UNVERIFIED). Shallow water in caves is a later change |
| Thin and thick natural roof, above 0.728 and 0.798 (`RimWorld/GenStep_RocksFromGrid.cs:58-63`; the reference's "Rock objects and natural roofs" row) | not adopted | Stillmere's roof is a flag per cell, not a thickness. A thickness model belongs to the roofs area (`roofs.md`) |
| Natural roof groups under 20 cells are removed (`RimWorld/GenStep_RocksFromGrid.cs:101-107`; the reference's "Rock objects and natural roofs" row) | not adopted | Roof rule in the roofs area. Stillmere's flag comes from its own rock rule and is not regrouped |
| Rock threshold by hilliness, scaled elevation above 0.7 (`RimWorld/GenStep_RocksFromGrid.cs:55`, `RimWorld/MapGenTuning.cs:5`; the reference's "Rock threshold by hilliness" row) | not adopted | Not caves. Changing the rock threshold changes the rock on every map, and it is a separate change (map.md, "Hilliness mapping") |
| Cave hives: 1,000 cave cells per hive, gated by the difficulty flag and the insects faction (`RimWorld/GenStep_CaveHives.cs:19`, `:25`, `:48`) | not adopted | Insect hives are not in Stillmere. A fauna and threats change |
| Cave exit and pocket maps: the cave exit and the undercave definitions (`RimWorld/ThingDefOf.cs:1025-1026`, `RimWorld/MapGeneratorDefOf.cs:14-15`, `RimWorld/BiomeDefOf.cs:28-29`) | **excluded: DLC** | The exit, the undercave map and the undercave biome carry the Anomaly gate at the cited lines. CLAUDE.md excludes Anomaly. Stillmere's entrances are the open tunnels, which are base game. The exit's generation step, its component and `RimWorld/TileMutatorWorker_UndergroundCave.cs` are not read here, so their gates are **UNVERIFIED**; they are not adopted |
| Cavern mutator (`RimWorld/TileMutatorWorker_Cavern.cs:50`) | **excluded: DLC** | Its own opening noise runs only when the Odyssey check passes (Odyssey) |
| Lake caves (`RimWorld/TileMutatorWorker_CaveLakes.cs:29`), lava caves (`RimWorld/TileMutatorWorker_LavaCaves.cs:29`), ice caves (`RimWorld/TileMutatorWorker_IceCaves.cs:19`), insect lair caves (`RimWorld/GenStep_InsectLairCave.cs:91`) | **excluded: DLC** | Each is gated on Odyssey |
| Cave debris and ancient turrets in caves (`RimWorld/GenStep_ScatterCaveDebris.cs:18`, `RimWorld/GenStep_ScatterAncientTurret.cs:15`) | **excluded: DLC** | Each is gated on the Ideology check, and also on the world tile having caves |
| Undercave interest (`RimWorld/GenStep_UndercaveInterest.cs`) | not adopted | The file is not read here. Its name places it in the undercave map, which is gated (`RimWorld/MapGeneratorDefOf.cs:14-15`). Its own DLC gate is **UNVERIFIED** |
| Mountain chance for impassable tiles (`RimWorld.Planet/WorldGenStep_Mutators.cs:57` gives impassable tiles none) | not adopted, mapped to the mountainous rule | See the MOUNTAIN row above. It changes no colony map, because no colony stands on a mountain tile (open item 1) |
| Mutator placement rules: the tile-validity check (`RimWorld/TileMutatorDef.cs:172`, D) and the non-landmark chance | not adopted | The tile-validity check is defined in source at `RimWorld/TileMutatorDef.cs:172`. It refuses a tile by the def's own rules: a required faction, a mutator already on the tile, the worker's check, the category rules, and the biome's animal and plant density, pollution and temperature ranges. The Caves def's values for those rules are set in its XML, which is not read here, so they are **UNVERIFIED**. The Caves def's non-landmark chance is XML too and is **UNVERIFIED**. Stillmere rolls caves on every mountain and large-hill tile with no other rule (C), so it may give caves to a tile the base game would refuse |
| Rock types inside caves | not adopted | The walls keep their rock types as they were (C; carving changes only the terrain, in `GameMap.generate`). The reference's "Rock types per map" row is not caves-specific and is not changed |
| The insect-faction gate in `GenStep_CaveHives` | not adopted | The gate is at `RimWorld/GenStep_CaveHives.cs:25`; see the cave hives row. Stillmere has no insect faction (C) |
| Settling a colony on a mountain tile | not applicable | Stillmere does not allow it (`Caravans.kt:485` checks `World.passable`, which is false on mountain tiles at `WorldMap.kt:64`). The base game's rule is not checked (UNVERIFIED) |

## Saves on disk

No saved field changed and no enum constant was added. The new caves are plain terrain (`GRAVEL`) with the roof flag set, which
version 39 already stores. The world's caves flag is not saved, and it is rebuilt from the seed on every load, as the rest of the
world is. So:

* a save made before this change loads as before, and its map is not regenerated;
* a colony saved after this change round-trips cell for cell (the test `CavesTest.aColonyWithCavesSavesAndLoadsAsItWas`);
* `VERSION` stays 39. No fixture is needed, because nothing in the format changed.

## Tests

`sim/src/test/kotlin/io/github/teamomuito/colony/sim/CavesTest.kt`, new, 10 tests, all passing:

* `aLargeRockMassIsTunnelledAndItsOwnTunnelsReachOutOfTheRock`: a 90 by 90 rock mass gets carved cells (only rock is carved), an
  open tunnel starts on its edge, and most of the rock stays as wall (seeds 1 to 8).
* `theSameSeedCarvesTheSameTunnelsAndDifferentSeedsDoNot`.
* `aMassTooSmallForTunnelsIsNotTunnelled`: a 15 by 15 mass, and a 19 by 19 mass (361 cells, but fewer than 300 after the trim),
  get no caves for 20 seeds. This checks that the minimum applies after trimming.
* `aMassThatStaysLargeAfterTrimmingCanGetTunnels`: a 25 by 25 mass gets tunnels for some of 12 seeds.
* `aCaveMapHasGravelFloorUnderItsRoofAndNoOreInIt` (250 by 250, seeds 1 to 3): each cave cell is passable and has no ore.
* `aCaveMapOpensOntoTheSurfaceAndKeepsEverySideReachable` (seeds 1 to 4): a cave touches open ground, and each side of the map is
  reachable from the start.
* `caveMapsAreDeterministic`.
* `worldGivesCavesOnlyToHillTilesAndAtTheChanceOfEachKind` (seeds 1 to 40): caves only on land hills; mountain share within 0.05
  of 0.5, large hills within 0.04 of 0.25.
* `aHomeTileCarriesItsCavesIntoItsLocalTerrain`.
* `aColonyWithCavesSavesAndLoadsAsItWas` (the first temperate cave seed, 43): terrain, ore and roofs round-trip.

No existing test asserted an old value that this change moves, so no existing test was changed. The suite was run after the citation
review. A single foreground `./gradlew :sim:test --console=plain` does not finish within the 600-second limit (the whole suite takes
about 12 minutes), so the suite was run as four foreground calls of that command, each with `--tests` filters that together name
all 80 top-level classes of the sim test sources. The calls passed: BUILD SUCCESSFUL in 2m 59s (269 tests), 8m 47s (272), 1m 16s
(134) and 54s (101). Together that is 776 tests in 76 test classes, with 0 failures, 0 errors and 0 skipped, which is every `@Test`
in the sources (776). Version 39 is unchanged (`ColonyMapSizeTest` still checks it).

## Results

**Unchanged maps (checked against the pre-change commit).** A copy of commit `249d420` was made with `git archive`, and the same
digest probe (SHA-256 of terrain, ore, rock types, roofs, spawn and plants) was run in both trees. The probe was removed afterwards.

* Colony maps (250 by 250, planet home, seeds 1 to 300): the digests differ for exactly the 15 seeds whose home has caves (43, 64,
  84, 95, 145, 168, 174, 179, 185, 210, 211, 236, 263, 293, 297). All other 285 are identical.
* Colony maps for a temperate home (seeds 1 to 300): differ for exactly the 12 cave seeds (43, 59, 85, 97, 121, 130, 140, 179,
  211, 218, 279, 297). All others identical.
* Battle maps (44 by 44, seeds 1 to 40) and worlds (seeds 1 to 40): all identical.

So a map without caves is the same as before, and the change touches only the maps that have caves.

**Carving (synthetic masses, 150 by 150 map, seeds 1 to 6).** A 90 by 90 mass: 7 to 20 percent of its rock is carved, with 9 to 27
carved cells next to outside rock. A 60 by 60 mass: 7 to 19 percent. A 25 by 25 mass: tunnels for 2 of 6 seeds. Masses of 15 by 15
and 19 by 19 get none.

**Cave maps (250 by 250, large-hill home with caves, seeds 1 to 12).** Each map has 610 to 1,943 carved cells, of which 19 to 91
touch open ground (the entrances). No ore is in a cave. Map generation takes 41 to 136 ms per map in the test JVM (the first
includes JIT warm-up). No phone was measured.

**Share of colonies with caves.** Among 300 seeds: 12 temperate homes (4.0 percent) and 15 planet homes (5.0 percent) have caves.
Large hills are 45 of the 300 homes (15 percent), and a quarter of those have caves. Mountain tiles never host a home, so the 0.5
chance for them applies to no colony yet.

**World shares.** Over seeds 1 to 60, 49.8 percent of mountain tiles and 25.7 percent of large-hill tiles carry caves (chances 0.5
and 0.25). No cave is on a water tile or on another hilliness.

**Deviations from the base game in the carver, all C:** the heading noise is 2-D value noise (not Perlin, and given two inputs, not
three), the width curve is a straight line between its points and flat outside them, the floor is gravel with no water or gravel
patches, and the roof is one flag (no thin or thick split). The tunnel rules, counts, widths, steps and branching are the base
game's values.

## Open items

1. **Mountain mapping and colony frequency.** The world gives mountain tiles a caves chance of 0.5 (the base game's mountainous
   rule), but no colony stands on one. Only large-hills homes and settled tiles can have caves, at 0.25. The owner should confirm
   that mountain tiles keep the 0.5 chance, and decide whether colonies may start on mountain tiles (which would change the home
   choice and the world, so it is a separate change).
2. **Cave floor patches.** Water and gravel patches are not adopted (see the table). The owner decides whether caves get shallow
   water patches, and which noise is used for them.
3. **Heading noise.** Tunnels bend with Stillmere's 2-D value noise, not the base game's Perlin, and the noise takes two inputs instead of
   three. The shape of a tunnel will differ.
4. **Entrances.** An open tunnel's end can be rock that the trim removed, so the entrance is not guaranteed to be open ground. The
   base game does the same. A test measures how often a cave has an open entrance (see the results section).
5. **Battle maps.** They get no caves. Whether a fight should have caves is the owner's decision.
