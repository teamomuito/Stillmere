package io.github.teamomuito.colony

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import io.github.teamomuito.colony.sim.Biome
import io.github.teamomuito.colony.sim.Caravan
import io.github.teamomuito.colony.sim.Game
import io.github.teamomuito.colony.sim.Hills
import io.github.teamomuito.colony.sim.ItemType
import io.github.teamomuito.colony.sim.Pawn
import io.github.teamomuito.colony.sim.Settlement
import io.github.teamomuito.colony.sim.hostileTo
import io.github.teamomuito.colony.sim.peaceCost
import io.github.teamomuito.colony.sim.peaceTalks
import io.github.teamomuito.colony.sim.requestAid
import io.github.teamomuito.colony.sim.requestTraders
import io.github.teamomuito.colony.sim.silverInStockpiles
import io.github.teamomuito.colony.sim.standing
import io.github.teamomuito.colony.sim.TICKS_PER_DAY
import io.github.teamomuito.colony.sim.canJoinCaravan
import io.github.teamomuito.colony.sim.capacity
import io.github.teamomuito.colony.sim.caravanBuy
import io.github.teamomuito.colony.sim.caravanBuyPrice
import io.github.teamomuito.colony.sim.caravanDaysLeft
import io.github.teamomuito.colony.sim.caravanSell
import io.github.teamomuito.colony.sim.caravanSellPrice
import io.github.teamomuito.colony.sim.caravanSilver
import io.github.teamomuito.colony.sim.carryCapacity
import io.github.teamomuito.colony.sim.disbandCaravan
import io.github.teamomuito.colony.sim.foodDays
import io.github.teamomuito.colony.sim.formCaravan
import io.github.teamomuito.colony.sim.fulfillRequest
import io.github.teamomuito.colony.sim.giftGoods
import io.github.teamomuito.colony.sim.load
import io.github.teamomuito.colony.sim.mass
import io.github.teamomuito.colony.sim.orderCaravan
import io.github.teamomuito.colony.sim.packableItems
import io.github.teamomuito.colony.sim.refreshSettlement
import io.github.teamomuito.colony.sim.routeDays
import io.github.teamomuito.colony.sim.speedFactor
import io.github.teamomuito.colony.sim.tileName
import kotlin.math.max
import kotlin.math.min

/** Procedurally drawn planet map: biome-textured tiles, roads, settlements and caravans. */
class WorldMapView(context: Context, private val game: Game, private val onPick: (Int) -> Unit) : View(context) {
    var selected = -1
    var route: List<Int> = emptyList()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val w get() = game.world

