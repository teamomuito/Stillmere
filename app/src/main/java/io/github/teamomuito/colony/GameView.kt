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
import io.github.teamomuito.colony.sim.Game
import io.github.teamomuito.colony.sim.ItemType
import io.github.teamomuito.colony.sim.Pawn
import io.github.teamomuito.colony.sim.PlantType
import io.github.teamomuito.colony.sim.Terrain
import io.github.teamomuito.colony.sim.ZoneKind
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

sealed class Tool(val label: String, val paints: Boolean) {
    object Select : Tool("Select", false)
    object Mine : Tool("Mine", true)
    object Cut : Tool("Chop / harvest", true)
    object Deconstruct : Tool("Deconstruct", true)
    object CancelOrders : Tool("Cancel orders", true)
    object Stockpile : Tool("Stockpile zone", true)
    class Growing(val crop: PlantType) : Tool("Growing zone: ${crop.label}", true)
    object ClearZone : Tool("Remove zone", true)
    class Build(val def: BuildDef) : Tool("Build: ${def.label}", true)
}

class GameView(context: Context) : View(context) {
    var game: Game? = null
    var tool: Tool = Tool.Select
    var selectedId: Int = -1

    var onTileTap: ((Int, Int) -> Unit)? = null
    var onArea: ((Int, Int, Int, Int) -> Unit)? = null

    private var scale = 40f
    private var camX = 0f
    private var camY = 0f
    private var centered = false

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    private val path = Path()

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
            scale = (scale * d.scaleFactor).coerceIn(14f, 100f)
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
                // Avoid a jump when one finger lifts.
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

    // ------------------------------------------------------------------ drawing
    private fun terrainColor(t: Terrain): Int = when (t) {
        Terrain.SOIL -> 0xFF6B5A3C.toInt()
        Terrain.RICH_SOIL -> 0xFF54452B.toInt()
        Terrain.GRAVEL -> 0xFF807D76.toInt()
        Terrain.SAND -> 0xFFC4B482.toInt()
        Terrain.MARSH -> 0xFF4C6948.toInt()
        Terrain.WATER_SHALLOW -> 0xFF4F82AD.toInt()
        Terrain.WATER_DEEP -> 0xFF2C4D78.toInt()
        Terrain.ROCK -> 0xFF4A4B50.toInt()
    }

    private fun itemColor(t: ItemType): Int = when (t) {
        ItemType.WOOD -> 0xFFA9743C.toInt()
        ItemType.STONE -> 0xFFB9B9BD.toInt()
        ItemType.STEEL -> 0xFF9FC2E0.toInt()
        ItemType.RAW_FOOD -> 0xFF8DCB5A.toInt()
        ItemType.MEAL -> 0xFFF0A23C.toInt()
    }

    fun pawnColor(p: Pawn): Int {
        if (p.hostile) return 0xFFD9453B.toInt()
        val hue = (p.id * 67 % 360).toFloat()
        return Color.HSVToColor(floatArrayOf(hue, 0.45f, 0.95f))
    }

