package io.github.teamomuito.colony

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import io.github.teamomuito.colony.sim.ApparelSlot
import io.github.teamomuito.colony.sim.Biome
import io.github.teamomuito.colony.sim.BuildDef
import io.github.teamomuito.colony.sim.GameMap
import io.github.teamomuito.colony.sim.ItemCat
import io.github.teamomuito.colony.sim.ItemType
import io.github.teamomuito.colony.sim.Pawn
import io.github.teamomuito.colony.sim.PlantType
import io.github.teamomuito.colony.sim.Race
import io.github.teamomuito.colony.sim.RockType
import io.github.teamomuito.colony.sim.Terrain
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Procedural top-down art in the muted, hand-painted manner of colony sims: textured ground, outlined wall blocks that
 * join their neighbours, shadowed furniture, per-species animals and rotated, layered pawns. Everything is drawn with
 * canvas primitives, so there are no image assets.
 */
class Sprites(private val fill: Paint, private val stroke: Paint) {
    private val rect = RectF()
    private val path = Path()

    // ------------------------------------------------------------------ helpers
    fun h(x: Int, y: Int, k: Int): Float {
        var v = x * 374761393 + y * 668265263 + k * 1274126177
        v = (v xor (v ushr 13)) * 1274126177
        v = v xor (v ushr 16)
        return (v and 0xffff) / 65535f
    }

    fun shade(c: Int, f: Float): Int {
        val r = (Color.red(c) * f).toInt().coerceIn(0, 255)
        val g = (Color.green(c) * f).toInt().coerceIn(0, 255)
        val b = (Color.blue(c) * f).toInt().coerceIn(0, 255)
        return Color.argb(Color.alpha(c), r, g, b)
    }

    private fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    private fun line(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, color: Int, w: Float) {
        stroke.color = color; stroke.strokeWidth = max(1f, w)
        c.drawLine(x0, y0, x1, y1, stroke)
    }

    private fun disc(c: Canvas, x: Float, y: Float, r: Float, color: Int) { fill.color = color; c.drawCircle(x, y, r, fill) }
    private fun oval(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, color: Int) { fill.color = color; rect.set(x0, y0, x1, y1); c.drawOval(rect, fill) }
    private fun rr(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, r: Float, color: Int) { fill.color = color; rect.set(x0, y0, x1, y1); c.drawRoundRect(rect, r, r, fill) }
    private fun box(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, color: Int) { fill.color = color; c.drawRect(x0, y0, x1, y1, fill) }

    // ------------------------------------------------------------------ ground
    private fun soilColor(t: Terrain, biome: Biome): Int {
        val rich = t == Terrain.RICH_SOIL
        val c = when (biome) {
            Biome.DESERT -> 0xFF9A8458.toInt()
            Biome.ARID -> 0xFF8A8052.toInt()
            Biome.TUNDRA -> 0xFF7F8574.toInt()
            Biome.BOREAL -> 0xFF5E7142.toInt()
            Biome.TROPICAL -> 0xFF4F7A33.toInt()
            Biome.TEMPERATE -> 0xFF667F42.toInt()
        }
        return if (rich) shade(c, 0.78f) else c
    }

    private fun rockColor(r: RockType): Int = when (r) {
        RockType.GRANITE -> 0xFF5A5B60.toInt(); RockType.MARBLE -> 0xFF80828A.toInt(); RockType.LIMESTONE -> 0xFF76736A.toInt()
        RockType.SANDSTONE -> 0xFF7E705D.toInt(); RockType.SLATE -> 0xFF484C54.toInt()
    }

    fun baseColor(m: GameMap, i: Int, biome: Biome): Int = when (val t = m.terrain[i]) {
        Terrain.SOIL, Terrain.RICH_SOIL -> soilColor(t, biome)
        Terrain.GRAVEL -> 0xFF8A877E.toInt()
        Terrain.SAND -> 0xFFCDBA86.toInt()
        Terrain.MARSH -> 0xFF4D6A44.toInt()
        Terrain.MUD -> 0xFF5A4A36.toInt()
        Terrain.ICE -> 0xFFD2E8F2.toInt()
        Terrain.WATER_SHALLOW -> 0xFF5E92B8.toInt()
        Terrain.WATER_DEEP -> 0xFF325A86.toInt()
        Terrain.ROCK -> rockColor(m.rockType[i])
    }

    private fun isRock(m: GameMap, x: Int, y: Int) = m.inB(x, y) && m.terrain[m.idx(x, y)] == Terrain.ROCK
    private fun isWater(m: GameMap, x: Int, y: Int) = !m.inB(x, y) || m.terrain[m.idx(x, y)].let { it == Terrain.WATER_DEEP || it == Terrain.WATER_SHALLOW }

    fun ground(c: Canvas, m: GameMap, x: Int, y: Int, sx: Float, sy: Float, s: Float, frame: Int, biome: Biome) {
        val i = m.idx(x, y)
        val t = m.terrain[i]
        val r1 = h(x, y, 1)
        val base = shade(baseColor(m, i, biome), 0.92f + 0.16f * r1)
        box(c, sx, sy, sx + s + 1, sy + s + 1, base)
        val lw = max(1f, s * 0.04f)
        when (t) {
            Terrain.SOIL, Terrain.RICH_SOIL -> {
                // Soft mottling plus tufts of grass.
                disc(c, sx + s * (0.2f + 0.6f * h(x, y, 2)), sy + s * (0.2f + 0.6f * h(x, y, 3)), s * 0.28f, withAlpha(shade(base, 0.9f), 70))
                val tuft = shade(base, 1.28f)
                val n = 2 + (h(x, y, 4) * 3).toInt()
                for (k in 0 until n) {
                    val px = sx + s * (0.1f + 0.8f * h(x, y, 10 + k)); val py = sy + s * (0.25f + 0.7f * h(x, y, 20 + k))
                    line(c, px, py, px - s * 0.05f, py - s * 0.13f, tuft, lw); line(c, px, py, px + s * 0.01f, py - s * 0.16f, tuft, lw); line(c, px, py, px + s * 0.06f, py - s * 0.11f, tuft, lw)
                }
            }
            Terrain.GRAVEL -> for (k in 0 until 6) {
                val px = sx + s * (0.1f + 0.8f * h(x, y, 10 + k)); val py = sy + s * (0.1f + 0.8f * h(x, y, 20 + k))
                disc(c, px, py, s * (0.04f + 0.04f * h(x, y, 30 + k)), if (k % 2 == 0) shade(base, 1.2f) else shade(base, 0.78f))
            }
            Terrain.SAND -> {
                val rip = shade(base, 0.9f)
                for (k in 0 until 2) {
                    val py = sy + s * (0.25f + 0.45f * k + 0.1f * h(x, y, 5 + k)); val px = sx + s * (0.1f + 0.2f * h(x, y, 8 + k))
                    line(c, px, py, px + s * 0.28f, py - s * 0.05f, rip, lw); line(c, px + s * 0.28f, py - s * 0.05f, px + s * 0.55f, py, rip, lw)
                }
            }
            Terrain.MARSH -> {
                disc(c, sx + s * 0.35f, sy + s * 0.6f, s * 0.25f, 0x55486E86)
                disc(c, sx + s * 0.7f, sy + s * 0.35f, s * 0.18f, 0x55486E86)
                val reed = 0xFF8AA864.toInt()
                for (k in 0 until 3) { val px = sx + s * (0.15f + 0.7f * h(x, y, 10 + k)); val py = sy + s * (0.5f + 0.4f * h(x, y, 20 + k)); line(c, px, py, px, py - s * 0.22f, reed, lw) }
            }
            Terrain.MUD -> {
                disc(c, sx + s * 0.35f, sy + s * 0.4f, s * 0.2f, 0x44281A10)
                disc(c, sx + s * 0.7f, sy + s * 0.7f, s * 0.15f, 0x33C8A070)
            }
            Terrain.ICE -> {
                line(c, sx + s * 0.1f, sy + s * 0.3f, sx + s * 0.6f, sy + s * 0.55f, 0x88FFFFFF.toInt(), lw)
                line(c, sx + s * 0.4f, sy + s * 0.8f, sx + s * 0.9f, sy + s * 0.5f, 0x66FFFFFF, lw)
            }
            Terrain.WATER_SHALLOW, Terrain.WATER_DEEP -> {
                val wave = withAlpha(0xFFFFFFFF.toInt(), if (t == Terrain.WATER_DEEP) 35 else 55)
                val ph = frame * 0.04f + x * 0.9f + y * 0.6f
                for (k in 0 until 2) {
                    val py = sy + s * (0.3f + 0.4f * k) + sin(ph + k) * s * 0.04f
                    line(c, sx + s * 0.15f, py, sx + s * 0.45f, py - s * 0.03f, wave, lw); line(c, sx + s * 0.45f, py - s * 0.03f, sx + s * 0.8f, py, wave, lw)
                }
                // Shoreline foam where water meets land.
                val foam = 0x66FFFFFF
                if (!isWater(m, x, y - 1)) box(c, sx, sy, sx + s + 1, sy + s * 0.1f, foam)
                if (!isWater(m, x, y + 1)) box(c, sx, sy + s * 0.9f, sx + s + 1, sy + s + 1, foam)
                if (!isWater(m, x - 1, y)) box(c, sx, sy, sx + s * 0.1f, sy + s + 1, foam)
                if (!isWater(m, x + 1, y)) box(c, sx + s * 0.9f, sy, sx + s + 1, sy + s + 1, foam)
            }
            Terrain.ROCK -> {
                // Rough stone face with cracks, a lit top edge and a dark cliff foot / outline.
                disc(c, sx + s * 0.3f, sy + s * 0.3f, s * 0.3f, withAlpha(shade(base, 1.18f), 80))
                val crack = shade(base, 0.62f)
                val ax = sx + s * (0.15f + 0.4f * h(x, y, 6)); val ay = sy + s * (0.2f + 0.3f * h(x, y, 7))
                line(c, ax, ay, ax + s * 0.25f, ay + s * 0.2f, crack, lw); line(c, ax + s * 0.25f, ay + s * 0.2f, ax + s * 0.2f, ay + s * 0.45f, crack, lw)
                val edge = 0xCC15161A.toInt()
                val ew = max(2f, s * 0.07f)
                if (!isRock(m, x, y - 1)) { box(c, sx, sy, sx + s + 1, sy + ew, edge) }
                if (!isRock(m, x - 1, y)) { box(c, sx, sy, sx + ew, sy + s + 1, edge) }
                if (!isRock(m, x + 1, y)) { box(c, sx + s - ew, sy, sx + s + 1, sy + s + 1, edge) }
                if (!isRock(m, x, y + 1)) { box(c, sx, sy + s * 0.78f, sx + s + 1, sy + s + 1, withAlpha(0xFF101012.toInt(), 150)); box(c, sx, sy + s - ew, sx + s + 1, sy + s + 1, edge) }
            }
        }
    }