    private fun hash(x: Int, y: Int, k: Int): Float {
        var h = x * 374761393 + y * 668265263 + k * 2246822519.toInt()
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xffff) / 65535f
    }

    private fun base(b: Biome) = when (b) {
        Biome.TEMPERATE -> 0xFF5C8A44.toInt(); Biome.BOREAL -> 0xFF47694A.toInt(); Biome.TUNDRA -> 0xFFB7C2BE.toInt()
        Biome.DESERT -> 0xFFD3B676.toInt(); Biome.TROPICAL -> 0xFF2F7A3A.toInt(); Biome.ARID -> 0xFFA39B5A.toInt()
    }

    private fun geom(): Triple<Float, Float, Float> {
        val cell = min(width.toFloat() / w.w, height.toFloat() / w.h)
        return Triple(cell, (width - cell * w.w) / 2f, (height - cell * w.h) / 2f)
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(0xFF0F1A24.toInt())
        val (cell, ox, oy) = geom()
        for (y in 0 until w.h) for (x in 0 until w.w) {
            val t = w.tile(x, y)
            val l = ox + x * cell; val tp = oy + y * cell
            p.style = Paint.Style.FILL
            if (w.water[t]) {
                p.color = 0xFF2B5A86.toInt(); c.drawRect(l, tp, l + cell + 1, tp + cell + 1, p)
                p.color = 0x33FFFFFF; p.style = Paint.Style.STROKE; p.strokeWidth = max(1f, cell * 0.06f)
                if (hash(x, y, 1) > 0.5f) c.drawLine(l + cell * 0.2f, tp + cell * 0.4f, l + cell * 0.55f, tp + cell * 0.4f, p)
                if (hash(x, y, 2) > 0.5f) c.drawLine(l + cell * 0.45f, tp + cell * 0.75f, l + cell * 0.85f, tp + cell * 0.75f, p)
                continue
            }
            val b = w.biome[t]
            p.color = base(b); c.drawRect(l, tp, l + cell + 1, tp + cell + 1, p)
            // Texture dots: trees in forests, snow flecks, dunes.
            p.style = Paint.Style.FILL
            when (b) {
                Biome.TEMPERATE, Biome.BOREAL, Biome.TROPICAL -> {
                    p.color = if (b == Biome.TROPICAL) 0xFF1D5A2A.toInt() else if (b == Biome.BOREAL) 0xFF2F4A34.toInt() else 0xFF3D6B30.toInt()
                    repeat(3) { k -> c.drawCircle(l + hash(x, y, 10 + k) * cell, tp + hash(x, y, 20 + k) * cell, cell * 0.14f, p) }
                }
                Biome.TUNDRA -> { p.color = Color.WHITE; repeat(3) { k -> c.drawCircle(l + hash(x, y, 10 + k) * cell, tp + hash(x, y, 20 + k) * cell, cell * 0.08f, p) } }
                Biome.DESERT -> { p.color = 0xFFC2A25E.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = max(1f, cell * 0.07f); c.drawLine(l + cell * 0.1f, tp + cell * 0.6f, l + cell * 0.5f, tp + cell * 0.4f, p); c.drawLine(l + cell * 0.5f, tp + cell * 0.4f, l + cell * 0.9f, tp + cell * 0.65f, p) }
                Biome.ARID -> { p.color = 0xFF7F7A40.toInt(); repeat(2) { k -> c.drawCircle(l + hash(x, y, 10 + k) * cell, tp + hash(x, y, 20 + k) * cell, cell * 0.1f, p) } }
            }
            p.style = Paint.Style.FILL
            when (w.hills[t]) {
                Hills.SMALL, Hills.LARGE -> {
                    val n = if (w.hills[t] == Hills.LARGE) 2 else 1
                    for (k in 0 until n) {
                        val cx = l + cell * (0.3f + 0.4f * k); val base0 = tp + cell * 0.78f
                        val path = Path().apply { moveTo(cx - cell * 0.28f, base0); lineTo(cx, base0 - cell * (0.35f + 0.1f * n)); lineTo(cx + cell * 0.28f, base0); close() }
                        p.color = 0xFF7A6A52.toInt(); c.drawPath(path, p)
                        p.color = 0x44FFFFFF; c.drawLine(cx, base0 - cell * (0.35f + 0.1f * n), cx + cell * 0.28f, base0, p)
                    }
                }
                Hills.MOUNTAIN -> {
                    for (k in 0 until 2) {
                        val cx = l + cell * (0.3f + 0.4f * k); val base0 = tp + cell * 0.9f
                        val top = base0 - cell * (0.7f - 0.1f * k)
                        p.color = 0xFF5C5E66.toInt(); c.drawPath(Path().apply { moveTo(cx - cell * 0.32f, base0); lineTo(cx, top); lineTo(cx + cell * 0.32f, base0); close() }, p)
                        p.color = Color.WHITE; c.drawPath(Path().apply { moveTo(cx - cell * 0.1f, top + cell * 0.18f); lineTo(cx, top); lineTo(cx + cell * 0.1f, top + cell * 0.18f); close() }, p)
                    }
                }
                else -> {}
            }
        }
        // Rivers.
        p.style = Paint.Style.STROKE; p.color = 0xFF4C86B8.toInt(); p.strokeWidth = max(2f, cell * 0.2f); p.strokeCap = Paint.Cap.ROUND
        for (y in 0 until w.h) for (x in 0 until w.w) {
            val t = w.tile(x, y)
            if (!w.river[t]) continue
            val cx0 = ox + (x + 0.5f) * cell; val cy0 = oy + (y + 0.5f) * cell
            var linked = false
            for ((dx, dy) in listOf(1 to 0, 0 to 1, -1 to 0, 0 to -1)) {
                val nx = x + dx; val ny = y + dy
                if (w.inB(nx, ny) && (w.river[w.tile(nx, ny)] || w.water[w.tile(nx, ny)])) { linked = true; if (dx > 0 || dy > 0 || w.water[w.tile(nx, ny)]) c.drawLine(cx0, cy0, ox + (nx + 0.5f) * cell, oy + (ny + 0.5f) * cell, p) }
            }
            if (!linked) c.drawPoint(cx0, cy0, p)
        }
        // Roads.
        p.style = Paint.Style.STROKE; p.color = 0xFFE3CE95.toInt(); p.strokeWidth = max(1.5f, cell * 0.12f); p.strokeCap = Paint.Cap.ROUND
        for (y in 0 until w.h) for (x in 0 until w.w) {
            val t = w.tile(x, y)
            if (!w.road[t]) continue
            for ((dx, dy) in listOf(1 to 0, 0 to 1, 1 to 1, -1 to 1)) {
                val nx = x + dx; val ny = y + dy
                if (w.inB(nx, ny) && w.road[w.tile(nx, ny)]) c.drawLine(ox + (x + 0.5f) * cell, oy + (y + 0.5f) * cell, ox + (nx + 0.5f) * cell, oy + (ny + 0.5f) * cell, p)
            }
        }
        // Route preview.
        if (route.isNotEmpty() && selected >= 0) {
            p.color = Color.WHITE; p.strokeWidth = max(2f, cell * 0.14f)
            p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(cell * 0.3f, cell * 0.25f), 0f)
            val path = Path()
            val start = route.first()
            path.moveTo(ox + (w.x(start) + 0.5f) * cell, oy + (w.y(start) + 0.5f) * cell)
            for (t in route.drop(1)) path.lineTo(ox + (w.x(t) + 0.5f) * cell, oy + (w.y(t) + 0.5f) * cell)
            c.drawPath(path, p)
            p.pathEffect = null
        }
        // Settlements.
        p.style = Paint.Style.FILL
        for (s in game.world.settlements) {
            val cx = ox + (w.x(s.tile) + 0.5f) * cell; val cy = oy + (w.y(s.tile) + 0.5f) * cell; val r = cell * 0.34f
            p.color = Color.BLACK; c.drawRect(cx - r - 1.5f, cy - r - 1.5f, cx + r + 1.5f, cy + r + 1.5f, p)
            p.color = s.faction.color; c.drawRect(cx - r, cy - r * 0.2f, cx + r, cy + r, p)
            c.drawPath(Path().apply { moveTo(cx - r * 1.1f, cy - r * 0.2f); lineTo(cx, cy - r * 1.1f); lineTo(cx + r * 1.1f, cy - r * 0.2f); close() }, p)
            p.color = 0xFF2A2018.toInt(); c.drawRect(cx - r * 0.18f, cy + r * 0.2f, cx + r * 0.18f, cy + r, p)
        }
        // Sites (camps to clear).
        for (st in game.world.sites) {
            val cx = ox + (w.x(st.tile) + 0.5f) * cell; val cy = oy + (w.y(st.tile) + 0.5f) * cell; val r = cell * 0.3f
            p.style = Paint.Style.FILL; p.color = Color.BLACK; c.drawCircle(cx, cy, r + 2, p)
            p.color = 0xFFE05050.toInt(); c.drawCircle(cx, cy, r, p)
            p.color = Color.WHITE; p.strokeWidth = max(2f, cell * 0.08f); p.style = Paint.Style.STROKE
            c.drawLine(cx - r * 0.5f, cy - r * 0.5f, cx + r * 0.5f, cy + r * 0.5f, p); c.drawLine(cx - r * 0.5f, cy + r * 0.5f, cx + r * 0.5f, cy - r * 0.5f, p)
        }
        // Home.
        run {
            val t = w.homeTile
            val cx = ox + (w.x(t) + 0.5f) * cell; val cy = oy + (w.y(t) + 0.5f) * cell
            p.style = Paint.Style.STROKE; p.color = 0xFFFFD54A.toInt(); p.strokeWidth = max(2f, cell * 0.12f)
            c.drawCircle(cx, cy, cell * 0.42f, p)
            p.style = Paint.Style.FILL; c.drawCircle(cx, cy, cell * 0.16f, p)
        }
        // Caravans.
        for (cv in game.caravans) {
            val cx = ox + (w.x(cv.tile) + 0.5f) * cell; val cy = oy + (w.y(cv.tile) + 0.5f) * cell - cell * 0.05f; val r = cell * 0.3f
            p.style = Paint.Style.FILL; p.color = Color.BLACK
            c.drawPath(Path().apply { moveTo(cx, cy - r - 2); lineTo(cx + r + 2, cy); lineTo(cx, cy + r + 2); lineTo(cx - r - 2, cy); close() }, p)
            p.color = 0xFFFF9A2E.toInt()
            c.drawPath(Path().apply { moveTo(cx, cy - r); lineTo(cx + r, cy); lineTo(cx, cy + r); lineTo(cx - r, cy); close() }, p)
        }
        // Selection.
        if (selected >= 0) {
            p.style = Paint.Style.STROKE; p.color = Color.WHITE; p.strokeWidth = max(2f, cell * 0.08f)
            val l = ox + w.x(selected) * cell; val tp = oy + w.y(selected) * cell
            c.drawRect(l, tp, l + cell, tp + cell, p)
        }
        // Tiny labels on settlements when there is space.
        if (cell > 18f) {
            p.style = Paint.Style.FILL; p.textSize = cell * 0.36f; p.textAlign = Paint.Align.CENTER; p.color = Color.WHITE
            p.setShadowLayer(3f, 0f, 0f, Color.BLACK)
            for (s in game.world.settlements) c.drawText(s.name, ox + (w.x(s.tile) + 0.5f) * cell, oy + (w.y(s.tile) + 1.35f) * cell, p)
            p.clearShadowLayer()
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_UP) {
            val (cell, ox, oy) = geom()
            val x = ((e.x - ox) / cell).toInt(); val y = ((e.y - oy) / cell).toInt()
            if (w.inB(x, y)) onPick(w.tile(x, y))
        }
        return true
    }
}

