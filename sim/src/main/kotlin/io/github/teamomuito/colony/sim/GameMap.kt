package io.github.teamomuito.colony.sim

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class Plant(val type: PlantType, val x: Int, val y: Int, var growth: Float) {
    var age = 0
    var blighted = false
    val mature get() = (!type.crop && !type.isTree && !type.regrows && type != PlantType.BRAMBLE) || growth >= 1f
}

class Building(val def: BuildDef, val x: Int, val y: Int, var built: Boolean) {
    /** The material this structure is made of (only for [BuildDef.stuff] structures); null means the default. */
    var material: ItemType? = null
    /** Materials delivered so far, indexed like [cost]. */
    val delivered = IntArray(def.cost.size)
    var progress = 0f
    var hp = def.hp
    var ownerId = -1
    var fuel = 0f
    var cooldown = 0
    var quality = Quality.NORMAL
    var powered = false
    var forbidden = false
    var bills = ArrayList<Bill>()
    var temperature = 0f
    var shells = 0
    var charge = 0f
    var inUse = 0
    var prisonerBed = false
    var occupant = -1
    var variant = 0
    var rot = false
    val fw get() = if (rot) def.h else def.w
    val fh get() = if (rot) def.w else def.h
    fun covers(px: Int, py: Int) = px >= x && px < x + fw && py >= y && py < y + fh
    val lit get() = built && fuel > 0f && def.fuelCap > 0f
    /** Items this building needs, with the chosen material swapped in for the structure's main cost entry. */
    val cost: List<Pair<ItemType, Int>>
        get() {
            val m = material ?: return def.cost
            return def.cost.mapIndexed { i, c -> if (i == 0 && def.stuff != null) m to c.second else c }
        }

    private val defaultStuff get() = Materials.of(def.stuff?.first())
    private val chosenStuff get() = Materials.of(material ?: def.stuff?.first())

    /** Hit points of this structure in its material. */
    val maxHp: Float get() {
        val base = defaultStuff ?: return def.hp
        val now = chosenStuff ?: return def.hp
        return def.hp * now.hpMult / base.hpMult
    }

    val flam: Float get() = chosenStuff?.flam ?: def.flam

    val beauty: Float get() {
        val base = defaultStuff ?: return def.beauty
        val now = chosenStuff ?: return def.beauty
        return def.beauty + now.beauty - base.beauty
    }

    /** "Wall (steel)" for a structure built from a material; the plain name otherwise. */
    val displayName: String get() = if (def.stuff != null) "${def.label} (${(chosenStuff?.item?.label ?: def.label).lowercase()})" else def.label

    /** A material that needs research must be researched before it can be used for this structure. */
    fun researchMet(done: Set<Research>): Boolean =
        (def.research == null || def.research in done) && (chosenStuff?.research?.let { it in done } ?: true)

    fun materialsComplete(): Boolean {
        val c = cost
        for (i in c.indices) if (delivered[i] < c[i].second) return false
        return true
    }
    fun missing(i: Int) = max(0, cost[i].second - delivered[i])
}

class ItemStack(val id: Int, val type: ItemType, var count: Int, var x: Int, var y: Int) {
    var quality = Quality.NORMAL
    var rot = 0f
    var hp = 1f
    var forbidden = false
    var corpseOf: String? = null
    var corpseRace: Race? = null
    var corpseColonist = false
    var corpseAge = 0
}

class Fire(var intensity: Float) { var age = 0 }

object Desig { const val NONE = 0; const val MINE = 1; const val CUT = 2; const val DECON = 3; const val HARVEST = 4; const val REPAIR = 5 }
object ZoneKind { const val NONE = 0; const val STOCKPILE = 1; const val GROWING = 2; const val DUMPING = 3 }

class Zone(val id: Int, val kind: Int) {
    var name = ""
    var priority = 2 // 0 low, 1 normal, 2 preferred, 3 important, 4 critical
    var crop = PlantType.RICE
    var sow = true
    var allowed = BooleanArray(ItemType.entries.size) { true }
    var minQuality = Quality.AWFUL
    var cells = 0
    fun accepts(t: ItemType, q: Quality): Boolean = allowed[t.ordinal] && q.ordinal >= minQuality.ordinal
}

