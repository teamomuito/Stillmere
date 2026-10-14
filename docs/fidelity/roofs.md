# Roofs, roof collapse and roofless rooms

Gap-report section 3.3 (roofs). Reference: `docs/fidelity/decompiled-reference.md`, section 3.3 row "Indoor and outdoor rule (roof
share)" (line 101) and section 3.8 row "Roofs and roof collapse (missing)" (line 348). Source tree: the decompiled RimWorld tree at
version 1.6.9438.38202 (read only). That is the pinned build (`decompiled-reference.md`, header), so each vanilla value
here matches the pinned build.

Tags:
* **D** = the value is taken from the decompiled source, with its repository, file and line cited. The source is `Chillu1/RimWorldDecompiled`,
  read from the local clone at `/home/user/chillu1/rimworlddecompiled`, version 1.6.9438.38202 (read only). The cited line was opened, and the
  value is there. A row of `decompiled-reference.md` is not needed for D (this round's rule). A D is a value from the 1.6.9438 source, which is
  the pinned build.
* **C** = Stillmere's own value or rule. Where a vanilla source file is cited for comparison only, the value or rule is not adopted under a D
  tag. Unless the row says otherwise, a C value is **UNVERIFIED** for the pinned build. The reason is given in the row.
* **Tag rule for this round (review).** Each claim is matched to its cited line. A claim that the cited line does not show exactly is C or
  **not adopted**, with the reason in its row. No citation is added to `decompiled-reference.md`. The D citations are:
  * `RimWorld/JobDriver_AffectRoof.cs:15` and `:43` (the work total, 65) and `:47` (the progress factor, 1.7). Vanilla multiplies the
    construction speed stat by 1.7; Stillmere multiplies the pawn's work speed, which is C (row 67).
  * `Verse/RoofCollapserImmediate.cs:10` (the crush range, 15 to 30). The crush roll and its scope are C (row 78).
  * `Verse/RoofCollapseUtility.cs:5` (the support radius, 6.9; table line 348, section 3.8).
  * `Verse/Room.cs:105` (the quarter rule; table line 101) and `:103` (the map edge; table line 102), section 3.3.
  * `RimWorld/GenDate.cs:9` (the day length, 60,000 ticks; section 3.1, context only).
  * `RimWorld/GenStep_RocksFromGrid.cs:59` (the thick threshold, 0.7 x 1.14; section 3.2, not adopted) and `:63` (the thin threshold,
    0.7 x 1.04; no table line cites it; not adopted).
* **The reference table is unchanged in this working tree.** `git diff HEAD -- docs/fidelity/decompiled-reference.md` is empty: no row is
  edited (rows 101 and 348 read as committed) and no row is added. See "Corrections in this round".
* **?** = `UNVERIFIED` (definition data, not in the tree, or not confirmed).
* **not adopted** = Stillmere does not implement the vanilla rule in this round, with the reason.

No identifier, comment or string from the decompiled source is copied here or in the code. Citations are file and line only.

## Status

Result: all 826 sim tests pass: 0 failures, 0 errors, 0 skipped, in one run of `./gradlew :sim:test --console=plain` that ended
`BUILD SUCCESSFUL in 15m 8s` (exit 0). Its 79 result files (`sim/build/test-results/test`, all written by that run) hold the 826 tests.
`RoofsTest` has 23 tests (one new this round: the roof designation the app makes) and `LegacyRoofMigrationTest` has 6. The fourth review
recorded 750 tests in 74 files. This round's sim change is that one test. No rule, value, or save field changed. The app (`app/`) was
not built: there is no Android SDK here, so CI must confirm the build. No change to the save format in this round.

Implemented in `sim/` on branch `ccr-41984ede-stlmp4`: `Roofs.kt` (rules, block roofing), `GameMap.kt` (roof grid, room rule, holder
removal), `JobDrivers.kt` and `Jobs.kt` (build job), `Game.kt` (designation, end-of-step crush), `SaveGame.kt` (version 38, migration).
The app side of this round: `GameView.kt` (the Build roof tool, the roof tint and mark), `MainActivity.kt` (the tool's tap and drag, the
status text), `Panels.kt` (the Orders entry). Not committed.

## Scope

1. A **roof grid** per cell (built roofs), saved. Natural (mountain) roof stays the existing `natRoof` flag. A cell is roofed when
   either flag is set.
2. **Indoor rule**: a room is indoor only when it does not touch the map edge and fewer than a quarter of its cells are open (unroofed),
   that is `4 * open < cells`. It is outdoor once the open cells reach the quarter rounded up. This replaces the old rule (at most
   700 cells, or a natural-roof majority).
3. **Build roof**: a designation (`Desig.ROOF`) and a construction job (`JobType.BUILD_ROOF`). A roof needs a roof holder within
   6.9 cells, linked to it through roofed cells. The app's **Build roof** tool (Orders menu) sets the designation on each tapped or
   dragged cell, through `Game.designate(x, y, Desig.ROOF)` (see "The app"). A built roof can be taken down with the Remove roof tool (`Desig.UNROOF`, C: same work as building one).
4. **Roof collapse**: removing a roof holder (a built wall or door, or mined rock) drops the built roofs that are no longer held.
   Crush damage to each pawn under a collapsing roof, drawn from the sim RNG. The collapse itself draws nothing.
5. **Roofless rooms** follow from 2 and 4: a room whose open cells reach a quarter of its cells, rounded up, uses the outdoor temperature
   (existing code).
6. **Save version 38** adds the roof flag per cell. Old saves are migrated (see "Save format and migration").

## Constants and rules

| Item | Stillmere value | Source | Tag | Status |
|---|---|---|---|---|
| Roof holder support radius | 6.9 cells, horizontal | `Verse/RoofCollapseUtility.cs:5` (the value 6.9). Reference table, section 3.8, "Roofs and roof collapse" row | D | adopted. Row 348 is `differs` because the mechanic was missing, not because of units or scope: both sides measure a horizontal distance in cells, and `:5` holds 6.9f (checked) |
| Horizontal distance test | squared distance at most the squared radius (`dx*dx + dy*dy <= r*r`) | Vanilla's helper is the same inequality: `Verse/IntVec3.cs:173-178`. Not in the reference table | C | adopted. Stillmere's own form of the test |
| Radial cell set for collapse | every cell within 6.9 of the removed holder, including its own cell | Vanilla's count is `Verse/RoofCollapseUtility.cs:7`; the loop covers the first entries of the sorted radial pattern (`Verse/RoofCollapseCellsFinder.cs:24-26`). The pattern is sorted by horizontal squared length (`Verse/GenRadial.cs:93-115`, the sort at `:100`; the squared length is `Verse/IntVec3.cs:48`), and the origin is the only cell of length zero, so the sort puts it first. The count is at `Verse/GenRadial.cs:166-188`. Not in the reference table | C | adopted. Stillmere builds the set from the squared distance, origin included |
| Adjacency used for holder and collapse checks | the cell, and its four cardinal neighbours | Vanilla's offsets: `Verse/GenAdj.cs:55-59` (the four cardinal offsets, then the cell at entry 4). Not in the reference table | C | adopted |
| Holder membership | a built wall or door, and natural rock (see below) | Vanilla's test is the def flag `holdsRoof`: `Verse/ThingDef.cs:304` (the field), tested at `Verse/RoofCollapseUtility.cs:20, 43`. Not in the reference table | C (walls and doors are Stillmere's choice); **?** (natural rock: the vanilla flag is definition data, not in the tree) | adopted as a Stillmere choice |
| Outdoor rule | outdoor if touching the map edge, or open cells at least `ceil(cells / 4)` | `Verse/Room.cs:103-108`, quarter rule at `:105`. Reference table, section 3.3, indoor and outdoor row (line 101) and map-edge row (line 102) | D | adopted. Row 101 is `differs` because the rule itself differs (the old 700-cell rule), not because of units or scope. Integer form: outdoor when `4 * open >= cells`, which is exactly "roofed share 75 percent or less" |
| Open cell | a cell with neither a built roof nor natural roof | Vanilla counts open cells per room: `OpenRoofCount` (`Verse/Room.cs:293-301`, used by the quarter rule at `:105`). That count calls the room's `OpenRoofCountStopAt` (`Verse/Room.cs:555-572`), which sums each district's count at `:564`. A district counts a cell as open when `Roofed` is false (`Verse/District.cs:261-285`, the `Roofed` test at `:273`). `Verse/RoofGrid.cs:60-63` defines `Roofed` as a non-null roof, so natural roof counts as roofed. The room's cells are the union of its districts' cells (`Verse/Room.cs:189-200`; the count at `:205-215`). Not in the reference table | C | adopted per room. Stillmere has no districts and counts open cells per room directly; the total is the same when the districts do not overlap (overlap is not checked in the tree). Vanilla's per-district split and its cache are not modelled (not adopted). Natural roof counts as roofed, as in vanilla |
| Build roof work total | 65 | `RimWorld/JobDriver_AffectRoof.cs:15` (the base work, 65) and `:43` (the reset to 65 at the start of the job), in the 1.6.9438 source (checked in the clone) | D | adopted. Stillmere's job ends when its work total reaches 65 (`JobDrivers.kt:756`). The pinned build is 1.6.9438.38202, the same as the source |
| Build roof progress per tick | 1.7 times the pawn's Architecture work speed, added per sim tick | `RimWorld/JobDriver_AffectRoof.cs:47` (the progress per interval is the construction speed stat x 1.7 x the interval length; checked in the clone). The stat in place of vanilla's is Stillmere's own mapping, `Pawn.workSpeed(ARCHITECTURE)` | D for the 1.7 (`:47`); D for the stat: vanilla multiplies the `ConstructionSpeed` stat (`:47`), and the 65 work is at `:43`; the mapping to Stillmere's architecture work speed is C | adopted for the 1.7. Ticks per roof at speed 1: about 38 (65 / 1.7). The rate is added per sim tick with no `TIME_SCALE` division (`JobDrivers.kt:753`); the generic job step divides by it (`JobDrivers.kt:21`, `doWork`), because the legacy rates are per 1,000-per-hour tick. The roof rate is per tick of Stillmere's clock, which has the vanilla day length (60,000 ticks, `Defs.kt:5`; the D row for `RimWorld/GenDate.cs:9`, section 3.1, which covers the day length only), so it is not divided. `TIME_SCALE` (`Defs.kt:18`) converts the legacy 1,000-per-hour rates |
| Build roof XP per tick | 0.07 / TIME_SCALE per tick, Architecture skill (`JobDrivers.kt:755`) | Vanilla: neither `RimWorld/JobDriver_AffectRoof.cs` nor `RimWorld/JobDriver_BuildRoof.cs` calls a skill learn method (checked: no `Learn` call in either file; other vanilla jobs learn in their own driver, e.g. `RimWorld/JobDriver_AffectFloor.cs:40`). The active skill is Construction (`RimWorld/JobDriver_AffectRoof.cs:62`), which the passion thought reads (`RimWorld/ThoughtWorker_PassionateWork.cs:19-23`). Not in the reference table | C | adopted. Stillmere's own rate: the generic default for a job with no reference rate (`JobDrivers.kt:20`; see `skills-traits.md`). Not a vanilla value |
| Build roof area effect | a finished roof job roofs the 3 by 3 block around its centre (nine cells, the centre included). Each designated, unroofed cell of the block that is held in range and has no tree is roofed. The block is scanned again until no cell changes, so a cell held only through another block cell is roofed in the same job | Vanilla's footprint: `RimWorld/JobDriver_BuildRoof.cs:24-31` and `Verse/GenAdj.cs:80-88` (nine entries, the centre last), outside the reference table. Its single pass: `RimWorld/JobDriver_BuildRoof.cs:27, 31` | C for the footprint and the fixed-point rule; **not adopted** for vanilla's single pass in its entry order | Not adopted: vanilla makes one pass, so a cell held only through a later entry waits for a later job. Stillmere roofs to a fixed point. Roofing a cell only adds holds, so the set reached does not depend on the scan order. The earlier version used GenAdj's order; it is removed (see "Corrections in this round") |
| Build roof support check | the job works only where a holder is within 6.9 (through roofed cells in range), and the cell connects to a holder | `RimWorld/JobDriver_BuildRoof.cs:13-15`; `RimWorld/WorkGiver_BuildRoof.cs:41-48`. Not in the reference table. (The 6.9 radius is D, first row) | C | adopted |
| Build roof cost | none | not in the tree (the roof's cost is definition data, if any) | D: `RoofDef` has no cost field (`Verse/RoofDef.cs:3-17`); the designator's cost is not in the tree | **UNVERIFIED**; Stillmere charges no material |
| Built roof kind | one kind, treated as thin (vanishes on collapse) | Vanilla: a roof vanishes on collapse unless it is thick: `Verse/RoofDef.cs:17`. Not in the reference table. The built roof's thick flag is definition data | C (thin is Stillmere's choice); **?** (the vanilla thick flag of a built roof: `RoofDef.isThickRoof`, `Verse/RoofDef.cs:7`, set in XML) | adopted as thin. The flag is **UNVERIFIED** |
| Collapse drops a built roof | each marked built roof is removed | `Verse/RoofCollapserImmediate.cs:178-181` (the roof is set to none when it vanishes on collapse). Not in the reference table | C | adopted |
| Collapse check on holder removal | three steps, in Stillmere's reading of vanilla's order. (0) the holder's cell and its four neighbours: a roofed group that touches no holder falls. (1) every roofed cell within the support radius of the holder that is not held in range is marked, if its def allows collapse. (2) the cell and its four cardinal neighbours, for each cell from step 1: a roofed group that touches no holder falls. The allow-collapse test is in step 1 only | `Verse/RoofCollapseCellsFinder.cs:19-42` (step 0 is the call at `:21`, the radial loop `:24-39`, step 2 the call at `:40`); the allow-collapse test at `:27` (step 1 only); the flood at `:87-121`; the default `canCollapse = true` at `Verse/RoofDef.cs:15`. Not in the reference table. Vanilla's step 0 uses the holder's whole rect; a Stillmere holder is one cell | C | adopted. Stillmere has no canCollapse flag, so every built roof is taken to allow collapse (the built roof's own flag is definition data, **UNVERIFIED**). Stillmere's steps 1 and 2 start only from built roofs, which is a difference (see the natural roof row). Natural roof is not marked (next row). Connectivity check: see the note below |
| Scan order of the collapse radius (difference) | Row by row: the radius is scanned with the row (dy) as the outer loop and the column (dx) as the inner, so the step 1 cells are marked in that order (`holderRemoved` in Roofs.kt) | Vanilla scans the radial pattern in its sorted order (`Verse/RoofCollapseCellsFinder.cs:24-26`), by horizontal distance, sorted at `Verse/GenRadial.cs:100`. Not in the reference table | C | **not adopted**. The fallen set does not depend on the scan order: each roofed group is decided once, and the holder test does not depend on the marks. Only the order of the crush draws changes. Vanilla's order between cells at the same distance comes from `List.Sort` at `Verse/GenRadial.cs:100`, which is not a stable sort (a property of the .NET sort, C, not a row of the table), so the exact vanilla order cannot be copied from the source. The 3 by 3 block is not affected: its order is GenAdj's (see "Build roof area effect") |
| Natural roof is not marked for collapse | natural roof is kept, and still counts as roofed. The connectivity walk passes through natural roof | Vanilla's group flood marks every roofed cell of a group that touches no holder, natural roof included: `Verse/RoofCollapseCellsFinder.cs:97-104`; the drop removes a marked roof only when it vanishes on collapse (`Verse/RoofCollapserImmediate.cs:178-181`, `Verse/RoofDef.cs:17`). Not in the reference table. The thick or thin split of natural roof is set at map generation (`RimWorld/GenStep_RocksFromGrid.cs:59, 63`; see the map-generation row) | C (the decision); **?** (whether each natural roof is thick is definition data, not in the tree) | **not adopted** (two differences). (a) Stillmere keeps natural roof whatever its def says. The reason: natural roof is one flag (`natRoof`) with no thick or thin split. Adopting the drop would have to guess which natural cells fall, and would change what existing saves do on a collapse. Test: `RoofsTest.naturalRoofNeverFalls` (the drop only). (b) Step 2 does not start from a natural roof cell in the radius that is not held in range. Vanilla marks that cell (`Verse/RoofCollapseCellsFinder.cs:27`, if its def allows collapse, which is **?** for natural roof) and walks its neighbours (`:40`). Example: a built roof outside the radius, joined to such a natural cell, with no holder in that group, falls in vanilla and is kept here. Stillmere's step 1 considers built roofs only (`holderRemoved` in `Roofs.kt`) |
| Collapse resolved | vanilla queues the marked cells in a buffer. Removal mode resolves it inside the check; otherwise the map resolves it during its tick | `Verse/RoofCollapseBuffer.cs:16-22` (mark), `Verse/RoofCollapseBufferResolver.cs:20-64` (drop, then clear), `Verse/RoofCollapseCellsFinder.cs:90-93` (removal mode), `Verse/Map.cs:945` (the map's resolve). Not in the reference table | C | adopted as a Stillmere choice. The drop runs in the call that removed the holder, not at the map's tick resolve; the crush is drawn once at the end of the same game step (`Game.kt`, `resolveRoofCollapses`, called inside `Game.step`). The timing therefore differs from vanilla's |
| Crush damage on collapse | per pawn on a fallen cell, of any faction: an integer from 15 to 30, inclusive, drawn once per pawn per fall | `Verse/RoofCollapserImmediate.cs:10` (the range 15 to 30). The branch that uses it is the one for a roof with no impassable leaving thing (`:81` is the test; the `else` at `:127`). Also `:133` (the things hit: items, plants, buildings, pawns), `:136` (the range draw, one per thing), `Verse/IntRange.cs:34` (the draw is inclusive), `:147` (the damage is then rounded by `GenMath.RoundRandom`, `Verse/GenMath.cs:62-65`, which draws once more; a whole number comes back unchanged). Checked in the clone | D for the range 15 to 30 (`:10`), adopted for pawns. C for the rest: the per-pawn scope, one roll per pawn per fall, and the inclusive draw through Stillmere's RNG (`Noise.kt:70`, `Rng.range`, `a + int(b - a + 1)`; vanilla's inclusive draw at `IntRange.cs:34` is cited for comparison only). Vanilla draws twice per thing (the range, then the rounding); Stillmere draws once per pawn, and the second draw changes no value, only the RNG stream. Stillmere items and plants take no damage (next row). Damage is the crush wound on the head part, or on the torso when there is no head (see `Health.kt`, `woundPart`). Mapping vanilla's top body region (`Verse/RoofCollapserImmediate.cs:148`) to the head part is **C** |
| Crush damage to items, plants and buildings | same roll | `Verse/RoofCollapserImmediate.cs:133-149`. Not in the reference table | not adopted | Stillmere items and plants have no hit points, and a collapse does not destroy buildings in this round (a building that reaches zero would cascade into another collapse) |
| Impassable leaving thing branch | the branch for a roof whose leaving thing is impassable (the test is on the leaving thing's passability, not on the roof's thick flag) | `Verse/RoofCollapserImmediate.cs:81` (the test). Not in the reference table | not adopted | no Stillmere roof has a leaving thing, and natural roof does not collapse here. Which vanilla roofs carry an impassable leaving thing is definition data, **UNVERIFIED** |
| Remove-roof designation | removes roofs; collapses in removal mode | Vanilla's removal mode: `Verse/RoofCollapseCellsFinder.cs:29-31`. The area and job: `RimWorld/JobDriver_RemoveRoof.cs`, `RimWorld/Area_NoRoof.cs`. Not in the reference table | simple designation adopted (C): `Desig.UNROOF` marks a built roof, a colonist takes it down with the build-roof work total and rate, and only that cell changes. The removal area, its job and removal-mode collapse are not adopted | the Remove roof tool is in the Orders menu (`Panels.kt`); natural roof cannot be taken down |
| Mined rock as a holder | mining a rock cell (and a subsoil auger drilling one out) removes a holder, so the same collapse check runs | `Verse/Building.cs:212-214` (a building whose def holds a roof notifies the finder when it despawns) and `Verse/RoofCollapseCellsFinder.cs:11-17` (the notify function). Not in the reference table. Whether rock carries the holder flag is **UNVERIFIED** | C | adopted, as a Stillmere call site (`JobDrivers.kt` mining and auger) |
| Plant that blocks a roof | vanilla: a plant with the interferes-with-roof flag must be cut first, and the 3 by 3 block skips such cells. Stillmere: trees only | `Verse/RoofUtility.cs:9-20` (the blocking test), `:58-73` (the cut job); `RimWorld/JobDriver_BuildRoof.cs:27` (the block skips it); `RimWorld/WorkGiver_BuildRoof.cs:49-53`. Not in the reference table. Which plants carry the flag is definition data | C (trees only); **?** (the plant list) | **partly**: a cell with a tree is skipped until the tree is gone. The pawn does not cut the tree first (open). Other plants are not modelled |
| Filth left by a collapse | per roof def | `Verse/RoofCollapserImmediate.cs:174-177`; `Verse/RoofDef.cs:11` (the field). Not in the reference table | not adopted | no filth leaving for the built roof (definition data, **UNVERIFIED**) |
| Placeworker needing a thick roof | not used in Stillmere | `RimWorld/Placeworker_OnlyUnderThickRoof.cs`. Not in the reference table | not adopted | no Stillmere def uses it |
| Thick-roof outdoors need, indoors need, sun array and rain effects | use roofed cells | see `docs/fidelity/needs-mood.md` and `docs/fidelity/economy.md`, which carry their own tags | n/a here | numbers unchanged; they now follow the roof grid |
| Beauty under natural roof (-0.1) | kept | `Rooms.kt` (`NATURAL_ROOF_BEAUTY`). The reference table's per-cell beauty row (section 3.3, row 106) states that vanilla's cell beauty has no natural-roof term; this row does not re-cite it | not adopted | a 3.3 rooms-stat row, not roofs |
| App: Build roof tool | Orders menu entry "Build roof" (`Panels.kt`, `orderTools`). Tap or drag marks each cell with `Desig.ROOF` through `g.designate`, the same call the other order tools make (`MainActivity.kt`, `onArea`, the `Tool.Roof` branch). Removing a roof is not offered | Not in the reference table. The mark is accepted by the sim's existing rule: a passable cell that is not roofed (`Game.kt:425`, `designate`) | C | adopted in the app; not built here (CI must confirm). The tool only marks cells. Whether a cell is roofed is decided by the sim's rules, which are unchanged |
| App: marked roof | Cell status text "Marked: roof" (`MainActivity.kt`, the `Desig` branch for `Desig.ROOF`). On the map, a light blue fill with a roof chevron (`GameView.kt`, the designation branch for `Desig.ROOF`) | Not in the reference table. A visual choice; no vanilla value is used | C | adopted; visual only, no effect on the sim |
| App: built roof tint | A roofed cell that is not natural roof (`roof` set, `natRoof` clear) gets a faint slate tint, `0x2E5A6E82` (ARGB, alpha 46 of 255). It is drawn over the floor, in the detail view and in the flat view (`GameView.kt`). Natural roof keeps its own shading, `0x30101018`, unchanged | Not in the reference table. A visual choice; no vanilla value is used | C | adopted; visual only. Lighting is unchanged: `drawLighting` (`GameView.kt`) still gives natural roof a fixed 0.5 and every other cell `dark * 0.68`, so a built roof is lit as before |
| Rock objects and natural roofs at map generation | thin and thick thresholds | Thick: `RimWorld/GenStep_RocksFromGrid.cs:59` (x 1.14; the 0.7 is at `:55`), reference table section 3.2, "Rock objects and natural roofs" row. Thin: `:63` (0.7 x 1.04; checked in the clone). The table row cites `:59` for both values, so `:63` has no row | D (thick, `:59`; thin, `:63`) | not adopted: map generation (3.2, `map.md`) |