    // ------------------------------------------------------------------ floors and zones
    fun floor(c: Canvas, def: BuildDef, x: Int, y: Int, sx: Float, sy: Float, s: Float) {
        val lw = max(1f, s * 0.03f)
        when (def) {
            BuildDef.WOOD_FLOOR -> {
                val b = shade(0xFFA9824F.toInt(), 0.94f + 0.1f * h(x, y, 1))
                box(c, sx, sy, sx + s + 1, sy + s + 1, b)
                for (k in 0 until 4) {
                    val py = sy + s * k / 4f
                    box(c, sx, py, sx + s + 1, py + s / 4f, shade(b, 0.94f + 0.12f * h(x, y, 5 + k)))
                    line(c, sx, py, sx + s, py, 0x44301A08, lw)
                    val jx = sx + s * (0.2f + 0.6f * h(x, y, 9 + k))
                    line(c, jx, py, jx, py + s / 4f, 0x33301A08, lw)
                }
            }
            BuildDef.STONE_FLOOR -> {
                val b = 0xFF9C9C9E.toInt()
                for (qy in 0 until 2) for (qx in 0 until 2) {
                    box(c, sx + s * qx / 2f, sy + s * qy / 2f, sx + s * (qx + 1) / 2f + 1, sy + s * (qy + 1) / 2f + 1, shade(b, 0.92f + 0.14f * h(x * 2 + qx, y * 2 + qy, 3)))
                }
                line(c, sx + s / 2, sy, sx + s / 2, sy + s, 0x44202024, lw); line(c, sx, sy + s / 2, sx + s, sy + s / 2, 0x44202024, lw)
                stroke.color = 0x33202024; stroke.strokeWidth = lw; c.drawRect(sx, sy, sx + s, sy + s, stroke)
            }
            BuildDef.STEEL_FLOOR -> {
                box(c, sx, sy, sx + s + 1, sy + s + 1, 0xFF8794A0.toInt())
                stroke.color = 0x55303A44; stroke.strokeWidth = lw; c.drawRect(sx + lw, sy + lw, sx + s - lw, sy + s - lw, stroke)
                val rv = 0xFFB9C4CE.toInt(); val d = s * 0.1f
                disc(c, sx + d, sy + d, s * 0.03f, rv); disc(c, sx + s - d, sy + d, s * 0.03f, rv); disc(c, sx + d, sy + s - d, s * 0.03f, rv); disc(c, sx + s - d, sy + s - d, s * 0.03f, rv)
            }
            else -> { // carpet
                box(c, sx, sy, sx + s + 1, sy + s + 1, 0xFF8A5870.toInt())
                stroke.color = 0x55D8A0C0; stroke.strokeWidth = max(1f, s * 0.05f); c.drawRect(sx + s * 0.12f, sy + s * 0.12f, sx + s * 0.88f, sy + s * 0.88f, stroke)
            }
        }
    }

    fun stockpile(c: Canvas, sx: Float, sy: Float, s: Float) {
        box(c, sx, sy, sx + s, sy + s, 0x30E8C547)
        val lw = max(1f, s * 0.03f)
        line(c, sx, sy + s * 0.5f, sx + s * 0.5f, sy, 0x40E8C547, lw); line(c, sx + s * 0.5f, sy + s, sx + s, sy + s * 0.5f, 0x40E8C547, lw)
        stroke.color = 0x88E8C547.toInt(); stroke.strokeWidth = lw; c.drawRect(sx + lw, sy + lw, sx + s - lw, sy + s - lw, stroke)
    }

    fun growing(c: Canvas, sx: Float, sy: Float, s: Float) {
        // Furrowed, freshly tilled rows.
        box(c, sx, sy, sx + s, sy + s, 0x40503820)
        val lw = max(1.5f, s * 0.07f)
        for (k in 0 until 3) { val py = sy + s * (0.2f + 0.3f * k); line(c, sx, py, sx + s, py, 0x44201408, lw); line(c, sx, py + lw, sx + s, py + lw, 0x22D8B880, max(1f, lw * 0.4f)) }
    }

    /** Base colour of the slab under long workbenches. */
    fun slabColor(def: BuildDef): Int = when (def) {
        BuildDef.STOVE_FUEL, BuildDef.STOVE_ELEC -> 0xFF4A4C52.toInt()
        BuildDef.BUTCHER_TABLE -> 0xFFA88A6A.toInt()
        BuildDef.TAILOR_BENCH -> 0xFF8A6A4A.toInt()
        BuildDef.SMITHY, BuildDef.MACHINING, BuildDef.FAB_BENCH -> 0xFF3A3D44.toInt()
        BuildDef.RESEARCH_BENCH -> 0xFF6B5A44.toInt()
        BuildDef.HI_TECH_BENCH -> 0xFF2E4A5E.toInt()
        BuildDef.DRUG_LAB -> 0xFF6AAE88.toInt()
        else -> 0xFF8E8E92.toInt()
    }

    // ------------------------------------------------------------------ walls
    private fun wallish(m: GameMap, x: Int, y: Int): Boolean {
        if (!m.inB(x, y)) return false
        val b = m.building[m.idx(x, y)] ?: return false
        return b.def.isWall || b.def.isDoor
    }

    fun wall(c: Canvas, m: GameMap, x: Int, y: Int, mat: ItemType, sx: Float, sy: Float, s: Float, alpha: Int) {
        fun col(argb: Int): Int = (argb and 0x00FFFFFF) or (alpha shl 24)
        val l = wallish(m, x - 1, y); val r = wallish(m, x + 1, y); val u = wallish(m, x, y - 1); val d = wallish(m, x, y + 1)
        val base = when (mat) { ItemType.WOOD -> 0xFF8E6A3A.toInt(); ItemType.STONE -> 0xFF93939A.toInt(); ItemType.PLASTEEL -> 0xFF9FD0CC.toInt(); else -> 0xFFB6C0CA.toInt() }
        // Soft drop shadow on the ground to the south-east.
        if (alpha > 200) {
            if (!d) box(c, sx + s * 0.08f, sy + s, sx + s * 1.1f, sy + s * 1.14f, 0x33000000)
            if (!r) box(c, sx + s, sy + s * 0.08f, sx + s * 1.1f, sy + s * 1.1f, 0x33000000)
        }
        box(c, sx, sy, sx + s + 1, sy + s + 1, col(shade(base, 0.82f + 0.08f * h(x, y, 1))))
        // Raised top face.
        val inset = s * 0.1f
        box(c, sx + (if (l) 0f else inset), sy + (if (u) 0f else inset), sx + s + 1 - (if (r) 0f else inset), sy + s + 1 - (if (d) 0f else inset), col(shade(base, 1.06f + 0.08f * h(x, y, 2))))
        val lw = max(1f, s * 0.03f)
        when (mat) {
            ItemType.WOOD -> for (k in 1..3) {
                val py = sy + s * k / 4f
                line(c, sx + (if (l) 0f else inset), py, sx + s - (if (r) 0f else inset), py, col(0x553A2410), lw)
            }
            ItemType.STONE -> {
                val m0 = sy + s * (0.38f + 0.2f * h(x, y, 3)); val m1 = sy + s * 0.72f
                line(c, sx, m0, sx + s, m0, col(0x55202024), lw); line(c, sx, m1, sx + s, m1, col(0x55202024), lw)
                line(c, sx + s * (0.3f + 0.2f * h(x, y, 4)), sy, sx + s * 0.5f, m0, col(0x44202024), lw)
                line(c, sx + s * (0.5f + 0.3f * h(x, y, 5)), m0, sx + s * 0.6f, m1, col(0x44202024), lw)
            }
            else -> {
                val rv = col(0xFFE2E8EE.toInt()); val dd = s * 0.2f
                disc(c, sx + dd, sy + dd, s * 0.035f, rv); disc(c, sx + s - dd, sy + dd, s * 0.035f, rv); disc(c, sx + dd, sy + s - dd, s * 0.035f, rv); disc(c, sx + s - dd, sy + s - dd, s * 0.035f, rv)
            }
        }
        // Dark outline where the wall ends.
        val ow = max(2f, s * 0.08f)
        val oc = col(0xFF1C130B.toInt())
        if (!u) box(c, sx, sy, sx + s + 1, sy + ow, oc)
        if (!d) box(c, sx, sy + s - ow, sx + s + 1, sy + s + 1, oc)
        if (!l) box(c, sx, sy, sx + ow, sy + s + 1, oc)
        if (!r) box(c, sx + s - ow, sy, sx + s + 1, sy + s + 1, oc)
        // Light catching the top edge.
        if (!u) box(c, sx + (if (l) 0f else ow), sy + ow, sx + s - (if (r) 0f else ow), sy + ow + max(1f, s * 0.04f), col(0x44FFFFFF))
    }