    override fun onDraw(c: Canvas) {
        val g = game ?: return
        val m = g.map
        c.drawColor(0xFF1B1A18.toInt())
        val x0 = max(0, camX.toInt() - 1)
        val y0 = max(0, camY.toInt() - 1)
        val x1 = min(m.w - 1, (camX + width / scale).toInt() + 1)
        val y1 = min(m.h - 1, (camY + height / scale).toInt() + 1)
        val s = scale
        fill.style = Paint.Style.FILL

        for (y in y0..y1) for (x in x0..x1) {
            val i = m.idx(x, y)
            val sx = (x - camX) * s
            val sy = (y - camY) * s
            val t = m.terrain[i]
            fill.color = terrainColor(t)
            c.drawRect(sx, sy, sx + s + 1, sy + s + 1, fill)
            if (t == Terrain.ROCK && m.ore[i]) {
                fill.color = 0xFFC8DDF0.toInt()
                c.drawCircle(sx + s * 0.3f, sy + s * 0.35f, s * 0.09f, fill)
                c.drawCircle(sx + s * 0.65f, sy + s * 0.6f, s * 0.11f, fill)
                c.drawCircle(sx + s * 0.4f, sy + s * 0.75f, s * 0.07f, fill)
            }
            val fl = m.floor[i]
            if (fl != null) {
                fill.color = when (fl) {
                    BuildDef.WOOD_FLOOR -> 0xFFA9824F.toInt()
                    BuildDef.STONE_FLOOR -> 0xFF9A9A9C.toInt()
                    else -> 0xFF8896A2.toInt()
                }
                c.drawRect(sx, sy, sx + s + 1, sy + s + 1, fill)
                stroke.color = 0x22000000; stroke.strokeWidth = 1f
                c.drawRect(sx, sy, sx + s, sy + s, stroke)
            }
            val z = m.zone[i].toInt()
            if (z != 0) {
                fill.color = if (z == ZoneKind.STOCKPILE) 0x55E8C547 else 0x4458C45A
                c.drawRect(sx, sy, sx + s, sy + s, fill)
            }
        }
        // Grid, only when zoomed in.
        if (s >= 30f) {
            stroke.color = 0x14000000; stroke.strokeWidth = 1f
            for (x in x0..x1 + 1) c.drawLine((x - camX) * s, (y0 - camY) * s, (x - camX) * s, (y1 + 1 - camY) * s, stroke)
            for (y in y0..y1 + 1) c.drawLine((x0 - camX) * s, (y - camY) * s, (x1 + 1 - camX) * s, (y - camY) * s, stroke)
        }

        for (y in y0..y1) for (x in x0..x1) {
            val i = m.idx(x, y)
            val sx = (x - camX) * s
            val sy = (y - camY) * s
            // Plants.
            val pl = m.plant[i]
            if (pl != null) {
                when (pl.type) {
                    PlantType.TREE -> {
                        fill.color = 0xFF2E5E2F.toInt(); c.drawCircle(sx + s / 2, sy + s / 2, s * 0.42f, fill)
                        fill.color = 0xFF3E7A3C.toInt(); c.drawCircle(sx + s * 0.42f, sy + s * 0.42f, s * 0.26f, fill)
                    }
                    PlantType.BERRY -> {
                        fill.color = 0xFF3F7D3A.toInt(); c.drawCircle(sx + s / 2, sy + s / 2, s * 0.3f, fill)
                        fill.color = 0xFFD2384B.toInt()
                        c.drawCircle(sx + s * 0.4f, sy + s * 0.45f, s * 0.07f, fill)
                        c.drawCircle(sx + s * 0.6f, sy + s * 0.55f, s * 0.07f, fill)
                    }
                    else -> {
                        val gr = pl.growth
                        fill.color = if (pl.mature) 0xFFD9C441.toInt() else Color.rgb(70 + (gr * 60).toInt(), 150 + (gr * 20).toInt(), 60)
                        val r = s * (0.12f + 0.3f * gr)
                        c.drawCircle(sx + s * 0.3f, sy + s * 0.35f, r * 0.8f, fill)
                        c.drawCircle(sx + s * 0.7f, sy + s * 0.4f, r * 0.8f, fill)
                        c.drawCircle(sx + s * 0.5f, sy + s * 0.7f, r * 0.8f, fill)
                    }
                }
            }
            // Buildings and blueprints.
            val b = m.building[i]
            if (b != null) {
                val a = if (b.built) 255 else 110
                drawBuilding(c, b.def, sx, sy, s, a, b.lit, b.built)
                if (!b.built && b.def.count > 0 && s >= 26f) {
                    text.textSize = s * 0.22f; text.color = Color.WHITE
                    c.drawText("${b.delivered}/${b.def.count}", sx + s / 2, sy + s * 0.9f, text)
                }
                if (b.built && b.hp < b.def.hp * 0.99f && b.def.hp > 10f) {
                    fill.color = 0xFFCC3333.toInt(); c.drawRect(sx, sy + s - 3, sx + s * (b.hp / b.def.hp), sy + s, fill)
                }
            }
            // Designations.
            when (m.desig[i].toInt()) {
                Desig.MINE -> {
                    fill.color = 0x66F2C230; c.drawRect(sx, sy, sx + s, sy + s, fill)
                    stroke.color = 0xFFF2C230.toInt(); stroke.strokeWidth = 3f
                    c.drawLine(sx + s * 0.25f, sy + s * 0.25f, sx + s * 0.75f, sy + s * 0.75f, stroke)
                    c.drawLine(sx + s * 0.75f, sy + s * 0.25f, sx + s * 0.25f, sy + s * 0.75f, stroke)
                }
                Desig.CUT -> {
                    fill.color = 0x4478E060; c.drawRect(sx, sy, sx + s, sy + s, fill)
                    stroke.color = 0xFF78E060.toInt(); stroke.strokeWidth = 3f
                    c.drawRect(sx + s * 0.2f, sy + s * 0.2f, sx + s * 0.8f, sy + s * 0.8f, stroke)
                }
                Desig.DECON -> {
                    fill.color = 0x55E05050; c.drawRect(sx, sy, sx + s, sy + s, fill)
                }
            }
        }

        // Items.
        for (it in m.items.values) {
            if (it.x < x0 || it.x > x1 || it.y < y0 || it.y > y1) continue
            val sx = (it.x - camX) * s
            val sy = (it.y - camY) * s
            fill.color = itemColor(it.type)
            rect.set(sx + s * 0.22f, sy + s * 0.22f, sx + s * 0.78f, sy + s * 0.78f)
            c.drawRoundRect(rect, s * 0.1f, s * 0.1f, fill)
            if (s >= 28f) {
                text.textSize = s * 0.28f; text.color = Color.BLACK
                c.drawText(it.count.toString(), sx + s / 2, sy + s * 0.58f, text)
            }
        }
        // Corpses.
        for (co in g.corpses) {
            if (co.x < x0 || co.x > x1 || co.y < y0 || co.y > y1) continue
            val sx = (co.x - camX) * s
            val sy = (co.y - camY) * s
            stroke.color = 0xFF2A0A0A.toInt(); stroke.strokeWidth = s * 0.1f
            c.drawLine(sx + s * 0.25f, sy + s * 0.25f, sx + s * 0.75f, sy + s * 0.75f, stroke)
            c.drawLine(sx + s * 0.75f, sy + s * 0.25f, sx + s * 0.25f, sy + s * 0.75f, stroke)
        }
        // Pawns.
        for (p in g.pawns) {
            if (!p.alive) continue
            val px = p.interpX(); val py = p.interpY()
            if (px < x0 - 1 || px > x1 + 1 || py < y0 - 1 || py > y1 + 1) continue
            drawPawn(c, g, p, (px - camX) * s, (py - camY) * s, s)
        }
        // Shots.
        for (sh in g.shots) {
            stroke.color = if (sh.hit) 0xFFFFE066.toInt() else 0x88CCCCCC.toInt()
            stroke.strokeWidth = max(2f, s * 0.05f)
            c.drawLine((sh.x0 + 0.5f - camX) * s, (sh.y0 + 0.5f - camY) * s, (sh.x1 + 0.5f - camX) * s, (sh.y1 + 0.5f - camY) * s, stroke)
        }
        // Night.
        val dark = 1f - g.daylight()
        if (dark > 0.01f) {
            fill.color = Color.argb((dark * 140).toInt(), 6, 10, 40)
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
            // Campfires cast a glow.
            for (y in y0..y1) for (x in x0..x1) {
                val b = m.building[m.idx(x, y)] ?: continue
                if (b.lit) {
                    fill.color = Color.argb((dark * 90).toInt(), 255, 170, 60)
                    c.drawCircle((x + 0.5f - camX) * s, (y + 0.5f - camY) * s, s * 3.2f, fill)
                }
            }
        }
        // Selection and drag rectangle.
        val sel = g.pawnById(selectedId)
        if (sel != null && sel.alive) {
            stroke.color = Color.WHITE; stroke.strokeWidth = 3f
            c.drawCircle((sel.interpX() + 0.5f - camX) * s, (sel.interpY() + 0.5f - camY) * s, s * 0.6f, stroke)
        }
        val a0 = areaStart; val a1 = areaEnd
        if (a0 != null && a1 != null) {
            val lx = min(a0[0], a1[0]); val hx = max(a0[0], a1[0])
            val ly = min(a0[1], a1[1]); val hy = max(a0[1], a1[1])
            val tl = tool
            for (y in ly..hy) for (x in lx..hx) {
                if (!m.inB(x, y)) continue
                val ok = when (tl) {
                    is Tool.Build -> g.canBuildAt(tl.def, x, y)
                    else -> true
                }
                fill.color = if (ok) 0x5560E0A0 else 0x55E05050
                c.drawRect((x - camX) * s, (y - camY) * s, (x + 1 - camX) * s, (y + 1 - camY) * s, fill)
            }
            stroke.color = Color.WHITE; stroke.strokeWidth = 2f
            c.drawRect((lx - camX) * s, (ly - camY) * s, (hx + 1 - camX) * s, (hy + 1 - camY) * s, stroke)
        }
    }