### Note on the connectivity test

Vanilla's collapse walks the roofed cells with one shared set of visited cells (`Verse/RoofCollapseCellsFinder.cs:9`, `:123-141`). Stillmere's
own reasoning is that the result equals a plain per-group test when every group is checked once: a group of roofed cells collapses when
no cell of it is next to a holder. Stillmere uses the plain test and records each group it checks, so each group is decided once.
This is C: the shared-set walk is not in the reference table.

### Corrections in this round

* **Fourth review: the 65, 1.7 and 15 to 30 values are D.** `RimWorld/JobDriver_AffectRoof.cs:15` and `:43` (65), `:47` (1.7) and
  `Verse/RoofCollapserImmediate.cs:10` (15 and 30) were opened in the clone (version 1.6.9438.38202), and the values are there. Each is
  tagged D with its file and line (`ROOF_WORK`, `ROOF_WORK_RATE`, `ROOF_CRUSH_MIN`, `ROOF_CRUSH_MAX`). No reference-table row is needed for
  D (this round's rule). The earlier text that called them C is withdrawn. Still C: the work speed used in place of vanilla's construction
  stat, the crush roll and its scope, the collapse steps and the holder test. Other changes, each checked in the clone: the holder despawn
  caller (`Verse/Building.cs:212-214`, row 82); the squared length of a radial cell (`Verse/IntVec3.cs:48`, row 61); the generic job step's
  division by `TIME_SCALE` (`JobDrivers.kt:21`, row 67); the crush branch (`Verse/RoofCollapserImmediate.cs:81`, and the `else` at `:127`,
  row 78). The earlier label "thin-roof collapse" for that row was wrong: the range is chosen by whether the roof's leaving thing is
  impassable, not by the roof's thickness. The thin threshold (`RimWorld/GenStep_RocksFromGrid.cs:63`) is D with the thick one (row 88), and
  is not adopted. The comments in `Roofs.kt`, `JobDrivers.kt` and `RoofsTest.kt`, and the roofs row of `gap-report.md`, now carry the same
  tags. The XP comment in `JobDrivers.kt` names the two vanilla roof drivers that were searched, and no wider claim.
* **Review round: what changed, and why.** (1) and (2) above replace the earlier text on the 65, 1.7 and 15 to 30 values. (3) The 3 by 3 block is roofed to
  a fixed point, not in vanilla's order (see the two bullets below). (4) Row 348 is `differs` because the mechanic was missing, not because
  of units or scope: both sides measure a horizontal distance in cells, so the 6.9 radius stays D. Row 101 is `differs` because the rule
  itself differs. (5) Every citation kept was opened in this pass at its cited line, with the value checked: `Verse/RoofCollapseUtility.cs:5,
  7, 9-30, 20, 32-52, 43`; `RimWorld/JobDriver_AffectRoof.cs:15, 43, 45-47, 45-53, 62`; `Verse/RoofCollapserImmediate.cs:10, 81, 127-152,
  133, 136, 147, 148, 174-177, 178-181`; `Verse/RoofCollapseCellsFinder.cs:9, 11-17, 19-42, 21, 24-26, 27, 29-31, 40, 87-121, 90-93, 97-104,
  123-141`; `Verse/GenAdj.cs:55-59, 80-88`; `Verse/GenRadial.cs:93-115, 100, 166-188`; `Verse/IntVec3.cs:173-178`; `Verse/IntRange.cs:34`;
  `Verse/GenMath.cs:62-65`; `Verse/Room.cs:103, 105, 189-200, 205-215, 293-301, 555-572, 564`; `Verse/District.cs:261-285, 273`;
  `Verse/RoofGrid.cs:60-63`; `Verse/ThingDef.cs:304`; `Verse/RoofDef.cs:11, 15, 17`; `Verse/RoofUtility.cs:9-20, 58-73`;
  `Verse/RoofCollapseBuffer.cs:16-22`; `Verse/RoofCollapseBufferResolver.cs:20-64`; `Verse/Map.cs:945`; `RimWorld/JobDriver_BuildRoof.cs:13-15,
  24-31, 27`; `RimWorld/WorkGiver_BuildRoof.cs:23-55, 41-48, 49-53`; `RimWorld/GenStep_RocksFromGrid.cs:55, 59, 63`; `RimWorld/GenDate.cs:9`;
  `RimWorld/ThoughtWorker_PassionateWork.cs:19-23`; `RimWorld/JobDriver_AffectFloor.cs:40`. Stillmere lines checked: `JobDrivers.kt:20,
  462, 686, 752-753, 755`; `Defs.kt:5, 18`; `Noise.kt:70`; `Game.kt:429, 493-497, 623, 633, 685-686`. Files named without a line are
  cited for existence only (`RimWorld/Area_NoRoof.cs`, `RimWorld/JobDriver_RemoveRoof.cs`, `RimWorld/Placeworker_OnlyUnderThickRoof.cs`).
  No `Learn` call is in `RimWorld/JobDriver_AffectRoof.cs` or `RimWorld/JobDriver_BuildRoof.cs` (searched). (6) Tests: `RoofsTest` has 22
  tests, one more than before; the block test was rewritten and a tree test added.

* **Open cells: the district count exists (reviewer finding, corrected).** The earlier row said `Verse/District.cs` has no
  open-roof count. That is false. `Verse/District.cs:261-285` defines `OpenRoofCountStopAt`, and `Verse/Room.cs:564` sums each
  district's count into the room's count (`Verse/Room.cs:555-572`). The open-cell row now cites both. It stays C and adopted per
  room, and the per-district split is not adopted (reason in the row).
* **Radius count range.** The count of cells in the support radius (`Verse/RoofCollapseUtility.cs:7`) is computed at
  `Verse/GenRadial.cs:166-188`. The earlier range `:166-185` cut the function short, and the row now cites `:166-188`.
* **Step 2 wording.** Vanilla's step 2 call (`Verse/RoofCollapseCellsFinder.cs:40`) checks each cell from step 1 with its four
  cardinal neighbours and the cell itself (entry 4 of `Verse/GenAdj.cs:55-59`). The row now says so. Step 0 has the same shape,
  on the holder's own cell.
* **Crush draws.** Vanilla makes two draws per thing: the range (`Verse/RoofCollapserImmediate.cs:136`) and the rounding in
  `GenMath.RoundRandom` (`:147`; `Verse/GenMath.cs:62-65`). The row now says so. The value is the same, since a whole number comes
  back unchanged.
* **Natural roof walk (a difference the earlier text did not record).** Stillmere's step 1 considers built roofs only, so a natural
  roof cell in the radius that is not held in range never starts a step 2 walk. Vanilla walks from it
  (`Verse/RoofCollapseCellsFinder.cs:27, 40`). Recorded as not adopted in the natural roof row, with an example.
* **Holder removal runs inside a step.** Checked: the holder removals are reached from `pawnTick` (`Game.kt:633`, calling `think`
  at `:685` and `driveJob` at `:686`) and from the mining and auger jobs. The blueprint removal at `Game.kt:429` only removes
  unbuilt blueprints, which never reach `holderRemoved`. The determinism section relies on this.
* **Quarter rule and radius, re-checked.** `Verse/Room.cs:103-108` (`:105` is `OpenRoofCount >= Mathf.CeilToInt(CellCount * 0.25f)`,
  `:103` is the map-edge test), and `Verse/RoofCollapseUtility.cs:5` (6.9). The D citations match rows 101, 102 and 348 of the
  reference table.
* **Every citation was opened again in this pass.** Each vanilla and Stillmere citation in this file was opened at its cited line
  and the value checked, including the 65, 1.7, 15 to 30, 6.9, the 3 by 3 block, the collapse steps and the holder flag. No other
  citation was changed.
* **The 3 by 3 block: vanilla's order is not adopted (review correction).** An earlier text said `Roofs.kt` used GenAdj's order and that
  this was C. That is withdrawn. The order has no row in the reference table, so under CLAUDE.md it cannot be adopted from the source. The
  code does not copy it: vanilla makes one pass over its nine entries (`RimWorld/JobDriver_BuildRoof.cs:24-31`, `Verse/GenAdj.cs:80-88`),
  and Stillmere scans the block until no cell changes (`finishRoofBlock` in `Roofs.kt`). Recorded as not adopted in the constants table
  ("Build roof area effect"). The row-by-row scan of the collapse radius is a separate difference, also not adopted (its row).
* **Timing of the cells next to the centre (difference, recorded, not adopted).** Vanilla makes one pass over the nine entries of
  `RimWorld/JobDriver_BuildRoof.cs:24-31`, with the centre last (entry 8). A cell next to the centre is roofed in that same job only when
  it is held without the centre; otherwise a later job roofs it. Stillmere scans the block to a fixed point, so both kinds are roofed in
  the same job. The test is `RoofsTest.aRoofJobRoofsEveryHeldCellOfItsBlockInOnePass`.
* **Citations read directly from the clone.** The vanilla files cited in this document (`RimWorld/JobDriver_AffectRoof.cs`,
  `RimWorld/JobDriver_BuildRoof.cs`, `Verse/RoofCollapserImmediate.cs`, `Verse/RoofCollapseCellsFinder.cs`, `Verse/RoofCollapseUtility.cs`
  (except line 5), `Verse/GenAdj.cs`, `Verse/GenRadial.cs`, `Verse/RoofUtility.cs`, `RimWorld/WorkGiver_BuildRoof.cs`, `Verse/RoofDef.cs`,
  `Verse/RoofCollapseBuffer.cs`, `Verse/RoofCollapseBufferResolver.cs`, `Verse/IntRange.cs`, `Verse/IntVec3.cs`, `Verse/ThingDef.cs`,
  `RimWorld/ThoughtWorker_PassionateWork.cs`, and the others) were read directly from the clone of version 1.6.9438.38202. They are not
  rows of `decompiled-reference.md`, and no row was added for them. Files named without a line are cited for existence only.
* **Every citation kept was opened at its cited line.** The values checked: 6.9 at `Verse/RoofCollapseUtility.cs:5`; the quarter rule at
  `Verse/Room.cs:105` and the map edge at `:103`; 65 at `RimWorld/JobDriver_AffectRoof.cs:15` and `:43`; 1.7 at `:47`; 15 and 30 at
  `Verse/RoofCollapserImmediate.cs:10`; the crush draw at `:136`, inclusive per `Verse/IntRange.cs:34`; the holder flag at
  `Verse/ThingDef.cs:304` and its tests at `Verse/RoofCollapseUtility.cs:20, 43`; the collapse steps at `Verse/RoofCollapseCellsFinder.cs:21,
  24-26, 27, 29-31, 40`; the radial sort at `Verse/GenRadial.cs:100`; the 3 by 3 block at `RimWorld/JobDriver_BuildRoof.cs:24-31`.
  One citation was corrected in an earlier pass: the `Roofs.kt` comment on the build check now cites `RimWorld/WorkGiver_BuildRoof.cs:23-55`
  (the whole function, it was `:23-53`).
* **D tags.** A citation to a file outside the reference table is D when it is a value, with the file and line cited, the line opened in
  the clone, and the value found there (the fourth-review rule). It is C or not adopted when it is a rule, or when the cited line does not
  show the claim. The D tags left in this document are: `RimWorld/JobDriver_AffectRoof.cs:15` and `:43` (65), `:47` (1.7),
  `Verse/RoofCollapserImmediate.cs:10` (15 to 30), `Verse/RoofCollapseUtility.cs:5` (6.9 cells, table line 348; line 5 is
  `RoofMaxSupportDistance = 6.9f`), `Verse/Room.cs:105` and `:103` (the quarter rule, table line 101, and the map edge, table line 102; line
  105 is the `CeilToInt` quarter test and line 103 the map-edge test), `RimWorld/GenDate.cs:9` (the day length, table section 3.1, as context
  only; line 9 is `TicksPerDay = 60000`) and `RimWorld/GenStep_RocksFromGrid.cs:59` and `:63` (the thick and thin thresholds, table section
  3.2 for `:59`; not adopted). Each was opened at the cited line in this round and the value is there.
* **Reference table: unchanged, checked against git.** `git diff HEAD -- docs/fidelity/decompiled-reference.md` is empty, so this working
  tree holds the committed table. Rows 101 and 348 are as committed, and no row was added. An earlier draft's edits to those rows (the
  Stillmere column, and row 101's status) are not in this tree. Both
  rows therefore describe the code before this work: row 101 still says `differs` and names `GameMap.kt:346`, and row 348 still says
  there are no roofs. Their decompiled columns and sources are unchanged, and the D citations above still hold. The owner should
  decide whether the two rows are corrected.
* **Reference table, row 3.2, not changed here.** The "Rock objects and natural roofs" row cites `:59` for both the thin value (0.728)
  and the thick value (0.798). The thin value is at `:63`. The owner should decide whether that row is corrected.

## Save format and migration (version 38)

Written from version 38 on, as one more flag after the snow of each cell. Version 37 and older saves have no flag and are migrated.

* `GameMap.roof` is one boolean per cell. Written after `snow` in each cell record, from version 38 on. `write(g, version)` still
  writes older layouts, without the flag, for tests.
* **Reading an older save** (version 37 or below): the flag is not in the file, so the reader rebuilds it with the old indoor rule.
  Every cell of each room that was indoor under the old rule (not touching the map edge, and at most 700 cells or with more natural
  roof than open ground) gets a built roof. Then the new rule runs.
* **Why no room changes status on load.** A room indoor under the old rule has all its cells roofed, so its open share is 0 and
  it stays indoor. A room outdoor under the old rule has no built roof on its cells, so its roofed share is at most its natural share.
  Under the old rule such a room was outdoor because it touched the edge, or had more than 700 cells with no natural majority; under the
  new rule its roofed share is then at most one half, which is below 75 percent. So it stays outdoor. Test:
  `LegacyRoofMigrationTest` (fixture `colony_v37.sav`).
* Fixture `colony_v37.sav` (with `colony_v37_expect.txt`) is a version-37 save with four walled areas, written by the code before the
  roof change (temporary generator, since deleted). `LegacyRoofMigrationTest` checks each room's status and each probe's roofed state.
  `colony_v36.sav` is unchanged.

## Determinism

* The indoor rule, support check and collapse draw nothing from the RNG. The crush roll is the only draw, from the sim's seeded RNG
  (`Rng.range`), one per pawn under a fallen roof. The fallen cells are in the order they were marked: the group walks of step 0, the row-by-row scan of the radius (step 1), then
  the group walks of step 2. The pawns are then taken in list order for each cell. Fallen cells are queued in
  `GameMap.pendingRoofFalls` and drained at the end of `Game.step` (`Game.kt:623`). The holder removals are reached from
  `pawnTick` (`Game.kt:633`, which calls `think` at `:685` and `driveJob` at `:686`) and from the mining and auger jobs
  (`JobDrivers.kt:462, 686`). The blueprint removal (`Game.kt:429`, `:493-497`) only removes unbuilt blueprints, which never
  reach `holderRemoved`. So a save between steps never holds a pending crush.
* No clock is read.

## The app (this round)

* **Build roof tool.** `Tool.Roof` ("Build roof") in `GameView.kt`, listed in the Orders menu after Repair (`Panels.kt`). Tap or drag
  marks each cell with `Desig.ROOF` through `g.designate`, as the other order tools do (`MainActivity.kt`, `onArea`). The tool is a
  paint tool, so the tap and the drag both reach `onArea`. Cancel orders clears a roof mark like any other.
* **Mark and status text.** A marked cell reads "Marked: roof" in the cell status (`MainActivity.kt`, next to the other marks). On the
  map, the mark is a light blue fill with a roof chevron (`GameView.kt`).
* **Drawing.** A built roof (`roof` set, `natRoof` clear) gets a faint slate tint over its floor, in the detail and the flat view. Natural
  roof keeps its shading, unchanged. Both are visual only: they change no sim value, no save and no lighting.
* **Not changed.** The roof rules, the sim's rule for a `Desig.ROOF` mark (`Game.kt:425`), the roof jobs, and the save format. Removing a
  roof is now available through the Remove roof tool (`Desig.UNROOF`). Vanilla's removal area and removal-mode collapse are not adopted.
* **Not built here.** The app cannot be compiled in this environment (no Android SDK). The edits follow the patterns in the same files.
  The `when` expressions over `Tool` all have an `else`, so the new object needs no other branch. CI must confirm the build.

## Tests

* `RoofsTest.kt` (new, 23 tests): the quarter rule and its edges, the 75 percent boundary, natural roof in the share, the support radius (six
  cells held, seven not), collapse on a holder's removal (strip, natural roof, outside the radius, a door), the crush roll (same seed,
  same damage; crush kind), building a designated room to indoors through `Game.step`, the 3 by 3 block roofed to a fixed point (the
  cells above and beside the centre are roofed in the same job; a tree cell in the block stays unroofed), the designation the app's roof
  tool makes (open ground takes the mark; rock and a roofed cell are refused; cancelling clears the mark), the save round trip, and a
  version 37 save loading with roofs on its indoor rooms.