class GameMap(val w: Int, val h: Int) {
    val size = w * h
    val terrain = Array(size) { Terrain.SOIL }
    val ore = Array(size) { Ore.NONE }
    val rockType = Array(size) { RockType.GRANITE }
    val floor = arrayOfNulls<BuildDef>(size)
    val floorQuality = Array(size) { Quality.NORMAL }
    val building = arrayOfNulls<Building>(size)
    private var bVersion = 0
    /**
     * Changes whenever something that decides walkability changes: buildings placed or removed, construction finishing,
     * terrain mined or changed, and doors powering on or off. Reachability caches are rebuilt when it changes.
     */
    var walkVersion = 0
        private set
    fun markWalkChanged() { walkVersion++ }
    private var bCache: List<Building> = emptyList()
    private var bCacheVersion = -1

    /** Put a building on every cell of its footprint. */
    fun setBuilding(b: Building) {
        for (yy in b.y until b.y + b.fh) for (xx in b.x until b.x + b.fw) if (inB(xx, yy)) building[idx(xx, yy)] = b
        roomDirty = true; bVersion++; walkVersion++
    }

    fun removeBuilding(b: Building) {
        for (yy in b.y until b.y + b.fh) for (xx in b.x until b.x + b.fw) if (inB(xx, yy) && building[idx(xx, yy)] === b) building[idx(xx, yy)] = null
        roomDirty = true; bVersion++; walkVersion++
    }

    /** Each building once, even if it covers several cells. */
    fun buildings(): List<Building> {
        if (bCacheVersion == bVersion) return bCache
        val out = ArrayList<Building>()
        for (y in 0 until h) for (x in 0 until w) {
            val b = building[y * w + x]
            if (b != null && b.x == x && b.y == y) out.add(b)
        }
        bCache = out; bCacheVersion = bVersion
        return out
    }
    val plant = arrayOfNulls<Plant>(size)
    val items = HashMap<Int, ItemStack>()
    val zoneId = IntArray(size)
    val zones = HashMap<Int, Zone>()
    val desig = ByteArray(size)
    val filth = ByteArray(size)
    val fires = HashMap<Int, Fire>()
    val natRoof = BooleanArray(size)
    val conduit = BooleanArray(size)
    val areas = Array(3) { BooleanArray(size) }
    val areaNames = arrayOf("Home", "Area 2", "Area 3")
    fun conduitAt(i: Int) = conduit[i]
    val light = FloatArray(size)
    val snow = FloatArray(size)
    var biome = Biome.TEMPERATE
    /** The colony's start cell, chosen when the map is generated; -1 on a map built by hand. Not saved: the save keeps the colony's home. */
    var spawnX = -1
    var spawnY = -1

    fun idx(x: Int, y: Int) = y * w + x
    fun inB(x: Int, y: Int) = x in 0 until w && y in 0 until h
    fun xOf(i: Int) = i % w
    fun yOf(i: Int) = i / w

    fun zoneAt(i: Int): Zone? = if (zoneId[i] == 0) null else zones[zoneId[i]]
    fun zoneKind(i: Int): Int = zoneAt(i)?.kind ?: 0

    private var nextZone = 1
    /** Bumped whenever zone membership changes, so cached lists of zone cells can be checked for staleness. */
    var zoneVersion = 0
    private var storageCache: IntArray? = null
    private var storageCacheVersion = -1

    /**
     * The cells that belong to a stockpile or dumping zone, in ascending index order. Rebuilt only when zone membership
     * changes: the destination search for every haul used to scan the whole map to find these.
     */
    fun storageCells(): IntArray {
        val cached = storageCache
        if (cached != null && storageCacheVersion == zoneVersion) return cached
        var n = 0
        for (i in 0 until size) if (isStorage(i)) n++
        val out = IntArray(n)
        var k = 0
        for (i in 0 until size) if (isStorage(i)) out[k++] = i
        storageCache = out; storageCacheVersion = zoneVersion
        return out
    }

    private fun isStorage(i: Int): Boolean {
        if (zoneId[i] == 0) return false
        val z = zones[zoneId[i]] ?: return false
        return z.kind == ZoneKind.STOCKPILE || z.kind == ZoneKind.DUMPING
    }

    fun newZone(kind: Int): Zone {
        zoneVersion++
        val z = Zone(nextZone++, kind)
        z.name = when (kind) { ZoneKind.STOCKPILE -> "Stockpile ${z.id}"; ZoneKind.GROWING -> "Growing zone ${z.id}"; else -> "Dumping ${z.id}" }
        zones[z.id] = z
        return z
    }
    fun setNextZone(n: Int) { nextZone = n }
    fun peekNextZone() = nextZone

    fun isWall(i: Int): Boolean {
        val b = building[i]
        return terrain[i] == Terrain.ROCK || (b != null && b.built && b.def.isWall)
    }