    fun door(c: Canvas, m: GameMap, x: Int, y: Int, sx: Float, sy: Float, s: Float, alpha: Int, def: BuildDef, mat: ItemType) {
        val steel = mat != ItemType.WOOD
        fun col(argb: Int): Int = (argb and 0x00FFFFFF) or (alpha shl 24)
        val horizontalWalls = wallish(m, x - 1, y) && wallish(m, x + 1, y)
        val ow = max(2f, s * 0.08f)
        box(c, sx, sy, sx + s + 1, sy + s + 1, col(if (steel) 0xFF4A5058.toInt() else 0xFF5A4326.toInt()))
        val leaf = if (steel) 0xFFAAB4BE.toInt() else 0xFF8A6535.toInt()
        if (horizontalWalls) {
            box(c, sx, sy, sx + s * 0.22f, sy + s + 1, col(0xFF2A1E12.toInt())); box(c, sx + s * 0.78f, sy, sx + s + 1, sy + s + 1, col(0xFF2A1E12.toInt()))
            rr(c, sx + s * 0.2f, sy + s * 0.32f, sx + s * 0.8f, sy + s * 0.68f, s * 0.05f, col(leaf))
            line(c, sx + s * 0.5f, sy + s * 0.32f, sx + s * 0.5f, sy + s * 0.68f, col(0x66201408), max(1f, s * 0.03f))
        } else {
            box(c, sx, sy, sx + s + 1, sy + s * 0.22f, col(0xFF2A1E12.toInt())); box(c, sx, sy + s * 0.78f, sx + s + 1, sy + s + 1, col(0xFF2A1E12.toInt()))
            rr(c, sx + s * 0.32f, sy + s * 0.2f, sx + s * 0.68f, sy + s * 0.8f, s * 0.05f, col(leaf))
            line(c, sx + s * 0.32f, sy + s * 0.5f, sx + s * 0.68f, sy + s * 0.5f, col(0x66201408), max(1f, s * 0.03f))
        }
        disc(c, sx + s * 0.62f, sy + s * 0.5f, s * 0.04f, col(if (def == BuildDef.AUTODOOR) 0xFF62B0FF.toInt() else 0xFFE6C878.toInt()))
        stroke.color = col(0xFF1C130B.toInt()); stroke.strokeWidth = ow * 0.5f; c.drawRect(sx + 1, sy + 1, sx + s, sy + s, stroke)
    }

    /** A small shadow under furniture, drawn before the piece itself. */
    fun furnitureShadow(c: Canvas, sx: Float, sy: Float, s: Float) {
        rr(c, sx + s * 0.1f, sy + s * 0.12f, sx + s * 0.98f, sy + s * 0.98f, s * 0.12f, 0x33000000)
    }

    // ------------------------------------------------------------------ plants
    fun plant(c: Canvas, type: PlantType, growth: Float, mature: Boolean, x: Int, y: Int, sx: Float, sy: Float, s: Float, frame: Int) {
        val cx = sx + s / 2; val cy = sy + s / 2
        val lw = max(1f, s * 0.04f)
        when (type) {
            PlantType.OAK, PlantType.POPLAR -> {
                val gr = (0.35f + 0.65f * growth).coerceAtMost(1f)
                val base = if (type == PlantType.OAK) 0xFF3A6B30.toInt() else 0xFF5C8A38.toInt()
                oval(c, cx - s * 0.36f * gr + s * 0.06f, cy - s * 0.2f * gr + s * 0.12f, cx + s * 0.4f * gr + s * 0.06f, cy + s * 0.44f * gr + s * 0.12f, 0x33000000)
                val n = if (type == PlantType.OAK) 6 else 5
                for (k in 0 until n) {
                    val a = k * 6.2832f / n + h(x, y, 40) * 2f
                    val rad = s * (if (type == PlantType.OAK) 0.2f else 0.16f) * gr
                    val px = cx + cos(a) * rad; val py = cy + sin(a) * rad * (if (type == PlantType.POPLAR) 1.4f else 1f)
                    disc(c, px, py, s * (if (type == PlantType.OAK) 0.2f else 0.15f) * gr, shade(base, 0.82f + 0.12f * (k % 3) + 0.1f * h(x, y, 50 + k)))
                }
                disc(c, cx - s * 0.04f * gr, cy - s * 0.05f * gr, s * 0.2f * gr, shade(base, 1.18f))
                disc(c, cx - s * 0.1f * gr, cy - s * 0.1f * gr, s * 0.08f * gr, shade(base, 1.4f))
            }
            PlantType.PINE -> {
                val gr = (0.35f + 0.65f * growth).coerceAtMost(1f)
                oval(c, cx - s * 0.3f * gr + s * 0.07f, cy - s * 0.1f * gr + s * 0.13f, cx + s * 0.34f * gr + s * 0.07f, cy + s * 0.42f * gr + s * 0.13f, 0x33000000)
                for (k in 0 until 3) {
                    val rr0 = s * (0.42f - 0.11f * k) * gr
                    path.reset(); path.moveTo(cx, cy - rr0); path.lineTo(cx + rr0 * 0.95f, cy + rr0 * 0.7f); path.lineTo(cx - rr0 * 0.95f, cy + rr0 * 0.7f); path.close()
                    fill.color = shade(0xFF235640.toInt(), 0.85f + 0.2f * k); c.drawPath(path, fill)
                }
                disc(c, cx, cy - s * 0.02f, s * 0.04f, 0xFF4A3524.toInt())
            }
            PlantType.PALM -> {
                val gr = (0.35f + 0.65f * growth).coerceAtMost(1f)
                oval(c, cx - s * 0.3f + s * 0.06f, cy - s * 0.2f + s * 0.12f, cx + s * 0.3f + s * 0.06f, cy + s * 0.3f + s * 0.12f, 0x33000000)
                for (k in 0 until 7) {
                    val a = k * 0.8976f + h(x, y, 40)
                    val ex = cx + cos(a) * s * 0.4f * gr; val ey = cy + sin(a) * s * 0.4f * gr
                    line(c, cx, cy, ex, ey, 0xFF4F8F3A.toInt(), max(2f, s * 0.09f))
                    line(c, cx, cy, cx + (ex - cx) * 0.85f, cy + (ey - cy) * 0.85f, 0xFF7ABF52.toInt(), max(1f, s * 0.03f))
                }
                disc(c, cx, cy, s * 0.07f, 0xFF6B4A2A.toInt()); disc(c, cx - s * 0.02f, cy + s * 0.02f, s * 0.03f, 0xFF3A2A18.toInt())
            }
            PlantType.BERRY -> {
                oval(c, cx - s * 0.3f + s * 0.05f, cy - s * 0.2f + s * 0.1f, cx + s * 0.3f + s * 0.05f, cy + s * 0.3f + s * 0.1f, 0x33000000)
                disc(c, cx - s * 0.1f, cy, s * 0.2f, 0xFF3F7D3A.toInt()); disc(c, cx + s * 0.12f, cy + s * 0.02f, s * 0.19f, 0xFF4A8C42.toInt()); disc(c, cx, cy - s * 0.1f, s * 0.17f, 0xFF58A04C.toInt())
                if (mature) for (k in 0 until 5) disc(c, cx + (h(x, y, 60 + k) - 0.5f) * s * 0.5f, cy + (h(x, y, 70 + k) - 0.5f) * s * 0.4f, s * 0.055f, 0xFFD2384B.toInt())
            }
            PlantType.BRAMBLE -> {
                disc(c, cx, cy, s * 0.3f, 0xFF3A4A2A.toInt()); disc(c, cx - s * 0.1f, cy - s * 0.08f, s * 0.17f, 0xFF4A5C34.toInt())
                for (k in 0 until 5) {
                    val a = k * 1.2566f + h(x, y, 40)
                    line(c, cx, cy, cx + cos(a) * s * 0.4f, cy + sin(a) * s * 0.4f, 0xFF20281A.toInt(), lw)
                    disc(c, cx + cos(a) * s * 0.4f, cy + sin(a) * s * 0.4f, s * 0.03f, 0xFFC9C0A0.toInt())
                }
            }
            PlantType.WILD_HEALROOT -> {
                for (k in 0 until 5) { val a = k * 1.2566f; oval(c, cx + cos(a) * s * 0.12f - s * 0.08f, cy + sin(a) * s * 0.12f - s * 0.05f, cx + cos(a) * s * 0.12f + s * 0.08f, cy + sin(a) * s * 0.12f + s * 0.05f, 0xFF63D18F.toInt()) }
                disc(c, cx, cy, s * 0.05f, 0xFFE8F4C8.toInt())
            }
            else -> crop(c, type, growth, mature, x, y, sx, sy, s, frame)
        }
    }

