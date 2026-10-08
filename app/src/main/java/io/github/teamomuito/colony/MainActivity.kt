package io.github.teamomuito.colony

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.teamomuito.colony.sim.BuildDef
import io.github.teamomuito.colony.sim.Desig
import io.github.teamomuito.colony.sim.Game
import io.github.teamomuito.colony.sim.ItemType
import io.github.teamomuito.colony.sim.Pawn
import io.github.teamomuito.colony.sim.PlantType
import io.github.teamomuito.colony.sim.Research
import io.github.teamomuito.colony.sim.SaveGame
import io.github.teamomuito.colony.sim.SkillType
import io.github.teamomuito.colony.sim.Terrain
import io.github.teamomuito.colony.sim.WorkType
import io.github.teamomuito.colony.sim.ZoneKind
import java.io.File
import kotlin.math.min

class MainActivity : Activity() {
    private lateinit var view: GameView
    private lateinit var game: Game
    private lateinit var root: FrameLayout

    private lateinit var status: TextView
    private lateinit var resources2: TextView
    private lateinit var banner: TextView
    private lateinit var toolChip: TextView
    private lateinit var tileInfo: TextView
    private lateinit var colonistBar: LinearLayout
    private val speedButtons = ArrayList<TextView>()
    private var chipIds: List<Int> = emptyList()
    private val chips = ArrayList<TextView>()

    private var panel: FrameLayout? = null
    private var panelKind = ""
    private var pawnPanel: LinearLayout? = null
    private var pawnText: TextView? = null
    private var draftButton: TextView? = null
    private var workCells = ArrayList<Triple<TextView, Pawn, WorkType>>()
    private var researchHost: LinearLayout? = null
    private var logText: TextView? = null

    private var speed = 1
    private var acc = 0.0
    private var lastFrameNs = 0L
    private var hudAcc = 0.0
    private var lastLogEntry: io.github.teamomuito.colony.sim.LogEntry? = null
    private var bannerUntil = 0L
    private var overShown = false
    private var lastSavedHour = -1L

