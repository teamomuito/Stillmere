package io.github.teamomuito.colony.sim

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/** Hilliness of a world tile. Mountainous tiles cannot be crossed. */
enum class Hills(val label: String, val cost: Float) {
    FLAT("Flat", 0f), SMALL("Small hills", 0.9f), LARGE("Large hills", 1.7f), MOUNTAIN("Impassable mountains", 99f)
}

/** A faction of the world: kind 0 tribe, 1 outlander union, 2 pirate gang. */
class WorldFaction(val id: Int, val name: String, val kind: Int, val color: Int) {
    val label get() = name
    val permanentEnemy get() = kind == 2
    val trades get() = kind != 2
    val kindLabel get() = when (kind) { 0 -> "Tribal"; 1 -> "Outlander"; else -> "Pirate" }
}

class SettlementRequest(val type: ItemType, val count: Int, val reward: Int, val expires: Long)

class Settlement(val index: Int, val name: String, val tile: Int, val faction: WorldFaction) {
    val stock = Stock()
    var silver = 0
    var stockTick = -1_000_000L
    var request: SettlementRequest? = null
    var destroyedUntil = 0L
}

/** A temporary place of interest on the world map (an enemy camp to clear). */
class Site(val id: Int, val tile: Int, val kind: Int, val factionId: Int, val reward: Int, val expires: Long, val strength: Float, val name: String)

/** The planet: a coarse grid of tiles with biomes, rivers, roads and settlements. Regenerated from the seed; only dynamic state is saved. */
class World(val w: Int, val h: Int) {
    val biome = Array(w * h) { Biome.TEMPERATE }
    val water = BooleanArray(w * h)
    val river = BooleanArray(w * h)
    val hills = Array(w * h) { Hills.FLAT }
    val road = BooleanArray(w * h)
    val elevation = FloatArray(w * h)
    val settlements = ArrayList<Settlement>()
    val factions = ArrayList<WorldFaction>()
    val goodwill = IntArray(8)
    /** relation[a][b]: -1 at war, 0 neutral, 1 allied. */
    val relation = Array(8) { IntArray(8) }
    val sites = ArrayList<Site>()
    var nextSiteId = 1
    /** Offers and obligations; see Quests.kt. Saved. */
    val quests = ArrayList<Quest>()
    var nextQuestId = 1
    /** Colonists the enemy holds at camps. Saved with their full pawn state. */
    val captives = ArrayList<Captive>()
    var homeTile = 0

    fun x(t: Int) = t % w
    fun y(t: Int) = t / w
    fun tile(x: Int, y: Int) = y * w + x
    fun inB(x: Int, y: Int) = x in 0 until w && y in 0 until h
    fun settlementAt(t: Int): Settlement? = settlements.firstOrNull { it.tile == t }
    fun siteAt(t: Int): Site? = sites.firstOrNull { it.tile == t }
    fun passable(t: Int) = !water[t] && hills[t] != Hills.MOUNTAIN
    fun adjacentWater(t: Int): Boolean {
        for (dy in -1..1) for (dx in -1..1) { val nx = x(t) + dx; val ny = y(t) + dy; if (inB(nx, ny) && water[tile(nx, ny)]) return true }
        return false
    }

    fun biomeCost(b: Biome) = when (b) {
        Biome.TEMPERATE -> 1.5f; Biome.BOREAL -> 1.8f; Biome.TUNDRA -> 2.0f
        Biome.DESERT -> 1.9f; Biome.TROPICAL -> 3.0f; Biome.ARID -> 1.7f
    }

    /** Movement difficulty of a tile; roads cut it down a lot, rivers slow crossings. */
    fun cost(t: Int): Float = ((biomeCost(biome[t]) + hills[t].cost) + (if (river[t]) 1.4f else 0f)) * (if (road[t]) 0.45f else 1f)

    fun forageChance(t: Int) = when (biome[t]) {
        Biome.TEMPERATE -> 0.8f; Biome.BOREAL -> 0.5f; Biome.TUNDRA -> 0.15f
        Biome.DESERT -> 0.08f; Biome.TROPICAL -> 0.9f; Biome.ARID -> 0.3f
    }

    fun relationOf(a: Int, b: Int) = if (a == b) 1 else relation[a][b]