    private fun crop(c: Canvas, type: PlantType, growth: Float, mature: Boolean, x: Int, y: Int, sx: Float, sy: Float, s: Float, frame: Int) {
        val lw = max(1f, s * 0.04f)
        val gr = growth.coerceIn(0.05f, 1f)
        for (qy in 0 until 2) for (qx in 0 until 2) {
            val px = sx + s * (0.27f + 0.46f * qx) + (h(x * 4 + qx, y * 4 + qy, 1) - 0.5f) * s * 0.06f
            val py = sy + s * (0.3f + 0.42f * qy)
            val sway = sin(frame * 0.05f + qx + qy * 2 + x) * s * 0.01f
            if (growth < 0.12f) { disc(c, px, py, s * 0.035f, 0xFF7BC25A.toInt()); continue }
            when (type) {
                PlantType.RICE -> {
                    val col = if (mature) 0xFFD6C25A.toInt() else shade(0xFF7BC25A.toInt(), 0.8f + 0.3f * gr)
                    for (k in -1..1) {
                        val tx = px + k * s * 0.07f
                        line(c, tx, py + s * 0.1f, tx + k * s * 0.05f + sway, py - s * (0.08f + 0.2f * gr), col, lw)
                        if (mature) disc(c, tx + k * s * 0.05f + sway, py - s * 0.3f, s * 0.035f, 0xFFF0DE8A.toInt())
                    }
                }
                PlantType.CORN -> {
                    val top = py - s * (0.1f + 0.28f * gr)
                    line(c, px, py + s * 0.1f, px + sway, top, 0xFF5E9A3C.toInt(), max(2f, s * 0.06f))
                    line(c, px, py, px - s * 0.14f * gr, py - s * 0.16f * gr, 0xFF7ABF52.toInt(), lw); line(c, px, py - s * 0.05f, px + s * 0.14f * gr, py - s * 0.2f * gr, 0xFF7ABF52.toInt(), lw)
                    if (mature) { oval(c, px - s * 0.04f, top + s * 0.04f, px + s * 0.05f, top + s * 0.17f, 0xFFF2D54A.toInt()) }
                }
                PlantType.POTATO -> {
                    disc(c, px, py, s * (0.06f + 0.1f * gr), 0xFF4F8A3C.toInt()); disc(c, px - s * 0.05f, py - s * 0.04f, s * (0.04f + 0.06f * gr), 0xFF65A44E.toInt())
                    if (mature) disc(c, px + s * 0.05f, py - s * 0.05f, s * 0.035f, 0xFFB38AD8.toInt())
                }
                PlantType.STRAWBERRY -> {
                    disc(c, px, py, s * (0.06f + 0.08f * gr), 0xFF3F8A3A.toInt()); disc(c, px + s * 0.04f, py + s * 0.02f, s * (0.04f + 0.06f * gr), 0xFF58A04C.toInt())
                    if (mature) { disc(c, px - s * 0.05f, py + s * 0.04f, s * 0.04f, 0xFFD2384B.toInt()); disc(c, px + s * 0.07f, py - s * 0.02f, s * 0.04f, 0xFFD2384B.toInt()) }
                }
                PlantType.COTTON -> {
                    disc(c, px, py, s * (0.06f + 0.09f * gr), 0xFF5E9A44.toInt())
                    if (mature) { disc(c, px - s * 0.05f, py - s * 0.04f, s * 0.05f, 0xFFF4F4EC.toInt()); disc(c, px + s * 0.05f, py, s * 0.05f, 0xFFF4F4EC.toInt()) }
                }
                PlantType.HEALROOT -> {
                    for (k in 0 until 3) { val a = -1.1f - k * 0.45f; line(c, px, py, px + cos(a) * s * 0.17f * gr, py + sin(a) * s * 0.17f * gr, 0xFF4CBF86.toInt(), max(2f, s * 0.05f)) }
                }
                PlantType.SMOKELEAF -> {
                    for (k in -1..1) oval(c, px + k * s * 0.07f - s * 0.04f, py - s * 0.2f * gr, px + k * s * 0.07f + s * 0.04f, py + s * 0.04f, shade(0xFF3F7A3A.toInt(), 0.8f + 0.25f * gr))
                }
                PlantType.PSYCHOID -> {
                    disc(c, px, py, s * (0.05f + 0.09f * gr), 0xFF6B4A7A.toInt())
                    if (mature) { disc(c, px - s * 0.04f, py - s * 0.05f, s * 0.04f, 0xFFD27AE0.toInt()); disc(c, px + s * 0.05f, py - s * 0.02f, s * 0.04f, 0xFFD27AE0.toInt()) }
                }
                else -> { // hay grass
                    val col = shade(0xFFB6C45A.toInt(), 0.75f + 0.3f * gr)
                    for (k in -2..2) line(c, px + k * s * 0.035f, py + s * 0.08f, px + k * s * 0.06f + sway, py - s * (0.06f + 0.2f * gr), col, lw)
                }
            }
        }
    }

    // ------------------------------------------------------------------ items
    fun item(c: Canvas, type: ItemType, color: Int, count: Int, sx: Float, sy: Float, s: Float, rot: Float, seed: Int) {
        val piles = when { count >= 60 -> 3; count >= 20 -> 2; else -> 1 }
        for (k in piles - 1 downTo 0) {
            val o = k * s * 0.06f
            itemShape(c, type, color, sx + s * 0.5f - o, sy + s * 0.5f - o, s * 0.62f, seed + k)
        }
    }