    private val speedMult = intArrayOf(0, 1, 3, 6)
    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * density).toInt()

    private val saveFile get() = File(filesDir, "colony.sav")

    // ------------------------------------------------------------------ lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        game = loadOrNew()
        buildUi()
        immersive()
    }

    private fun loadOrNew(): Game {
        try {
            if (saveFile.exists()) return SaveGame.read(saveFile.readBytes()).also { hookAutosave(it) }
        } catch (e: Exception) {
            saveFile.delete()
        }
        return newGame()
    }

    private fun newGame(): Game {
        val g = Game(System.currentTimeMillis())
        g.startNewColony()
        hookAutosave(g)
        return g
    }

    private fun hookAutosave(g: Game) {
        g.autosaveHook = {
            val stamp = g.tick / 6000
            if (stamp != lastSavedHour) { lastSavedHour = stamp; save() }
        }
    }

    private fun save() {
        if (game.gameOver) { saveFile.delete(); return }
        try {
            val tmp = File(filesDir, "colony.sav.tmp")
            tmp.writeBytes(SaveGame.write(game))
            tmp.renameTo(saveFile)
        } catch (_: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        immersive()
        lastFrameNs = 0
        Choreographer.getInstance().postFrameCallback(frame)
    }

    override fun onPause() {
        super.onPause()
        Choreographer.getInstance().removeFrameCallback(frame)
        save()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    @Suppress("DEPRECATION")
    private fun immersive() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            panel != null -> closePanel()
            view.tool !== Tool.Select -> setTool(Tool.Select)
            view.selectedId >= 0 -> select(null)
            else -> super.onBackPressed()
        }
    }

    // ------------------------------------------------------------------ loop
    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val dt = if (lastFrameNs == 0L) 0.0 else (frameTimeNanos - lastFrameNs) / 1e9
            lastFrameNs = frameTimeNanos
            if (speed > 0 && !game.gameOver) {
                acc += min(dt, 0.1) * 30.0 * speedMult[speed]
                var n = acc.toInt()
                acc -= n
                if (n > 60) n = 60
                repeat(n) { game.step() }
            }
            view.invalidate()
            hudAcc += dt
            if (hudAcc > 0.25) { hudAcc = 0.0; refreshHud() }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    // ------------------------------------------------------------------ UI helpers
    private val cText = 0xFFF2E8D5.toInt()
    private val cAccent = 0xFFE8B04A.toInt()
    private val cPanel = 0xEE1E1B17.toInt()
    private val cButton = 0xFF3A332A.toInt()

    private fun bg(color: Int, radius: Int = 10, strokeColor: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color); cornerRadius = dp(radius).toFloat()
            if (strokeColor != 0) setStroke(dp(1), strokeColor)
        }

    private fun label(t: String, size: Float = 13f, color: Int = cText, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = t; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun button(t: String, size: Float = 13f, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = t; textSize = size; setTextColor(cText); gravity = Gravity.CENTER
            background = bg(cButton, 10, 0x33FFFFFF)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { onClick() }
        }

    private fun lp(w: Int, h: Int, gravity: Int = Gravity.NO_GRAVITY, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) =
        FrameLayout.LayoutParams(w, h, gravity).apply { setMargins(dp(l), dp(t), dp(r), dp(b)) }

    private fun lin(w: Int, h: Int, weight: Float = 0f, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) =
        LinearLayout.LayoutParams(w, h, weight).apply { setMargins(dp(l), dp(t), dp(r), dp(b)) }

    // ------------------------------------------------------------------ UI
    private fun buildUi() {
        root = FrameLayout(this)
        view = GameView(this)
        view.game = game
        view.onTileTap = { x, y -> onTileTap(x, y) }
        view.onArea = { x0, y0, x1, y1 -> onArea(x0, y0, x1, y1) }
        root.addView(view, lp(-1, -1))

        // Top HUD.
        val top = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = bg(cPanel, 12); setPadding(dp(10), dp(6), dp(6), dp(6))
        }
        status = label("", 13f, cText, true)
        row1.addView(status, lin(0, -2, 1f))
        for ((idx, s) in listOf("II", "▶", "▶▶", "▶▶▶").withIndex()) {
            val b = button(s, 12f) { speed = idx; refreshSpeed() }
            b.setPadding(dp(10), dp(4), dp(10), dp(4))
            speedButtons.add(b)
            row1.addView(b, lin(-2, -2, 0f, 3, 0, 0, 0))
        }
        top.addView(row1, lin(-1, -2))
        resources2 = label("", 12f).apply { background = bg(cPanel, 10); setPadding(dp(10), dp(4), dp(10), dp(4)) }
        top.addView(resources2, lin(-1, -2, 0f, 0, 4, 0, 0))
        val scroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        colonistBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        scroller.addView(colonistBar)
        top.addView(scroller, lin(-1, -2, 0f, 0, 4, 0, 0))
        banner = label("", 13f, Color.WHITE, true).apply {
            background = bg(0xDD3A2A14.toInt(), 10); setPadding(dp(10), dp(6), dp(10), dp(6)); visibility = View.GONE
        }
        top.addView(banner, lin(-2, -2, 0f, 0, 4, 0, 0))
        root.addView(top, lp(dp(430), -2, Gravity.TOP or Gravity.START, 8, 6, 0, 0))

        // Bottom bar.
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = bg(cPanel, 14); setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        toolChip = button("", 12f) { setTool(Tool.Select) }.apply { visibility = View.GONE; setTextColor(cAccent) }
        bar.addView(toolChip, lin(-2, -2, 0f, 0, 0, 6, 0))
        val items = listOf<Pair<String, () -> Unit>>(
            "Architect" to { togglePanel("architect") },
            "Work" to { togglePanel("work") },
            "Research" to { togglePanel("research") },
            "Log" to { togglePanel("log") },
            "Menu" to { showMenu() },
        )
        for ((t, a) in items) bar.addView(button(t, 13f) { a() }, lin(-2, -2, 0f, 0, 0, 5, 0))
        root.addView(bar, lp(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 0, 0, 6))

        tileInfo = label("", 12f).apply {
            background = bg(cPanel, 10); setPadding(dp(10), dp(6), dp(10), dp(6)); visibility = View.GONE
        }
        root.addView(tileInfo, lp(dp(260), -2, Gravity.BOTTOM or Gravity.START, 8, 0, 0, 56))

        setContentView(root)
        refreshSpeed()
        refreshHud()
    }

    private fun refreshSpeed() {
        for ((i, b) in speedButtons.withIndex()) {
            b.background = bg(if (i == speed) 0xFF7A5A1E.toInt() else cButton, 10, if (i == speed) cAccent else 0x33FFFFFF)
        }
    }

    // ------------------------------------------------------------------ HUD refresh
    private fun refreshHud() {
        val g = game
        val temp = g.outdoorTemp()
        val ev = if (g.tempEventUntil > 0) "  ⚠ ${g.tempEventName}" else ""
        status.text = "${g.dateLabel()}  ·  ${temp.toInt()}°C$ev"
        val m = g.map
        resources2.text = "Wood ${m.countItems(ItemType.WOOD)}  Stone ${m.countItems(ItemType.STONE)}  " +
            "Steel ${m.countItems(ItemType.STEEL)}  Meals ${m.countItems(ItemType.MEAL)}  Raw ${m.countItems(ItemType.RAW_FOOD)}"
        refreshColonistBar()
        refreshBanner()
        refreshPawnPanel()
        when (panelKind) {
            "work" -> for ((tv, p, w) in workCells) tv.text = prioText(p.priority[w.ordinal])
            "research" -> refreshResearch()
            "log" -> refreshLog()
        }
        if (g.gameOver && !overShown) { overShown = true; showGameOver() }
    }

    private fun prioText(v: Int) = if (v == 0) "–" else v.toString()

    private fun refreshColonistBar() {
        val cols = game.colonists
        val ids = cols.map { it.id }
        if (ids != chipIds) {
            colonistBar.removeAllViews(); chips.clear(); chipIds = ids
            for (p in cols) {
                val tv = label("", 11f, Color.WHITE, true).apply { setPadding(dp(8), dp(4), dp(8), dp(4)) }
                tv.setOnClickListener {
                    if (view.selectedId == p.id) view.centerOn(p.x.toFloat(), p.y.toFloat())
                    else { select(p); view.centerOn(p.x.toFloat(), p.y.toFloat()) }
                }
                chips.add(tv)
                colonistBar.addView(tv, lin(-2, -2, 0f, 0, 0, 4, 0))
            }
        }
        for ((i, p) in cols.withIndex()) {
            val tv = chips[i]
            val moodColor = when {
                p.downed -> 0xFF8A2C2C.toInt()
                p.breakUntil > 0 -> 0xFF9A4A1A.toInt()
                p.mood < 0.3f -> 0xFF7A5A1E.toInt()
                else -> 0xFF2F4A2A.toInt()
            }
            tv.background = bg(moodColor, 8, if (p.id == view.selectedId) Color.WHITE else 0x33FFFFFF)
            val state = when {
                p.downed -> "DOWN"
                p.drafted -> "DRAFTED"
                else -> p.job?.type?.label ?: "Idle"
            }
            tv.text = "${p.name.substringBefore(' ')}  ${(p.mood * 100).toInt()}%\n$state"
        }
    }

    private fun refreshBanner() {
        val g = game
        val newest = g.log.lastOrNull()
        if (newest !== lastLogEntry) {
            lastLogEntry = newest
            val last = newest
            if (last != null) {
                banner.text = last.text
                banner.setTextColor(when (last.level) { 3 -> 0xFFFF8A80.toInt(); 2 -> 0xFFFFD27A.toInt(); 1 -> 0xFFA5E6A0.toInt(); else -> Color.WHITE })
                banner.visibility = View.VISIBLE
                bannerUntil = SystemClock.uptimeMillis() + 6000
            }
        }
        if (banner.visibility == View.VISIBLE && SystemClock.uptimeMillis() > bannerUntil) banner.visibility = View.GONE
    }

    // ------------------------------------------------------------------ selection and tools
    private fun select(p: Pawn?) {
        view.selectedId = p?.id ?: -1
        pawnPanel?.let { root.removeView(it) }
        pawnPanel = null; pawnText = null; draftButton = null
        if (p == null) return
        closePanel()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = bg(cPanel, 12); setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        val tv = label("", 11f).apply { typeface = Typeface.MONOSPACE }
        val sc = ScrollView(this).apply { addView(tv) }
        box.addView(sc, lin(-1, 0, 1f))
        if (p.colonist) {
            val d = button("Draft", 13f) {
                val sel = game.pawnById(view.selectedId) ?: return@button
                game.setDrafted(sel, !sel.drafted)
                refreshPawnPanel()
            }
            draftButton = d
            box.addView(d, lin(-1, -2, 0f, 0, 6, 0, 0))
        }
        val close = button("Close", 12f) { select(null) }
        box.addView(close, lin(-1, -2, 0f, 0, 4, 0, 0))
        root.addView(box, lp(dp(250), dp(320), Gravity.END or Gravity.CENTER_VERTICAL, 0, 0, 8, 0))
        pawnPanel = box; pawnText = tv
        refreshPawnPanel()
    }

    private fun bar(v: Float, n: Int = 10): String {
        val f = (v.coerceIn(0f, 1f) * n).toInt()
        return "█".repeat(f) + "░".repeat(n - f)
    }

    private fun refreshPawnPanel() {
        val tv = pawnText ?: return
        val p = game.pawnById(view.selectedId)
        if (p == null || !p.alive) { select(null); return }
        val sb = StringBuilder()
        sb.append(p.name).append(if (p.colonist) "" else "  (hostile)").append('\n')
        if (p.colonist) sb.append("Age ${p.age}  ·  ${p.weapon.label}\n")
        else sb.append(p.weapon.label).append('\n')
        sb.append("Doing: ").append(if (p.downed) "Downed" else if (p.drafted) "Drafted" else p.job?.type?.label ?: "Idle").append('\n')
        sb.append("Health ").append(bar(p.hp / p.maxHp)).append(' ').append(p.hp.toInt()).append('\n')
        if (p.colonist) {
            sb.append("Mood   ").append(bar(p.mood)).append(' ').append((p.mood * 100).toInt()).append("%\n")
            sb.append("Food   ").append(bar(p.food)).append('\n')
            sb.append("Rest   ").append(bar(p.rest)).append('\n')
            sb.append("Temp ${p.temp.toInt()}°C\n")
            if (p.traits.isNotEmpty()) sb.append("Traits: ").append(p.traits.joinToString { it.label }).append('\n')
            if (p.injuries.isNotEmpty()) {
                sb.append("Wounds: ${p.injuries.size}").append(if (p.untended) " (bleeding)" else " (tended)").append('\n')
            }
            sb.append("\nSkills\n")
            for (s in SkillType.entries) {
                val pas = when (p.passion[s.ordinal]) { 2 -> "★★"; 1 -> "★"; else -> "" }
                sb.append(s.label.padEnd(13)).append(p.skill[s.ordinal].toString().padStart(2)).append(' ').append(pas).append('\n')
            }
            val now = game.tick
            val th = p.thoughts.filter { it.expires > now }
            if (th.isNotEmpty()) {
                sb.append("\nThoughts\n")
                for (t in th) sb.append(if (t.mood >= 0) "+" else "").append((t.mood * 100).toInt()).append("% ").append(t.label).append('\n')
            }
        }
        tv.text = sb.toString()
        draftButton?.let {
            it.text = if (p.drafted) "Undraft" else "Draft"
            it.background = bg(if (p.drafted) 0xFF7A5A1E.toInt() else cButton, 10, if (p.drafted) cAccent else 0x33FFFFFF)
        }
    }

    private fun setTool(t: Tool) {
        view.tool = t
        toolChip.visibility = if (t === Tool.Select) View.GONE else View.VISIBLE
        toolChip.text = "${t.label}  ✕"
        tileInfo.visibility = View.GONE
        view.invalidate()
    }

    private fun onTileTap(x: Int, y: Int) {
        val g = game
        if (!g.map.inB(x, y)) return
        closePanel()
        val p = g.pawnAt(x, y)
        val sel = g.pawnById(view.selectedId)
        if (p != null) { select(p); tileInfo.visibility = View.GONE; return }
        if (sel != null && sel.colonist && sel.drafted) {
            g.orderMove(sel, x, y)
            return
        }
        if (sel != null) select(null)
        showTileInfo(x, y)
    }

    private fun showTileInfo(x: Int, y: Int) {
        val g = game
        val m = g.map
        val i = m.idx(x, y)
        val sb = StringBuilder()
        sb.append(m.terrain[i].label)
        if (m.terrain[i] == Terrain.ROCK && m.ore[i]) sb.append(" (steel ore)")
        if (m.terrain[i].fertility > 0f) sb.append("  ·  fertility ${(m.terrain[i].fertility * 100).toInt()}%")
        m.floor[i]?.let { sb.append("\n${it.label}") }
        m.building[i]?.let { b ->
            sb.append("\n${b.def.label}")
            if (!b.built) sb.append(" (blueprint ${b.delivered}/${b.def.count} ${b.def.item.label.lowercase()})")
            else if (b.def == BuildDef.CAMPFIRE) sb.append(if (b.fuel > 0) " (fuel ${b.fuel.toInt()}h)" else " (needs wood)")
            else if (b.def == BuildDef.BED && b.ownerId >= 0) sb.append(" (${g.pawnById(b.ownerId)?.name ?: "owned"})")
        }
        m.plant[i]?.let { pl ->
            sb.append("\n${pl.type.label}")
            if (pl.type.crop) sb.append(" ${(pl.growth * 100).toInt()}% grown")
        }
        m.items[i]?.let { sb.append("\n${it.count} × ${it.type.label}") }
        when (m.zone[i].toInt()) {
            ZoneKind.STOCKPILE -> sb.append("\nStockpile zone")
            ZoneKind.GROWING -> sb.append("\nGrowing zone: ${PlantType.entries[m.zoneCrop[i].toInt()].label}")
        }
        when (m.desig[i].toInt()) {
            Desig.MINE -> sb.append("\nMarked: mine")
            Desig.CUT -> sb.append("\nMarked: cut")
            Desig.DECON -> sb.append("\nMarked: deconstruct")
        }
        if (m.roomIndoorAt(i)) sb.append("\nIndoors, ${g.map.tempAt(i, g.outdoorTemp()).toInt()}°C")
        tileInfo.text = sb.toString()
        tileInfo.visibility = View.VISIBLE
    }

    private fun onArea(x0: Int, y0: Int, x1: Int, y1: Int) {
        val g = game
        val t = view.tool
        if (t === Tool.Select) return
        var n = 0
        for (y in y0..y1) for (x in x0..x1) {
            if (!g.map.inB(x, y)) continue
            val ok = when (t) {
                Tool.Mine -> g.designate(x, y, Desig.MINE)
                Tool.Cut -> g.designate(x, y, Desig.CUT)
                Tool.Deconstruct -> g.designate(x, y, Desig.DECON)
                Tool.CancelOrders -> {
                    g.clearDesignation(x, y)
                    if (g.map.building[g.map.idx(x, y)]?.built == false) g.designate(x, y, Desig.DECON) else true
                }
                Tool.Stockpile -> { g.setZone(x, y, ZoneKind.STOCKPILE); true }
                is Tool.Growing -> { g.setZone(x, y, ZoneKind.GROWING, t.crop); true }
                Tool.ClearZone -> { g.setZone(x, y, ZoneKind.NONE); true }
                is Tool.Build -> g.placeBlueprint(t.def, x, y)
                else -> false
            }
            if (ok) n++
        }
        if (n == 0 && t is Tool.Build) toast("Can't build ${t.def.label.lowercase()} there")
    }

    private fun toast(s: String) {
        banner.text = s
        banner.setTextColor(0xFFFFD27A.toInt())
        banner.visibility = View.VISIBLE
        bannerUntil = SystemClock.uptimeMillis() + 3000
    }

    // ------------------------------------------------------------------ panels
    private fun closePanel() {
        panel?.let { root.removeView(it) }
        panel = null; panelKind = ""
        workCells.clear(); researchHost = null; logText = null
    }

    private fun togglePanel(kind: String) {
        val was = panelKind
        closePanel()
        if (was == kind) return
        tileInfo.visibility = View.GONE
        panelKind = kind
        val wrap = FrameLayout(this).apply { background = bg(cPanel, 14); setPadding(dp(10), dp(8), dp(10), dp(8)) }
        val metrics = resources.displayMetrics
        val h = (metrics.heightPixels * 0.5f).toInt()
        when (kind) {
            "architect" -> buildArchitect(wrap)
            "work" -> buildWork(wrap)
            "research" -> buildResearch(wrap)
            "log" -> buildLog(wrap)
        }
        val height = if (kind == "architect") ViewGroup.LayoutParams.WRAP_CONTENT else h
        root.addView(wrap, lp(min(metrics.widthPixels - dp(40), dp(620)), height, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 0, 0, 56))
        panel = wrap
    }

    private fun buildArchitect(wrap: FrameLayout) {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val categories = linkedMapOf<String, () -> List<Pair<String, () -> Unit>>>(
            "Orders" to {
                listOf(
                    "Mine" to { setTool(Tool.Mine) },
                    "Chop / harvest" to { setTool(Tool.Cut) },
                    "Deconstruct" to { setTool(Tool.Deconstruct) },
                    "Cancel orders" to { setTool(Tool.CancelOrders) },
                )
            },
            "Zones" to {
                listOf(
                    "Stockpile" to { setTool(Tool.Stockpile) },
                    "Grow rice" to { setTool(Tool.Growing(PlantType.RICE)) },
                    "Grow potatoes" to { setTool(Tool.Growing(PlantType.POTATO)) },
                    "Grow corn" to { setTool(Tool.Growing(PlantType.CORN)) },
                    "Remove zone" to { setTool(Tool.ClearZone) },
                )
            },
        )
        for (cat in listOf("Structure", "Furniture", "Production", "Security", "Ship")) {
            categories[cat] = {
                BuildDef.entries.filter { it.category == cat }.map { d ->
                    val locked = d.research != null && d.research !in game.researchDone
                    val txt = if (locked) "${d.label}\n🔒 ${d.research!!.label}" else "${d.label}\n${d.count} ${d.item.label.lowercase()}"
                    txt to { if (locked) toast("Needs research: ${d.research!!.label}") else setTool(Tool.Build(d)) }
                }
            }
        }
        fun showCat(name: String) {
            body.removeAllViews()
            val rowScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for ((t, a) in categories[name]!!.invoke()) {
                row.addView(button(t, 12f) { a(); closePanel() }, lin(-2, -2, 0f, 0, 0, 6, 0))
            }
            rowScroll.addView(row)
            body.addView(rowScroll)
        }
        val tabScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        for (name in categories.keys) {
            tabs.addView(button(name, 12f) { showCat(name) }, lin(-2, -2, 0f, 0, 0, 6, 6))
        }
        tabScroll.addView(tabs)
        col.addView(tabScroll)
        col.addView(body)
        showCat("Orders")
        wrap.addView(col)
    }

    private fun buildWork(wrap: FrameLayout) {
        val cols = game.colonists
        val sv = ScrollView(this)
        val hs = HorizontalScrollView(this)
        val table = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        table.addView(label("Work priorities  (1 = first, 4 = last, – = never)", 12f, cAccent, true))
        val hdr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        hdr.addView(label("", 11f), lin(dp(90), -2))
        for (w in WorkType.entries) hdr.addView(label(w.label.replace(" ", "\n"), 10f, 0xFFB8AD98.toInt()).apply { gravity = Gravity.CENTER }, lin(dp(56), -2))
        table.addView(hdr)
        workCells.clear()
        for (p in cols) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(label(p.name.substringBefore(' '), 12f, cText, true), lin(dp(90), -2))
            for (w in WorkType.entries) {
                val tv = TextView(this).apply {
                    text = prioText(p.priority[w.ordinal]); textSize = 15f; setTextColor(cText); gravity = Gravity.CENTER
                    background = bg(cButton, 8, 0x33FFFFFF)
                    setOnClickListener {
                        val cur = p.priority[w.ordinal]
                        game.setPriority(p, w, if (cur == 0) 1 else if (cur >= 4) 0 else cur + 1)
                        text = prioText(p.priority[w.ordinal])
                    }
                }
                workCells.add(Triple(tv, p, w))
                row.addView(tv, lin(dp(50), dp(40), 0f, 3, 3, 3, 3))
            }
            table.addView(row)
        }
        hs.addView(table)
        sv.addView(hs)
        wrap.addView(sv)
    }

    private fun buildResearch(wrap: FrameLayout) {
        val sv = ScrollView(this)
        val host = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sv.addView(host)
        wrap.addView(sv)
        researchHost = host
        refreshResearch(force = true)
    }

    private var researchSig = ""

    private fun refreshResearch(force: Boolean = false) {
        val host = researchHost ?: return
        val g = game
        val sig = g.researchCurrent?.name + g.researchDone.size + (g.researchProgress[g.researchCurrent] ?: 0f).toInt() / 20
        if (!force && sig == researchSig) return
        researchSig = sig
        host.removeAllViews()
        val hasBench = g.map.building.any { it != null && it.built && it.def == BuildDef.RESEARCH_BENCH }
        host.addView(label(if (hasBench) "Research (colonists with Research work will use the bench)" else "Build a research bench to start researching", 12f, cAccent, true))
        for (r in Research.entries) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val prog = (g.researchProgress[r] ?: 0f) / r.cost
            val state = when {
                r in g.researchDone -> "Done"
                !g.researchAvailable(r) -> "Needs ${r.needs!!.label}"
                g.researchCurrent == r -> "In progress ${(prog * 100).toInt()}%"
                else -> "${(prog * 100).toInt()}%  ·  cost ${r.cost.toInt()}"
            }
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(label(r.label, 13f, cText, true))
            col.addView(label("${r.unlocks}  ·  $state", 11f, 0xFFB8AD98.toInt()))
            row.addView(col, lin(0, -2, 1f))
            if (g.researchAvailable(r)) {
                val active = g.researchCurrent == r
                row.addView(button(if (active) "Stop" else "Start", 12f) {
                    g.startResearch(if (active) null else r); refreshResearch(true)
                })
            }
            host.addView(row, lin(-1, -2, 0f, 0, 6, 0, 0))
        }
    }

    private fun buildLog(wrap: FrameLayout) {
        val sv = ScrollView(this)
        val tv = label("", 12f)
        sv.addView(tv)
        wrap.addView(sv)
        logText = tv
        refreshLog()
    }

    private fun refreshLog() {
        val tv = logText ?: return
        val sb = StringBuilder()
        for (l in game.log.asReversed().take(60)) {
            val day = l.tick / 24000 + 1
            val hour = (l.tick / 1000 % 24).toInt()
            sb.append("Day $day ${hour.toString().padStart(2, '0')}:00  ").append(l.text).append('\n')
        }
        tv.text = sb.toString()
    }

    // ------------------------------------------------------------------ dialogs
    private fun showMenu() {
        val items = ArrayList<String>()
        items.add("Save game")
        items.add("How to play")
        val ship = game.map.building.any { it != null && it.built && it.def == BuildDef.SHIP }
        if (ship) items.add("🚀 Launch the escape ship")
        items.add("New colony…")
        AlertDialog.Builder(this).setTitle("Colony").setItems(items.toTypedArray()) { _, which ->
            when (items[which]) {
                "Save game" -> { save(); toast("Game saved") }
                "How to play" -> showHelp()
                "New colony…" -> AlertDialog.Builder(this).setMessage("Abandon this colony and start over?")
                    .setPositiveButton("Start over") { _, _ -> restart() }.setNegativeButton("Keep playing", null).show()
                else -> AlertDialog.Builder(this).setMessage("Launch the ship and leave the rim for good? This ends the game.")
                    .setPositiveButton("Launch") { _, _ -> if (game.launchShip()) { refreshHud() } }.setNegativeButton("Not yet", null).show()
            }
        }.show()
    }

    private fun restart() {
        saveFile.delete()
        game = newGame()
        view.game = game
        view.selectedId = -1
        view.recenter()
        overShown = false
        lastLogEntry = null
        chipIds = emptyList()
        select(null)
        closePanel()
        setTool(Tool.Select)
    }

    private fun showGameOver() {
        val won = game.won
        AlertDialog.Builder(this)
            .setTitle(if (won) "You escaped!" else "Colony lost")
            .setMessage(
                if (won) "The ship leaves the rim behind. You survived ${game.day} days and beat back ${game.raidsSurvived} raids."
                else "Everyone is gone after ${game.day} days. You beat back ${game.raidsSurvived} raids.",
            )
            .setCancelable(false)
            .setPositiveButton("New colony") { _, _ -> restart() }
            .show()
    }

    private fun showHelp() {
        val msg = """
            Survive on a hostile rimworld and build a ship to leave it.

            • Drag to look around, pinch to zoom.
            • Architect → Orders: mine rock (steel veins are pale dots), chop trees, harvest berries.
            • Colonists haul loot into a Stockpile zone. Make one first!
            • Build walls, doors, beds and tables. Colonists need beds, and food on tables is happier.
            • Grow zones plant crops on soil. Cook raw food at a stove into meals.
            • Work tab: set what each colonist does (1 is first).
            • Tap a colonist, then Draft to fight. Tap the ground to move drafted colonists. Raiders arrive every few days.
            • Research at a bench unlocks steel, gun turrets and finally the escape ship.
            • Keep an eye on moods. Unhappy colonists have mental breaks.
        """.trimIndent()
        AlertDialog.Builder(this).setTitle("How to play").setMessage(msg).setPositiveButton("OK", null).show()
    }
}
