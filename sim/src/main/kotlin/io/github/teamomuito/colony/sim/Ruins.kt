package io.github.teamomuito.colony.sim

import kotlin.math.max
import kotlin.math.round

/**
 * Generated ruins on a colony map: square ruins of stone or steel walls with a stone floor inside, walls and floors damaged by the
 * vanilla rule for destroyed buildings, rough rock where walls fell, and loot in the footprint. Every constant and its source is in
 * docs/fidelity/ruins.md.
 *
 * Ruins draw from a generator of their own, so the map's terrain, ore and plant rolls are the same as on a map without ruins. They
 * are placed after ore and before plants; plants are cleared from the footprints afterwards, and the rubble and loot come last.
 */
internal class Ruins private constructor(private val m: GameMap, seed: Long) {
    private val rng = Rng(seed xor RNG_SALT)

    /** Every cell inside a placed ruin, its walls included. */
    private val footprint = BooleanArray(m.size)

    /** Cells with a standing ruin wall: the walls placed so far, and the candidate being tested. */
    private val wallBlocked = BooleanArray(m.size)

    private val rects = ArrayList<Rect>()
    private val centres = ArrayList<IntArray>()
    private val placed = ArrayList<RuinPlan>()

    /** The ruins placed so far, in placement order. */
    internal val plans: List<RuinPlan> get() = placed

    private class Rect(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
        val width get() = x1 - x0 + 1
        val height get() = y1 - y0 + 1
        fun overlaps(o: Rect) = x0 <= o.x1 && o.x0 <= x1 && y0 <= o.y1 && o.y0 <= y1
        fun expanded(n: Int) = Rect(x0 - n, y0 - n, x1 + n, y1 + n)
        fun clipped(w: Int, h: Int) = Rect(max(0, x0), max(0, y0), minOf(w - 1, x1), minOf(h - 1, y1))
    }

    private class Entry(val cell: Int, val wall: Boolean)

    private class LootDraw(val type: ItemType, val count: Int)

    private fun buildable(i: Int) = m.terrain[i].passable && m.terrain[i] != Terrain.WATER_SHALLOW

    private fun passable(i: Int) = m.terrain[i].passable && !wallBlocked[i]

    /** Places the ruins in the walls and floors. Plants and rubble come in [finish]. */
    private fun placeWalls() {
        val count = ruinCount(m.w, m.h, COUNT_PER_10K_MIN + (COUNT_PER_10K_MAX - COUNT_PER_10K_MIN) * rng.float())
        for (k in 0 until count) {
            val (cx, cy, size) = findCentre() ?: return
            place(cx, cy, size)
            centres.add(intArrayOf(cx, cy))
        }
    }

    /** One search for a centre, as the scatterer does: a size is drawn first, then up to [SEARCH_TRIES] cells are tried. */
    private fun findCentre(): Triple<Int, Int, Int>? {
        val size = drawSize(rng)
        repeat(SEARCH_TRIES) {
            val x = rng.range(MIN_EDGE, m.w - MIN_EDGE - 1)
            val y = rng.range(MIN_EDGE, m.h - MIN_EDGE - 1)
            if (canScatterAt(x, y, size)) return Triple(x, y, size)
        }
        return null
    }

    private fun canScatterAt(cx: Int, cy: Int, size: Int): Boolean {
        for (c in centres) {
            val dx = c[0] - cx
            val dy = c[1] - cy
            if (dx * dx + dy * dy <= MIN_SPACING * MIN_SPACING) return false
        }
        val full = centred(cx, cy, size)
        if (rects.any { it.overlaps(full.expanded(USED_PADDING)) }) return false
        if (!buildable(m.idx(cx, cy))) return false
        val start = Rect(m.spawnX - START_CLEAR, m.spawnY - START_CLEAR, m.spawnX + START_CLEAR, m.spawnY + START_CLEAR)
        if (start.overlaps(full)) return false
        return canPlaceIn(full.clipped(m.w, m.h))
    }

    private fun canPlaceIn(r: Rect): Boolean {
        if (r.width < MIN_SIDE || r.height < MIN_SIDE) return false
        if (rects.any { it.overlaps(r) }) return false
        for (y in r.y0..r.y1) for (x in r.x0..r.x1) {
            val i = m.idx(x, y)
            if (!buildable(i) || m.building[i] != null) return false
        }
        return true
    }

    private fun centred(cx: Int, cy: Int, size: Int): Rect {
        val x0 = cx - size / 2
        val y0 = cy - size / 2
        return Rect(x0, y0, x0 + size - 1, y0 + size - 1)
    }