    /** Cheapest route from [from] to [to], excluding [from]; null when unreachable. */
    fun path(from: Int, to: Int): List<Int>? {
        if (from == to) return emptyList()
        if (!passable(to)) return null
        val dist = FloatArray(w * h) { Float.MAX_VALUE }
        val prev = IntArray(w * h) { -1 }
        val pq = PriorityQueue<Pair<Float, Int>>(compareBy { it.first })
        dist[from] = 0f; pq.add(0f to from)
        while (pq.isNotEmpty()) {
            val (d, t) = pq.poll()
            if (d > dist[t]) continue
            if (t == to) break
            val tx = x(t); val ty = y(t)
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = tx + dx; val ny = ty + dy
                if (!inB(nx, ny)) continue
                val n = tile(nx, ny)
                if (!passable(n)) continue
                val step = (cost(t) + cost(n)) * 0.5f * (if (dx != 0 && dy != 0) 1.41f else 1f)
                val nd = d + step
                if (nd < dist[n]) { dist[n] = nd; prev[n] = t; pq.add(nd to n) }
            }
        }
        if (prev[to] < 0) return null
        val out = ArrayList<Int>()
        var c = to
        while (c != from) { out.add(c); c = prev[c] }
        out.reverse()
        return out
    }

    fun routeCost(from: Int, route: List<Int>): Float {
        var c = 0f; var p = from
        for (t in route) { c += (cost(p) + cost(t)) * 0.5f * (if (x(p) != x(t) && y(p) != y(t)) 1.41f else 1f); p = t }
        return c
    }

    companion object {
        const val W = 60
        const val H = 40
        const val TICKS_PER_COST = 1900f

        private val names1 = listOf("Ash", "Red", "Stone", "Mill", "Crow", "Dun", "Bright", "Grey", "Thorn", "Fox", "Salt", "Iron", "Moss", "Wolf", "Ember", "Cinder", "Hollow", "Rook", "Black", "Frost", "Elm", "Hawk", "Marsh", "Gull")
        private val names2 = listOf("ford", "haven", "wick", "stead", "ridge", "mere", "fall", "gate", "holm", "camp", "post", "reach", "hold", "bury", "field", "crest")
        private val tribeNames = listOf("Wild-Vale tribe", "Ash-Mother clans", "Stonefist tribe", "River-Hawk tribe", "Deep-Moss people", "Sun-Spear tribe")
        private val outlanderNames = listOf("Cross-Harbor union", "Free Settlers' league", "New Haven co-op", "Iron Road company", "Greenfield union", "Quarry Guild")
        private val pirateNames = listOf("Rough-Knife gang", "Black Dune pirates", "Red Fang raiders", "Salt Wolves", "Grim Flag band", "Dust Vultures")

        /**
         * The planet for [seed]. The home tile is the land tile nearest the middle, of [homeBiome] when one is given. Given no
         * biome, the home tile's biome is whatever the planet has there, and that is the colony's biome.
         */
        fun generate(seed: Long, homeBiome: Biome? = null): World {
            val world = World(W, H)
            val rng = Rng(seed * 31 + 7)
            val s = (seed xor 0x5bd1e995L).toInt()
            val elev = Noise(s); val temp = Noise(s + 11); val rain = Noise(s + 23); val hill = Noise(s + 37); val lakeN = Noise(s + 53)
            for (y in 0 until H) for (x in 0 until W) {
                val t = y * W + x
                val e = elev.fractal(x.toFloat(), y.toFloat(), 0.09f)
                val edge = min(min(x, W - 1 - x), min(y, H - 1 - y)) / 4f
                val e2 = e + min(edge, 1f) * 0.18f
                world.elevation[t] = e
                world.water[t] = e2 < 0.5f || (lakeN.fractal(x.toFloat(), y.toFloat(), 0.3f) > 0.8f && e2 > 0.58f)
                val lat = 1f - abs(y / (H - 1f) - 0.5f) * 2f
                val tp = lat * 0.85f + (temp.fractal(x.toFloat(), y.toFloat(), 0.1f) - 0.5f) * 0.5f
                val r = rain.fractal(x.toFloat(), y.toFloat(), 0.11f)
                world.biome[t] = when {
                    tp < 0.28f -> Biome.TUNDRA
                    tp < 0.45f -> Biome.BOREAL
                    tp > 0.78f -> if (r > 0.52f) Biome.TROPICAL else if (r > 0.4f) Biome.ARID else Biome.DESERT
                    r > 0.42f -> Biome.TEMPERATE
                    else -> Biome.ARID
                }
                val hv = hill.fractal(x.toFloat(), y.toFloat(), 0.16f) + (1f - e2) * 0.1f
                world.hills[t] = when { hv > 0.74f -> Hills.MOUNTAIN; hv > 0.64f -> Hills.LARGE; hv > 0.54f -> Hills.SMALL; else -> Hills.FLAT }
                if (world.water[t]) world.hills[t] = Hills.FLAT
            }
            // Rivers: spring from high ground and run to the nearest water, preferring to flow downhill.
            val wd = IntArray(W * H) { if (world.water[it]) 0 else Int.MAX_VALUE }
            val bq = ArrayDeque<Int>(); for (t in 0 until W * H) if (world.water[t]) bq.add(t)
            while (bq.isNotEmpty()) {
                val t = bq.removeFirst()
                for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    val nx = world.x(t) + dx; val ny = world.y(t) + dy
                    if (!world.inB(nx, ny)) continue
                    val n = world.tile(nx, ny)
                    if (wd[n] > wd[t] + 1) { wd[n] = wd[t] + 1; bq.add(n) }
                }
            }
            val highs = (0 until W * H).filter { !world.water[it] && wd[it] >= 4 }.sortedByDescending { world.elevation[it] }.take(120).shuffled(java.util.Random(seed + 91))
            var made = 0
            for (src in highs) {
                if (made >= 9) break
                if (world.river[src]) continue
                val dist = FloatArray(W * H) { Float.MAX_VALUE }
                val prev = IntArray(W * H) { -1 }
                val pq = PriorityQueue<Pair<Float, Int>>(compareBy { it.first })
                dist[src] = 0f; pq.add(0f to src)
                var sink = -1
                while (pq.isNotEmpty()) {
                    val (d, t) = pq.poll()
                    if (d > dist[t]) continue
                    if (world.water[t] && t != src) { sink = t; break }
                    for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                        val nx = world.x(t) + dx; val ny = world.y(t) + dy
                        if (!world.inB(nx, ny)) continue
                        val n = world.tile(nx, ny)
                        val up = max(0f, world.elevation[n] - world.elevation[t])
                        val nd = d + 1f + up * 14f - (if (world.river[n]) 0.7f else 0f)
                        if (nd < dist[n]) { dist[n] = nd; prev[n] = t; pq.add(nd to n) }
                    }
                }
                if (sink < 0) continue
                val path = ArrayList<Int>()
                var c = prev[sink]
                while (c >= 0 && c != src) { path.add(c); c = prev[c] }
                path.add(src)
                if (path.size < 5) continue
                for (t in path) if (!world.water[t]) world.river[t] = true
                made++
            }
            // Home tile: nearest land of the colony's biome to the middle, else force one.
            var best = -1; var bd = Int.MAX_VALUE
            for (y in 0 until H) for (x in 0 until W) {
                val t = y * W + x
                if (world.water[t] || world.hills[t] == Hills.MOUNTAIN || (homeBiome != null && world.biome[t] != homeBiome)) continue
                val d = abs(x - W / 2) + abs(y - H / 2)
                if (d < bd) { bd = d; best = t }
            }
            if (best < 0) {
                for (y in 0 until H) for (x in 0 until W) {
                    val t = y * W + x
                    if (world.water[t]) continue
                    val d = abs(x - W / 2) + abs(y - H / 2)
                    if (d < bd) { bd = d; best = t }
                }
                if (best < 0) { best = (H / 2) * W + W / 2; world.water[best] = false }
                world.biome[best] = homeBiome ?: Biome.TEMPERATE
            }
            world.hills[best] = if (world.hills[best] == Hills.MOUNTAIN) Hills.LARGE else world.hills[best]
            world.homeTile = best

            // Factions and who likes whom.
            val tn = tribeNames.shuffled(java.util.Random(seed + 1)); val on = outlanderNames.shuffled(java.util.Random(seed + 2)); val pn = pirateNames.shuffled(java.util.Random(seed + 3))
            val tribeColors = intArrayOf(0xFF6FBF5A.toInt(), 0xFFB5D24A.toInt()); val outColors = intArrayOf(0xFF5EA8E8.toInt(), 0xFF8E7AE0.toInt()); val pirColors = intArrayOf(0xFFE05050.toInt(), 0xFFD98A2E.toInt())
            for (k in 0 until 2) world.factions.add(WorldFaction(world.factions.size, tn[k], 0, tribeColors[k]))
            for (k in 0 until 2) world.factions.add(WorldFaction(world.factions.size, on[k], 1, outColors[k]))
            for (k in 0 until 2) world.factions.add(WorldFaction(world.factions.size, pn[k], 2, pirColors[k]))
            for (f in world.factions) {
                world.goodwill[f.id] = when (f.kind) { 2 -> -100; else -> rng.range(-30, 25) }
            }
            for (a in world.factions) for (b in world.factions) if (a.id < b.id) {
                val rel = when {
                    a.kind == 2 && b.kind == 2 -> 0
                    a.kind == 2 || b.kind == 2 -> if (rng.chance(0.65f)) -1 else 0
                    else -> { val r = rng.float(); if (r < 0.28f) 1 else if (r < 0.6f) -1 else 0 }
                }
                world.relation[a.id][b.id] = rel; world.relation[b.id][a.id] = rel
            }

            // Settlements, spread out.
            val used = HashSet<String>()
            var guard = 0
            for (f in world.factions) {
                var made2 = 0
                val wanted = if (f.kind == 2) 3 else 5
                while (made2 < wanted && guard++ < 20000) {
                    val x = rng.range(1, W - 2); val y = rng.range(1, H - 2)
                    val t = y * W + x
                    if (!world.passable(t) || world.river[t]) continue
                    if (abs(x - world.x(best)) + abs(y - world.y(best)) < 5) continue
                    if (world.settlements.any { abs(world.x(it.tile) - x) + abs(world.y(it.tile) - y) < 4 }) continue
                    var nm: String
                    do { nm = rng.pick(names1) + rng.pick(names2) } while (!used.add(nm))
                    world.settlements.add(Settlement(world.settlements.size, nm, t, f))
                    made2++
                }
            }
            // Roads join friendly settlements to their two nearest neighbours and the colony.
            val hubs = ArrayList<Int>().apply { add(best); addAll(world.settlements.filter { it.faction.trades }.map { it.tile }) }
            for (a in hubs) {
                val near = hubs.filter { it != a }.sortedBy { abs(world.x(it) - world.x(a)) + abs(world.y(it) - world.y(a)) }.take(2)
                for (b in near) world.path(a, b)?.let { p -> if (world.routeCost(a, p) < 45f) for (t in p) world.road[t] = true }
            }
            return world
        }
    }

    /**
     * What the map of tile [t] (the colony's home by default) should look like: its river and lake, its hills, and the way they
     * run in from the neighbouring tiles. A river runs across the map along the axis it enters on; the lake sits on the side
     * the neighbouring water is on. Either is null when the neighbours do not settle it, and the map then picks at random.
     */
    fun localTerrain(t: Int = homeTile): LocalTerrain {
        fun riverAt(dx: Int, dy: Int) = inB(x(t) + dx, y(t) + dy) && river[tile(x(t) + dx, y(t) + dy)]
        fun waterAt(dx: Int, dy: Int) = inB(x(t) + dx, y(t) + dy) && water[tile(x(t) + dx, y(t) + dy)]
        val acrossEW = riverAt(1, 0) || riverAt(-1, 0)
        val acrossNS = riverAt(0, -1) || riverAt(0, 1)
        val horizontal: Boolean? = if (acrossEW && !acrossNS) true else if (acrossNS && !acrossEW) false else null
        var sx = 0; var sy = 0; var nWater = 0
        for (dy in -1..1) for (dx in -1..1) if ((dx != 0 || dy != 0) && waterAt(dx, dy)) { sx += dx; sy += dy; nWater++ }
        val lakeAngle = if (nWater > 0 && (sx != 0 || sy != 0)) atan2(sy.toFloat(), sx.toFloat()) else null
        return LocalTerrain(river[t], adjacentWater(t) && !river[t], hills[t], horizontal, lakeAngle)
    }


    /** [riverHorizontal]: true when the river runs east-west across the map, false for north-south, null when unknown. [lakeAngle]: the direction of the lake, in radians with y pointing down. */
    class LocalTerrain(val river: Boolean, val lake: Boolean, val hills: Hills, val riverHorizontal: Boolean? = null, val lakeAngle: Float? = null)
}