* `LegacyRoofMigrationTest.kt` (new): fixture `colony_v37.sav`; every room keeps its status on load; roofed state is unchanged.

Existing tests changed, and the reason (no expected number changed, except the version):
* `ColonyMapSizeTest`: `colony_v37.sav` added to the table (250); the version 36 test now expects version 38 (it expected 37).
* `TemperatureRoomsTest`: `heatedRoomGame` and `twoRoomPlan` roof their floor; `roomStatsMatchAHandCalculatedLayout`,
  `aDiningRoomHasARoleAndABareFloorIsUgly` and `outdoorCellsAndPlantsAndRoofAreScored` roof their rooms. Their hand-calculated values
  are unchanged.
* `GameTest` (`roomsHoldHeatFromACampfire`): roofs the 3 by 3 floor of its walled room.

## Open items

* Vanilla's removal area and removal-mode collapse, and thin and thick roof kinds. A simple remove-roof designation exists (C).
* Trees that block a roof are not cut first.
* Plants other than trees that block a roof in vanilla are not modelled: Stillmere's plant types carry only a tree flag, and the
  vanilla flag's plant list is definition data (`UNVERIFIED`).
* Collapse does not destroy buildings or plants under it, and does not cascade.
* The app has a Build roof tool (Orders menu, tap or drag) that marks cells with `Desig.ROOF`, and it draws built roofs and the marks
  (see "The app"). Taking down a roof is available as a simple designation (`Desig.UNROOF`, C). The app is not built
  here: CI must confirm the build.