    fun blocksSight(i: Int): Boolean {
        val b = building[i]
        return terrain[i] == Terrain.ROCK || (b != null && b.built && b.def.blocksSight)
    }

    fun walkable(i: Int): Boolean {
        if (!terrain[i].passable) return false
        val b = building[i]
        if (b != null && b.built && b.def == BuildDef.AUTODOOR && !b.powered) return false
        return !(b != null && b.built && b.def.blocksMove)
    }

    fun stepCost(i: Int): Int {
        var c = terrain[i].cost * 10
        val p = plant[i]
        if (p != null && (p.type.isTree || p.type == PlantType.BRAMBLE)) c += 50
        if (floor[i] != null) c -= 3
        if (snow[i] > 0.5f) c += 3
        val b = building[i]
        if (b != null && b.built && b.def.cover > 0f) c += 12
        if (b != null && b.built && b.def.trap) c += 0
        if (fires.containsKey(i)) c += 200
        return max(c, 6)
    }

    fun lineOfSight(x0: Int, y0: Int, x1: Int, y1: Int): Boolean {
        var x = x0
        var y = y0
        val dx = abs(x1 - x0)
        val dy = abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx - dy
        while (!(x == x1 && y == y1)) {
            val e2 = 2 * err
            if (e2 > -dy) { err -= dy; x += sx }
            if (e2 < dx) { err += dx; y += sy }
            if (!(x == x1 && y == y1) && blocksSight(idx(x, y))) return false
        }
        return true
    }

    /** True when a line of fire between two cells is blocked by cover that stands right in front of the target. */
    /** The cells strictly between two cells, in the order a shot passes them. Same line as [lineOfSight]. */
    fun cellsBetween(x0: Int, y0: Int, x1: Int, y1: Int): IntArray {
        val out = ArrayList<Int>()
        var x = x0
        var y = y0
        val dx = abs(x1 - x0)
        val dy = abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx - dy
        while (!(x == x1 && y == y1)) {
            val e2 = 2 * err
            if (e2 > -dy) { err -= dy; x += sx }
            if (e2 < dx) { err += dx; y += sy }
            if (!(x == x1 && y == y1)) out.add(idx(x, y))
        }
        return out.toIntArray()
    }

    fun coverAt(x0: Int, y0: Int, x1: Int, y1: Int): Float {
        // Cover on the cell adjacent to the target along the shot.
        val dx = Integer.signum(x0 - x1)
        val dy = Integer.signum(y0 - y1)
        val cx = x1 + dx
        val cy = y1 + dy
        if (!inB(cx, cy)) return 0f
        val b = building[idx(cx, cy)] ?: return 0f
        return if (b.built) b.def.cover else 0f
    }

    // ---------------------------------------------------------------- rooms
    var roomDirty = true
    var roomId = IntArray(size)
    var roomSize = IntArray(0)
    var roomIndoor = BooleanArray(0)
    var roomTemp = FloatArray(0)
    var roomBeauty = FloatArray(0)
    var roomClean = FloatArray(0)
    var roomImpress = FloatArray(0)
    var roomRole = IntArray(0) // 0 none, 1 bedroom, 2 barracks, 3 dining, 4 hospital, 5 prison, 6 rec, 7 workshop, 8 kitchen
    var roomWealth = FloatArray(0)
    /** Heat paths of indoor rooms: room [linkA] exchanges with [linkB] (a room id, or [Thermal.OUTDOORS] / [Thermal.GROUND]) with conductance [linkC]. */
    var linkA = IntArray(0)
    var linkB = IntArray(0)
    var linkC = FloatArray(0)

    private fun separates(i: Int): Boolean {
        val b = building[i]
        return terrain[i] == Terrain.ROCK || (b != null && b.built && (b.def.isWall || b.def.isDoor))
    }

