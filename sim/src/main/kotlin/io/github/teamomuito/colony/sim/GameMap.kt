package io.github.teamomuito.colony.sim

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
        roomDirty = false
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

    companion object {
        val DX4 = intArrayOf(1, -1, 0, 0)
        val DY4 = intArrayOf(0, 0, 1, -1)
        val DX8 = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
        val DY8 = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)

        /** A map whose river, lake and ruggedness follow the world tile the colony sits on. */
        fun generateFor(w: Int, h: Int, seed: Long, biome: Biome): GameMap =
            generate(w, h, seed, biome, World.generate(seed, biome).localTerrain())

        fun generate(w: Int, h: Int, seed: Long, biome: Biome = Biome.TEMPERATE, local: World.LocalTerrain? = null): GameMap {
            val m = GameMap(w, h)
            m.biome = biome
            val rng = Rng(seed)
            val elev = Noise(seed.toInt() xor 0x1234)
            val fert = Noise(seed.toInt() xor 0x5678)
            val wet = Noise(seed.toInt() xor 0x9abc)
            val tree = Noise(seed.toInt() xor 0xdef0)
            val rockN = Noise(seed.toInt() xor 0x2468)
            val cx = w / 2
            val cy = h / 2
            val desert = biome == Biome.DESERT || biome == Biome.ARID
            val cold = biome == Biome.TUNDRA || biome == Biome.BOREAL
            // A river meanders across the map for most seeds.
            val riverY = FloatArray(w)
            val riverNoise = Noise(seed.toInt() xor 0x77)
            val riverRoll = rng.chance(0.65f) && !desert
            val riverOn = if (local != null) local.river else riverRoll
            val rockThr = when (local?.hills) { Hills.FLAT -> 0.70f; Hills.SMALL -> 0.64f; Hills.LARGE -> 0.58f; Hills.MOUNTAIN -> 0.5f; null -> 0.62f }
            // A lake when the world tile borders water.
            val lakeAng = rng.float() * 6.283f
            val lakeR = 8f + rng.float() * 5f
            val lakeX = cx + (Math.cos(lakeAng.toDouble()) * (w * 0.3)).toFloat()
            val lakeY = cy + (Math.sin(lakeAng.toDouble()) * (h * 0.3)).toFloat()
            val lakeOn = local?.lake == true
            val horizontal = rng.chance(0.5f)
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
                    val dl = Math.hypot((x - lakeX).toDouble(), (y - lakeY).toDouble()).toFloat() + (e - 0.5f) * 8f
                    if (dl < lakeR * 0.6f) t = Terrain.WATER_DEEP else if (dl < lakeR) t = Terrain.WATER_SHALLOW
                }
                if (biome == Biome.TUNDRA && (t == Terrain.SOIL || t == Terrain.RICH_SOIL) && rng.chance(0.3f)) t = Terrain.GRAVEL
                m.terrain[i] = t
                if (t == Terrain.ROCK) {
                    m.natRoof[i] = true
                    val r = rockN.fractal(x.toFloat(), y.toFloat(), 40f)
                    m.rockType[i] = when {
                        r < 0.3f -> RockType.GRANITE
                        r < 0.45f -> RockType.SLATE
                        r < 0.58f -> RockType.LIMESTONE
                        r < 0.72f -> RockType.SANDSTONE
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
            // Ore veins.
            val ores = listOf(Ore.STEEL to 8, Ore.SILVER to 3, Ore.GOLD to 2, Ore.PLASTEEL to 2, Ore.COMPONENTS to 3)
            for ((ore, veins) in ores) {
                repeat(veins) {
                    for (attempt in 0 until 80) {
                        val ox = rng.int(w); val oy = rng.int(h)
                        if (m.terrain[m.idx(ox, oy)] != Terrain.ROCK) continue
                        val n = if (ore == Ore.STEEL) 11 else if (ore == Ore.COMPONENTS) 4 else 6
                        for (k in 0 until n) {
                            val x = ox + rng.range(-2, 2); val y = oy + rng.range(-2, 2)
                            if (m.inB(x, y) && m.terrain[m.idx(x, y)] == Terrain.ROCK) m.ore[m.idx(x, y)] = ore
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
