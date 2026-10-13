package io.github.teamomuito.colony.sim

import kotlin.math.ceil

/*
 * Roofs and roof collapse (docs/fidelity/roofs.md). Tags: D for the 6.9 support radius (a row of decompiled-reference.md, section 3.8),
 * for the work total 65 and the progress factor 1.7 of one roof job, and for the crush range 15 to 30. The last three carry D by their
 * cited lines in the decompiled source (RimWorld/JobDriver_AffectRoof.cs:15, :43 and :47; Verse/RoofCollapserImmediate.cs:10); no
 * reference-table row is needed for D. Every other value or rule here is C, which means Stillmere's own value or rule, or it is recorded
 * as not adopted with its reason. A vanilla source file cited for comparison only is not a D. Nothing here reads the clock. The only draw
 * from the RNG is the crush roll in [Game.resolveRoofCollapses].
 */

/** D: horizontal reach of a roof holder, in cells (Verse/RoofCollapseUtility.cs:5; reference table, section 3.8). */
internal const val ROOF_SUPPORT_RADIUS = 6.9f
private const val ROOF_SUPPORT_RADIUS_SQ = ROOF_SUPPORT_RADIUS * ROOF_SUPPORT_RADIUS

/**
 * D: the work total (65) of one roof job (RimWorld/JobDriver_AffectRoof.cs:15, the base work, and :43, the reset at the start of the job;
 * version 1.6.9438.38202 in the clone). D: the progress factor (1.7) per tick, applied to the pawn's work speed in place of vanilla's
 * construction speed stat (RimWorld/JobDriver_AffectRoof.cs:47). That stat mapping is Stillmere's own (C). The pinned build is
 * 1.6.9438.38202, the same as the source (docs/fidelity/roofs.md, constants table).
 */
internal const val ROOF_WORK = 65f
internal const val ROOF_WORK_RATE = 1.7f

/**
 * D: the crush range, 15 to 30 (Verse/RoofCollapserImmediate.cs:10), used as the damage of each crush wound. The draw itself is
 * Stillmere's own (C): inclusive through the sim RNG (`Rng.range`, Noise.kt:70); vanilla's inclusive draw is at Verse/IntRange.cs:34,
 * cited for comparison only. Who is hit and how many rolls are made is also Stillmere's own rule (C). The pinned build is 1.6.9438.38202,
 * the same as the source.
 */
internal const val ROOF_CRUSH_MIN = 15
internal const val ROOF_CRUSH_MAX = 30

/**
 * C: the cell and its four cardinal neighbours (Stillmere's own adjacency). Vanilla's offsets at Verse/GenAdj.cs:55-59 are the same
 * four, cited for comparison only (not in the reference table). The last entry is the cell itself.
 */
private val ROOF_DX = intArrayOf(0, 1, 0, -1, 0)
private val ROOF_DY = intArrayOf(1, 0, -1, 0, 0)

/** C: whether (x, y) is within the support radius of (cx, cy). The squared horizontal distance is at most the radius squared (Verse/IntVec3.cs:173-178; not in the reference table). */
internal fun inRoofRange(x: Int, y: Int, cx: Int, cy: Int): Boolean {
    val dx = (x - cx).toFloat()
    val dy = (y - cy).toFloat()
    return dx * dx + dy * dy <= ROOF_SUPPORT_RADIUS_SQ
}

/** The neighbour in direction [d] (0 to 4, see [ROOF_DX]), or -1 off the map. */
private fun GameMap.neighbour(i: Int, d: Int): Int {
    val x = xOf(i) + ROOF_DX[d]
    val y = yOf(i) + ROOF_DY[d]
    return if (inB(x, y)) idx(x, y) else -1
}

/**
 * A roof holder: natural rock, or a built wall or door. C: the holder test is vanilla's (Verse/RoofCollapseUtility.cs:20 and :43 test the
 * holder flag of the def, which Verse/ThingDef.cs:304 declares; neither is a row of the reference table). Which Stillmere defs carry
 * the flag is a Stillmere choice (walls and doors), and natural rock holding a roof is UNVERIFIED (docs/fidelity/roofs.md).
 */
internal fun GameMap.isRoofHolder(i: Int): Boolean {
    if (terrain[i] == Terrain.ROCK) return true
    val b = building[i] ?: return false
    return b.built && (b.def.isWall || b.def.isDoor)
}