private fun f1(v: Float) = String.format("%.1f", v)

fun Dialogs.worldMap(focus: Caravan? = null) {
    val d = Dialog(a, android.R.style.Theme_Material_Dialog_NoActionBar)
    val box = ui.column()
    box.background = ui.bg(0xFF1E1B17.toInt(), 12, 0x44FFFFFF)
    box.setPadding(ui.dp(8), ui.dp(6), ui.dp(8), ui.dp(8))
    val w = game.world
    var sel = -1
    var active: Caravan? = focus
    val info = ui.column()
    lateinit var map: WorldMapView
    fun render() {
        info.removeAllViews()
        if (sel < 0) {
            info.addView(ui.label("Tap a tile. Orange diamonds are caravans, the gold ring is your colony.", 11.5f, ui.dim))
        } else {
            val s = w.settlementAt(sel)
            val title = if (sel == w.homeTile) "${game.colonyName} (your colony)" else tileNameUi(s, sel)
            info.addView(ui.label(title, 13.5f, ui.accent, true))
            info.addView(ui.label("${w.biome[sel].label} · ${w.hills[sel].label}${if (w.water[sel]) " · water" else ""}${if (w.road[sel]) " · road" else ""} · difficulty ${f1(w.cost(sel))}", 11f, ui.dim))
            if (s != null) {
                val gw = w.goodwill[s.faction.id]
                val st = game.standing(s.faction)
                info.addView(ui.label("${s.faction.label} (${s.faction.kindLabel}) · goodwill $gw (${st.label})${if (s.destroyedUntil > game.tick) " · in ruins" else ""}", 11.5f, if (st == io.github.teamomuito.colony.sim.Standing.HOSTILE) ui.bad else ui.good))
            }
            val site = w.siteAt(sel)
            if (site != null) {
                info.addView(ui.label("${site.name}: clear it for ${site.reward} silver. Strength ${site.strength.toInt()}, expires in ${f1((site.expires - game.tick).toFloat() / TICKS_PER_DAY)} days.", 11.5f, ui.warn))
            }
        }
        val a2 = active
        val from = a2?.tile ?: w.homeTile
        map.selected = sel
        map.route = if (sel >= 0 && w.passable(sel) && sel != from) (w.path(from, sel)?.let { listOf(from) + it } ?: emptyList()) else emptyList()
        val btns = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        if (sel >= 0 && w.passable(sel) && sel != from) {
            val days = game.routeDays(from, sel, a2?.speedFactor() ?: 0.85f)
            val eta = if (days != null) "${f1(days)} days" else "no route"
            if (a2 == null) {
                if (sel != w.homeTile && days != null) btns.addView(ui.button("Form caravan → $eta", 12f) { d.dismiss(); formCaravanDialog(sel) }, ui.lin(-2, -2, 0f, 0, 6, 6, 0))
            } else if (days != null) {
                btns.addView(ui.button("Send ${a2.name} → $eta", 12f) {
                    val err = game.orderCaravan(a2, sel)
                    if (err != null) a.toast(err) else { a.toast("${a2.name} is on its way."); render(); map.invalidate() }
                }, ui.lin(-2, -2, 0f, 0, 6, 6, 0))
            }
        }
        for (cv in game.caravans.filter { it.tile == sel }) btns.addView(ui.button("Manage ${cv.name}", 12f) { d.dismiss(); caravanDialog(cv) }, ui.lin(-2, -2, 0f, 0, 6, 6, 0))
        info.addView(ui.hscroll(btns))
        map.invalidate()
    }
    box.addView(ui.label("World map", 14f, ui.accent, true))
    // Caravan chips.
    val chips = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
    fun renderChips() {
        chips.removeAllViews()
        if (game.caravans.isEmpty()) chips.addView(ui.label("No caravans out. Pick a destination tile and form one.", 11f, ui.dim))
        for (cv in game.caravans) {
            val left = f1(game.caravanDaysLeft(cv))
            chips.addView(ui.button("${cv.name} (${if (cv.route.isEmpty()) "idle" else "$left d"})", 11.5f, selected = active === cv) {
                active = if (active === cv) null else cv
                if (active != null) sel = cv.tile
                renderChips(); render()
            }, ui.lin(-2, -2, 0f, 0, 3, 5, 0))
        }
    }
    renderChips()
    box.addView(ui.hscroll(chips))
    map = WorldMapView(a, game) { t -> sel = t; render() }
    val h = (a.resources.displayMetrics.heightPixels * 0.55f).toInt()
    box.addView(map, ui.lin(-1, h, 0f, 0, 6, 0, 0))
    box.addView(info, ui.lin(-1, -2, 0f, 0, 6, 0, 0))
    val bottom = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
    bottom.addView(ui.button("Factions", 12f) { d.dismiss(); factionsDialog() }, ui.lin(0, -2, 1f, 0, 0, 4, 0))
    bottom.addView(closeRow(d), ui.lin(0, -2, 1f, 4, 0, 0, 0))
    box.addView(bottom, ui.lin(-1, -2, 0f, 0, 6, 0, 0))
    render()
    d.setContentView(box)
    val prev = a.speed
    if (prev > 0) { a.speed = 0; a.refreshSpeed() }
    d.setOnDismissListener { if (a.speed == 0 && prev > 0 && !a.game.gameOver) { a.speed = prev; a.refreshSpeed() } }
    d.window?.setLayout(min(a.resources.displayMetrics.widthPixels - ui.dp(16), ui.dp(900)), ViewGroupWrap)
    d.show()
}