    private fun place(cx: Int, cy: Int, size: Int) {
        val r = centred(cx, cy, size).clipped(m.w, m.h)
        if (!canPlaceIn(r)) return
        build(cx, cy, r)
    }

    private fun build(cx: Int, cy: Int, r: Rect) {
        val material = if (rng.chance(0.5f)) ItemType.STONE else ItemType.STEEL
        val maxHp = Building(BuildDef.WOOD_WALL, 0, 0, true).also { it.material = material }.maxHp
        val outline = ArrayList<Int>()
        val interior = ArrayList<Int>()
        for (y in r.y0..r.y1) for (x in r.x0..r.x1) {
            val i = m.idx(x, y)
            if (x == r.x0 || x == r.x1 || y == r.y0 || y == r.y1) outline.add(i) else interior.add(i)
        }
        val entrance = pickEntrance(r)
        val entries = ArrayList<Entry>(outline.size + interior.size)
        for (i in outline) if (i != entrance) entries.add(Entry(i, true))
        for (i in interior) entries.add(Entry(i, false))
        // The entrance counts as a buildable in the damage pass, as the monument's door did (the cap is the same as the reference).
        val buildables = entries.size + (if (entrance >= 0) 1 else 0)
        val dir = rng.int(4)
        shuffle(entries)
        val horizontal = dir == 1 || dir == 3
        val cap = buildables * MAX_DESTROYED
        val gone = HashSet<Int>()
        val hpOf = HashMap<Int, Int>()
        var destroyed = 0
        for (e in entries) {
            val x = m.xOf(e.cell)
            val y = m.yOf(e.cell)
            var num = if (horizontal) (x - r.x0).toFloat() / r.width else (y - r.y0).toFloat() / r.height
            if (dir == 1 || dir == 2) num = 1f - num
            val f = StrictMath.pow(num.toDouble(), DESTROY_EXP.toDouble()).toFloat()
            if (rng.float() < f) {
                gone.add(e.cell)
                destroyed++
                if (destroyed.toFloat() > cap) break
            } else if (e.wall) {
                val factor = 1f + (HP_RANDOM - 1f) * rng.float()
                hpOf[e.cell] = round(maxHp * (1f - num) * factor).toInt().coerceIn(1, round(maxHp).toInt())
            }
        }
        val walls = outline.filter { it != entrance && it !in gone }
        val floors = interior.filter { it !in gone }
        val destroyedWalls = outline.filter { it != entrance && it in gone }
        // A ruin that seals a side of the map is dropped. Its walls are tested as standing, then released.
        for (i in walls) wallBlocked[i] = true
        if (!reachesEverySide()) {
            for (i in walls) wallBlocked[i] = false
            return
        }
        for (i in walls) {
            val b = Building(BuildDef.WOOD_WALL, m.xOf(i), m.yOf(i), true)
            b.material = material
            b.hp = (hpOf[i] ?: round(maxHp).toInt()).toFloat()
            m.setBuilding(b)
        }
        for (i in floors) m.floor[i] = BuildDef.STONE_FLOOR
        for (y in r.y0..r.y1) for (x in r.x0..r.x1) footprint[m.idx(x, y)] = true
        rects.add(r)
        placed.add(RuinPlan(cx, cy, r.x0, r.y0, r.x1, r.y1, material, walls.toIntArray(), floors.toIntArray(), destroyedWalls.toIntArray(), buildables, destroyed))
    }

    /**
     * The entrance: an outline cell that is not a corner, whose outside neighbour is walkable. One is drawn at random. Vanilla replaces a
     * random wall that borders the edge with a door (a closed monument), and Stillmere keeps that as an open cell. -1 when none qualifies.
     */
    private fun pickEntrance(r: Rect): Int {
        val options = ArrayList<Int>()
        for (y in r.y0..r.y1) for (x in r.x0..r.x1) {
            val onOutline = x == r.x0 || x == r.x1 || y == r.y0 || y == r.y1
            if (!onOutline) continue
            val corner = (x == r.x0 || x == r.x1) && (y == r.y0 || y == r.y1)
            if (corner) continue
            val ox = when { x == r.x0 -> x - 1; x == r.x1 -> x + 1; else -> x }
            val oy = when { y == r.y0 -> y - 1; y == r.y1 -> y + 1; else -> y }
            if (!m.inB(ox, oy) || !passable(m.idx(ox, oy))) continue
            options.add(m.idx(x, y))
        }
        return if (options.isEmpty()) -1 else options[rng.int(options.size)]
    }

    private fun shuffle(list: MutableList<Entry>) {
        for (k in list.size - 1 downTo 1) {
            val j = rng.int(k + 1)
            val t = list[k]; list[k] = list[j]; list[j] = t
        }
    }