    fun rebuildRooms(outdoorTemp: Float) {
        val old = roomTemp
        val oldId = roomId
        val id = IntArray(size) { -1 }
        val sizes = ArrayList<Int>()
        val indoor = ArrayList<Boolean>()
        val stack = IntArray(size)
        for (start in 0 until size) {
            if (id[start] != -1 || separates(start) || terrain[start] == Terrain.WATER_DEEP) continue
            val rid = sizes.size
            var sp = 0
            stack[sp++] = start
            id[start] = rid
            var count = 0
            var edge = false
            var natural = 0
            while (sp > 0) {
                val c = stack[--sp]
                count++
                if (natRoof[c]) natural++
                val cx = xOf(c); val cy = yOf(c)
                if (cx == 0 || cy == 0 || cx == w - 1 || cy == h - 1) edge = true
                for (d in 0 until 4) {
                    val nx = cx + DX4[d]; val ny = cy + DY4[d]
                    if (!inB(nx, ny)) continue
                    val n = idx(nx, ny)
                    if (id[n] != -1 || separates(n) || terrain[n] == Terrain.WATER_DEEP) continue
                    id[n] = rid
                    stack[sp++] = n
                }
            }
            sizes.add(count)
            indoor.add(!edge && (count <= 700 || natural * 2 > count))
        }
        roomId = id
        roomSize = sizes.toIntArray()
        roomIndoor = indoor.toBooleanArray()
        val temps = FloatArray(sizes.size) { outdoorTemp }
        for (c in 0 until size) {
            val r = id[c]
            if (r < 0 || !roomIndoor[r]) continue
            val o = oldId.getOrNull(c) ?: -1
            if (o >= 0 && o < old.size) temps[r] = old[o]
        }
        roomTemp = temps
        roomBeauty = FloatArray(sizes.size)
        roomClean = FloatArray(sizes.size)
        roomImpress = FloatArray(sizes.size)
        roomRole = IntArray(sizes.size)
        roomWealth = FloatArray(sizes.size)
        buildHeatLinks()
        roomDirty = false
    }

    /** One link per wall, door or rock edge of an indoor room, to whatever lies straight through it. */
    private fun buildHeatLinks() {
        val a = ArrayList<Int>(); val b = ArrayList<Int>(); val c = ArrayList<Float>()
        for (cell in 0 until size) {
            val r = roomId[cell]
            if (r < 0 || !roomIndoor[r]) continue
            val cx = xOf(cell); val cy = yOf(cell)
            for (d in 0 until 4) {
                val nx = cx + DX4[d]; val ny = cy + DY4[d]
                if (!inB(nx, ny)) continue
                val n = idx(nx, ny)
                if (!separates(n)) continue
                val bd = building[n]
                val cond: Float
                var other = Thermal.OUTDOORS
                if (terrain[n] == Terrain.ROCK) { cond = Thermal.ROCK_CONDUCTANCE; other = Thermal.GROUND }
                else {
                    cond = if (bd!!.def.isDoor) Thermal.DOOR_CONDUCTANCE else Thermal.wallConductance(bd.material)
                    val ox = nx + DX4[d]; val oy = ny + DY4[d]
                    if (inB(ox, oy)) {
                        val q = roomId[idx(ox, oy)]
                        if (q == r) continue
                        if (q >= 0) other = q
                    }
                }
                a.add(r); b.add(other); c.add(cond)
            }
        }
        linkA = a.toIntArray(); linkB = b.toIntArray(); linkC = c.toFloatArray()
    }

    fun roomIndoorAt(i: Int): Boolean {
        if (roomDirty) return false
        val r = roomId[i]
        return r >= 0 && roomIndoor[r]
    }

    fun roofed(i: Int): Boolean = natRoof[i] || roomIndoorAt(i)

    fun tempAt(i: Int, outdoor: Float): Float {
        if (roomDirty) return outdoor
        val r = roomId[i]
        return if (r >= 0 && roomIndoor[r]) roomTemp[r] else outdoor
    }

    // ---------------------------------------------------------------- items
    private var nextItemId = 1
    fun nextId() = nextItemId++
    fun setNextId(n: Int) { nextItemId = n }

    fun dropCell(i: Int): Boolean {
        if (!terrain[i].passable || terrain[i] == Terrain.WATER_SHALLOW) return false
        val b = building[i]
        if (b != null && b.built && b.def.blocksMove) return false
        if (b != null && !b.built && b.def.blocksMove) return false
        return true
    }

    /** Drops items on or near (x, y), merging with matching stacks. Returns what could not be placed. */
    /** Drops goods on the nearest free cells. [condition] is the percentage of full hit points the goods keep. */
    fun drop(type: ItemType, count: Int, x: Int, y: Int, quality: Quality = Quality.NORMAL, rot: Float = 0f, forbid: Boolean = false, condition: Int = 100): Int {
        var left = count
        var radius = 0
        while (left > 0 && radius < 14) {
            for (yy in y - radius..y + radius) for (xx in x - radius..x + radius) {
                if (max(abs(xx - x), abs(yy - y)) != radius || !inB(xx, yy)) continue
                val i = idx(xx, yy)
                if (!dropCell(i)) continue
                val s = items[i]
                if (s == null) {
                    val n = min(left, type.stack)
                    val ns = ItemStack(nextId(), type, n, xx, yy)
                    ns.quality = quality; ns.rot = rot; ns.forbidden = forbid; ns.hp = condition / 100f
                    items[i] = ns
                    left -= n
                } else if (s.type == type && s.count < type.stack && s.quality == quality && conditionPercent(s.hp) == condition) {
                    val n = min(left, type.stack - s.count)
                    // Mixing fresh with old food ages the whole pile a bit.
                    s.rot = (s.rot * s.count + rot * n) / (s.count + n)
                    s.count += n
                    left -= n
                }
                if (left <= 0) return 0
            }
            radius++
        }
        return left
    }