/** Whether a holder lies on [i] or one of its cardinal neighbours. With [c] at or above zero, only holders within the support radius of [c] count. */
private fun GameMap.holderNextTo(i: Int, c: Int): Boolean {
    for (d in 0 until 5) {
        val n = neighbour(i, d)
        if (n < 0) continue
        if (c >= 0 && !inRoofRange(xOf(n), yOf(n), xOf(c), yOf(c))) continue
        if (isRoofHolder(n)) return true
    }
    return false
}

/**
 * The cells reached from [start] through cardinal neighbours that are roofed. [start] is always included, as the root of the flood.
 */
private fun GameMap.roofedGroup(start: Int): List<Int> {
    val out = ArrayList<Int>()
    val seen = HashSet<Int>()
    val queue = ArrayDeque<Int>()
    queue.add(start); seen.add(start)
    while (queue.isNotEmpty()) {
        val x = queue.removeFirst()
        out.add(x)
        for (d in 0 until 4) {
            val n = neighbour(x, d)
            if (n < 0 || n in seen || !roofed(n)) continue
            seen.add(n); queue.add(n)
        }
    }
    return out
}

/**
 * C: Stillmere's form of the vanilla rule at Verse/RoofCollapseUtility.cs:9-30 (not in the reference table). A cell is held in range when
 * a holder lies next to a roofed cell that is itself within the support radius of [c] and reachable from [c] through such cells. [c]
 * counts as the root whether or not it is roofed.
 */
internal fun GameMap.heldInRange(c: Int): Boolean {
    val cx = xOf(c)
    val cy = yOf(c)
    val seen = HashSet<Int>()
    val queue = ArrayDeque<Int>()
    queue.add(c); seen.add(c)
    while (queue.isNotEmpty()) {
        val x = queue.removeFirst()
        if (holderNextTo(x, c)) return true
        for (d in 0 until 4) {
            val n = neighbour(x, d)
            if (n < 0 || n in seen || !roofed(n) || !inRoofRange(xOf(n), yOf(n), cx, cy)) continue
            seen.add(n); queue.add(n)
        }
    }
    return false
}

/**
 * C: Stillmere's form of the vanilla rule at Verse/RoofCollapseUtility.cs:32-52 (not in the reference table). The roofed group of [c],
 * with no distance limit, touches a holder. With [rootCountsAsRoofed], [c] counts as roofed.
 */
internal fun GameMap.connectedToHolder(c: Int, rootCountsAsRoofed: Boolean): Boolean {
    if (!rootCountsAsRoofed && !roofed(c)) return false
    return roofedGroup(c).any { holderNextTo(it, -1) }
}

/**
 * Whether a built roof can go on [i] now: not roofed yet, no tree in the way, and held. C: Stillmere's form of RimWorld/WorkGiver_BuildRoof.cs:23-55
 * (not in the reference table). Vanilla
 * asks for a plant that blocks a roof to be cut first (Verse/RoofUtility.cs:9-20, :58-73). Here only a tree blocks, and the cell is
 * skipped until the tree is gone (docs/fidelity/roofs.md, plant rule).
 */
internal fun GameMap.canBuildRoofAt(i: Int): Boolean =
    !roofed(i) && plant[i]?.type?.isTree != true && heldInRange(i) && connectedToHolder(i, rootCountsAsRoofed = true)

/**
 * A holder at ([hx], [hy]) has gone (its building was removed, or its rock was mined). Built roofs that lost their support fall. C: these
 * steps are Stillmere's form of Verse/RoofCollapseCellsFinder.cs:19-42 and :87-121 (not in the reference table), in this order:
 *  0. the holder's own cell and its four neighbours: a roofed group that touches no holder falls;
 *  1. every cell within the support radius of the holder that is not held in range is marked;
 *  2. the neighbours of the cells from step 1: a roofed group that touches no holder falls.
 * Only built roofs fall. Natural roof is kept, and still counts as roofed (docs/fidelity/roofs.md). The radius is scanned row by row
 * (not vanilla's radial order, a difference not adopted in docs/fidelity/roofs.md). The fallen set does not depend on that order, since
 * each roofed group is decided once; only the order of the crush draws does. The fallen cells are queued in [GameMap.pendingRoofFalls]
 * for the crush roll. Returns the cells whose roof fell.
 */