    private fun drawBuilding(c: Canvas, def: BuildDef, sx: Float, sy: Float, s: Float, alpha: Int, lit: Boolean, built: Boolean) {
        fun col(argb: Int): Int = (argb and 0x00FFFFFF) or (alpha shl 24)
        when (def) {
            BuildDef.WOOD_WALL, BuildDef.STONE_WALL, BuildDef.STEEL_WALL -> {
                fill.color = col(when (def) { BuildDef.WOOD_WALL -> 0xFF8A6535.toInt(); BuildDef.STONE_WALL -> 0xFF8D8D90.toInt(); else -> 0xFFAEB8C2.toInt() })
                c.drawRect(sx, sy, sx + s + 1, sy + s + 1, fill)
                stroke.color = col(0xFF2A2018.toInt()); stroke.strokeWidth = 2f
                c.drawRect(sx + 1, sy + 1, sx + s, sy + s, stroke)
            }
            BuildDef.DOOR -> {
                fill.color = col(0xFF6B4A22.toInt())
                c.drawRect(sx + s * 0.1f, sy + s * 0.3f, sx + s * 0.9f, sy + s * 0.7f, fill)
                fill.color = col(0xFFE0C070.toInt()); c.drawCircle(sx + s * 0.75f, sy + s * 0.5f, s * 0.05f, fill)
            }
            BuildDef.WOOD_FLOOR, BuildDef.STONE_FLOOR, BuildDef.STEEL_FLOOR -> {
                fill.color = col(0x889A8A6A.toInt()); c.drawRect(sx, sy, sx + s, sy + s, fill)
            }
            BuildDef.BED -> {
                fill.color = col(0xFF5B7BB8.toInt()); rect.set(sx + s * 0.12f, sy + s * 0.08f, sx + s * 0.88f, sy + s * 0.92f)
                c.drawRoundRect(rect, s * 0.1f, s * 0.1f, fill)
                fill.color = col(0xFFE8E8F0.toInt()); c.drawRect(sx + s * 0.2f, sy + s * 0.14f, sx + s * 0.8f, sy + s * 0.34f, fill)
            }
            BuildDef.TABLE -> {
                fill.color = col(0xFF8B5E34.toInt()); rect.set(sx + s * 0.08f, sy + s * 0.18f, sx + s * 0.92f, sy + s * 0.82f)
                c.drawRoundRect(rect, s * 0.08f, s * 0.08f, fill)
            }
            BuildDef.CAMPFIRE -> {
                fill.color = col(0xFF5B4A3A.toInt()); c.drawCircle(sx + s / 2, sy + s / 2, s * 0.3f, fill)
                if (lit) { fill.color = col(0xFFFF9A2E.toInt()); c.drawCircle(sx + s / 2, sy + s / 2, s * 0.18f, fill) }
            }
            BuildDef.STOVE -> {
                fill.color = col(0xFF55575C.toInt()); rect.set(sx + s * 0.06f, sy + s * 0.1f, sx + s * 0.94f, sy + s * 0.9f)
                c.drawRoundRect(rect, s * 0.08f, s * 0.08f, fill)
                fill.color = col(0xFFE0762E.toInt())
                c.drawCircle(sx + s * 0.32f, sy + s * 0.35f, s * 0.12f, fill); c.drawCircle(sx + s * 0.68f, sy + s * 0.35f, s * 0.12f, fill)
            }
            BuildDef.RESEARCH_BENCH -> {
                fill.color = col(0xFF3F7F86.toInt()); rect.set(sx + s * 0.06f, sy + s * 0.18f, sx + s * 0.94f, sy + s * 0.82f)
                c.drawRoundRect(rect, s * 0.08f, s * 0.08f, fill)
                fill.color = col(0xFFBEEAF0.toInt()); c.drawRect(sx + s * 0.3f, sy + s * 0.3f, sx + s * 0.7f, sy + s * 0.5f, fill)
            }
            BuildDef.TURRET -> {
                fill.color = col(0xFF3B3E44.toInt()); c.drawCircle(sx + s / 2, sy + s / 2, s * 0.38f, fill)
                fill.color = col(0xFF9AA0A8.toInt()); c.drawRect(sx + s * 0.45f, sy + s * 0.05f, sx + s * 0.55f, sy + s * 0.5f, fill)
            }
            BuildDef.SHIP -> {
                fill.color = col(0xFFE6E8EE.toInt())
                path.reset(); path.moveTo(sx + s / 2, sy + s * 0.05f); path.lineTo(sx + s * 0.9f, sy + s * 0.92f); path.lineTo(sx + s * 0.1f, sy + s * 0.92f); path.close()
                c.drawPath(path, fill)
                fill.color = col(0xFF4F82AD.toInt()); c.drawCircle(sx + s / 2, sy + s * 0.5f, s * 0.12f, fill)
            }
        }
        if (!built) {
            stroke.color = 0xAAFFFFFF.toInt(); stroke.strokeWidth = 2f
            c.drawRect(sx + 2, sy + 2, sx + s - 2, sy + s - 2, stroke)
        }
    }