    private fun itemShape(c: Canvas, type: ItemType, color: Int, cx: Float, cy: Float, z: Float, seed: Int) {
        val lw = max(1f, z * 0.05f)
        rr(c, cx - z * 0.4f, cy - z * 0.26f + z * 0.08f, cx + z * 0.46f, cy + z * 0.38f + z * 0.08f, z * 0.2f, 0x22000000)
        when (type) {
            ItemType.WOOD -> for (k in 0 until 3) {
                val oy = cy + (k - 1) * z * 0.22f
                rr(c, cx - z * 0.42f, oy - z * 0.1f, cx + z * 0.42f, oy + z * 0.1f, z * 0.08f, 0xFF9A6A38.toInt())
                disc(c, cx + z * 0.38f, oy, z * 0.1f, 0xFFD8B27A.toInt()); disc(c, cx + z * 0.38f, oy, z * 0.04f, 0xFFA97A44.toInt())
            }
            ItemType.STONE, ItemType.STONE_CHUNK -> {
                path.reset(); path.moveTo(cx - z * 0.4f, cy + z * 0.25f); path.lineTo(cx - z * 0.28f, cy - z * 0.25f); path.lineTo(cx + z * 0.1f, cy - z * 0.35f); path.lineTo(cx + z * 0.42f, cy - z * 0.05f); path.lineTo(cx + z * 0.3f, cy + z * 0.3f); path.close()
                fill.color = 0xFFA6A6AA.toInt(); c.drawPath(path, fill)
                line(c, cx - z * 0.28f, cy - z * 0.25f, cx, cy + z * 0.05f, 0x55000000, lw); line(c, cx + z * 0.42f, cy - z * 0.05f, cx, cy + z * 0.05f, 0x55000000, lw)
            }
            ItemType.STEEL, ItemType.PLASTEEL -> {
                val b = if (type == ItemType.STEEL) 0xFF9FB4C8.toInt() else 0xFF6CC9BC.toInt()
                for (k in 0 until 2) { val oy = cy + (k - 0.5f) * z * 0.36f; rr(c, cx - z * 0.42f, oy - z * 0.14f, cx + z * 0.42f, oy + z * 0.14f, z * 0.05f, b); box(c, cx - z * 0.38f, oy - z * 0.12f, cx + z * 0.38f, oy - z * 0.06f, shade(b, 1.3f)) }
            }
            ItemType.SILVER, ItemType.GOLD -> {
                val b = if (type == ItemType.SILVER) 0xFFE3E3E8.toInt() else 0xFFF2C94C.toInt()
                for (k in 0 until 3) { val ox = cx + (k - 1) * z * 0.22f; val oy = cy + (if (k == 1) -z * 0.12f else z * 0.1f); disc(c, ox, oy, z * 0.21f, shade(b, 0.8f)); disc(c, ox, oy - z * 0.02f, z * 0.17f, b) }
            }
            ItemType.COMPONENT -> {
                rr(c, cx - z * 0.38f, cy - z * 0.28f, cx + z * 0.38f, cy + z * 0.28f, z * 0.05f, 0xFF2F7A4A.toInt())
                box(c, cx - z * 0.2f, cy - z * 0.14f, cx + z * 0.06f, cy + z * 0.08f, 0xFF22262A.toInt()); box(c, cx + z * 0.14f, cy - z * 0.18f, cx + z * 0.3f, cy - z * 0.04f, 0xFF3A3F46.toInt())
                for (k in 0 until 4) box(c, cx - z * 0.3f + k * z * 0.12f, cy + z * 0.2f, cx - z * 0.24f + k * z * 0.12f, cy + z * 0.28f, 0xFFE8A83A.toInt())
            }
            ItemType.CLOTH -> { rr(c, cx - z * 0.4f, cy - z * 0.28f, cx + z * 0.4f, cy + z * 0.28f, z * 0.06f, 0xFFE8E0C8.toInt()); line(c, cx - z * 0.4f, cy - z * 0.06f, cx + z * 0.4f, cy - z * 0.06f, 0x33000000, lw); line(c, cx - z * 0.4f, cy + z * 0.14f, cx + z * 0.4f, cy + z * 0.14f, 0x33000000, lw) }
            ItemType.WOOL -> { for (k in 0 until 4) disc(c, cx + (k % 2 - 0.5f) * z * 0.34f, cy + (k / 2 - 0.5f) * z * 0.3f, z * 0.22f, 0xFFF2EEE4.toInt()) }
            ItemType.LEATHER -> { oval(c, cx - z * 0.42f, cy - z * 0.3f, cx + z * 0.42f, cy + z * 0.3f, 0xFF8A5A33.toInt()); oval(c, cx - z * 0.3f, cy - z * 0.2f, cx + z * 0.3f, cy + z * 0.1f, 0xFFA3703F.toInt()) }
            ItemType.RICE -> { for (k in 0 until 7) disc(c, cx + (h(seed, k, 1) - 0.5f) * z * 0.7f, cy + (h(seed, k, 2) - 0.5f) * z * 0.5f, z * 0.07f, 0xFFF1E9C4.toInt()) }
            ItemType.POTATOES -> { for (k in 0 until 3) oval(c, cx + (k - 1) * z * 0.25f - z * 0.16f, cy + (k % 2) * z * 0.18f - z * 0.18f, cx + (k - 1) * z * 0.25f + z * 0.16f, cy + (k % 2) * z * 0.18f + z * 0.12f, 0xFFB08A58.toInt()) }
            ItemType.CORN -> { for (k in 0 until 2) { oval(c, cx - z * 0.34f + k * z * 0.28f, cy - z * 0.3f, cx - z * 0.1f + k * z * 0.28f, cy + z * 0.3f, 0xFFF2D54A.toInt()); line(c, cx - z * 0.22f + k * z * 0.28f, cy - z * 0.3f, cx - z * 0.22f + k * z * 0.28f, cy + z * 0.3f, 0x44A07A10, lw) } }
            ItemType.STRAWBERRIES -> { for (k in 0 until 4) { val ox = cx + (k % 2 - 0.5f) * z * 0.4f; val oy = cy + (k / 2 - 0.5f) * z * 0.36f; disc(c, ox, oy, z * 0.17f, 0xFFD2384B.toInt()); disc(c, ox, oy - z * 0.14f, z * 0.06f, 0xFF3F8A3A.toInt()) } }
            ItemType.MEAT, ItemType.HUMAN_MEAT, ItemType.INSECT_MEAT -> {
                oval(c, cx - z * 0.4f, cy - z * 0.28f, cx + z * 0.3f, cy + z * 0.3f, if (type == ItemType.INSECT_MEAT) 0xFFB9B060.toInt() else 0xFFD9605A.toInt())
                oval(c, cx - z * 0.28f, cy - z * 0.18f, cx + z * 0.1f, cy + z * 0.05f, 0xFFEB8A82.toInt()); rr(c, cx + z * 0.2f, cy - z * 0.07f, cx + z * 0.46f, cy + z * 0.07f, z * 0.05f, 0xFFF4EEDF.toInt())
            }
            ItemType.EGGS -> { for (k in 0 until 3) oval(c, cx + (k - 1) * z * 0.26f - z * 0.13f, cy + (k % 2) * z * 0.18f - z * 0.16f, cx + (k - 1) * z * 0.26f + z * 0.13f, cy + (k % 2) * z * 0.18f + z * 0.12f, 0xFFF4EFE0.toInt()) }
            ItemType.MILK -> { rr(c, cx - z * 0.17f, cy - z * 0.1f, cx + z * 0.17f, cy + z * 0.4f, z * 0.05f, 0xFFF4F4F4.toInt()); rr(c, cx - z * 0.1f, cy - z * 0.3f, cx + z * 0.1f, cy - z * 0.08f, z * 0.04f, 0xFFD8E4F0.toInt()) }
            ItemType.MEAL_SIMPLE, ItemType.MEAL_FINE, ItemType.MEAL_LAVISH -> {
                disc(c, cx, cy, z * 0.4f, 0xFFE8E4DC.toInt()); disc(c, cx, cy, z * 0.3f, 0xFFF6F2EA.toInt())
                if (type == ItemType.MEAL_FINE || type == ItemType.MEAL_LAVISH) { disc(c, cx - z * 0.08f, cy, z * 0.13f, 0xFFC66A3A.toInt()); disc(c, cx + z * 0.1f, cy - z * 0.05f, z * 0.1f, 0xFF7BB05A.toInt()); disc(c, cx + z * 0.05f, cy + z * 0.1f, z * 0.08f, 0xFFF0C84A.toInt()) }
                else { disc(c, cx, cy, z * 0.2f, 0xFFD89A4C.toInt()); disc(c, cx + z * 0.08f, cy - z * 0.06f, z * 0.07f, 0xFF8AB05A.toInt()) }
            }
            ItemType.MEAL_PACKAGED -> { rr(c, cx - z * 0.36f, cy - z * 0.26f, cx + z * 0.36f, cy + z * 0.26f, z * 0.06f, 0xFFC9B26A.toInt()); box(c, cx - z * 0.26f, cy - z * 0.12f, cx + z * 0.26f, cy + z * 0.12f, 0xFFF4EBCB.toInt()); box(c, cx - z * 0.14f, cy - z * 0.04f, cx + z * 0.14f, cy + z * 0.04f, 0xFFB03030.toInt()) }
            ItemType.PEMMICAN -> { for (k in 0 until 3) rr(c, cx - z * 0.4f, cy + (k - 1) * z * 0.22f - z * 0.08f, cx + z * 0.4f, cy + (k - 1) * z * 0.22f + z * 0.08f, z * 0.05f, 0xFF8A5A3A.toInt()) }
            ItemType.KIBBLE -> { for (k in 0 until 9) disc(c, cx + (h(seed, k, 3) - 0.5f) * z * 0.7f, cy + (h(seed, k, 4) - 0.5f) * z * 0.55f, z * 0.07f, 0xFF8A5E34.toInt()) }
            ItemType.HAY -> { for (k in 0 until 6) line(c, cx - z * 0.4f + k * z * 0.14f, cy + z * 0.25f, cx - z * 0.3f + k * z * 0.14f + (h(seed, k, 5) - 0.5f) * z * 0.2f, cy - z * 0.28f, 0xFFD8C65A.toInt(), max(1.5f, z * 0.07f)) }
            ItemType.HEALROOT, ItemType.MEDS_HERBAL -> { for (k in 0 until 4) { val a = -2.4f + k * 0.55f; line(c, cx, cy + z * 0.25f, cx + cos(a) * z * 0.4f, cy + z * 0.25f + sin(a) * z * 0.55f, 0xFF63D18F.toInt(), max(2f, z * 0.1f)) }; if (type == ItemType.MEDS_HERBAL) box(c, cx - z * 0.3f, cy + z * 0.16f, cx + z * 0.3f, cy + z * 0.26f, 0xFFB08A58.toInt()) }
            ItemType.MEDS_INDUSTRIAL -> { rr(c, cx - z * 0.34f, cy - z * 0.26f, cx + z * 0.34f, cy + z * 0.26f, z * 0.06f, 0xFFF2F2F2.toInt()); box(c, cx - z * 0.05f, cy - z * 0.18f, cx + z * 0.05f, cy + z * 0.18f, 0xFFD03030.toInt()); box(c, cx - z * 0.18f, cy - z * 0.05f, cx + z * 0.18f, cy + z * 0.05f, 0xFFD03030.toInt()) }
            ItemType.BEER -> { rr(c, cx - z * 0.14f, cy - z * 0.1f, cx + z * 0.14f, cy + z * 0.38f, z * 0.05f, 0xFF7A4A1E.toInt()); rr(c, cx - z * 0.06f, cy - z * 0.36f, cx + z * 0.06f, cy - z * 0.08f, z * 0.03f, 0xFF7A4A1E.toInt()); box(c, cx - z * 0.14f, cy + z * 0.04f, cx + z * 0.14f, cy + z * 0.2f, 0xFFE8D8A0.toInt()) }
            ItemType.SMOKELEAF, ItemType.PSYCHOID -> { val b = if (type == ItemType.SMOKELEAF) 0xFF4A8A3A.toInt() else 0xFF8A5AA8.toInt(); for (k in 0 until 3) oval(c, cx + (k - 1) * z * 0.22f - z * 0.1f, cy - z * 0.3f, cx + (k - 1) * z * 0.22f + z * 0.1f, cy + z * 0.3f, shade(b, 0.85f + 0.15f * k)) }
            ItemType.JOINT -> { rr(c, cx - z * 0.4f, cy - z * 0.05f, cx + z * 0.4f, cy + z * 0.05f, z * 0.04f, 0xFFF2EEE0.toInt()); disc(c, cx + z * 0.4f, cy, z * 0.06f, 0xFFE0762E.toInt()) }
            ItemType.PSYCHITE_TEA -> { disc(c, cx, cy, z * 0.3f, 0xFFE8E4DC.toInt()); disc(c, cx, cy, z * 0.22f, 0xFFA05AC8.toInt()); rr(c, cx + z * 0.26f, cy - z * 0.08f, cx + z * 0.42f, cy + z * 0.08f, z * 0.05f, 0xFFE8E4DC.toInt()) }
            ItemType.SCULPTURE_SMALL, ItemType.SCULPTURE_LARGE -> { oval(c, cx - z * 0.3f, cy + z * 0.15f, cx + z * 0.3f, cy + z * 0.38f, 0xFF8A8A8E.toInt()); path.reset(); path.moveTo(cx, cy - z * 0.38f); path.lineTo(cx + z * 0.22f, cy + z * 0.28f); path.lineTo(cx - z * 0.22f, cy + z * 0.28f); path.close(); fill.color = 0xFFD2CEC2.toInt(); c.drawPath(path, fill) }
            ItemType.SHELL -> { rr(c, cx - z * 0.14f, cy - z * 0.3f, cx + z * 0.14f, cy + z * 0.3f, z * 0.07f, 0xFF6A6F52.toInt()); box(c, cx - z * 0.14f, cy + z * 0.08f, cx + z * 0.14f, cy + z * 0.14f, 0xFFC9A14A.toInt()) }
            else -> when {
                type.weapon != null -> weaponShape(c, type, cx, cy, z)
                type.apparel != null -> {
                    path.reset()
                    path.moveTo(cx - z * 0.2f, cy - z * 0.32f); path.lineTo(cx - z * 0.46f, cy - z * 0.12f); path.lineTo(cx - z * 0.34f, cy + z * 0.02f); path.lineTo(cx - z * 0.22f, cy - z * 0.08f)
                    path.lineTo(cx - z * 0.22f, cy + z * 0.36f); path.lineTo(cx + z * 0.22f, cy + z * 0.36f); path.lineTo(cx + z * 0.22f, cy - z * 0.08f)
                    path.lineTo(cx + z * 0.34f, cy + z * 0.02f); path.lineTo(cx + z * 0.46f, cy - z * 0.12f); path.lineTo(cx + z * 0.2f, cy - z * 0.32f); path.close()
                    fill.color = color; c.drawPath(path, fill)
                    stroke.color = 0x55000000; stroke.strokeWidth = lw; c.drawPath(path, stroke)
                }
                else -> { rr(c, cx - z * 0.34f, cy - z * 0.28f, cx + z * 0.34f, cy + z * 0.28f, z * 0.1f, color) }
            }
        }
    }

