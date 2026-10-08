package io.github.teamomuito.colony

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import io.github.teamomuito.colony.sim.Faction
import io.github.teamomuito.colony.sim.Game
import io.github.teamomuito.colony.sim.Terrain
import io.github.teamomuito.colony.sim.ZoneKind

/** A whole-map overview. Tap to jump the camera there. */
class OverviewView(context: Context, private val game: Game, private val onPick: (Int, Int) -> Unit) : View(context) {
    private val bmp: Bitmap = Bitmap.createBitmap(game.map.w, game.map.h, Bitmap.Config.ARGB_8888)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val src = Rect(0, 0, game.map.w, game.map.h)
    private val dst = Rect()

    init {
        val m = game.map
        val px = IntArray(m.size)
        for (i in 0 until m.size) {
            var c = when (m.terrain[i]) {
                Terrain.SOIL -> 0xFF6B5A3C.toInt(); Terrain.RICH_SOIL -> 0xFF54452B.toInt(); Terrain.GRAVEL -> 0xFF807D76.toInt()
                Terrain.SAND -> 0xFFC4B482.toInt(); Terrain.MARSH -> 0xFF4C6948.toInt(); Terrain.MUD -> 0xFF4A3D2E.toInt()
                Terrain.ICE -> 0xFFCFE8F2.toInt(); Terrain.WATER_SHALLOW -> 0xFF4F82AD.toInt(); Terrain.WATER_DEEP -> 0xFF2C4D78.toInt()
                Terrain.ROCK -> 0xFF4A4B50.toInt()
            }
            if (m.plant[i]?.type?.isTree == true) c = 0xFF2E5E2F.toInt()
            val z = m.zoneAt(i)
            if (z != null) c = when (z.kind) { ZoneKind.STOCKPILE -> 0xFFB89A2E.toInt(); ZoneKind.GROWING -> 0xFF4C9A4C.toInt(); else -> 0xFF7A7A7A.toInt() }
            if (m.floor[i] != null) c = 0xFF9A8A6A.toInt()
            val b = m.building[i]
            if (b != null && b.built) c = 0xFFD8D0C0.toInt()
            if (m.fires.containsKey(i)) c = 0xFFFF6A00.toInt()
            px[i] = c
        }
        for (p in game.pawns) {
            if (!p.alive) continue
            val col = when {
                p.faction == Faction.PLAYER && !p.isAnimal && !p.prisoner -> 0xFF66FF66.toInt()
                p.hostile -> 0xFFFF4040.toInt()
                p.faction == Faction.VISITOR -> 0xFF5EA8E8.toInt()
                p.faction == Faction.PLAYER -> 0xFFE8B04A.toInt()
                else -> 0xFFB0A070.toInt()
            }
            px[m.idx(p.x, p.y)] = col
        }
        bmp.setPixels(px, 0, m.w, 0, 0, m.w, m.h)
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.BLACK)
        val side = minOf(width, height)
        val left = (width - side) / 2
        val top = (height - side) / 2
        dst.set(left, top, left + side, top + side)
        paint.isFilterBitmap = false
        c.drawBitmap(bmp, src, dst, paint)
        paint.style = Paint.Style.STROKE; paint.color = Color.WHITE; paint.strokeWidth = 3f
        c.drawRect(dst, paint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_UP) {
            val side = minOf(width, height)
            val left = (width - side) / 2
            val top = (height - side) / 2
            val x = ((e.x - left) / side * game.map.w).toInt()
            val y = ((e.y - top) / side * game.map.h).toInt()
            if (x in 0 until game.map.w && y in 0 until game.map.h) onPick(x, y)
        }
        return true
    }
}