    /** True when every side of the map can be reached from the start, with the standing ruin walls blocking (the rule of connectSides). */
    private fun reachesEverySide(): Boolean {
        val start = m.idx(m.spawnX, m.spawnY)
        if (!passable(start)) return false
        val seen = BooleanArray(m.size)
        val queue = IntArray(m.size)
        var head = 0
        var tail = 0
        seen[start] = true
        queue[tail++] = start
        while (head < tail) {
            val c = queue[head++]
            val cx = m.xOf(c)
            val cy = m.yOf(c)
            for (d in 0 until 8) {
                val nx = cx + GameMap.DX8[d]
                val ny = cy + GameMap.DY8[d]
                if (!m.inB(nx, ny)) continue
                val n = m.idx(nx, ny)
                if (seen[n] || !passable(n)) continue
                // No cutting corners around obstacles, as the pathfinder does.
                if (d >= 4 && (!passable(m.idx(nx, cy)) || !passable(m.idx(cx, ny)))) continue
                seen[n] = true
                queue[tail++] = n
            }
        }
        for (side in 0 until 4) {
            if (!m.sideCells(side).any { seen[it] && m.terrain[it] != Terrain.WATER_SHALLOW }) return false
        }
        return true
    }

    /** Drops the plants that the footprints cover, then the rubble and the loot. Runs after the plant pass. */
    internal fun finish() {
        for (i in 0 until m.size) if (footprint[i]) m.plant[i] = null
        for (p in placed) {
            for (cell in p.destroyedWalls) if (rng.chance(RUBBLE_CHANCE)) m.drop(ItemType.STONE_CHUNK, 1, m.xOf(cell), m.yOf(cell))
            val things = List(rng.range(LOOT_MIN, LOOT_MAX)) { pickLoot() }.toMutableList()
            val cells = ArrayList<Int>()
            for (y in p.y0..p.y1) for (x in p.x0..p.x1) {
                val i = m.idx(x, y)
                if (buildable(i) && m.building[i] == null && m.items[i] == null && m.plant[i] == null) cells.add(i)
            }
            // As GenerateLoot does: a random free cell and a random thing, until one runs out.
            while (cells.isNotEmpty() && things.isNotEmpty()) {
                val c = cells.removeAt(rng.int(cells.size))
                val t = things.removeAt(rng.int(things.size))
                m.items[c] = ItemStack(m.nextId(), t.type, t.count, m.xOf(c), m.yOf(c))
            }
        }
    }

    private fun pickLoot(): LootDraw {
        var u = rng.int(LOOT_TABLE.sumOf { it.weight })
        for (e in LOOT_TABLE) {
            if (u < e.weight) return LootDraw(e.type, rng.range(e.min, e.max))
            u -= e.weight
        }
        val e = LOOT_TABLE.last()
        return LootDraw(e.type, rng.range(e.min, e.max))
    }