    private fun weaponShape(c: Canvas, type: ItemType, cx: Float, cy: Float, z: Float) {
        val w = type.weapon ?: return
        val steel = 0xFFB9C0C8.toInt(); val dark = 0xFF3A3D42.toInt(); val wood = 0xFF8A5A33.toInt()
        val lw = max(2f, z * 0.1f)
        when {
            w == io.github.teamomuito.colony.sim.Weapon.BOW || w == io.github.teamomuito.colony.sim.Weapon.GREATBOW -> {
                stroke.color = wood; stroke.strokeWidth = lw
                rect.set(cx - z * 0.1f, cy - z * 0.42f, cx + z * 0.5f, cy + z * 0.42f); c.drawArc(rect, 110f, 140f, false, stroke)
                line(c, cx + z * 0.1f, cy - z * 0.4f, cx + z * 0.1f, cy + z * 0.4f, 0xFFE8E0C8.toInt(), max(1f, z * 0.03f))
            }
            w.ranged -> {
                val len = if (w.range > 26f) 0.5f else if (w.range > 18f) 0.42f else 0.3f
                rr(c, cx - z * len, cy - z * 0.08f, cx + z * len, cy + z * 0.04f, z * 0.03f, dark)
                if (w.burst > 1 || w.range > 26f) rr(c, cx - z * len, cy - z * 0.1f, cx - z * (len - 0.28f), cy + z * 0.14f, z * 0.03f, wood)
                rr(c, cx - z * 0.12f, cy, cx - z * 0.02f, cy + z * 0.28f, z * 0.03f, wood)
                if (w.burst > 1) rr(c, cx + z * 0.0f, cy + z * 0.02f, cx + z * 0.1f, cy + z * 0.24f, z * 0.02f, dark)
            }
            w == io.github.teamomuito.colony.sim.Weapon.SPEAR -> { line(c, cx - z * 0.44f, cy + z * 0.3f, cx + z * 0.4f, cy - z * 0.3f, wood, lw * 0.8f); path.reset(); path.moveTo(cx + z * 0.5f, cy - z * 0.36f); path.lineTo(cx + z * 0.3f, cy - z * 0.34f); path.lineTo(cx + z * 0.4f, cy - z * 0.16f); path.close(); fill.color = steel; c.drawPath(path, fill) }
            w == io.github.teamomuito.colony.sim.Weapon.CLUB || w == io.github.teamomuito.colony.sim.Weapon.MACE -> { line(c, cx - z * 0.4f, cy + z * 0.34f, cx + z * 0.25f, cy - z * 0.2f, wood, lw); disc(c, cx + z * 0.3f, cy - z * 0.26f, z * 0.17f, if (w == io.github.teamomuito.colony.sim.Weapon.MACE) steel else shade(wood, 0.8f)) }
            else -> { line(c, cx - z * 0.4f, cy + z * 0.36f, cx + z * 0.4f, cy - z * 0.36f, steel, lw * 0.9f); line(c, cx - z * 0.28f, cy + z * 0.14f, cx - z * 0.04f, cy + z * 0.38f, dark, lw * 0.8f) }
        }
    }

    // ------------------------------------------------------------------ fire
    private fun flame(c: Canvas, cx: Float, base: Float, w: Float, hgt: Float, color: Int) {
        path.reset()
        path.moveTo(cx - w, base)
        path.quadTo(cx - w * 1.1f, base - hgt * 0.55f, cx, base - hgt)
        path.quadTo(cx + w * 1.1f, base - hgt * 0.55f, cx + w, base)
        path.close()
        fill.color = color; c.drawPath(path, fill)
    }

    fun fire(c: Canvas, sx: Float, sy: Float, s: Float, intensity: Float, frame: Int, seed: Int) {
        val k = intensity.coerceIn(0.3f, 1f)
        disc(c, sx + s / 2, sy + s / 2, s * 0.62f * k, 0x33FF7A1E)
        for (j in 0 until 3) {
            val fl = 0.78f + 0.22f * sin(frame * (0.5f + 0.13f * j) + seed + j * 2f)
            val ox = (j - 1) * s * 0.2f
            val hh = s * (0.62f - 0.1f * abs(j - 1)) * k * fl
            flame(c, sx + s / 2 + ox, sy + s * 0.88f, s * 0.2f * k, hh, 0xFFE8431A.toInt())
            flame(c, sx + s / 2 + ox, sy + s * 0.88f, s * 0.14f * k, hh * 0.78f, 0xFFFF9A2E.toInt())
            flame(c, sx + s / 2 + ox, sy + s * 0.88f, s * 0.07f * k, hh * 0.5f, 0xFFFFE27A.toInt())
        }
    }

    // ------------------------------------------------------------------ people
    private val skins = intArrayOf(0xFFF0C8A0.toInt(), 0xFFE0AC84.toInt(), 0xFFC68E64.toInt(), 0xFFA66E46.toInt(), 0xFF7E4E30.toInt(), 0xFFF4D6B8.toInt())
    private val hairs = intArrayOf(0xFF2B1D14.toInt(), 0xFF5A3A22.toInt(), 0xFF8E5B2E.toInt(), 0xFFD1A64A.toInt(), 0xFFB04A2E.toInt(), 0xFF8A8A8E.toInt(), 0xFF141414.toInt())

    fun skinOf(p: Pawn) = skins[(p.id * 7 + 3) % skins.size]
    fun hairOf(p: Pawn) = if (p.age > 58) 0xFFBDBDC2.toInt() else hairs[(p.id * 5 + 1) % hairs.size]

    /** [ang] is the facing in degrees (0 = east). Everything is drawn facing east then rotated. */
    fun human(
        c: Canvas, p: Pawn, cx: Float, cy: Float, s: Float, ang: Float, bodyColor: Int, hatColor: Int?, ring: Int, ringW: Float,
        walking: Boolean, frame: Int, itemColor: Int?, aimed: Boolean,
    ) {
        val skin = skinOf(p); val hair = hairOf(p)
        oval(c, cx - s * 0.3f + s * 0.05f, cy - s * 0.3f + s * 0.07f, cx + s * 0.3f + s * 0.05f, cy + s * 0.3f + s * 0.07f, 0x44000000)
        c.save()
        c.rotate(ang, cx, cy)
        val swing = if (walking) sin(frame * 0.45f + p.id) * s * 0.07f else 0f
        val wp = if (p.weaponItem != null) p.weapon else null
        // Hands.
        if (wp != null && wp.ranged) {
            disc(c, cx + s * 0.28f, cy + s * 0.04f, s * 0.06f, skin); disc(c, cx + s * 0.14f, cy + s * 0.14f, s * 0.06f, skin)
        } else {
            disc(c, cx + s * 0.06f + swing, cy + s * 0.26f, s * 0.065f, skin); disc(c, cx + s * 0.06f - swing, cy - s * 0.26f, s * 0.065f, skin)
        }
        // Weapon.
        if (wp != null) {
            if (wp.ranged) {
                val len = if (wp.range > 24f) 0.46f else if (wp.range > 16f) 0.38f else 0.28f
                rr(c, cx + s * 0.08f, cy + s * 0.02f, cx + s * (0.08f + len), cy + s * 0.1f, s * 0.02f, 0xFF2E3034.toInt())
                rr(c, cx + s * 0.08f, cy + s * 0.04f, cx + s * 0.2f, cy + s * 0.16f, s * 0.02f, 0xFF6B4A2A.toInt())
            } else {
                line(c, cx + s * 0.1f, cy + s * 0.26f, cx + s * 0.46f, cy + s * 0.2f, 0xFFB9C0C8.toInt(), max(2f, s * 0.06f))
            }
        }
        // Torso: rounded shoulders seen from above.
        rr(c, cx - s * 0.17f, cy - s * 0.3f, cx + s * 0.17f, cy + s * 0.3f, s * 0.16f, bodyColor)
        rr(c, cx - s * 0.17f, cy - s * 0.3f, cx - s * 0.02f, cy + s * 0.3f, s * 0.14f, shade(bodyColor, 0.86f))
        stroke.color = 0x88101010.toInt(); stroke.strokeWidth = max(1f, s * 0.03f)
        rect.set(cx - s * 0.17f, cy - s * 0.3f, cx + s * 0.17f, cy + s * 0.3f); c.drawRoundRect(rect, s * 0.16f, s * 0.16f, stroke)
        // Head.
        val hx = cx + s * 0.03f
        disc(c, hx, cy, s * 0.165f, skin)
        // Hair.
        val long = p.female && p.id % 3 != 0
        when {
            long -> { oval(c, hx - s * 0.3f, cy - s * 0.2f, hx + s * 0.02f, cy + s * 0.2f, hair); fill.color = hair; rect.set(hx - s * 0.17f, cy - s * 0.17f, hx + s * 0.17f, cy + s * 0.17f); c.drawArc(rect, 100f, 160f, true, fill) }
            p.id % 4 == 0 -> { fill.color = hair; rect.set(hx - s * 0.17f, cy - s * 0.17f, hx + s * 0.17f, cy + s * 0.17f); c.drawArc(rect, 70f, 220f, true, fill) }
            p.id % 4 == 1 -> { fill.color = hair; rect.set(hx - s * 0.17f, cy - s * 0.17f, hx + s * 0.17f, cy + s * 0.17f); c.drawArc(rect, 115f, 130f, true, fill) }
            else -> { fill.color = hair; rect.set(hx - s * 0.17f, cy - s * 0.17f, hx + s * 0.17f, cy + s * 0.17f); c.drawArc(rect, 90f, 180f, true, fill) }
        }
        if (hatColor != null) {
            disc(c, hx - s * 0.01f, cy, s * 0.18f, hatColor)
            stroke.color = 0x66000000; stroke.strokeWidth = max(1f, s * 0.03f); c.drawCircle(hx - s * 0.01f, cy, s * 0.18f, stroke)
        } else {
            // Face: eyes and nose.
            disc(c, hx + s * 0.1f, cy - s * 0.06f, s * 0.02f, 0xFF1A1410.toInt()); disc(c, hx + s * 0.1f, cy + s * 0.06f, s * 0.02f, 0xFF1A1410.toInt())
            disc(c, hx + s * 0.16f, cy, s * 0.025f, shade(skin, 0.85f))
        }
        stroke.color = 0x88101010.toInt(); stroke.strokeWidth = max(1f, s * 0.03f); c.drawCircle(hx, cy, s * 0.165f, stroke)
        c.restore()
        if (ringW > 0f) { stroke.color = ring; stroke.strokeWidth = ringW; c.drawCircle(cx, cy, s * 0.36f, stroke) }
        if (itemColor != null) { rr(c, cx - s * 0.12f, cy - s * 0.52f, cx + s * 0.12f, cy - s * 0.3f, s * 0.04f, itemColor); stroke.color = 0x88000000.toInt(); stroke.strokeWidth = 1.5f; rect.set(cx - s * 0.12f, cy - s * 0.52f, cx + s * 0.12f, cy - s * 0.3f); c.drawRoundRect(rect, s * 0.04f, s * 0.04f, stroke) }
        if (aimed) { /* reserved for aim markers */ }
    }

