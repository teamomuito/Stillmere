package io.github.teamomuito.colony.sim

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class Plant(val type: PlantType, val x: Int, val y: Int, var growth: Float) {
    val mature get() = !type.crop || growth >= 1f
}

class Building(val def: BuildDef, val x: Int, val y: Int, var built: Boolean) {
    var delivered = 0
    var progress = 0f
    var hp = def.hp
    var ownerId = -1
    var fuel = 0f
    var cooldown = 0
    var health = 1f
    val lit get() = def == BuildDef.CAMPFIRE && built && fuel > 0f
}

class ItemStack(val id: Int, val type: ItemType, var count: Int, var x: Int, var y: Int)

object Desig { const val NONE = 0; const val MINE = 1; const val CUT = 2; const val DECON = 3 }
object ZoneKind { const val NONE = 0; const val STOCKPILE = 1; const val GROWING = 2 }

class GameMap(val w: Int, val h: Int) {
    val size = w * h
    val terrain = Array(size) { Terrain.SOIL }
    val ore = BooleanArray(size)
    val floor = arrayOfNulls<BuildDef>(size)
    val building = arrayOfNulls<Building>(size)
    val plant = arrayOfNulls<Plant>(size)
    val items = HashMap<Int, ItemStack>()
    val zone = ByteArray(size)
    val zoneCrop = ByteArray(size)
    val desig = ByteArray(size)

    fun idx(x: Int, y: Int) = y * w + x
    fun inB(x: Int, y: Int) = x in 0 until w && y in 0 until h
    fun xOf(i: Int) = i % w
    fun yOf(i: Int) = i / w

    fun isWall(i: Int): Boolean {
        val b = building[i]
        return terrain[i] == Terrain.ROCK || (b != null && b.built && b.def.isWall)
    }

    fun blocksSight(i: Int): Boolean {
        val b = building[i]
        return terrain[i] == Terrain.ROCK || (b != null && b.built && b.def.blocksSight)
    }

    /** Can pawns walk on this cell at all (doors are fine)? */
    fun walkable(i: Int): Boolean {
        if (!terrain[i].passable) return false
        val b = building[i]
        return !(b != null && b.built && b.def.blocksMove)
    }

    fun stepCost(i: Int): Int {
        var c = terrain[i].cost * 10
        if (plant[i]?.type == PlantType.TREE) c += 50
        if (floor[i] != null) c -= 3
        return max(c, 6)
    }

    /** Bresenham line of sight. */
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