    companion object {
        /** Salt of the ruin generator's seed (C). */
        internal const val RNG_SALT = 0x3c6e5a17L

        /** Edge distance of a centre (Verse/GenStep_Scatterer.cs:137, Verse/CellFinder.cs:138-147). */
        internal const val MIN_EDGE = 5
        /** Tries for one centre (Verse/CellFinderLoose.cs:33). */
        internal const val SEARCH_TRIES = 1000
        /** Minimum distance between centres (Verse/GenStep_Scatterer.cs:17). */
        internal const val MIN_SPACING = 10
        /** Gap kept between footprints (RimWorld/GenStep_ScatterRuinsSimple.cs:30). */
        internal const val USED_PADDING = 2
        /** No footprint within this distance of the start (C). */
        internal const val START_CLEAR = 6
        /** Narrowest footprint with an interior (C). */
        internal const val MIN_SIDE = 3

        /** Power of the position in the destroy chance (RimWorld/GenStep_ScatterRuinsSimple.cs:22). */
        internal const val DESTROY_EXP = 1.32f
        /** Hit points of a surviving wall vary by up to this factor (RimWorld.SketchGen/SketchResolver_DamageBuildings.cs:11). */
        internal const val HP_RANDOM = 1.2f
        /** The damage pass stops once this share of the buildables is gone (RimWorld.SketchGen/SketchResolver_DamageBuildings.cs:9). */
        internal const val MAX_DESTROYED = 0.65f

        /** Ruins per 10,000 cells, drawn between these (C, UNVERIFIED: the vanilla range is XML). */
        internal const val COUNT_PER_10K_MIN = 0.5f
        internal const val COUNT_PER_10K_MAX = 1.0f

        /** Chance that a destroyed wall leaves rough rock (C). Vanilla uses 0.5 around a tank trap, not per wall (RimWorld.BaseGen/SymbolResolver_AncientComplex_Defences.cs:77). */
        internal const val RUBBLE_CHANCE = 0.5f

        /** Loot stacks per ruin, inclusive (RimWorld.BaseGen/SymbolResolver_LootScatter.cs:8). */
        internal const val LOOT_MIN = 3
        internal const val LOOT_MAX = 10

        /** Size curve of the ruin's side (RimWorld/GenStep_ScatterRuinsSimple.cs:12-18): (side, weight) points. */
        internal val SIZE_CURVE = listOf(6f to 0f, 6.001f to 4f, 10f to 1f, 30f to 0f)

        /** Loot table (C, UNVERIFIED): weights, and the count of each stack. */
        internal val LOOT_TABLE: List<LootEntry> = listOf(
            LootEntry(ItemType.STEEL, 4, 15, 45),
            LootEntry(ItemType.STONE, 3, 10, 30),
            LootEntry(ItemType.CLOTH, 3, 10, 30),
            LootEntry(ItemType.SILVER, 3, 20, 60),
            LootEntry(ItemType.GOLD, 1, 5, 15),
            LootEntry(ItemType.HARDALLOY, 1, 5, 15),
            LootEntry(ItemType.COMPONENT, 2, 1, 4),
            LootEntry(ItemType.MEDS_STERILE_DRESSING, 2, 1, 3),
            LootEntry(ItemType.SCULPTURE_SMALL, 1, 1, 1),
            LootEntry(ItemType.W_RIFLE, 1, 1, 1),
            LootEntry(ItemType.A_PADDED_COAT, 1, 1, 1),
        )

        /** Places the ruins of [m] for [seed]. Call it after the ore and before the plants; then call [finish]. */
        internal fun place(m: GameMap, seed: Long): Ruins = Ruins(m, seed).also { it.placeWalls() }

        /**
         * Ruins on a [w] by [h] map at [per10k] per 10,000 cells: the count of the scatterer (Verse/GenStep_Scatterer.cs:266-274).
         * One ruin covers round(10,000 / per10k) cells, and the count is the area over that, rounded.
         */
        internal fun ruinCount(w: Int, h: Int, per10k: Float): Int {
            val cellsPerRuin = round(10000f / per10k).toInt()
            return round((w * h).toFloat() / cellsPerRuin).toInt()
        }

        /**
         * A side drawn from [SIZE_CURVE]. The curve is a density: a segment's weight is its width times the sum of its heights, and
         * within a segment the density is linear (Verse/Rand.cs:456-495, the same density). The segment is chosen by weight,
         * then the side by rejection on the segment's density. The side is rounded to the nearest whole number.
         */
        internal fun drawSize(rng: Rng): Int {
            var total = 0f
            for (k in 0 until SIZE_CURVE.size - 1) total += segmentWeight(k)
            var u = rng.float() * total
            var k = 0
            while (k < SIZE_CURVE.size - 2) {
                val w = segmentWeight(k)
                if (u < w) break
                u -= w
                k++
            }
            val (x0, y0) = SIZE_CURVE[k]
            val (x1, y1) = SIZE_CURVE[k + 1]
            val top = max(y0, y1)
            var x = x0
            for (tries in 0 until 1000) {
                val cand = x0 + (x1 - x0) * rng.float()
                val height = y0 + (y1 - y0) * ((cand - x0) / (x1 - x0))
                if (rng.float() * top < height) {
                    x = cand
                    break
                }
            }
            return round(x).toInt()
        }

        private fun segmentWeight(k: Int): Float {
            val (x0, y0) = SIZE_CURVE[k]
            val (x1, y1) = SIZE_CURVE[k + 1]
            return (x1 - x0) * (y0 + y1)
        }
    }
}

/** Each loot kind: its weight in the table and the count of one stack. */
internal class LootEntry(val type: ItemType, val weight: Int, val min: Int, val max: Int)

/**
 * One ruin as placed. Not saved: it records how the map was generated, for the tests. [destroyedWalls] fell in the damage pass and may
 * leave rough rock; [buildables] is the number of walls and floors the damage pass saw, and [destroyed] the number it removed.
 */
internal class RuinPlan(
    val centreX: Int,
    val centreY: Int,
    val x0: Int,
    val y0: Int,
    val x1: Int,
    val y1: Int,
    val material: ItemType,
    val walls: IntArray,
    val floors: IntArray,
    val destroyedWalls: IntArray,
    val buildables: Int,
    val destroyed: Int,
)