    fun downedHuman(c: Canvas, p: Pawn, cx: Float, cy: Float, s: Float, bodyColor: Int) {
        val skin = skinOf(p)
        oval(c, cx - s * 0.42f, cy - s * 0.18f + s * 0.06f, cx + s * 0.44f, cy + s * 0.2f + s * 0.06f, 0x44000000)
        rr(c, cx - s * 0.3f, cy - s * 0.18f, cx + s * 0.4f, cy + s * 0.18f, s * 0.16f, bodyColor)
        disc(c, cx - s * 0.36f, cy, s * 0.15f, skin)
        fill.color = hairOf(p); rect.set(cx - s * 0.51f, cy - s * 0.15f, cx - s * 0.21f, cy + s * 0.15f); c.drawArc(rect, 270f, 180f, true, fill)
        stroke.color = 0x88101010.toInt(); stroke.strokeWidth = max(1f, s * 0.03f); c.drawCircle(cx - s * 0.36f, cy, s * 0.15f, stroke)
        line(c, cx - s * 0.1f, cy - s * 0.04f, cx + s * 0.1f, cy + s * 0.06f, 0x33000000, max(1f, s * 0.04f))
    }

    // ------------------------------------------------------------------ animals and machines
    fun animal(c: Canvas, p: Pawn, cx: Float, cy: Float, s: Float, ang: Float, walking: Boolean, frame: Int) {
        val race = p.race
        val size = race.size.coerceIn(0.5f, 2.2f)
        val len = s * 0.34f * size.coerceAtMost(1.6f)
        val col = race.color
        val dark = shade(col, 0.72f); val light = shade(col, 1.25f)
        val gait = if (walking) sin(frame * 0.5f + p.id) * len * 0.12f else 0f
        oval(c, cx - len * 1.05f + len * 0.1f, cy - len * 0.55f + len * 0.2f, cx + len * 1.1f + len * 0.1f, cy + len * 0.55f + len * 0.2f, 0x40000000)
        c.save(); c.rotate(ang, cx, cy)
        when {
            race.mech -> mech(c, race, cx, cy, len, frame, gait)
            race.insect -> bug(c, race, cx, cy, len, frame, walking, p.id)
            race == Race.CHICKEN || race == Race.TURKEY -> bird(c, race, cx, cy, len, col, gait)
            else -> quadruped(c, p, cx, cy, len, col, dark, light, gait)
        }
        c.restore()
    }

    private fun quadruped(c: Canvas, p: Pawn, cx: Float, cy: Float, len: Float, col: Int, dark: Int, light: Int, gait: Float) {
        val race = p.race
        val lw = max(1.5f, len * 0.12f)
        // Legs.
        for (sx in intArrayOf(-1, 1)) {
            line(c, cx + len * 0.55f, cy + sx * len * 0.34f, cx + len * 0.55f + gait * sx, cy + sx * len * 0.34f, dark, lw * 1.4f)
            line(c, cx - len * 0.55f, cy + sx * len * 0.34f, cx - len * 0.55f - gait * sx, cy + sx * len * 0.34f, dark, lw * 1.4f)
        }
        // Tail.
        when (race) {
            Race.WOLF, Race.HUSKY -> oval(c, cx - len * 1.35f, cy - len * 0.12f, cx - len * 0.8f, cy + len * 0.14f, dark)
            Race.RAT -> line(c, cx - len * 0.95f, cy, cx - len * 1.6f, cy + len * 0.18f, 0xFFD8A8A0.toInt(), max(1f, len * 0.07f))
            Race.HARE -> disc(c, cx - len * 0.95f, cy, len * 0.18f, 0xFFF4F0E8.toInt())
            else -> line(c, cx - len * 0.9f, cy, cx - len * 1.25f, cy + len * 0.1f, dark, lw)
        }
        // Body.
        val bodyH = when (race) { Race.MUFFALO, Race.BEAR, Race.COW, Race.THRUMBO -> 0.62f; else -> 0.48f }
        oval(c, cx - len, cy - len * bodyH, cx + len * 0.9f, cy + len * bodyH, col)
        oval(c, cx - len * 0.8f, cy - len * bodyH * 0.8f, cx + len * 0.6f, cy - len * bodyH * 0.1f, withAlpha(light, 110))
        when (race) {
            Race.COW -> { disc(c, cx - len * 0.3f, cy - len * 0.2f, len * 0.28f, 0xFF2A2420.toInt()); disc(c, cx + len * 0.3f, cy + len * 0.22f, len * 0.22f, 0xFF2A2420.toInt()); disc(c, cx - len * 0.55f, cy + len * 0.25f, len * 0.16f, 0xFF2A2420.toInt()) }
            Race.MUFFALO -> { oval(c, cx - len * 0.2f, cy - len * 0.7f, cx + len * 0.8f, cy + len * 0.7f, dark); for (k in -2..2) line(c, cx - len * 0.9f, cy + k * len * 0.2f, cx - len * 0.3f, cy + k * len * 0.2f, shade(col, 0.6f), max(1f, len * 0.05f)) }
            Race.DEER -> for (k in 0 until 4) disc(c, cx - len * 0.5f + (k % 2) * len * 0.5f, cy + (if (k < 2) -1 else 1) * len * 0.2f, len * 0.06f, 0xFFF0E4D0.toInt())
            Race.BOAR -> line(c, cx - len * 0.9f, cy, cx + len * 0.6f, cy, 0xFF1E1612.toInt(), lw)
            Race.WOLF -> oval(c, cx - len * 0.2f, cy - len * 0.2f, cx + len * 0.6f, cy + len * 0.2f, shade(col, 0.82f))
            Race.HUSKY -> { oval(c, cx - len * 0.9f, cy - len * 0.2f, cx + len * 0.8f, cy + len * 0.2f, 0xFFF0F0F2.toInt()); oval(c, cx - len * 0.8f, cy - len * 0.42f, cx + len * 0.7f, cy - len * 0.12f, shade(col, 0.55f)) }
            else -> {}
        }
        // Head.
        val hx = cx + len * 1.0f
        val hr = len * when (race) { Race.HARE, Race.RAT -> 0.3f; Race.BEAR, Race.THRUMBO, Race.MUFFALO -> 0.42f; else -> 0.34f }
        // Ears and horns first so the head covers their roots.
        when (race) {
            Race.ELEPHANT -> { oval(c, hx - hr * 1.6f, cy - hr * 1.9f, hx - hr * 0.1f, cy - hr * 0.2f, dark); oval(c, hx - hr * 1.6f, cy + hr * 0.2f, hx - hr * 0.1f, cy + hr * 1.9f, dark); line(c, hx + hr * 0.6f, cy, hx + hr * 2.0f, cy + hr * 0.5f, shade(col, 0.9f), max(3f, len * 0.22f)); line(c, hx + hr * 0.4f, cy - hr * 0.5f, hx + hr * 1.6f, cy - hr * 0.8f, 0xFFF4EEDF.toInt(), max(2f, len * 0.1f)); line(c, hx + hr * 0.4f, cy + hr * 0.5f, hx + hr * 1.6f, cy + hr * 0.8f, 0xFFF4EEDF.toInt(), max(2f, len * 0.1f)) }
            Race.RHINO -> { line(c, hx + hr * 0.8f, cy, hx + hr * 1.7f, cy, 0xFFE8E0C8.toInt(), max(3f, len * 0.16f)); disc(c, hx - hr * 0.5f, cy - hr * 0.85f, hr * 0.3f, dark); disc(c, hx - hr * 0.5f, cy + hr * 0.85f, hr * 0.3f, dark) }
            Race.HARE -> { oval(c, hx - hr * 1.2f, cy - hr * 1.7f, hx - hr * 0.1f, cy - hr * 0.3f, col); oval(c, hx - hr * 1.2f, cy + hr * 0.3f, hx - hr * 0.1f, cy + hr * 1.7f, col) }
            Race.WOLF, Race.HUSKY, Race.WARG, Race.COUGAR, Race.FOX, Race.CAT, Race.LABRADOR -> { path.reset(); path.moveTo(hx - hr * 0.5f, cy - hr * 0.5f); path.lineTo(hx - hr * 0.9f, cy - hr * 1.3f); path.lineTo(hx + hr * 0.1f, cy - hr * 0.8f); path.close(); fill.color = dark; c.drawPath(path, fill); path.reset(); path.moveTo(hx - hr * 0.5f, cy + hr * 0.5f); path.lineTo(hx - hr * 0.9f, cy + hr * 1.3f); path.lineTo(hx + hr * 0.1f, cy + hr * 0.8f); path.close(); c.drawPath(path, fill) }
            Race.DEER, Race.ELK, Race.CARIBOU -> { val ac = 0xFFE8DCC0.toInt(); line(c, hx - hr * 0.3f, cy - hr * 0.5f, hx - hr * 1.2f, cy - hr * 1.5f, ac, max(1f, len * 0.07f)); line(c, hx - hr * 0.9f, cy - hr * 1.1f, hx - hr * 0.4f, cy - hr * 1.7f, ac, max(1f, len * 0.06f)); line(c, hx - hr * 0.3f, cy + hr * 0.5f, hx - hr * 1.2f, cy + hr * 1.5f, ac, max(1f, len * 0.07f)); line(c, hx - hr * 0.9f, cy + hr * 1.1f, hx - hr * 0.4f, cy + hr * 1.7f, ac, max(1f, len * 0.06f)) }
            Race.MUFFALO, Race.COW, Race.BISON, Race.BOOMALOPE -> { val hc = 0xFFE8E0C8.toInt(); line(c, hx - hr * 0.2f, cy - hr * 0.7f, hx + hr * 0.3f, cy - hr * 1.5f, hc, max(1.5f, len * 0.09f)); line(c, hx - hr * 0.2f, cy + hr * 0.7f, hx + hr * 0.3f, cy + hr * 1.5f, hc, max(1.5f, len * 0.09f)) }
            Race.THRUMBO -> { line(c, hx + hr * 0.2f, cy, hx + hr * 2.0f, cy, 0xFFF4EEDF.toInt(), max(2f, len * 0.12f)); line(c, hx - hr * 0.2f, cy - hr * 0.7f, hx - hr * 0.8f, cy - hr * 1.5f, 0xFFD8C8E0.toInt(), max(1.5f, len * 0.09f)); line(c, hx - hr * 0.2f, cy + hr * 0.7f, hx - hr * 0.8f, cy + hr * 1.5f, 0xFFD8C8E0.toInt(), max(1.5f, len * 0.09f)) }
            else -> { disc(c, hx - hr * 0.5f, cy - hr * 0.85f, hr * 0.38f, dark); disc(c, hx - hr * 0.5f, cy + hr * 0.85f, hr * 0.38f, dark) }
        }
        val snout = when (race) { Race.WOLF, Race.HUSKY, Race.BOAR, Race.DEER, Race.THRUMBO, Race.MUFFALO, Race.COW -> 1.5f; else -> 1.1f }
        oval(c, hx - hr, cy - hr * 0.85f, hx + hr * snout, cy + hr * 0.85f, shade(col, 0.92f))
        if (race == Race.HUSKY) oval(c, hx - hr * 0.2f, cy - hr * 0.55f, hx + hr * 1.3f, cy + hr * 0.55f, 0xFFF0F0F2.toInt())
        if (race == Race.BOAR) { path.reset(); path.moveTo(hx + hr * 1.1f, cy - hr * 0.5f); path.lineTo(hx + hr * 1.6f, cy - hr * 0.95f); path.lineTo(hx + hr * 1.3f, cy - hr * 0.3f); path.close(); fill.color = 0xFFF4EEDF.toInt(); c.drawPath(path, fill); path.reset(); path.moveTo(hx + hr * 1.1f, cy + hr * 0.5f); path.lineTo(hx + hr * 1.6f, cy + hr * 0.95f); path.lineTo(hx + hr * 1.3f, cy + hr * 0.3f); path.close(); c.drawPath(path, fill) }
        disc(c, hx + hr * snout, cy, hr * 0.2f, 0xFF2A1E1A.toInt())
        disc(c, hx + hr * 0.3f, cy - hr * 0.42f, hr * 0.14f, 0xFF140E0C.toInt()); disc(c, hx + hr * 0.3f, cy + hr * 0.42f, hr * 0.14f, 0xFF140E0C.toInt())
        stroke.color = 0x66000000; stroke.strokeWidth = max(1f, len * 0.05f); rect.set(cx - len, cy - len * bodyH, cx + len * 0.9f, cy + len * bodyH); c.drawOval(rect, stroke)
    }