private const val ViewGroupWrap = android.view.ViewGroup.LayoutParams.WRAP_CONTENT

private fun Dialogs.tileNameUi(s: Settlement?, t: Int) = s?.name ?: game.tileName(t)

// ====================================================================== form a caravan
fun Dialogs.formCaravanDialog(dest: Int) {
    val members = HashSet<Pawn>()
    val counts = LinkedHashMap<ItemType, Int>()
    val avail = game.packableItems()
    dialog("Form a caravan → ${game.tileName(dest)}", { body, d ->
        val summary = ui.label("", 12f, ui.accent, true)
        val warn = ui.label("", 11.5f, ui.warn)
        val nameEdit = android.widget.EditText(a).apply { setText("Caravan ${game.nextCaravanId}"); textSize = 13f; setTextColor(ui.text); setSingleLine() }
        fun capKg() = members.sumOf { it.carryCapacity().toDouble() }.toFloat()
        fun loadKg() = counts.entries.sumOf { (it.key.mass() * it.value).toDouble() }.toFloat()
        fun foodD(): Float {
            var nut = 0f
            for ((t, n) in counts) if (t.isFood && t.humanFood) nut += t.nutrition * n
            return nut / (0.7f * max(1, members.count { !it.isAnimal }))
        }
        fun refresh() {
            val days = game.routeDays(game.world.homeTile, dest, 0.85f)
            summary.text = "Load ${f1(loadKg())} / ${f1(capKg())} kg · ${members.count { !it.isAnimal }} people, ${members.count { it.isAnimal }} animals · food for ${f1(foodD())} days · trip ≈ ${days?.let { f1(it) } ?: "?"} days"
            warn.text = when {
                members.none { !it.isAnimal } -> "Choose at least one colonist."
                loadKg() > capKg() -> "Overloaded! Pick animals that can carry, or leave things behind."
                days != null && foodD() < days -> "Not enough food for the trip; they will forage on the way."
                else -> ""
            }
        }
        body.addView(ui.label("Name", 12f, ui.dim)); body.addView(nameEdit)
        body.addView(ui.label("Travellers (35 kg each; muffalo and cows carry 70)", 13f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 10, 0, 2))
        val folks = game.pawns.filter { game.canJoinCaravan(it) }
        if (folks.isEmpty()) body.addView(ui.label("Nobody is free to leave.", 11.5f, ui.dim))
        for (p in folks) {
            val cap = p.carryCapacity()
            lateinit var b: TextView
            b = ui.button("${p.name} (${p.race.label}${if (cap > 0) ", ${cap.toInt()} kg" else ""})", 11.5f) {
                if (!members.remove(p)) members.add(p)
                b.background = ui.bg(if (p in members) 0xFF7A5A1E.toInt() else ui.button, 10, if (p in members) ui.accent else 0x33FFFFFF)
                refresh()
            }
            body.addView(b, ui.lin(-1, -2, 0f, 0, 3, 0, 0))
        }
        body.addView(ui.label("Cargo (from stockpiles)", 13f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 12, 0, 2))
        if (avail.isEmpty()) body.addView(ui.label("Stockpiles are empty.", 11.5f, ui.dim))
        for ((type, have) in avail) {
            val label = ui.label("", 11.5f)
            fun upd() { label.text = "${type.label}: ${counts[type] ?: 0} / $have  (${f1(type.mass())} kg each)" }
            fun add(n: Int) {
                counts[type] = ((counts[type] ?: 0) + n).coerceIn(0, have)
                if (counts[type] == 0) counts.remove(type)
                upd(); refresh()
            }
            upd()
            val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(label, ui.lin(0, -2, 1f))
            for ((t, n) in listOf("−10" to -10, "−1" to -1, "+1" to 1, "+10" to 10)) row.addView(ui.button(t, 11f) { add(n) }, ui.lin(-2, -2, 0f, 0, 0, 3, 0))
            body.addView(row, ui.lin(-1, -2, 0f, 0, 3, 0, 0))
        }
        body.addView(summary, ui.lin(-1, -2, 0f, 0, 12, 0, 0))
        body.addView(warn)
        val go = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        go.addView(ui.button("Depart", 13f) {
            val err = game.formCaravan(members.toList(), counts.toMap(), dest, nameEdit.text.toString().trim())
            if (err != null) a.toast(err) else { d.dismiss(); a.toast("The caravan has left."); a.refreshHud(); worldMap(game.caravans.lastOrNull()) }
        }, ui.lin(-2, -2, 0f, 0, 0, 8, 0))
        go.addView(ui.button("Cancel", 13f) { d.dismiss(); worldMap() })
        body.addView(go, ui.lin(-1, -2, 0f, 0, 10, 0, 0))
        refresh()
    })
}