    /** Drops the goods of [lot] with their quality and condition. */
    fun drop(lot: Lot, count: Int, x: Int, y: Int, forbid: Boolean = false): Int =
        drop(lot.type, count, x, y, lot.quality, 0f, forbid, lot.condition)

    fun take(i: Int, n: Int): Int {
        val s = items[i] ?: return 0
        val t = min(n, s.count)
        s.count -= t
        if (s.count <= 0) items.remove(i)
        return t
    }

    /** Counts of every kind of item in one pass, indexed by [ItemType.ordinal]. Corpses are not counted. */
    fun itemTotals(): IntArray {
        val out = IntArray(ItemType.entries.size)
        for (s in items.values) if (s.corpseOf == null) out[s.type.ordinal] += s.count
        return out
    }

    fun countItems(type: ItemType): Int {
        var n = 0
        for (s in items.values) if (s.type == type && s.corpseOf == null) n += s.count
        return n
    }

    fun countItems(pred: (ItemType) -> Boolean): Int {
        var n = 0
        for (s in items.values) if (pred(s.type) && s.corpseOf == null) n += s.count
        return n
    }

    fun wealth(): Float {
        var v = 0f
        for (s in items.values) v += s.type.value * s.count * (if (s.type.isGear) s.quality.mult else 1f)
        for (b in buildings()) if (b.built) v += b.def.totalCost * b.quality.mult
        return v
    }

    /** Cells of [side] (0 west, 1 east, 2 north, 3 south) that a raider, wanderer or trader can enter from. Corners are left out. */
    fun sideCells(side: Int): IntArray {
        val out = ArrayList<Int>()
        if (side < 2) for (y in 3 until h - 3) out.add(idx(if (side == 0) 1 else w - 2, y))
        else for (x in 3 until w - 3) out.add(idx(x, if (side == 2) 1 else h - 2))
        return out.toIntArray()
    }

    /**
     * Cells reachable from [from] over the terrain alone, with the same moves as [Pathfinder] (eight directions, no cutting
     * corners). Buildings are ignored: this is the map's shape, not what the player has walled off.
     */
    fun reachableFrom(from: Int): BooleanArray {
        val seen = BooleanArray(size)
        if (!terrain[from].passable) return seen
        val queue = IntArray(size)
        var head = 0; var tail = 0
        seen[from] = true; queue[tail++] = from
        while (head < tail) {
            val c = queue[head++]
            val cx = xOf(c); val cy = yOf(c)
            for (d in 0 until 8) {
                val nx = cx + DX8[d]; val ny = cy + DY8[d]
                if (!inB(nx, ny)) continue
                val n = idx(nx, ny)
                if (seen[n] || !terrain[n].passable) continue
                if (d >= 4 && (!terrain[idx(cx + DX8[d], cy)].passable || !terrain[idx(cx, cy + DY8[d])].passable)) continue
                seen[n] = true; queue[tail++] = n
            }
        }
        return seen
    }

    /**
     * The first open 7x7 pad (every cell passable and not shallow water), searched outward from the middle of the map.
     * Soil is preferred to other ground. Null when the map has no such pad.
     */
    fun findOpenPad(): Pair<Int, Int>? {
        val cx = w / 2; val cy = h / 2
        val desertLike = biome == Biome.DESERT || biome == Biome.ARID
        fun open(x: Int, y: Int): Boolean {
            for (yy in y - 3..y + 3) for (xx in x - 3..x + 3) {
                if (!inB(xx, yy)) return false
                val t = terrain[idx(xx, yy)]
                if (!t.passable || t == Terrain.WATER_SHALLOW) return false
            }
            return true
        }
        for (soilOnly in listOf(true, false)) for (r in 0 until max(w, h)) for (y in cy - r..cy + r) for (x in cx - r..cx + r) {
            if (!inB(x, y)) continue
            val t = terrain[idx(x, y)]
            if (soilOnly && !(t == Terrain.SOIL || t == Terrain.RICH_SOIL || (desertLike && t == Terrain.SAND))) continue
            if (open(x, y)) return x to y
        }
        return null
    }