    private fun drawPawn(c: Canvas, g: Game, p: Pawn, sx: Float, sy: Float, s: Float) {
        val cx = sx + s / 2
        val cy = sy + s / 2
        if (p.downed) {
            fill.color = pawnColor(p)
            rect.set(cx - s * 0.4f, cy - s * 0.2f, cx + s * 0.4f, cy + s * 0.2f)
            c.drawRoundRect(rect, s * 0.2f, s * 0.2f, fill)
        } else {
            fill.color = 0x55000000; c.drawCircle(cx + s * 0.04f, cy + s * 0.06f, s * 0.32f, fill)
            fill.color = pawnColor(p); c.drawCircle(cx, cy, s * 0.32f, fill)
            stroke.color = if (p.drafted) 0xFFFFD34D.toInt() else 0xCC202020.toInt()
            stroke.strokeWidth = if (p.drafted) 4f else 2f
            c.drawCircle(cx, cy, s * 0.32f, stroke)
            if (p.weapon.ranged) {
                stroke.color = 0xFF222222.toInt(); stroke.strokeWidth = max(2f, s * 0.07f)
                c.drawLine(cx, cy, cx + s * 0.38f, cy - s * 0.1f, stroke)
            }
        }
        if (p.carryCount > 0) {
            fill.color = itemColor(p.carryType ?: ItemType.WOOD)
            c.drawRect(cx - s * 0.1f, cy - s * 0.5f, cx + s * 0.1f, cy - s * 0.3f, fill)
        }
        if (p.hp < p.maxHp - 1f) {
            fill.color = 0xFF401010.toInt(); c.drawRect(cx - s * 0.35f, cy + s * 0.38f, cx + s * 0.35f, cy + s * 0.46f, fill)
            fill.color = if (p.hostile) 0xFFE05050.toInt() else 0xFF66D06A.toInt()
            c.drawRect(cx - s * 0.35f, cy + s * 0.38f, cx - s * 0.35f + s * 0.7f * (p.hp / p.maxHp).coerceIn(0f, 1f), cy + s * 0.46f, fill)
        }
        if (s >= 26f && p.colonist) {
            text.textSize = s * 0.26f; text.color = Color.WHITE
            text.setShadowLayer(3f, 0f, 0f, Color.BLACK)
            c.drawText(p.name.substringBefore(' '), cx, cy - s * 0.42f, text)
            text.clearShadowLayer()
        }
    }
}