// ====================================================================== manage a caravan
fun Dialogs.caravanDialog(c: Caravan) {
    dialog(c.name, { body, d ->
        fun render() {
            body.removeAllViews()
            val w = game.world
            val s = w.settlementAt(c.tile)
            val status = when {
                c.resting -> "Resting"
                c.route.isNotEmpty() -> "Travelling to ${game.tileName(c.destination)} (${f1(game.caravanDaysLeft(c))} days left)"
                else -> "Stopped at ${game.tileName(c.tile)}"
            }
            body.addView(ui.label(status, 13f, ui.accent, true))
            body.addView(ui.label("Load ${f1(c.load())} / ${f1(c.capacity())} kg · speed ${(c.speedFactor() * 100).toInt()}% · food for ${f1(c.foodDays())} days · silver ${game.caravanSilver(c)}", 11.5f, ui.dim))
            if (c.lastEvent.isNotEmpty()) body.addView(ui.label("Last event: ${c.lastEvent}", 11f, ui.warn))
            body.addView(ui.label("Travellers", 13f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 10, 0, 2))
            for (p in c.members) {
                val hurt = p.injuries.count { !it.scar }
                val st = when { !p.alive -> "dead"; p.downed -> "downed"; hurt > 0 -> "$hurt wounds"; else -> "healthy" }
                body.addView(ui.label("${p.name} (${p.race.label}) · $st · food ${(p.food * 100).toInt()}% · rest ${(p.rest * 100).toInt()}%", 11.5f, if (p.downed) ui.bad else ui.text))
            }
            body.addView(ui.label("Cargo", 13f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 10, 0, 2))
            if (c.inventory.isEmpty()) body.addView(ui.label("Empty.", 11.5f, ui.dim))
            for ((t, n) in c.inventory) body.addView(ui.label("${t.label} ×$n  (${f1(t.mass() * n)} kg)", 11.5f))
            val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            if (s != null && s.faction.trades && !game.hostileTo(s.faction) && c.tile == s.tile && c.route.isEmpty())
                row.addView(ui.button("Trade at ${s.name}", 12f) { d.dismiss(); caravanTrade(c, s) }, ui.lin(-2, -2, 0f, 0, 0, 6, 0))
            if (c.tile != w.homeTile) row.addView(ui.button("Come home", 12f) {
                val err = game.orderCaravan(c, w.homeTile)
                if (err != null) a.toast(err) else { a.toast("Heading home."); render() }
            }, ui.lin(-2, -2, 0f, 0, 0, 6, 0))
            row.addView(ui.button("On map", 12f) { d.dismiss(); worldMap(c) }, ui.lin(-2, -2, 0f, 0, 0, 6, 0))
            row.addView(ui.button("Abandon", 12f) {
                AlertDialog.Builder(a).setMessage("Abandon ${c.name}? Its people and cargo are lost for good.")
                    .setPositiveButton("Abandon") { _, _ -> game.disbandCaravan(c); d.dismiss(); a.refreshHud() }.setNegativeButton("Keep", null).show()
            })
            body.addView(ui.hscroll(row), ui.lin(-1, -2, 0f, 0, 10, 0, 0))
            body.addView(closeRow(d), ui.lin(-1, -2, 0f, 0, 8, 0, 0))
        }
        render()
    })
}