    /**
     * Every side of the map gets at least one entry the colony can walk to. Where water or rock cuts the start off from a side,
     * the cheapest crossing is cut: deep water becomes a ford (shallow) and rock becomes a pass (gravel) up to the edge.
     */
    private fun connectSides(sx: Int, sy: Int) {
        val start = idx(sx, sy)
        for (side in 0 until 4) {
            val reach = reachableFrom(start)
            if (sideCells(side).any { reach[it] && terrain[it] != Terrain.WATER_SHALLOW }) continue
            val route = crossing(start, side)
            if (route.isEmpty()) continue
            for (i in route) when (terrain[i]) {
                Terrain.WATER_DEEP -> terrain[i] = Terrain.WATER_SHALLOW
                Terrain.ROCK -> { terrain[i] = Terrain.GRAVEL; natRoof[i] = false }
                else -> {}
            }
            // The entry cell itself must be dry, passable ground.
            val entry = route.last()
            if (terrain[entry] == Terrain.WATER_SHALLOW || terrain[entry] == Terrain.WATER_DEEP || terrain[entry] == Terrain.ROCK) {
                terrain[entry] = Terrain.GRAVEL; natRoof[entry] = false
            }
        }
    }

    /** Cheapest route from [start] to any cell of [side], as cells after the start. Rock costs most, then deep water, then shallows. */
    private fun crossing(start: Int, side: Int): List<Int> {
        val goals = sideCells(side).toHashSet()
        val dist = IntArray(size) { Int.MAX_VALUE }
        val prev = IntArray(size) { -1 }
        val queue = PriorityQueue<Pair<Int, Int>>(compareBy { it.first })
        dist[start] = 0; queue.add(0 to start)
        var goal = -1
        while (queue.isNotEmpty()) {
            val (d, c) = queue.poll()
            if (d > dist[c]) continue
            if (c in goals) { goal = c; break }
            val cx = xOf(c); val cy = yOf(c)
            for (k in 0 until 4) {
                val nx = cx + DX4[k]; val ny = cy + DY4[k]
                if (!inB(nx, ny)) continue
                val n = idx(nx, ny)
                val step = when (terrain[n]) { Terrain.ROCK -> 10; Terrain.WATER_DEEP -> 6; Terrain.WATER_SHALLOW -> 2; else -> 1 }
                val nd = d + step
                if (nd < dist[n]) { dist[n] = nd; prev[n] = c; queue.add(nd to n) }
            }
        }
        val out = ArrayList<Int>()
        if (goal < 0) return out
        var c = goal
        while (c != start) { out.add(c); c = prev[c] }
        out.reverse()
        return out
    }

    companion object {
        /**
         * Scale of the noise that patches rock types, and the cut points that split it over the rock tiles of generated maps:
         * granite 40%, then slate, limestone and sandstone 15% each, marble 15%.
         */
        const val ROCK_PATCH = 40f
        val ROCK_CUTS = floatArrayOf(0.451f, 0.518f, 0.586f, 0.669f)
        /** Scale and threshold of the tundra gravel patches: about 30% of the soil. */
        const val GRAVEL_PATCH = 12f
        const val GRAVEL_SHARE = 0.58f

        val DX4 = intArrayOf(1, -1, 0, 0)
        val DY4 = intArrayOf(0, 0, 1, -1)
        val DX8 = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
        val DY8 = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)

        /**
         * The colony's map. The planet picks the home tile: the nearest tile of [biome] to the middle, or of any land when [biome]
         * is null. The map takes that tile's biome, and its river, lake and ruggedness.
         */
        fun generateFor(w: Int, h: Int, seed: Long, biome: Biome? = null): GameMap {
            val world = World.generate(seed, biome)
            return generate(w, h, seed, world.biome[world.homeTile], world.localTerrain())
        }

