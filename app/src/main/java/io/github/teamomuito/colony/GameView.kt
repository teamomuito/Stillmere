package io.github.teamomuito.colony

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import io.github.teamomuito.colony.sim.BuildDef
import io.github.teamomuito.colony.sim.Desig
import io.github.teamomuito.colony.sim.Faction
import io.github.teamomuito.colony.sim.Game
import io.github.teamomuito.colony.sim.ItemCat
import io.github.teamomuito.colony.sim.ItemType
import io.github.teamomuito.colony.sim.Ore
import io.github.teamomuito.colony.sim.Pawn
import io.github.teamomuito.colony.sim.PlantType
import io.github.teamomuito.colony.sim.RockType
import io.github.teamomuito.colony.sim.Terrain
import io.github.teamomuito.colony.sim.Weather
import io.github.teamomuito.colony.sim.ZoneKind
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

sealed class Tool(val label: String, val paints: Boolean) {
    object Select : Tool("Select", false)
    object Mine : Tool("Mine", true)
    object Cut : Tool("Chop trees", true)
    object Harvest : Tool("Harvest plants", true)
    object Deconstruct : Tool("Deconstruct", true)
    object Repair : Tool("Repair", true)
    object CancelOrders : Tool("Cancel orders", true)
    object Hunt : Tool("Hunt animals", true)
    object Tame : Tool("Tame animals", true)
    object Slaughter : Tool("Slaughter", true)
    object Forbid : Tool("Forbid / allow", true)
    object Stockpile : Tool("Stockpile zone", true)
    object Dumping : Tool("Dumping zone", true)
    class Growing(val crop: PlantType) : Tool("Growing zone: ${crop.label}", true)
    object ClearZone : Tool("Remove zone", true)
    class Build(val def: BuildDef) : Tool("Build: ${def.label}", true)
    class Area(val index: Int, val add: Boolean, name: String) : Tool("${if (add) "Paint" else "Erase"} $name", true)
}

class GameView(context: Context) : View(context) {
    var game: Game? = null
    var tool: Tool = Tool.Select
    var selectedId: Int = -1
    var selectedCell: Int = -1
    var buildRot = false

    var onTileTap: ((Int, Int) -> Unit)? = null
    var onArea: ((Int, Int, Int, Int) -> Unit)? = null

    private var scale = 38f
    private var camX = 0f
    private var camY = 0f
    private var centered = false
    private var frame = 0

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    private val path = Path()
    private val sprites = Sprites(fill, stroke)
    private val facing = HashMap<Int, Float>()