// ====================================================================== trade at a settlement
fun Dialogs.caravanTrade(c: Caravan, s: Settlement) {
    game.refreshSettlement(s)
    dialog("Trade at ${s.name}", { body, d ->
        fun render() {
            val y = (body.parent as? android.widget.ScrollView)?.scrollY ?: 0
            body.removeAllViews()
            body.addView(ui.label("Caravan silver: ${game.caravanSilver(c)}   ·   ${s.name}'s silver: ${s.silver}   ·   load ${f1(c.load())}/${f1(c.capacity())} kg", 12f, ui.accent, true))
            val req = s.request
            if (req != null && game.tick <= req.expires) {
                val have = c.inventory[req.type] ?: 0
                val days = f1((req.expires - game.tick).toFloat() / TICKS_PER_DAY)
                body.addView(ui.label("Request: ${req.count} × ${req.type.label} for ${req.reward} silver (+goodwill), $days days left (you carry $have)", 11.5f, ui.warn), ui.lin(-2, -2, 0f, 0, 8, 0, 0))
                if (have >= req.count) body.addView(ui.button("Deliver", 12f) { game.fulfillRequest(c, s); render() }, ui.lin(-2, -2, 0f, 0, 3, 0, 0))
            }
            body.addView(ui.label("Sell", 13f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 10, 0, 2))
            val mine = c.inventory.entries.filter { it.key != ItemType.SILVER }
            if (mine.isEmpty()) body.addView(ui.label("Nothing to sell.", 11.5f, ui.dim))
            for ((type, n) in mine) {
                val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                row.addView(ui.label("${type.label} ×$n  (${f1(game.caravanSellPrice(c, type))} each)", 11.5f), ui.lin(0, -2, 1f))
                row.addView(ui.button("1", 11f) { game.caravanSell(c, s, type, 1); render() }, ui.lin(-2, -2, 0f, 0, 0, 3, 0))
                row.addView(ui.button("10", 11f) { game.caravanSell(c, s, type, 10); render() }, ui.lin(-2, -2, 0f, 0, 0, 3, 0))
                row.addView(ui.button("All", 11f) { game.caravanSell(c, s, type, n); render() }, ui.lin(-2, -2, 0f, 0, 0, 3, 0))
                row.addView(ui.button("Gift", 11f) { val g = game.giftGoods(c, s, type, n.coerceAtMost(10)); a.toast("Goodwill +$g"); render() })
                body.addView(row, ui.lin(-1, -2, 0f, 0, 3, 0, 0))
            }
            body.addView(ui.label("Buy", 13f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 12, 0, 2))
            if (s.stock.isEmpty()) body.addView(ui.label("Nothing left.", 11.5f, ui.dim))
            for ((type, n) in s.stock.entries.sortedBy { it.key.cat.ordinal }) {
                val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                row.addView(ui.label("${type.label} ×$n  (${f1(game.caravanBuyPrice(c, type))} each, ${f1(type.mass())} kg)", 11.5f), ui.lin(0, -2, 1f))
                for ((t, k) in listOf("1" to 1, "10" to 10, "All" to n)) row.addView(ui.button(t, 11f) {
                    if (!game.caravanBuy(c, s, type, k)) a.toast("Not enough silver or carrying capacity")
                    render()
                }, ui.lin(-2, -2, 0f, 0, 0, 3, 0))
                body.addView(row, ui.lin(-1, -2, 0f, 0, 3, 0, 0))
            }
            body.addView(ui.label("Buying adds weight; load animals or leave things behind if you run out of room.", 10.5f, ui.dim), ui.lin(-2, -2, 0f, 0, 8, 0, 0))
            body.addView(closeRow(d), ui.lin(-1, -2, 0f, 0, 8, 0, 0))
            (body.parent as? android.widget.ScrollView)?.post { (body.parent as? android.widget.ScrollView)?.scrollTo(0, y) }
        }
        render()
    })
}