        fun generate(w: Int, h: Int, seed: Long, biome: Biome = Biome.TEMPERATE, local: World.LocalTerrain? = null): GameMap {
            val m = GameMap(w, h)
            m.biome = biome
            val rng = Rng(seed)
            val elev = Noise(seed.toInt() xor 0x1234)
            val fert = Noise(seed.toInt() xor 0x5678)
            val wet = Noise(seed.toInt() xor 0x9abc)
            val tree = Noise(seed.toInt() xor 0xdef0)
            val rockN = Noise(seed.toInt() xor 0x2468)
            val gravelN = Noise(seed.toInt() xor 0x3579)
            val cx = w / 2
            val cy = h / 2
            val desert = biome == Biome.DESERT || biome == Biome.ARID
            val cold = biome == Biome.TUNDRA || biome == Biome.BOREAL
            val tundra = biome == Biome.TUNDRA
            // A river meanders across the map for most seeds. Its direction and the lake's side come from the world tile when it has them.
            val riverY = FloatArray(w)
            val riverNoise = Noise(seed.toInt() xor 0x77)
            val riverRoll = rng.chance(0.65f) && !desert
            val riverOn = if (local != null) local.river else riverRoll
            val rockThr = when (local?.hills) { Hills.FLAT -> 0.70f; Hills.SMALL -> 0.64f; Hills.LARGE -> 0.58f; Hills.MOUNTAIN -> 0.5f; null -> 0.62f }
            // A lake when the world tile borders water.
            val lakeRoll = rng.float() * 6.283f
            val lakeR = 8f + rng.float() * 5f
            val lakeAng = local?.lakeAngle ?: lakeRoll
            val lakeX = cx + (StrictMath.cos(lakeAng.toDouble()) * (w * 0.3)).toFloat()
            val lakeY = cy + (StrictMath.sin(lakeAng.toDouble()) * (h * 0.3)).toFloat()
            val lakeOn = local?.lake == true
            val horizontalRoll = rng.chance(0.5f)
            val horizontal = local?.riverHorizontal ?: horizontalRoll
            for (x in 0 until w) riverY[x] = (if (horizontal) h * 0.22f else w * 0.22f) + (riverNoise.fractal(x.toFloat(), 0f, 24f) - 0.5f) * 30f
            for (y in 0 until h) for (x in 0 until w) {
                val i = m.idx(x, y)
                val e = elev.fractal(x.toFloat(), y.toFloat(), 26f)
                val wt = wet.fractal(x.toFloat(), y.toFloat(), 20f)
                val f = fert.fractal(x.toFloat(), y.toFloat(), 11f) + biome.soilBias
                val d = max(abs(x - cx), abs(y - cy))
                val calm = if (d < 14) (14 - d) / 14f * 0.35f else 0f
                val ee = e - calm
                val ww = wt + calm
                var t = when {
                    ee > rockThr -> Terrain.ROCK
                    !desert && ww < 0.22f -> Terrain.WATER_DEEP
                    !desert && ww < 0.29f -> Terrain.WATER_SHALLOW
                    ww < 0.32f -> if (cold) Terrain.MUD else Terrain.SAND
                    ee > 0.55f -> Terrain.GRAVEL
                    desert -> if (f > 0.55f) Terrain.SOIL else if (f > 0.35f) Terrain.SAND else Terrain.GRAVEL
                    f > 0.64f -> Terrain.RICH_SOIL
                    f < 0.28f -> Terrain.MARSH
                    else -> Terrain.SOIL
                }
                if (riverOn && t != Terrain.ROCK) {
                    val along = if (horizontal) x else y
                    val across = if (horizontal) y else x
                    val dist = abs(across - riverY[along])
                    if (dist < 1.6f && d > 6) t = Terrain.WATER_DEEP
                    else if (dist < 3.2f && d > 6) t = Terrain.WATER_SHALLOW
                }
                if (lakeOn && t != Terrain.ROCK) {
                    val dl = StrictMath.hypot((x - lakeX).toDouble(), (y - lakeY).toDouble()).toFloat() + (e - 0.5f) * 8f
                    if (dl < lakeR * 0.6f) t = Terrain.WATER_DEEP else if (dl < lakeR) t = Terrain.WATER_SHALLOW
                }
                // Frozen shallows in the tundra are walkable ice; deep water stays open.
                if (tundra && t == Terrain.WATER_SHALLOW) t = Terrain.ICE
                // Gravel in the tundra comes in patches from its own noise, not as scattered single tiles.
                if (tundra && (t == Terrain.SOIL || t == Terrain.RICH_SOIL) && gravelN.fractal(x.toFloat(), y.toFloat(), GRAVEL_PATCH) > GRAVEL_SHARE) t = Terrain.GRAVEL
                m.terrain[i] = t
                if (t == Terrain.ROCK) {
                    m.natRoof[i] = true
                    val r = rockN.fractal(x.toFloat(), y.toFloat(), ROCK_PATCH)
                    m.rockType[i] = when {
                        r < ROCK_CUTS[0] -> RockType.GRANITE
                        r < ROCK_CUTS[1] -> RockType.SLATE
                        r < ROCK_CUTS[2] -> RockType.LIMESTONE
                        r < ROCK_CUTS[3] -> RockType.SANDSTONE
                        else -> RockType.MARBLE
                    }
                }
            }
            // Thick mountain interior stays roofed after mining; thin edges are open sky.
            for (y in 0 until h) for (x in 0 until w) {
                val i = m.idx(x, y)
                if (m.terrain[i] == Terrain.ROCK) continue
                var rockNb = 0
                for (dy in -2..2) for (dx in -2..2) {
                    val nx = x + dx; val ny = y + dy
                    if (m.inB(nx, ny) && m.terrain[m.idx(nx, ny)] == Terrain.ROCK) rockNb++
                }
                if (rockNb >= 14) m.natRoof[i] = true
            }
            // The colony's start: an open pad near the middle, with a walkable way from it to every side of the map. The pad is
            // made outright when the terrain has none, so the start is always open ground.
            val (sx, sy) = m.findOpenPad() ?: run {
                for (y in cy - 3..cy + 3) for (x in cx - 3..cx + 3) { val i = m.idx(x, y); m.terrain[i] = Terrain.SOIL; m.natRoof[i] = false }
                cx to cy
            }
            m.spawnX = sx; m.spawnY = sy
            m.connectSides(sx, sy)
            // Ore veins: each one is a lump grown through rock from a random rock cell, so it stays one connected blob. Lump sizes
            // keep the ore per map where the old scattered veins left it.
            val ores = listOf(Ore.STEEL to 8, Ore.SILVER to 3, Ore.GOLD to 2, Ore.PLASTEEL to 2, Ore.COMPONENTS to 3)
            for ((ore, veins) in ores) {
                val lump = if (ore == Ore.STEEL) 7 else if (ore == Ore.COMPONENTS) 3 else 4
                repeat(veins) {
                    for (attempt in 0 until 80) {
                        val ox = rng.int(w); val oy = rng.int(h)
                        val start = m.idx(ox, oy)
                        if (m.terrain[start] != Terrain.ROCK || m.ore[start] != Ore.NONE) continue
                        val cells = arrayListOf(start)
                        m.ore[start] = ore
                        var tries = 0
                        while (cells.size < lump && tries++ < lump * 12) {
                            val c = cells[rng.int(cells.size)]
                            val k = rng.int(4)
                            val nx = m.xOf(c) + DX4[k]; val ny = m.yOf(c) + DY4[k]
                            if (!m.inB(nx, ny)) continue
                            val n = m.idx(nx, ny)
                            if (m.terrain[n] != Terrain.ROCK || m.ore[n] != Ore.NONE) continue
                            m.ore[n] = ore; cells.add(n)
                        }
                        break
                    }
                }
            }
            // Plants.
            val trees = when (biome) {
                Biome.TEMPERATE -> listOf(PlantType.OAK, PlantType.POPLAR, PlantType.OAK, PlantType.BIRCH, PlantType.MAPLE)
                Biome.BOREAL -> listOf(PlantType.PINE, PlantType.PINE, PlantType.POPLAR, PlantType.BIRCH)
                Biome.TUNDRA -> listOf(PlantType.PINE)
                Biome.DESERT -> listOf(PlantType.SAGUARO, PlantType.PALM)
                Biome.ARID -> listOf(PlantType.PALM, PlantType.SAGUARO, PlantType.PALM)
                Biome.TROPICAL -> listOf(PlantType.PALM, PlantType.TEAK, PlantType.TEAK, PlantType.POPLAR)
            }
            for (y in 0 until h) for (x in 0 until w) {
                val i = m.idx(x, y)
                val t = m.terrain[i]
                if (!t.passable || t == Terrain.WATER_SHALLOW || t == Terrain.ICE) continue
                val dens = tree.fractal(x.toFloat(), y.toFloat(), 9f)
                val d = max(abs(x - cx), abs(y - cy))
                if (d < 4) continue
                val fertile = t.fertility > 0.1f
                val threshold = 0.62f - 0.12f * (biome.treeDensity - 0.6f)
                if (fertile && dens > threshold && rng.chance(0.55f * min(1.2f, biome.treeDensity))) {
                    m.plant[i] = Plant(rng.pick(trees), x, y, 1f).also { it.age = rng.int(30) }
                } else if (fertile && rng.chance(0.006f * biome.treeDensity)) {
                    m.plant[i] = Plant(PlantType.BERRY, x, y, 1f)
                } else if (fertile && rng.chance(0.004f)) {
                    m.plant[i] = Plant(PlantType.WILD_HEALROOT, x, y, 1f)
                } else if (rng.chance(0.004f)) {
                    m.plant[i] = Plant(PlantType.BRAMBLE, x, y, 1f)
                }
            }
            return m
        }
    }
}