    private fun withAlphaF(c: Int, a: Int) = withAlpha(c, a)

    private fun bird(c: Canvas, race: Race, cx: Float, cy: Float, len: Float, col: Int, gait: Float) {
        val lw = max(1.5f, len * 0.1f)
        line(c, cx, cy - len * 0.2f, cx + gait, cy - len * 0.2f, 0xFFD8A83A.toInt(), lw); line(c, cx, cy + len * 0.2f, cx - gait, cy + len * 0.2f, 0xFFD8A83A.toInt(), lw)
        if (race == Race.TURKEY) { for (k in -2..2) line(c, cx - len * 0.5f, cy, cx - len * 1.5f, cy + k * len * 0.32f, shade(col, 0.7f + 0.1f * abs(k)), max(2f, len * 0.16f)) }
        else { path.reset(); path.moveTo(cx - len * 0.6f, cy); path.lineTo(cx - len * 1.2f, cy - len * 0.3f); path.lineTo(cx - len * 1.1f, cy + len * 0.3f); path.close(); fill.color = shade(col, 0.9f); c.drawPath(path, fill) }
        oval(c, cx - len * 0.8f, cy - len * 0.55f, cx + len * 0.7f, cy + len * 0.55f, col)
        oval(c, cx - len * 0.5f, cy - len * 0.5f, cx + len * 0.2f, cy - len * 0.1f, shade(col, 0.85f)); oval(c, cx - len * 0.5f, cy + len * 0.1f, cx + len * 0.2f, cy + len * 0.5f, shade(col, 0.85f))
        disc(c, cx + len * 0.8f, cy, len * 0.28f, col)
        path.reset(); path.moveTo(cx + len * 1.05f, cy - len * 0.1f); path.lineTo(cx + len * 1.35f, cy); path.lineTo(cx + len * 1.05f, cy + len * 0.1f); path.close(); fill.color = 0xFFE8A82E.toInt(); c.drawPath(path, fill)
        disc(c, cx + len * 0.75f, cy, len * 0.12f, 0xFFC83A32.toInt())
        disc(c, cx + len * 0.9f, cy - len * 0.14f, len * 0.05f, 0xFF140E0C.toInt()); disc(c, cx + len * 0.9f, cy + len * 0.14f, len * 0.05f, 0xFF140E0C.toInt())
    }

    private fun bug(c: Canvas, race: Race, cx: Float, cy: Float, len: Float, frame: Int, walking: Boolean, id: Int) {
        val col = race.color
        val lw = max(1.5f, len * 0.07f)
        val legs = if (race == Race.MEGASPIDER) 4 else 3
        val ph = if (walking) sin(frame * 0.6f + id) * len * 0.15f else 0f
        for (k in 0 until legs) {
            val ox = len * (0.55f - 0.5f * k * (3f / legs))
            line(c, cx + ox, cy, cx + ox + ph * (if (k % 2 == 0) 1 else -1), cy - len * 0.95f, shade(col, 0.6f), lw)
            line(c, cx + ox, cy, cx + ox - ph * (if (k % 2 == 0) 1 else -1), cy + len * 0.95f, shade(col, 0.6f), lw)
        }
        when (race) {
            Race.SPELOPEDE -> { for (k in 0 until 5) oval(c, cx - len * 1.2f + k * len * 0.5f, cy - len * 0.34f, cx - len * 0.7f + k * len * 0.5f, cy + len * 0.34f, shade(col, 0.8f + 0.08f * k)) }
            Race.MEGASPIDER -> { oval(c, cx - len * 1.1f, cy - len * 0.65f, cx + len * 0.1f, cy + len * 0.65f, shade(col, 0.85f)); oval(c, cx - len * 0.1f, cy - len * 0.45f, cx + len * 0.9f, cy + len * 0.45f, col) }
            else -> { oval(c, cx - len * 0.9f, cy - len * 0.55f, cx + len * 0.5f, cy + len * 0.55f, col); line(c, cx - len * 0.9f, cy, cx + len * 0.5f, cy, shade(col, 0.5f), lw); oval(c, cx - len * 0.6f, cy - len * 0.4f, cx, cy - len * 0.1f, withAlpha(0xFFFFFFFF.toInt(), 60)) }
        }
        disc(c, cx + len * 0.85f, cy, len * 0.28f, shade(col, 0.9f))
        line(c, cx + len * 1.0f, cy - len * 0.1f, cx + len * 1.5f, cy - len * 0.35f, shade(col, 0.6f), lw); line(c, cx + len * 1.0f, cy + len * 0.1f, cx + len * 1.5f, cy + len * 0.35f, shade(col, 0.6f), lw)
        disc(c, cx + len * 0.95f, cy - len * 0.13f, len * 0.07f, 0xFFE8D84A.toInt()); disc(c, cx + len * 0.95f, cy + len * 0.13f, len * 0.07f, 0xFFE8D84A.toInt())
    }

    private fun mech(c: Canvas, race: Race, cx: Float, cy: Float, len: Float, frame: Int, gait: Float) {
        val col = race.color
        val metal = 0xFFB8C4D0.toInt()
        val glow = if ((frame / 8) % 2 == 0) 0xFFFF3A2A.toInt() else 0xFFC02418.toInt()
        when (race) {
            Race.SCYTHER -> {
                line(c, cx + len * 0.2f, cy - len * 0.3f, cx + len * 1.4f, cy - len * 0.95f, metal, max(3f, len * 0.16f)); line(c, cx + len * 0.2f, cy + len * 0.3f, cx + len * 1.4f, cy + len * 0.95f, metal, max(3f, len * 0.16f))
                for (sx in intArrayOf(-1, 1)) { line(c, cx - len * 0.6f, cy + sx * len * 0.35f, cx - len * 0.6f - gait * sx, cy + sx * len * 0.8f, shade(col, 0.6f), max(2f, len * 0.12f)) }
                oval(c, cx - len * 0.95f, cy - len * 0.45f, cx + len * 0.75f, cy + len * 0.45f, col); oval(c, cx - len * 0.6f, cy - len * 0.3f, cx + len * 0.2f, cy - len * 0.05f, withAlpha(0xFFFFFFFF.toInt(), 70))
                disc(c, cx + len * 0.6f, cy, len * 0.14f, glow)
            }
            Race.LANCER -> {
                oval(c, cx - len * 0.9f, cy - len * 0.55f, cx + len * 0.7f, cy + len * 0.55f, col)
                rr(c, cx + len * 0.1f, cy - len * 0.1f, cx + len * 1.5f, cy + len * 0.1f, len * 0.05f, metal)
                disc(c, cx - len * 0.1f, cy, len * 0.3f, shade(col, 0.7f)); disc(c, cx - len * 0.1f, cy, len * 0.14f, glow)
            }
            else -> { // centipede
                for (k in 0 until 5) {
                    val x0 = cx - len * 1.3f + k * len * 0.55f
                    line(c, x0, cy, x0 + gait * (if (k % 2 == 0) 1 else -1), cy - len * 0.7f, shade(col, 0.55f), max(2f, len * 0.1f)); line(c, x0, cy, x0 - gait * (if (k % 2 == 0) 1 else -1), cy + len * 0.7f, shade(col, 0.55f), max(2f, len * 0.1f))
                    oval(c, x0 - len * 0.32f, cy - len * 0.42f, x0 + len * 0.32f, cy + len * 0.42f, shade(col, 0.85f + 0.06f * k))
                    disc(c, x0, cy, len * 0.08f, glow)
                }
                rr(c, cx + len * 1.3f, cy - len * 0.08f, cx + len * 2.0f, cy + len * 0.08f, len * 0.04f, metal)
            }
        }
        stroke.color = 0x88000000.toInt(); stroke.strokeWidth = max(1f, len * 0.05f)
    }
}