    // ---- rooms ----
    var roomDirty = true
    var roomId = IntArray(size)
    var roomSize = IntArray(0)
    var roomIndoor = BooleanArray(0)
    var roomTemp = FloatArray(0)

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
            while (sp > 0) {
                val c = stack[--sp]
                count++
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
            indoor.add(!edge && count <= 600)
        }
        // Walls and doors take the temperature of the neighbouring room (or the outdoors).
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
        roomDirty = false
    }

    fun roomIndoorAt(i: Int): Boolean {
        if (roomDirty) return false
        val r = roomId[i]
        return r >= 0 && roomIndoor[r]
    }

    fun tempAt(i: Int, outdoor: Float): Float {
        if (roomDirty) return outdoor
        val r = roomId[i]
        return if (r >= 0 && roomIndoor[r]) roomTemp[r] else outdoor
    }

    // ---- items ----
    private var nextItemId = 1
    fun nextId() = nextItemId++
    fun setNextId(n: Int) { nextItemId = n }

    fun itemAt(i: Int) = items[i]

    /** Places items on or near (x, y), merging with matching stacks. Returns what could not be placed. */
    fun drop(type: ItemType, count: Int, x: Int, y: Int): Int {
        var left = count
        var radius = 0
        while (left > 0 && radius < 12) {
            for (yy in y - radius..y + radius) for (xx in x - radius..x + radius) {
                if (max(abs(xx - x), abs(yy - y)) != radius || !inB(xx, yy)) continue
                val i = idx(xx, yy)
                if (!terrain[i].passable || terrain[i] == Terrain.WATER_SHALLOW) continue
                val b = building[i]
                if (b != null && b.built && b.def.blocksMove) continue
                val s = items[i]
                if (s == null) {
                    val n = min(left, type.stack)
                    items[i] = ItemStack(nextId(), type, n, xx, yy)
                    left -= n
                } else if (s.type == type && s.count < type.stack) {
                    val n = min(left, type.stack - s.count)
                    s.count += n
                    left -= n
                }
                if (left <= 0) return 0
            }
            radius++
        }
        return left
    }

    fun take(i: Int, n: Int): Int {
        val s = items[i] ?: return 0
        val t = min(n, s.count)
        s.count -= t
        if (s.count <= 0) items.remove(i)
        return t
    }

    fun countItems(type: ItemType): Int = items.values.filter { it.type == type }.sumOf { it.count }

    fun wealth(): Float {
        var v = 0f
        for (s in items.values) v += s.type.value * s.count
        for (b in building) if (b != null && b.built) v += b.def.count * b.def.item.value
        return v
    }

    companion object {
        val DX4 = intArrayOf(1, -1, 0, 0)
        val DY4 = intArrayOf(0, 0, 1, -1)
        val DX8 = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
        val DY8 = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)

        fun generate(w: Int, h: Int, seed: Long): GameMap {
            val m = GameMap(w, h)
            val rng = Rng(seed)
            val elev = Noise(seed.toInt() xor 0x1234)
            val fert = Noise(seed.toInt() xor 0x5678)
            val wet = Noise(seed.toInt() xor 0x9abc)
            val tree = Noise(seed.toInt() xor 0xdef0)
            val cx = w / 2
            val cy = h / 2
            for (y in 0 until h) for (x in 0 until w) {
                val i = m.idx(x, y)
                val e = elev.fractal(x.toFloat(), y.toFloat(), 22f)
                val wt = wet.fractal(x.toFloat(), y.toFloat(), 18f)
                val f = fert.fractal(x.toFloat(), y.toFloat(), 10f)
                // Keep the landing zone in the middle gentle.
                val d = max(abs(x - cx), abs(y - cy))
                val calm = if (d < 12) (12 - d) / 12f * 0.35f else 0f
                val ee = e - calm
                val ww = wt + calm
                m.terrain[i] = when {
                    ee > 0.62f -> Terrain.ROCK
                    ww < 0.24f -> Terrain.WATER_DEEP
                    ww < 0.31f -> Terrain.WATER_SHALLOW
                    ww < 0.34f -> Terrain.SAND
                    ee > 0.55f -> Terrain.GRAVEL
                    f > 0.62f -> Terrain.RICH_SOIL
                    f < 0.28f -> Terrain.MARSH
                    else -> Terrain.SOIL
                }
            }
            // Steel veins inside the rock.
            repeat(7) {
                for (attempt in 0 until 60) {
                    val ox = rng.int(w); val oy = rng.int(h)
                    if (m.terrain[m.idx(ox, oy)] != Terrain.ROCK) continue
                    for (k in 0 until 9) {
                        val x = ox + rng.range(-2, 2); val y = oy + rng.range(-2, 2)
                        if (m.inB(x, y) && m.terrain[m.idx(x, y)] == Terrain.ROCK) m.ore[m.idx(x, y)] = true
                    }
                    break
                }
            }
            // Plants.
            for (y in 0 until h) for (x in 0 until w) {
                val i = m.idx(x, y)
                val t = m.terrain[i]
                if (t == Terrain.ROCK || t == Terrain.WATER_DEEP || t == Terrain.WATER_SHALLOW) continue
                val dens = tree.fractal(x.toFloat(), y.toFloat(), 9f)
                val d = max(abs(x - cx), abs(y - cy))
                if (d < 4) continue
                val fertile = t.fertility > 0.1f
                if (fertile && dens > 0.56f && rng.chance(0.55f)) {
                    m.plant[i] = Plant(PlantType.TREE, x, y, 1f)
                } else if (fertile && rng.chance(0.006f)) {
                    m.plant[i] = Plant(PlantType.BERRY, x, y, 1f)
                }
            }
            return m
        }
    }
}