internal fun GameMap.holderRemoved(hx: Int, hy: Int): List<Int> {
    val center = idx(hx, hy)
    val marked = LinkedHashSet<Int>()
    val seen = HashSet<Int>()

    fun fallIfUnheld(start: Int) {
        val group = roofedGroup(start)
        seen.addAll(group)
        if (group.none { holderNextTo(it, -1) }) for (x in group) if (roof[x]) marked.add(x)
    }

    for (d in 0 until 5) {
        val n = neighbour(center, d)
        if (n < 0 || !roofed(n) || n in seen || n in marked) continue
        fallIfUnheld(n)
    }

    val tooFar = ArrayList<Int>()
    val r = ceil(ROOF_SUPPORT_RADIUS).toInt()
    for (dy in -r..r) for (dx in -r..r) {
        val x = hx + dx
        val y = hy + dy
        if (!inB(x, y) || !inRoofRange(x, y, hx, hy)) continue
        val c = idx(x, y)
        if (!roof[c] || c in marked) continue
        if (!heldInRange(c)) { marked.add(c); tooFar.add(c) }
    }

    for (c in tooFar) for (d in 0 until 5) {
        val n = neighbour(c, d)
        if (n < 0 || !roofed(n) || n in seen || n in marked) continue
        fallIfUnheld(n)
    }

    val fell = ArrayList<Int>()
    for (c in marked) if (roof[c]) { roof[c] = false; fell.add(c) }
    if (fell.isNotEmpty()) {
        roomDirty = true
        pendingRoofFalls.addAll(fell)
    }
    return fell
}

/**
 * A finished roof job roofs the three by three block around its cell (C: the nine-cell footprint, see docs/fidelity/roofs.md). Each
 * designated, unroofed cell of the block that is held in range and has no tree is roofed. The block is scanned again until no cell
 * changes, so a cell held only through another block cell is roofed in this same job once that cell is roofed.
 *
 * Not adopted: vanilla makes one pass over its nine entries (RimWorld/JobDriver_BuildRoof.cs:24-31, Verse/GenAdj.cs:80-88, outside
 * the reference table), so a cell held only through a later entry waits for a later job. Stillmere does not copy that order. Roofing a
 * cell can only add holds and never remove one, so the set reached is the same whatever order the cells are scanned in.
 */
internal fun GameMap.finishRoofBlock(cx: Int, cy: Int) {
    val block = ArrayList<Int>(9)
    for (y in cy - 1..cy + 1) for (x in cx - 1..cx + 1) if (inB(x, y)) block.add(idx(x, y))
    var changed = true
    while (changed) {
        changed = false
        for (i in block) {
            if (desig[i].toInt() != Desig.ROOF || roofed(i) || plant[i]?.type?.isTree == true || !heldInRange(i)) continue
            setRoof(i, true)
            desig[i] = 0
            changed = true
        }
    }
}

/**
 * Crush roll for each pawn standing under the built roofs that fell. C: the roll is Stillmere's own rule, one draw per pawn per fall. Its
 * range, [ROOF_CRUSH_MIN] to [ROOF_CRUSH_MAX] inclusive, is D (see above). Vanilla draws once per thing, in the branch at
 * Verse/RoofCollapserImmediate.cs:127-152 (the roof has no impassable leaving thing, :81); that per-thing draw is not adopted.
 * Each pawn takes its roll on its head (or its torso if it has no head). Items, plants and buildings take no damage here
 * (docs/fidelity/roofs.md).
 * The queue is emptied, so the roll happens once per fall.
 */
internal fun Game.resolveRoofCollapses() {
    val queue = map.pendingRoofFalls
    if (queue.isEmpty()) return
    val fallen = queue.toList()
    queue.clear()
    for (c in fallen) {
        val x = map.xOf(c)
        val y = map.yOf(c)
        for (p in pawns.toList()) {
            if (!p.alive || p.x != x || p.y != y) continue
            val part = partsWithTag(p, PartTag.HEAD).firstOrNull() ?: partsWithTag(p, PartTag.TORSO).firstOrNull() ?: continue
            woundPart(p, part, DamageKind.CRUSH, rng.range(ROOF_CRUSH_MIN, ROOF_CRUSH_MAX).toFloat())
        }
    }
}