* The holder list (walls, doors, rock) is Stillmere's choice (C). The support radius is vanilla (D). The 3 by 3 block is roofed to a
  fixed point (C), not vanilla's single pass, which is not adopted (see "Corrections in this round").
* The build roof cost, the thick or thin flag of the built roof, and the holder flag of natural rock are definition data, not in the
  tree, and are `UNVERIFIED`. Stillmere charges no cost and treats the built roof as thin.
* Roof XP is Stillmere's own rate (C). Neither vanilla roof driver (`RimWorld/JobDriver_AffectRoof.cs`, `RimWorld/JobDriver_BuildRoof.cs`)
  calls a learn method (searched), so the rate is a design choice, not a verified value.
* Natural roof that is not thick falls in vanilla when its group loses support. Stillmere keeps it (decision in the natural roof row).
  Revisit this with the natural roof thick or thin split of 3.2.
* Step 2 does not start from a natural roof cell in the radius that is not held in range (not adopted; see the natural roof row). Whether
  vanilla's natural roof can collapse at all is definition data, **UNVERIFIED**.
* Owner decision: the 65 work, the 1.7 rate and the 15 to 30 crush roll are now tagged D with their file and line (the fourth review), so
  they need no reference-table row. Whether to add rows for them anyway is the owner's decision. The collapse steps and the holder test
  stay C: they are rules in Stillmere's own form, and a row for them needs a second-pass check before it can carry D. The 3 by 3 block is
  not adopted from vanilla, so it needs no row.
* Owner decision: rows 101 and 348 of the reference table describe the code before this work (see "Corrections in this round").
* The source is version 1.6.9438.38202, the same as the pinned build. Each vanilla value here is from the source build (see Tags).