// ====================================================================== factions and diplomacy
fun Dialogs.factionsDialog() {
    dialog("Factions", { body, d ->
        fun render() {
            body.removeAllViews()
            val w = game.world
            body.addView(ui.label("Silver in stockpiles: ${game.silverInStockpiles()}", 12f, ui.accent, true))
            for (f in w.factions) {
                val st = game.standing(f)
                val col = if (st == io.github.teamomuito.colony.sim.Standing.HOSTILE) ui.bad else if (st == io.github.teamomuito.colony.sim.Standing.WARY) ui.warn else ui.good
                body.addView(ui.label("${f.name} · ${f.kindLabel}", 13f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 10, 0, 0))
                body.addView(ui.label("Goodwill ${w.goodwill[f.id]} · ${st.label} · ${w.settlements.count { it.faction.id == f.id }} settlements", 11.5f, col))
                val rel = w.factions.filter { it.id != f.id && w.relation[f.id][it.id] != 0 }.joinToString { (if (w.relation[f.id][it.id] > 0) "allied with " else "at war with ") + it.name }
                if (rel.isNotEmpty()) body.addView(ui.label(rel, 10.5f, ui.dim))
                if (!f.permanentEnemy) {
                    val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
                    if (st == io.github.teamomuito.colony.sim.Standing.HOSTILE || st == io.github.teamomuito.colony.sim.Standing.WARY)
                        row.addView(ui.button("Peace talks (${game.peaceCost(f)} silver)", 11f) { val e = game.peaceTalks(f); a.toast(e ?: "Relations improved."); render() }, ui.lin(-2, -2, 0f, 0, 4, 4, 0))
                    if (f.trades && st != io.github.teamomuito.colony.sim.Standing.HOSTILE)
                        row.addView(ui.button("Request traders (150)", 11f) { val e = game.requestTraders(f); a.toast(e ?: "A trade caravan is on its way."); render() }, ui.lin(-2, -2, 0f, 0, 4, 4, 0))
                    if (st == io.github.teamomuito.colony.sim.Standing.ALLY)
                        row.addView(ui.button("Military aid", 11f) { val e = game.requestAid(f); a.toast(e ?: "Soldiers are coming."); render() }, ui.lin(-2, -2, 0f, 0, 4, 4, 0))
                    body.addView(ui.hscroll(row))
                }
            }
            body.addView(closeRow(d), ui.lin(-1, -2, 0f, 0, 10, 0, 0))
        }
        render()
    })
}