    private var areaStart: IntArray? = null
    private var areaEnd: IntArray? = null
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var multi = false
    private var slop = 18f * resources.displayMetrics.density

    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val old = scale
            scale = (scale * d.scaleFactor).coerceIn(12f, 100f)
            val wx = camX + d.focusX / old
            val wy = camY + d.focusY / old
            camX = wx - d.focusX / scale
            camY = wy - d.focusY / scale
            clampCamera()
            invalidate()
            return true
        }
    })

    fun centerOn(x: Float, y: Float) {
        camX = x + 0.5f - width / scale / 2f
        camY = y + 0.5f - height / scale / 2f
        clampCamera()
        invalidate()
    }

    private fun clampCamera() {
        val g = game ?: return
        val vw = width / scale
        val vh = height / scale
        camX = camX.coerceIn(-2f, max(-2f, g.map.w - vw + 2f))
        camY = camY.coerceIn(-2f, max(-2f, g.map.h - vh + 2f))
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val g = game
        if (!centered && g != null) { centerOn(g.homeX.toFloat(), g.homeY.toFloat()); centered = true }
        else clampCamera()
    }

    fun recenter() { centered = false; if (width > 0) onSizeChanged(width, height, 0, 0) }

    private fun tileX(sx: Float) = ((sx / scale) + camX).toInt()
    private fun tileY(sy: Float) = ((sy / scale) + camY).toInt()

    // ------------------------------------------------------------------ input
    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaler.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; lastX = e.x; lastY = e.y
                dragging = false; multi = false
                areaStart = if (tool.paints) intArrayOf(tileX(e.x), tileY(e.y)) else null
                areaEnd = areaStart
                invalidate()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                multi = true; areaStart = null; areaEnd = null
                lastX = focusX(e); lastY = focusY(e)
            }
            MotionEvent.ACTION_MOVE -> {
                if (e.pointerCount >= 2) {
                    val fx = focusX(e); val fy = focusY(e)
                    camX -= (fx - lastX) / scale
                    camY -= (fy - lastY) / scale
                    lastX = fx; lastY = fy
                    clampCamera(); invalidate()
                } else if (!multi) {
                    if (areaStart != null) {
                        areaEnd = intArrayOf(tileX(e.x), tileY(e.y))
                        invalidate()
                    } else {
                        if (!dragging && (abs(e.x - downX) > slop || abs(e.y - downY) > slop)) dragging = true
                        if (dragging) {
                            camX -= (e.x - lastX) / scale
                            camY -= (e.y - lastY) / scale
                            clampCamera(); invalidate()
                        }
                        lastX = e.x; lastY = e.y
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val skip = e.actionIndex
                var sx = 0f; var sy = 0f; var n = 0
                for (i in 0 until e.pointerCount) if (i != skip) { sx += e.getX(i); sy += e.getY(i); n++ }
                if (n > 0) { lastX = sx / n; lastY = sy / n }
            }
            MotionEvent.ACTION_UP -> {
                if (!multi) {
                    val s = areaStart
                    val en = areaEnd
                    if (s != null && en != null) {
                        onArea?.invoke(min(s[0], en[0]), min(s[1], en[1]), max(s[0], en[0]), max(s[1], en[1]))
                    } else if (!dragging) {
                        onTileTap?.invoke(tileX(e.x), tileY(e.y))
                    }
                }
                areaStart = null; areaEnd = null; multi = false; dragging = false
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { areaStart = null; areaEnd = null; multi = false; dragging = false; invalidate() }
        }
        return true
    }

    private fun focusX(e: MotionEvent): Float { var s = 0f; for (i in 0 until e.pointerCount) s += e.getX(i); return s / e.pointerCount }
    private fun focusY(e: MotionEvent): Float { var s = 0f; for (i in 0 until e.pointerCount) s += e.getY(i); return s / e.pointerCount }

    // ------------------------------------------------------------------ colours
    private fun terrainColor(t: Terrain, rock: RockType): Int = when (t) {
        Terrain.SOIL -> 0xFF6B5A3C.toInt()
        Terrain.RICH_SOIL -> 0xFF54452B.toInt()
        Terrain.GRAVEL -> 0xFF807D76.toInt()
        Terrain.SAND -> 0xFFC4B482.toInt()
        Terrain.MARSH -> 0xFF4C6948.toInt()
        Terrain.MUD -> 0xFF4A3D2E.toInt()
        Terrain.ICE -> 0xFFCFE8F2.toInt()
        Terrain.WATER_SHALLOW -> 0xFF4F82AD.toInt()
        Terrain.WATER_DEEP -> 0xFF2C4D78.toInt()
        Terrain.ROCK -> when (rock) {
            RockType.GRANITE -> 0xFF4C4D52.toInt(); RockType.MARBLE -> 0xFF6A6C73.toInt(); RockType.LIMESTONE -> 0xFF5E5C55.toInt()
            RockType.SANDSTONE -> 0xFF685B4C.toInt(); RockType.SLATE -> 0xFF3F434A.toInt()
        }
    }

    private fun oreColor(o: Ore): Int = when (o) {
        Ore.STEEL -> 0xFFC8DDF0.toInt(); Ore.SILVER -> 0xFFEDEDED.toInt(); Ore.GOLD -> 0xFFF2C94C.toInt()
        Ore.PLASTEEL -> 0xFF6CE0D0.toInt(); Ore.COMPONENTS -> 0xFFE38B3A.toInt(); else -> 0
    }

    fun itemColor(t: ItemType): Int = when (t.cat) {
        ItemCat.RESOURCE -> when (t) {
            ItemType.WOOD -> 0xFFA9743C.toInt(); ItemType.STONE, ItemType.STONE_CHUNK -> 0xFFB9B9BD.toInt(); ItemType.STEEL -> 0xFF9FC2E0.toInt()
            ItemType.PLASTEEL -> 0xFF6CE0D0.toInt(); ItemType.SILVER -> 0xFFEDEDED.toInt(); ItemType.GOLD -> 0xFFF2C94C.toInt()
            ItemType.COMPONENT -> 0xFFE38B3A.toInt(); ItemType.CLOTH -> 0xFFE8E0C8.toInt(); ItemType.LEATHER -> 0xFF8A5A33.toInt(); else -> 0xFFD8D2C0.toInt()
        }
        ItemCat.FOOD_PLANT -> 0xFF8DCB5A.toInt()
        ItemCat.FOOD_MEAT -> 0xFFD9605A.toInt()
        ItemCat.FOOD_MEAL -> 0xFFF0A23C.toInt()
        ItemCat.FOOD_ANIMAL -> 0xFFC9B26A.toInt()
        ItemCat.MEDICINE -> 0xFF63D18F.toInt()
        ItemCat.DRUG -> 0xFFB77AE0.toInt()
        ItemCat.WEAPON -> 0xFF909AA4.toInt()
        ItemCat.APPAREL -> 0xFF8AA7D8.toInt()
        ItemCat.ART -> 0xFFE8D28A.toInt()
        else -> 0xFFA0A0A0.toInt()
    }

    fun pawnColor(p: Pawn): Int {
        if (p.isAnimal) return p.race.color
        if (p.faction == Faction.ENEMY) return 0xFFD9453B.toInt()
        val hue = (p.id * 67 % 360).toFloat()
        return Color.HSVToColor(floatArrayOf(hue, 0.4f, 0.95f))
    }

    private fun apparelColor(t: ItemType): Int = when (t) {
        ItemType.A_TSHIRT -> 0xFFD9D6C8.toInt(); ItemType.A_BUTTONDOWN -> 0xFF8BA8C9.toInt(); ItemType.A_PANTS -> 0xFF5E6B8C.toInt()
        ItemType.A_TRIBAL -> 0xFF8A5A33.toInt(); ItemType.A_DUSTER -> 0xFF9A7B4F.toInt(); ItemType.A_PARKA -> 0xFF4C6A9A.toInt()
        ItemType.A_FLAK_VEST, ItemType.A_FLAK_JACKET -> 0xFF5C6B52.toInt(); ItemType.A_ARMOR -> 0xFF8E959E.toInt(); ItemType.A_RECON -> 0xFF3F4A44.toInt()
        else -> 0xFFCCCCCC.toInt()
    }

    // ------------------------------------------------------------------ drawing
    override fun onDraw(c: Canvas) {
        val g = game ?: return
        frame++
        val m = g.map
        c.drawColor(0xFF1B1A18.toInt())
        val x0 = max(0, camX.toInt() - 1)
        val y0 = max(0, camY.toInt() - 1)
        val x1 = min(m.w - 1, (camX + width / scale).toInt() + 1)
        val y1 = min(m.h - 1, (camY + height / scale).toInt() + 1)
        val s = scale
        fill.style = Paint.Style.FILL

        // Ground.
        for (y in y0..y1) for (x in x0..x1) {
            val i = m.idx(x, y)
            val sx = (x - camX) * s
            val sy = (y - camY) * s
            val t = m.terrain[i]
            sprites.ground(c, m, x, y, sx, sy, s, frame, m.biome)
            if (t == Terrain.ROCK && m.ore[i] != Ore.NONE) {
                val oc = oreColor(m.ore[i])
                for ((ox, oy, r) in listOf(Triple(0.3f, 0.35f, 0.1f), Triple(0.65f, 0.55f, 0.12f), Triple(0.4f, 0.75f, 0.075f), Triple(0.72f, 0.25f, 0.06f))) {
                    fill.color = 0x66000000; c.drawCircle(sx + s * ox + s * 0.02f, sy + s * oy + s * 0.025f, s * r, fill)
                    fill.color = oc; c.drawCircle(sx + s * ox, sy + s * oy, s * r, fill)
                    fill.color = 0x66FFFFFF; c.drawCircle(sx + s * (ox - 0.03f), sy + s * (oy - 0.03f), s * r * 0.35f, fill)
                }
            }
            if (m.natRoof[i] && t != Terrain.ROCK) { fill.color = 0x30101018; c.drawRect(sx, sy, sx + s + 1, sy + s + 1, fill) }
            val fl = m.floor[i]
            if (fl != null) sprites.floor(c, fl, x, y, sx, sy, s)
            if (m.snow[i] > 0.05f) { fill.color = Color.argb((m.snow[i] * 190).toInt(), 240, 246, 255); c.drawRect(sx, sy, sx + s + 1, sy + s + 1, fill) }
            val z = m.zoneAt(i)
            if (z != null) {
                when (z.kind) {
                    ZoneKind.STOCKPILE -> sprites.stockpile(c, sx, sy, s)
                    ZoneKind.GROWING -> sprites.growing(c, sx, sy, s)
                    else -> { fill.color = 0x558A8A8A; c.drawRect(sx, sy, sx + s, sy + s, fill) }
                }
            }
            if (m.filth[i] > 0) {
                fill.color = 0x66301810
                val n = m.filth[i].toInt()
                c.drawCircle(sx + s * 0.3f, sy + s * 0.7f, s * 0.07f * n, fill)
                if (n > 1) c.drawCircle(sx + s * 0.7f, sy + s * 0.4f, s * 0.06f * n, fill)
            }
        }
        if (s >= 30f) {
            stroke.color = 0x14000000; stroke.strokeWidth = 1f
            for (x in x0..x1 + 1) c.drawLine((x - camX) * s, (y0 - camY) * s, (x - camX) * s, (y1 + 1 - camY) * s, stroke)
            for (y in y0..y1 + 1) c.drawLine((x0 - camX) * s, (y - camY) * s, (x1 + 1 - camX) * s, (y - camY) * s, stroke)
        }

        // Conduits.
        for (y in y0..y1) for (x in x0..x1) {
            if (!m.conduit[m.idx(x, y)]) continue
            val sx = (x - camX) * s; val sy = (y - camY) * s
            stroke.color = 0xFFD28F3A.toInt(); stroke.strokeWidth = max(2f, s * 0.1f)
            c.drawLine(sx + s * 0.5f, sy + s * 0.5f, sx + s * 0.5f, sy + s * 0.5f, stroke)
            for (d in 0 until 4) {
                val nx = x + io.github.teamomuito.colony.sim.GameMap.DX4[d]; val ny = y + io.github.teamomuito.colony.sim.GameMap.DY4[d]
                if (m.inB(nx, ny) && m.conduit[m.idx(nx, ny)]) c.drawLine(sx + s * 0.5f, sy + s * 0.5f, sx + s * (0.5f + 0.5f * io.github.teamomuito.colony.sim.GameMap.DX4[d]), sy + s * (0.5f + 0.5f * io.github.teamomuito.colony.sim.GameMap.DY4[d]), stroke)
            }
        }

        // Plants, buildings, designations.
        for (y in y0..y1) for (x in x0..x1) {
            val i = m.idx(x, y)
            val sx = (x - camX) * s
            val sy = (y - camY) * s
            val pl = m.plant[i]
            if (pl != null) sprites.plant(c, pl.type, pl.growth, pl.mature, x, y, sx, sy, s, frame)
            val b = m.building[i]?.takeIf { it.x == x && it.y == y }
            if (b != null) {
                val a = if (b.built) 255 else 110
                if (!b.def.isFloor && !b.def.isDoor && b.def != BuildDef.WOOD_WALL && b.def != BuildDef.STONE_WALL && b.def != BuildDef.STEEL_WALL && b.def != BuildDef.CONDUIT && b.def != BuildDef.TRAP_SPIKE && b.def != BuildDef.TRAP_DEADFALL && a > 200) sprites.furnitureShadow(c, sx, sy, s)
                drawBig(c, m, b, sx, sy, s, a)
                if (!b.built && s >= 26f) {
                    var done = 0; var tot = 0
                    for (k in b.def.cost.indices) { done += b.delivered[k]; tot += b.def.cost[k].second }
                    if (tot > 0) { text.textSize = s * 0.22f; text.color = Color.WHITE; c.drawText("$done/$tot", sx + s / 2, sy + s * 0.9f, text) }
                }
                if (b.built && b.hp < b.def.hp * 0.99f && b.def.hp > 10f) {
                    fill.color = 0xFFCC3333.toInt(); c.drawRect(sx, sy + s - 3, sx + s * (b.hp / b.def.hp), sy + s, fill)
                }
                if (b.forbidden) { fill.color = 0x88B02020.toInt(); c.drawRect(sx, sy, sx + s * b.fw, sy + s * b.fh, fill) }
                if (b.built && b.def.consumesPower && !b.powered && s >= 22f) {
                    text.textSize = s * 0.3f; text.color = 0xFFFFD27A.toInt(); c.drawText("⚡", sx + s * 0.78f, sy + s * 0.3f, text)
                }
            }
            when (m.desig[i].toInt()) {
                Desig.MINE -> {
                    fill.color = 0x66F2C230; c.drawRect(sx, sy, sx + s, sy + s, fill)
                    stroke.color = 0xFFF2C230.toInt(); stroke.strokeWidth = 3f
                    c.drawLine(sx + s * 0.25f, sy + s * 0.25f, sx + s * 0.75f, sy + s * 0.75f, stroke)
                    c.drawLine(sx + s * 0.75f, sy + s * 0.25f, sx + s * 0.25f, sy + s * 0.75f, stroke)
                }
                Desig.CUT, Desig.HARVEST -> {
                    fill.color = 0x4478E060; c.drawRect(sx, sy, sx + s, sy + s, fill)
                    stroke.color = 0xFF78E060.toInt(); stroke.strokeWidth = 3f
                    c.drawRect(sx + s * 0.2f, sy + s * 0.2f, sx + s * 0.8f, sy + s * 0.8f, stroke)
                }
                Desig.DECON -> { fill.color = 0x55E05050; c.drawRect(sx, sy, sx + s, sy + s, fill) }
                Desig.REPAIR -> { fill.color = 0x5550A0E0; c.drawRect(sx, sy, sx + s, sy + s, fill) }
            }
        }

        // Items.
        for (it in m.items.values) {
            if (it.x < x0 || it.x > x1 || it.y < y0 || it.y > y1) continue
            val sx = (it.x - camX) * s
            val sy = (it.y - camY) * s
            if (it.corpseOf != null) { drawCorpse(c, it.corpseRace?.color ?: 0xFF888888.toInt(), it.corpseRace?.isAnimal == true, sx, sy, s, it.rot); continue }
            sprites.item(c, it.type, itemColor(it.type), it.count, sx, sy, s, it.rot, it.x * 31 + it.y)
            if (it.rot > 0.3f && it.type.spoilDays > 0f) { fill.color = Color.argb((it.rot * 130).toInt(), 80, 100, 30); c.drawCircle(sx + s * 0.5f, sy + s * 0.5f, s * 0.3f, fill) }
            if (it.forbidden) { stroke.color = 0xFFD03030.toInt(); stroke.strokeWidth = 3f; c.drawLine(sx + s * 0.2f, sy + s * 0.2f, sx + s * 0.8f, sy + s * 0.8f, stroke) }
            if (s >= 28f && it.type.stack > 1) {
                text.textSize = s * 0.26f; text.color = Color.BLACK
                c.drawText(it.count.toString(), sx + s / 2, sy + s * 0.58f, text)
            }
        }

        // Fires.
        for ((i, f) in m.fires) {
            val x = i % m.w; val y = i / m.w
            if (x < x0 || x > x1 || y < y0 || y > y1) continue
            val sx = (x - camX) * s; val sy = (y - camY) * s
            sprites.fire(c, sx, sy, s, f.intensity, frame, i)
        }

        // Pawns.
        for (p in g.pawns) {
            if (!p.alive) continue
            val px = p.interpX(); val py = p.interpY()
            if (px < x0 - 1 || px > x1 + 1 || py < y0 - 1 || py > y1 + 1) continue
            drawPawn(c, p, (px - camX) * s, (py - camY) * s, s)
        }

        // Shots and blasts.
        for (sh in g.shots) {
            val ax = (sh.x0 + 0.5f - camX) * s; val ay = (sh.y0 + 0.5f - camY) * s
            val bx = (sh.x1 + 0.5f - camX) * s; val by = (sh.y1 + 0.5f - camY) * s
            when (sh.kind) {
                2 -> { // lightning
                    stroke.color = 0xFFFFFFE0.toInt(); stroke.strokeWidth = max(3f, s * 0.12f)
                    var px = ax; var py = (0 - camY) * s
                    for (k in 1..5) { val t = k / 5f; val nx = ax + (bx - ax) * t + sin(k * 3f + frame) * s * 0.5f; val ny = py + (by - py) * (1f / (6 - k)); c.drawLine(px, py, nx, ny, stroke); px = nx; py = ny }
                }
                1 -> { stroke.color = 0xFFFFC857.toInt(); stroke.strokeWidth = max(3f, s * 0.1f); c.drawLine(ax, ay, bx, by, stroke); fill.color = 0xFFFFC857.toInt(); c.drawCircle(bx, by, s * 0.2f, fill) }
                3 -> { stroke.color = 0xFFD9C79A.toInt(); stroke.strokeWidth = max(2f, s * 0.05f); c.drawLine(ax, ay, bx, by, stroke) }
                4 -> { stroke.color = if (sh.hit) 0xFFFF6B6B.toInt() else 0x66FFFFFF; stroke.strokeWidth = max(3f, s * 0.12f); c.drawLine(ax, ay, bx, by, stroke) }
                else -> { stroke.color = if (sh.hit) 0xFFFFE066.toInt() else 0x88CCCCCC.toInt(); stroke.strokeWidth = max(2f, s * 0.05f); c.drawLine(ax, ay, bx, by, stroke) }
            }
        }
        for (bl in g.blasts) {
            val left = (bl.expires - g.tick).coerceIn(0, 14) / 14f
            fill.color = Color.argb((left * 200).toInt(), 255, 170, 60)
            c.drawCircle((bl.x + 0.5f - camX) * s, (bl.y + 0.5f - camY) * s, bl.radius * s * (1.2f - left * 0.4f), fill)
        }

        val at = tool
        if (at is Tool.Area) {
            fill.color = 0x553A7ACC
            for (y in y0..y1) for (x in x0..x1) if (m.areas[at.index][m.idx(x, y)]) c.drawRect((x - camX) * s, (y - camY) * s, (x + 1 - camX) * s, (y + 1 - camY) * s, fill)
        }
        drawLighting(c, g, x0, y0, x1, y1, s)
        drawWeather(c, g)

        // Selection and drag preview.
        val sel = g.pawnById(selectedId)
        if (sel != null && sel.alive) {
            stroke.color = Color.WHITE; stroke.strokeWidth = 3f
            c.drawCircle((sel.interpX() + 0.5f - camX) * s, (sel.interpY() + 0.5f - camY) * s, s * 0.6f, stroke)
        }
        if (selectedCell >= 0) {
            stroke.color = 0xFFFFFFFF.toInt(); stroke.strokeWidth = 3f
            val sx = (m.xOf(selectedCell) - camX) * s; val sy = (m.yOf(selectedCell) - camY) * s
            c.drawRect(sx, sy, sx + s, sy + s, stroke)
        }
        val a0 = areaStart; val a1 = areaEnd
        if (a0 != null && a1 != null) {
            val lx = min(a0[0], a1[0]); val hx = max(a0[0], a1[0])
            val ly = min(a0[1], a1[1]); val hy = max(a0[1], a1[1])
            val tl = tool
            val fwp = if (tl is Tool.Build) (if (buildRot) tl.def.h else tl.def.w) else 1
            val fhp = if (tl is Tool.Build) (if (buildRot) tl.def.w else tl.def.h) else 1
            for (y in ly..hy step fhp) for (x in lx..hx step fwp) {
                if (!m.inB(x, y)) continue
                val ok = when (tl) {
                    is Tool.Build -> g.canBuildAt(tl.def, x, y, buildRot)
                    else -> true
                }
                fill.color = if (ok) 0x5560E0A0 else 0x55E05050
                c.drawRect((x - camX) * s, (y - camY) * s, (x + fwp - camX) * s, (y + fhp - camY) * s, fill)
            }
            stroke.color = Color.WHITE; stroke.strokeWidth = 2f
            c.drawRect((lx - camX) * s, (ly - camY) * s, (hx + 1 - camX) * s, (hy + 1 - camY) * s, stroke)
        }
    }

    private fun drawLighting(c: Canvas, g: Game, x0: Int, y0: Int, x1: Int, y1: Int, s: Float) {
        val dark = 1f - g.daylight()
        val m = g.map
        if (dark <= 0.02f && g.eclipseUntil <= g.tick) {
            // Caves stay dim by day.
        }
        for (y in y0..y1) for (x in x0..x1) {
            val i = m.idx(x, y)
            val l = m.light[i]
            var a = if (m.natRoof[i]) 0.5f else dark * 0.68f
            a = max(0f, a - l * 0.62f)
            if (a < 0.03f) continue
            fill.color = Color.argb((a * 255).toInt(), 6, 10, 38)
            c.drawRect((x - camX) * s, (y - camY) * s, (x + 1 - camX) * s + 1, (y + 1 - camY) * s + 1, fill)
        }
        if (g.toxicFalloutUntil > g.tick) { fill.color = 0x2262A020; c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill) }
    }

    private fun drawWeather(c: Canvas, g: Game) {
        when (g.weather) {
            Weather.RAIN, Weather.THUNDER -> {
                stroke.color = 0x88AFC8E8.toInt(); stroke.strokeWidth = 2f
                for (k in 0 until 90) {
                    val rx = ((k * 977 + frame * 23) % width).toFloat()
                    val ry = ((k * 631 + frame * 41) % height).toFloat()
                    c.drawLine(rx, ry, rx - 6f, ry + 22f, stroke)
                }
                fill.color = 0x22304060; c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
            }
            Weather.SNOW -> {
                fill.color = 0xDDFFFFFF.toInt()
                for (k in 0 until 80) {
                    val rx = ((k * 733 + frame * 4 + sin(frame * 0.03f + k) * 30).toInt() % width).toFloat()
                    val ry = ((k * 521 + frame * 6) % height).toFloat()
                    c.drawCircle(abs(rx), ry, 3f, fill)
                }
            }
            Weather.FOG -> { fill.color = 0x40D8DCE0; c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill) }
            Weather.CLOUDY -> { fill.color = 0x18202028; c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill) }
            else -> {}
        }
    }

    private fun drawPlant(c: Canvas, type: PlantType, growth: Float, mature: Boolean, sx: Float, sy: Float, s: Float) {
        when (type) {
            PlantType.OAK, PlantType.POPLAR, PlantType.PINE, PlantType.PALM -> {
                val gr = (0.35f + 0.65f * growth).coerceAtMost(1f)
                val base = when (type) { PlantType.PINE -> 0xFF22503A.toInt(); PlantType.PALM -> 0xFF4C8A3A.toInt(); PlantType.POPLAR -> 0xFF4C7A32.toInt(); else -> 0xFF2E5E2F.toInt() }
                fill.color = base; c.drawCircle(sx + s / 2, sy + s / 2, s * 0.42f * gr, fill)
                fill.color = (base and 0x00FFFFFF) + 0x20202000 or (0xFF shl 24)
                c.drawCircle(sx + s * 0.42f, sy + s * 0.42f, s * 0.26f * gr, fill)
                if (type == PlantType.PINE) { fill.color = 0xFF2F6A4C.toInt(); c.drawCircle(sx + s * 0.55f, sy + s * 0.45f, s * 0.15f * gr, fill) }
            }
            PlantType.BERRY -> {
                fill.color = 0xFF3F7D3A.toInt(); c.drawCircle(sx + s / 2, sy + s / 2, s * 0.3f, fill)
                fill.color = 0xFFD2384B.toInt()
                c.drawCircle(sx + s * 0.4f, sy + s * 0.45f, s * 0.07f, fill); c.drawCircle(sx + s * 0.6f, sy + s * 0.55f, s * 0.07f, fill)
            }
            PlantType.BRAMBLE -> { fill.color = 0xFF3A4A2A.toInt(); c.drawCircle(sx + s * 0.5f, sy + s * 0.5f, s * 0.32f, fill); stroke.color = 0xFF222A18.toInt(); stroke.strokeWidth = 2f; c.drawLine(sx + s * 0.2f, sy + s * 0.3f, sx + s * 0.8f, sy + s * 0.7f, stroke) }
            PlantType.WILD_HEALROOT -> { fill.color = 0xFF63D18F.toInt(); c.drawCircle(sx + s / 2, sy + s / 2, s * 0.17f, fill); c.drawCircle(sx + s * 0.35f, sy + s * 0.6f, s * 0.1f, fill) }
            else -> {
                val gr = growth
                val col = when (type) {
                    PlantType.COTTON -> if (mature) 0xFFF0F0E8.toInt() else Color.rgb(90 + (gr * 50).toInt(), 150, 80)
                    PlantType.SMOKELEAF -> Color.rgb(60, 110 + (gr * 40).toInt(), 50)
                    PlantType.PSYCHOID -> Color.rgb(130 + (gr * 40).toInt(), 60, 140)
                    PlantType.HEALROOT -> Color.rgb(60, 170 + (gr * 40).toInt(), 110)
                    else -> if (mature) 0xFFD9C441.toInt() else Color.rgb(70 + (gr * 60).toInt(), 150 + (gr * 20).toInt(), 60)
                }
                fill.color = col
                val r = s * (0.12f + 0.3f * gr)
                c.drawCircle(sx + s * 0.3f, sy + s * 0.35f, r * 0.8f, fill)
                c.drawCircle(sx + s * 0.7f, sy + s * 0.4f, r * 0.8f, fill)
                c.drawCircle(sx + s * 0.5f, sy + s * 0.7f, r * 0.8f, fill)
            }
        }
    }

    private fun drawCorpse(c: Canvas, color: Int, animal: Boolean, sx: Float, sy: Float, s: Float, rot: Float) {
        fill.color = Color.argb(230, Color.red(color) / 2, Color.green(color) / 2, Color.blue(color) / 2)
        if (animal) { rect.set(sx + s * 0.15f, sy + s * 0.3f, sx + s * 0.85f, sy + s * 0.7f); c.drawOval(rect, fill) }
        else { rect.set(sx + s * 0.12f, sy + s * 0.38f, sx + s * 0.88f, sy + s * 0.62f); c.drawRoundRect(rect, s * 0.1f, s * 0.1f, fill); c.drawCircle(sx + s * 0.2f, sy + s * 0.5f, s * 0.12f, fill) }
        if (rot > 0.4f) { fill.color = Color.argb((rot * 140).toInt(), 90, 110, 30); c.drawCircle(sx + s / 2, sy + s / 2, s * 0.36f, fill) }
    }

    /** Draws a building over its whole footprint: stretched for furniture, cell by cell on a slab for long workbenches. */
    private fun drawBig(c: Canvas, m: io.github.teamomuito.colony.sim.GameMap, b: io.github.teamomuito.colony.sim.Building, sx: Float, sy: Float, s: Float, alpha: Int) {
        val def = b.def
        val lit = b.lit || (b.powered && def.light > 0f)
        val fw = b.fw; val fh = b.fh
        if (fw == 1 && fh == 1) { drawBuilding(c, m, b.x, b.y, def, sx, sy, s, alpha, lit, b.built, b.fuel > 0f, def.fuelCap); return }
        // Shadow under the whole piece.
        if (alpha > 200) { fill.color = 0x33000000; rect.set(sx + s * 0.1f, sy + s * 0.12f, sx + s * (fw - 0.02f), sy + s * (fh - 0.02f)); c.drawRoundRect(rect, s * 0.12f, s * 0.12f, fill) }
        val perCell = def.workbench && maxOf(fw, fh) >= 3
        if (perCell) {
            fill.color = (sprites.slabColor(def) and 0x00FFFFFF) or (alpha shl 24)
            rect.set(sx + s * 0.04f, sy + s * 0.08f, sx + s * (fw - 0.04f), sy + s * (fh - 0.08f)); c.drawRoundRect(rect, s * 0.1f, s * 0.1f, fill)
            for (iy in 0 until fh) for (ix in 0 until fw) drawBuilding(c, m, b.x, b.y, def, sx + ix * s, sy + iy * s, s, alpha, lit, b.built, b.fuel > 0f, def.fuelCap)
        } else {
            c.save()
            c.translate(sx + s * fw / 2f, sy + s * fh / 2f)
            if (b.rot) c.rotate(90f)
            c.scale(def.w.toFloat(), def.h.toFloat())
            drawBuilding(c, m, b.x, b.y, def, -s / 2f, -s / 2f, s, alpha, lit, b.built, b.fuel > 0f, def.fuelCap)
            c.restore()
        }
    }

    private fun rr(x0: Float, y0: Float, x1: Float, y1: Float, r: Float, color: Int) { fill.color = color; rect.set(x0, y0, x1, y1); canvasRef?.drawRoundRect(rect, r, r, fill) }
    private fun oval(x0: Float, y0: Float, x1: Float, y1: Float, color: Int) { fill.color = color; rect.set(x0, y0, x1, y1); canvasRef?.drawOval(rect, fill) }
    private var canvasRef: Canvas? = null

    private fun drawBuilding(c: Canvas, m: io.github.teamomuito.colony.sim.GameMap, bx: Int, by: Int, def: BuildDef, sx: Float, sy: Float, s: Float, alpha: Int, lit: Boolean, built: Boolean, hasFuel: Boolean, fuelCap: Float) {
        fun col(argb: Int): Int = (argb and 0x00FFFFFF) or (alpha shl 24)
        canvasRef = c
        when (def) {
            BuildDef.WOOD_WALL, BuildDef.STONE_WALL, BuildDef.STEEL_WALL, BuildDef.PLASTEEL_WALL -> sprites.wall(c, m, bx, by, def, sx, sy, s, alpha)
            BuildDef.DOOR, BuildDef.STEEL_DOOR, BuildDef.AUTODOOR -> sprites.door(c, m, bx, by, sx, sy, s, alpha, def)
            BuildDef.SANDBAGS -> { fill.color = col(0xFFBFA878.toInt()); rect.set(sx + s * 0.05f, sy + s * 0.25f, sx + s * 0.95f, sy + s * 0.75f); c.drawRoundRect(rect, s * 0.2f, s * 0.2f, fill) }
            BuildDef.WOOD_FLOOR, BuildDef.STONE_FLOOR, BuildDef.STEEL_FLOOR, BuildDef.CARPET -> sprites.floor(c, def, bx, by, sx, sy, s)
            BuildDef.CONDUIT -> { stroke.color = col(0xFFD28F3A.toInt()); stroke.strokeWidth = max(2f, s * 0.1f); c.drawLine(sx + s * 0.2f, sy + s * 0.5f, sx + s * 0.8f, sy + s * 0.5f, stroke) }
            BuildDef.SLEEPING_SPOT -> { fill.color = col(0xFF8E8168.toInt()); rect.set(sx + s * 0.18f, sy + s * 0.15f, sx + s * 0.82f, sy + s * 0.85f); c.drawRoundRect(rect, s * 0.15f, s * 0.15f, fill) }
            BuildDef.BED, BuildDef.HOSPITAL_BED -> {
                val hosp = def == BuildDef.HOSPITAL_BED
                rr(sx + s * 0.1f, sy + s * 0.04f, sx + s * 0.9f, sy + s * 0.96f, s * 0.08f, col(if (hosp) 0xFF9AA4AE.toInt() else 0xFF6B4A2A.toInt()))
                rr(sx + s * 0.16f, sy + s * 0.1f, sx + s * 0.84f, sy + s * 0.9f, s * 0.06f, col(if (hosp) 0xFFE8EEF2.toInt() else 0xFF5B7BB8.toInt()))
                rr(sx + s * 0.22f, sy + s * 0.14f, sx + s * 0.78f, sy + s * 0.34f, s * 0.07f, col(0xFFF6F6FA.toInt()))
                fill.color = col(if (hosp) 0xFFC7D4DC.toInt() else 0xFF4A6AA4.toInt()); c.drawRect(sx + s * 0.16f, sy + s * 0.5f, sx + s * 0.84f, sy + s * 0.9f, fill)
                stroke.color = col(0x33000000); stroke.strokeWidth = max(1f, s * 0.03f); c.drawLine(sx + s * 0.16f, sy + s * 0.5f, sx + s * 0.84f, sy + s * 0.5f, stroke)
                if (hosp) { fill.color = col(0xFFD03030.toInt()); c.drawRect(sx + s * 0.44f, sy + s * 0.56f, sx + s * 0.56f, sy + s * 0.84f, fill); c.drawRect(sx + s * 0.32f, sy + s * 0.64f, sx + s * 0.68f, sy + s * 0.76f, fill) }
            }
            BuildDef.TABLE, BuildDef.CHESS_TABLE, BuildDef.BILLIARDS -> {
                val felt = def == BuildDef.BILLIARDS
                rr(sx + s * 0.06f, sy + s * 0.14f, sx + s * 0.94f, sy + s * 0.86f, s * 0.07f, col(if (felt) 0xFF5A3A1E.toInt() else 0xFF8B5E34.toInt()))
                if (felt) { fill.color = col(0xFF2F7A4A.toInt()); c.drawRect(sx + s * 0.14f, sy + s * 0.22f, sx + s * 0.86f, sy + s * 0.78f, fill); fill.color = col(0xFFF4F4F0.toInt()); c.drawCircle(sx + s * 0.38f, sy + s * 0.5f, s * 0.05f, fill); fill.color = col(0xFFD03030.toInt()); c.drawCircle(sx + s * 0.62f, sy + s * 0.46f, s * 0.05f, fill) }
                else {
                    stroke.color = col(0x44281808); stroke.strokeWidth = max(1f, s * 0.025f)
                    for (k in 1..3) c.drawLine(sx + s * 0.08f, sy + s * (0.14f + 0.18f * k), sx + s * 0.92f, sy + s * (0.14f + 0.18f * k), stroke)
                    if (def == BuildDef.CHESS_TABLE) { for (qy in 0 until 4) for (qx in 0 until 4) { fill.color = col(if ((qx + qy) % 2 == 0) 0xFFE8E0C8.toInt() else 0xFF3A2A1A.toInt()); c.drawRect(sx + s * (0.3f + 0.1f * qx), sy + s * (0.3f + 0.1f * qy), sx + s * (0.4f + 0.1f * qx), sy + s * (0.4f + 0.1f * qy), fill) } }
                }
            }
            BuildDef.STOOL, BuildDef.CHAIR -> {
                fill.color = col(0xFF9A6A3A.toInt()); c.drawCircle(sx + s / 2, sy + s / 2, s * 0.25f, fill)
                fill.color = col(0xFFB98650.toInt()); c.drawCircle(sx + s * 0.46f, sy + s * 0.46f, s * 0.17f, fill)
                if (def == BuildDef.CHAIR) { fill.color = col(0xFF7A4E26.toInt()); c.drawRect(sx + s * 0.2f, sy + s * 0.22f, sx + s * 0.8f, sy + s * 0.32f, fill) }
            }
            BuildDef.PLANT_POT -> { fill.color = col(0xFF9A5A3A.toInt()); c.drawCircle(sx + s / 2, sy + s * 0.62f, s * 0.2f, fill); fill.color = col(0xFF4C9A3A.toInt()); c.drawCircle(sx + s / 2, sy + s * 0.4f, s * 0.22f, fill) }
            BuildDef.SCULPTURE_SMALL, BuildDef.SCULPTURE_LARGE -> { fill.color = col(0xFFC9C4B8.toInt()); path.reset(); path.moveTo(sx + s * 0.5f, sy + s * 0.1f); path.lineTo(sx + s * 0.8f, sy + s * 0.85f); path.lineTo(sx + s * 0.2f, sy + s * 0.85f); path.close(); c.drawPath(path, fill) }
            BuildDef.TORCH_LAMP, BuildDef.STANDING_LAMP -> {
                fill.color = col(0xFF6B5A44.toInt()); c.drawCircle(sx + s / 2, sy + s / 2, s * 0.12f, fill)
                if (lit) { fill.color = col(0xFFFFE08A.toInt()); c.drawCircle(sx + s / 2, sy + s * 0.4f, s * 0.2f, fill) }
            }
            BuildDef.HORSESHOES -> { stroke.color = col(0xFF7A7A7A.toInt()); stroke.strokeWidth = 3f; c.drawCircle(sx + s * 0.35f, sy + s * 0.5f, s * 0.14f, stroke); c.drawCircle(sx + s * 0.7f, sy + s * 0.5f, s * 0.14f, stroke) }
            BuildDef.TELEVISION -> { fill.color = col(0xFF22252A.toInt()); c.drawRect(sx + s * 0.1f, sy + s * 0.2f, sx + s * 0.9f, sy + s * 0.75f, fill); fill.color = col(if (lit) 0xFF66B8FF.toInt() else 0xFF3A4048.toInt()); c.drawRect(sx + s * 0.18f, sy + s * 0.28f, sx + s * 0.82f, sy + s * 0.65f, fill) }
            BuildDef.CRAFTING_SPOT -> { stroke.color = col(0xFFC9B28A.toInt()); stroke.strokeWidth = 2f; c.drawRect(sx + s * 0.15f, sy + s * 0.15f, sx + s * 0.85f, sy + s * 0.85f, stroke) }
            BuildDef.CAMPFIRE -> {
                for (k in 0 until 8) { val a = k * 0.7854f; fill.color = col(if (k % 2 == 0) 0xFF7A7A7E.toInt() else 0xFF5E5E62.toInt()); c.drawCircle(sx + s / 2 + kotlin.math.cos(a) * s * 0.3f, sy + s / 2 + sin(a) * s * 0.3f, s * 0.09f, fill) }
                stroke.color = col(0xFF4A3220.toInt()); stroke.strokeWidth = max(2f, s * 0.09f); c.drawLine(sx + s * 0.3f, sy + s * 0.62f, sx + s * 0.7f, sy + s * 0.4f, stroke); c.drawLine(sx + s * 0.3f, sy + s * 0.4f, sx + s * 0.7f, sy + s * 0.62f, stroke)
                if (hasFuel && built) sprites.fire(c, sx, sy, s * 0.9f, 0.8f, frame, bx * 7 + by)
            }
            BuildDef.STOVE_FUEL, BuildDef.STOVE_ELEC -> {
                rr(sx + s * 0.05f, sy + s * 0.1f, sx + s * 0.95f, sy + s * 0.9f, s * 0.08f, col(0xFF4A4C52.toInt()))
                rr(sx + s * 0.1f, sy + s * 0.15f, sx + s * 0.9f, sy + s * 0.85f, s * 0.06f, col(0xFF6A6D74.toInt()))
                val burner = if (def == BuildDef.STOVE_ELEC) 0xFF62B0FF.toInt() else 0xFFE0762E.toInt()
                for (k in 0 until 4) { val bx2 = sx + s * (0.32f + 0.36f * (k % 2)); val by2 = sy + s * (0.32f + 0.36f * (k / 2)); fill.color = col(0xFF26272A.toInt()); c.drawCircle(bx2, by2, s * 0.13f, fill); fill.color = col(if (built && (def == BuildDef.STOVE_ELEC || hasFuel)) burner else 0xFF55575C.toInt()); c.drawCircle(bx2, by2, s * 0.07f, fill) }
            }
            BuildDef.BUTCHER_TABLE -> {
                rr(sx + s * 0.05f, sy + s * 0.16f, sx + s * 0.95f, sy + s * 0.84f, s * 0.05f, col(0xFFA88A6A.toInt()))
                rr(sx + s * 0.12f, sy + s * 0.22f, sx + s * 0.88f, sy + s * 0.78f, s * 0.04f, col(0xFFC2A888.toInt()))
                stroke.color = col(0xFFB8BEC6.toInt()); stroke.strokeWidth = max(2f, s * 0.07f); c.drawLine(sx + s * 0.3f, sy + s * 0.65f, sx + s * 0.62f, sy + s * 0.35f, stroke)
                fill.color = col(0xFF8A2A24.toInt()); c.drawCircle(sx + s * 0.7f, sy + s * 0.6f, s * 0.07f, fill)
            }
            BuildDef.TAILOR_BENCH -> {
                rr(sx + s * 0.05f, sy + s * 0.16f, sx + s * 0.95f, sy + s * 0.84f, s * 0.05f, col(0xFF8A6A4A.toInt()))
                fill.color = col(0xFF9A6AA8.toInt()); c.drawRect(sx + s * 0.14f, sy + s * 0.24f, sx + s * 0.58f, sy + s * 0.76f, fill)
                fill.color = col(0xFFE8E4DC.toInt()); c.drawRect(sx + s * 0.64f, sy + s * 0.3f, sx + s * 0.86f, sy + s * 0.5f, fill)
                stroke.color = col(0xFF2A2A2E.toInt()); stroke.strokeWidth = max(1f, s * 0.04f); c.drawLine(sx + s * 0.7f, sy + s * 0.64f, sx + s * 0.8f, sy + s * 0.7f, stroke)
            }
            BuildDef.SMITHY, BuildDef.MACHINING, BuildDef.FAB_BENCH -> {
                rr(sx + s * 0.05f, sy + s * 0.1f, sx + s * 0.95f, sy + s * 0.9f, s * 0.06f, col(0xFF3A3D44.toInt()))
                rr(sx + s * 0.1f, sy + s * 0.15f, sx + s * 0.9f, sy + s * 0.85f, s * 0.05f, col(0xFF565A62.toInt()))
                if (def == BuildDef.SMITHY) { fill.color = col(if (built) 0xFFE0762E.toInt() else 0xFF55575C.toInt()); c.drawRect(sx + s * 0.18f, sy + s * 0.24f, sx + s * 0.5f, sy + s * 0.46f, fill); fill.color = col(0xFF22242A.toInt()); path.reset(); path.moveTo(sx + s * 0.55f, sy + s * 0.62f); path.lineTo(sx + s * 0.85f, sy + s * 0.62f); path.lineTo(sx + s * 0.78f, sy + s * 0.76f); path.lineTo(sx + s * 0.6f, sy + s * 0.76f); path.close(); c.drawPath(path, fill) }
                else { fill.color = col(0xFF62B0FF.toInt()); c.drawRect(sx + s * 0.18f, sy + s * 0.24f, sx + s * 0.5f, sy + s * 0.4f, fill); fill.color = col(0xFFC9CED6.toInt()); c.drawCircle(sx + s * 0.7f, sy + s * 0.64f, s * 0.12f, fill); fill.color = col(0xFF22242A.toInt()); c.drawCircle(sx + s * 0.7f, sy + s * 0.64f, s * 0.05f, fill) }
            }
            BuildDef.STONECUTTER, BuildDef.DRUG_LAB -> { fill.color = col(if (def == BuildDef.DRUG_LAB) 0xFF6AAE88.toInt() else 0xFF8E8E92.toInt()); rect.set(sx + s * 0.06f, sy + s * 0.2f, sx + s * 0.94f, sy + s * 0.8f); c.drawRoundRect(rect, s * 0.06f, s * 0.06f, fill) }
            BuildDef.RESEARCH_BENCH, BuildDef.HI_TECH_BENCH -> {
                rr(sx + s * 0.04f, sy + s * 0.14f, sx + s * 0.96f, sy + s * 0.86f, s * 0.06f, col(if (def == BuildDef.HI_TECH_BENCH) 0xFF2E4A5E.toInt() else 0xFF6B5A44.toInt()))
                fill.color = col(0xFF1E2A32.toInt()); c.drawRect(sx + s * 0.16f, sy + s * 0.2f, sx + s * 0.62f, sy + s * 0.5f, fill)
                fill.color = col(if (built) 0xFF7ADCEA.toInt() else 0xFF3A4A52.toInt()); c.drawRect(sx + s * 0.2f, sy + s * 0.24f, sx + s * 0.58f, sy + s * 0.46f, fill)
                fill.color = col(0xFFD8DCE0.toInt()); c.drawRect(sx + s * 0.2f, sy + s * 0.6f, sx + s * 0.58f, sy + s * 0.76f, fill)
                fill.color = col(0xFFE8E0C8.toInt()); c.drawRect(sx + s * 0.68f, sy + s * 0.3f, sx + s * 0.88f, sy + s * 0.56f, fill)
            }
            BuildDef.HYDROPONICS -> { fill.color = col(0xFF2E5A8A.toInt()); c.drawRect(sx + s * 0.05f, sy + s * 0.05f, sx + s * 0.95f, sy + s * 0.95f, fill); fill.color = col(0xFF3F8A55.toInt()); c.drawRect(sx + s * 0.15f, sy + s * 0.15f, sx + s * 0.85f, sy + s * 0.85f, fill) }
            BuildDef.WOOD_GENERATOR -> { fill.color = col(0xFF6B5A44.toInt()); c.drawRect(sx + s * 0.08f, sy + s * 0.15f, sx + s * 0.92f, sy + s * 0.85f, fill); fill.color = col(if (hasFuel) 0xFFFF9A2E.toInt() else 0xFF3A3A3A.toInt()); c.drawCircle(sx + s / 2, sy + s / 2, s * 0.16f, fill) }
            BuildDef.SOLAR_PANEL -> { fill.color = col(0xFF1E2F55.toInt()); c.drawRect(sx + s * 0.04f, sy + s * 0.04f, sx + s * 0.96f, sy + s * 0.96f, fill); stroke.color = col(0xFF7A9AD0.toInt()); stroke.strokeWidth = 1.5f; c.drawLine(sx + s * 0.5f, sy + s * 0.04f, sx + s * 0.5f, sy + s * 0.96f, stroke); c.drawLine(sx + s * 0.04f, sy + s * 0.5f, sx + s * 0.96f, sy + s * 0.5f, stroke) }
            BuildDef.WIND_TURBINE -> { fill.color = col(0xFFD8DADD.toInt()); c.drawCircle(sx + s / 2, sy + s / 2, s * 0.1f, fill); stroke.color = col(0xFFD8DADD.toInt()); stroke.strokeWidth = 3f; val a = frame * 0.12f; for (k in 0 until 3) { val ang = a + k * 2.094f; c.drawLine(sx + s / 2, sy + s / 2, sx + s / 2 + kotlin.math.cos(ang) * s * 0.42f, sy + s / 2 + sin(ang) * s * 0.42f, stroke) } }
            BuildDef.BATTERY -> { fill.color = col(0xFF4C5F3A.toInt()); c.drawRect(sx + s * 0.12f, sy + s * 0.2f, sx + s * 0.88f, sy + s * 0.85f, fill); fill.color = col(0xFFE8E8E8.toInt()); c.drawRect(sx + s * 0.4f, sy + s * 0.1f, sx + s * 0.6f, sy + s * 0.2f, fill) }
            BuildDef.HEATER -> { fill.color = col(0xFFB04A2E.toInt()); c.drawRect(sx + s * 0.1f, sy + s * 0.2f, sx + s * 0.9f, sy + s * 0.8f, fill) }
            BuildDef.COOLER -> { fill.color = col(0xFF2E7AB0.toInt()); c.drawRect(sx + s * 0.1f, sy + s * 0.2f, sx + s * 0.9f, sy + s * 0.8f, fill) }
            BuildDef.TURRET -> {
                rr(sx + s * 0.1f, sy + s * 0.1f, sx + s * 0.9f, sy + s * 0.9f, s * 0.12f, col(0xFF4A4D55.toInt()))
                fill.color = col(0xFF2C2E34.toInt()); c.drawCircle(sx + s / 2, sy + s / 2, s * 0.3f, fill)
                fill.color = col(0xFF70757E.toInt()); c.drawCircle(sx + s / 2, sy + s / 2, s * 0.22f, fill)
                rr(sx + s * 0.44f, sy + s * 0.02f, sx + s * 0.56f, sy + s * 0.5f, s * 0.03f, col(0xFF22242A.toInt()))
            }
            BuildDef.MORTAR -> { fill.color = col(0xFF4A4F3A.toInt()); c.drawCircle(sx + s / 2, sy + s / 2, s * 0.4f, fill); fill.color = col(0xFF22241A.toInt()); c.drawCircle(sx + s / 2, sy + s / 2, s * 0.16f, fill) }
            BuildDef.TRAP_SPIKE -> { stroke.color = col(0xFFB0B4B8.toInt()); stroke.strokeWidth = 2.5f; for (k in 0 until 4) c.drawLine(sx + s * (0.2f + 0.2f * k), sy + s * 0.8f, sx + s * (0.2f + 0.2f * k), sy + s * 0.3f, stroke) }
            BuildDef.TRAP_DEADFALL -> { fill.color = col(0xFF7A7A7E.toInt()); c.drawRect(sx + s * 0.15f, sy + s * 0.15f, sx + s * 0.85f, sy + s * 0.85f, fill) }
            BuildDef.ARMCHAIR -> {
                rr(sx + s * 0.12f, sy + s * 0.12f, sx + s * 0.88f, sy + s * 0.88f, s * 0.16f, col(0xFF6A4A8A.toInt()))
                rr(sx + s * 0.2f, sy + s * 0.3f, sx + s * 0.8f, sy + s * 0.82f, s * 0.12f, col(0xFF8A6AAA.toInt()))
                rr(sx + s * 0.1f, sy + s * 0.1f, sx + s * 0.9f, sy + s * 0.28f, s * 0.1f, col(0xFF5A3E78.toInt()))
            }
            BuildDef.DRESSER -> {
                rr(sx + s * 0.08f, sy + s * 0.18f, sx + s * 0.92f, sy + s * 0.88f, s * 0.05f, col(0xFF7A5230.toInt()))
                stroke.color = col(0x55201008); stroke.strokeWidth = max(1f, s * 0.03f); c.drawLine(sx + s * 0.1f, sy + s * 0.53f, sx + s * 0.9f, sy + s * 0.53f, stroke); c.drawLine(sx + s * 0.5f, sy + s * 0.2f, sx + s * 0.5f, sy + s * 0.86f, stroke)
                fill.color = col(0xFFE6C878.toInt()); for (k in 0 until 4) c.drawCircle(sx + s * (0.3f + 0.4f * (k % 2)), sy + s * (0.36f + 0.34f * (k / 2)), s * 0.03f, fill)
            }
            BuildDef.END_TABLE -> { rr(sx + s * 0.2f, sy + s * 0.2f, sx + s * 0.8f, sy + s * 0.8f, s * 0.08f, col(0xFF8B5E34.toInt())); fill.color = col(0xFFE8E0C8.toInt()); c.drawCircle(sx + s * 0.5f, sy + s * 0.5f, s * 0.12f, fill) }
            BuildDef.COMMS_CONSOLE -> {
                rr(sx + s * 0.06f, sy + s * 0.14f, sx + s * 0.94f, sy + s * 0.86f, s * 0.06f, col(0xFF3A4048.toInt()))
                fill.color = col(0xFF1E2A32.toInt()); c.drawRect(sx + s * 0.16f, sy + s * 0.22f, sx + s * 0.84f, sy + s * 0.55f, fill)
                fill.color = col(if (built) 0xFF7ADCEA.toInt() else 0xFF3A4A52.toInt()); c.drawRect(sx + s * 0.2f, sy + s * 0.26f, sx + s * 0.8f, sy + s * 0.5f, fill)
                fill.color = col(0xFF9AA2AC.toInt()); c.drawRect(sx + s * 0.2f, sy + s * 0.62f, sx + s * 0.8f, sy + s * 0.76f, fill)
            }
            BuildDef.TRADE_BEACON -> {
                fill.color = col(0xFF4A4E56.toInt()); c.drawCircle(sx + s / 2, sy + s / 2, s * 0.32f, fill)
                stroke.color = col(0xFFE8B04A.toInt()); stroke.strokeWidth = max(2f, s * 0.06f); c.drawCircle(sx + s / 2, sy + s / 2, s * (0.2f + 0.05f * sin(frame * 0.2f)), stroke)
                fill.color = col(0xFFFF6A3A.toInt()); c.drawCircle(sx + s / 2, sy + s / 2, s * 0.07f, fill)
            }
            BuildDef.PASSIVE_COOLER -> { rr(sx + s * 0.1f, sy + s * 0.1f, sx + s * 0.9f, sy + s * 0.9f, s * 0.08f, col(0xFF7A8EA0.toInt())); stroke.color = col(0xFFC8DCEC.toInt()); stroke.strokeWidth = max(1.5f, s * 0.05f); for (k in 1..3) c.drawLine(sx + s * 0.18f, sy + s * (0.1f + 0.2f * k), sx + s * 0.82f, sy + s * (0.1f + 0.2f * k), stroke) }
            BuildDef.SUN_LAMP -> { rr(sx + s * 0.1f, sy + s * 0.1f, sx + s * 0.9f, sy + s * 0.9f, s * 0.1f, col(0xFF4A4E56.toInt())); fill.color = col(if (lit) 0xFFFFF0A0.toInt() else 0xFF8A8A70.toInt()); c.drawCircle(sx + s / 2, sy + s / 2, s * 0.32f, fill); fill.color = col(if (lit) 0xFFFFFFFF.toInt() else 0xFFA0A088.toInt()); c.drawCircle(sx + s / 2, sy + s / 2, s * 0.16f, fill) }
            BuildDef.FOAM_POPPER -> { rr(sx + s * 0.25f, sy + s * 0.25f, sx + s * 0.75f, sy + s * 0.75f, s * 0.08f, col(0xFFB03030.toInt())); fill.color = col(0xFFE8E8E8.toInt()); c.drawCircle(sx + s / 2, sy + s / 2, s * 0.1f, fill) }
            BuildDef.GRAVE -> {
                oval(sx + s * 0.18f, sy + s * 0.1f, sx + s * 0.82f, sy + s * 0.92f, col(0xFF6E5A42.toInt()))
                oval(sx + s * 0.26f, sy + s * 0.18f, sx + s * 0.7f, sy + s * 0.74f, col(0xFF86704F.toInt()))
                rr(sx + s * 0.34f, sy + s * 0.08f, sx + s * 0.66f, sy + s * 0.3f, s * 0.08f, col(0xFF9A9A9E.toInt()))
            }
            BuildDef.SHIP_COMPUTER, BuildDef.SHIP_ENGINE, BuildDef.SHIP_REACTOR, BuildDef.SHIP_CASKET -> {
                fill.color = col(when (def) { BuildDef.SHIP_ENGINE -> 0xFFB8C0CC.toInt(); BuildDef.SHIP_REACTOR -> 0xFF6CE0A8.toInt(); BuildDef.SHIP_CASKET -> 0xFF9AB8E8.toInt(); else -> 0xFFE6E8EE.toInt() })
                rect.set(sx + s * 0.06f, sy + s * 0.06f, sx + s * 0.94f, sy + s * 0.94f); c.drawRoundRect(rect, s * 0.2f, s * 0.2f, fill)
                fill.color = col(0xFF4F82AD.toInt()); c.drawCircle(sx + s / 2, sy + s * 0.5f, s * 0.14f, fill)
            }
            else -> {}
        }
        if (def.fuelCap > 0f && built && s >= 26f && def != BuildDef.CAMPFIRE && def != BuildDef.TORCH_LAMP) {
            // Small fuel marker for fuelled workbenches.
            fill.color = if (hasFuel) 0xFFE0A030.toInt() else 0xFFB03030.toInt()
            c.drawRect(sx + s * 0.1f, sy + s * 0.9f, sx + s * 0.28f, sy + s * 0.98f, fill)
        }
        if (!built) {
            stroke.color = 0xAAFFFFFF.toInt(); stroke.strokeWidth = 2f
            c.drawRect(sx + 2, sy + 2, sx + s - 2, sy + s - 2, stroke)
        }
    }

    private fun drawPawn(c: Canvas, p: Pawn, sx: Float, sy: Float, s: Float) {
        val cx = sx + s / 2
        val cy = sy + s / 2
        var ang = facing[p.id] ?: 0f
        val moving = p.moveCd > 0 && (p.x != p.fromX || p.y != p.fromY)
        if (moving) {
            ang = Math.toDegrees(kotlin.math.atan2((p.y - p.fromY).toDouble(), (p.x - p.fromX).toDouble())).toFloat()
        } else {
            val tid = p.job?.targetPawn ?: -1
            val t = if (tid >= 0) game?.pawnById(tid) else null
            if (t != null && (t.x != p.x || t.y != p.y)) ang = Math.toDegrees(kotlin.math.atan2((t.y - p.y).toDouble(), (t.x - p.x).toDouble())).toFloat()
        }
        facing[p.id] = ang
        if (p.isAnimal) {
            val size = p.race.size.coerceIn(0.5f, 2.2f)
            sprites.animal(c, p, cx, cy, s * p.bodyScale(), ang, moving, frame)
            if (p.downed) {
                stroke.color = 0xCC000000.toInt(); stroke.strokeWidth = 3f
                c.drawLine(cx - s * 0.2f, cy - s * 0.15f, cx + s * 0.2f, cy + s * 0.15f, stroke); c.drawLine(cx - s * 0.2f, cy + s * 0.15f, cx + s * 0.2f, cy - s * 0.15f, stroke)
            }
            if (p.faction == Faction.PLAYER) { stroke.color = 0xFFE8B04A.toInt(); stroke.strokeWidth = 2f; c.drawCircle(cx, cy, s * 0.42f * size.coerceAtMost(1.5f), stroke) }
            if (p.manhunter) { stroke.color = 0xFFD9453B.toInt(); stroke.strokeWidth = 3f; c.drawCircle(cx, cy, s * 0.42f * size.coerceAtMost(1.5f), stroke) }
            if (p.faction == Faction.PLAYER && s >= 30f) { text.textSize = s * 0.22f; text.color = Color.WHITE; text.setShadowLayer(3f, 0f, 0f, Color.BLACK); c.drawText(p.name, cx, cy - s * 0.45f, text); text.clearShadowLayer() }
            if (p.hp < 99f) hpBar(c, cx, cy, s, p)
            return
        }
        val outer = p.apparel.lastOrNull { it.type.apparel?.slot == io.github.teamomuito.colony.sim.ApparelSlot.OUTER }
            ?: p.apparel.firstOrNull { it.type.apparel?.slot == io.github.teamomuito.colony.sim.ApparelSlot.SHIRT }
        val body = if (outer != null) apparelColor(outer.type) else sprites.shade(sprites.skinOf(p), 0.9f)
        if (p.downed || p.isBaby) {
            sprites.downedHuman(c, p, cx, cy, s * p.bodyScale(), body)
        } else {
            val hat = p.apparel.firstOrNull { it.type.apparel?.slot == io.github.teamomuito.colony.sim.ApparelSlot.HEAD }
            val ring = when {
                p.drafted -> 0xFFFFD34D.toInt()
                p.prisoner -> 0xFFE88A30.toInt()
                p.faction == Faction.ENEMY || p.hostileFlag -> 0xFFD9453B.toInt()
                p.faction == Faction.VISITOR -> 0xFF5EA8E8.toInt()
                else -> 0
            }
            val carry = if (p.carryCount > 0) itemColor(p.carryType ?: ItemType.WOOD) else if (p.job?.stack != null) 0xFF777777.toInt() else null
            sprites.human(c, p, cx, cy, s * p.bodyScale(), ang, body, hat?.let { apparelColor(it.type) }, ring, if (ring != 0) max(2.5f, s * 0.07f) else 0f, moving, frame, carry, false)
        }
        if (p.hp < 99f) hpBar(c, cx, cy, s, p)
        if (p.breakUntil > 0) { text.textSize = s * 0.5f; text.color = 0xFFFF6B4A.toInt(); c.drawText("!", cx, cy - s * 0.55f, text) }
        if (s >= 26f && (p.colonist || p.prisoner || p.faction == Faction.VISITOR)) {
            text.textSize = s * 0.24f; text.color = Color.WHITE
            text.setShadowLayer(3f, 0f, 0f, Color.BLACK)
            c.drawText(p.name.substringBefore(' '), cx, cy - s * 0.42f, text)
            text.clearShadowLayer()
        }
    }

    private fun hpBar(c: Canvas, cx: Float, cy: Float, s: Float, p: Pawn) {
        fill.color = 0xFF401010.toInt(); c.drawRect(cx - s * 0.35f, cy + s * 0.38f, cx + s * 0.35f, cy + s * 0.46f, fill)
        fill.color = if (p.hostile) 0xFFE05050.toInt() else 0xFF66D06A.toInt()
        c.drawRect(cx - s * 0.35f, cy + s * 0.38f, cx - s * 0.35f + s * 0.7f * (p.hp / 100f).coerceIn(0f, 1f), cy + s * 0.46f, fill)
        if (p.bleeding > 0.00002f) { fill.color = 0xFFD02030.toInt(); c.drawCircle(cx + s * 0.4f, cy - s * 0.1f, s * 0.07f, fill) }
    }
}
