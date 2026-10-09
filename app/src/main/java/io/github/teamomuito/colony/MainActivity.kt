package io.github.teamomuito.colony

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.teamomuito.colony.sim.BuildDef
import io.github.teamomuito.colony.sim.Desig
import io.github.teamomuito.colony.sim.Faction
import io.github.teamomuito.colony.sim.Game
import io.github.teamomuito.colony.sim.ItemCat
import io.github.teamomuito.colony.sim.ItemType
import io.github.teamomuito.colony.sim.LogEntry
import io.github.teamomuito.colony.sim.Pawn
import io.github.teamomuito.colony.sim.Research
import io.github.teamomuito.colony.sim.SaveGame
import io.github.teamomuito.colony.sim.TutorialState
import io.github.teamomuito.colony.sim.acceptRansom
import io.github.teamomuito.colony.sim.beginBattle
import io.github.teamomuito.colony.sim.declineRansom
import io.github.teamomuito.colony.sim.requestRetreat
import io.github.teamomuito.colony.sim.resolveBattle
import io.github.teamomuito.colony.sim.settle
import io.github.teamomuito.colony.sim.Weather
import io.github.teamomuito.colony.sim.ZoneKind
import io.github.teamomuito.colony.sim.TICKS_PER_DAY
import io.github.teamomuito.colony.sim.bedCount
import io.github.teamomuito.colony.sim.totalFoodNutrition
import io.github.teamomuito.colony.sim.trader
import java.io.File
import kotlin.math.min

class MainActivity : Activity() {
    companion object {
        /** Intent extra saying what the main menu asked for: carry on, a new colony, or the tutorial colony. */
        const val EXTRA_ACTION = "action"
        const val ACTION_CONTINUE = "continue"
        const val ACTION_NEW = "new"
        const val ACTION_TUTORIAL = "tutorial"
    }

    lateinit var view: GameView
    lateinit var game: Game
    lateinit var root: FrameLayout
    lateinit var ui: UiKit
    lateinit var panels: Panels
    lateinit var dialogs: Dialogs

    private lateinit var status: TextView
    private lateinit var resources2: TextView
    private lateinit var banner: TextView
    private lateinit var toolChip: TextView
    private lateinit var rotChip: TextView
    private lateinit var materialChip: TextView
    private lateinit var battleChip: TextView
    private lateinit var prefs: Prefs
    private lateinit var tutorial: TutorialState
    private lateinit var tutorialCard: TutorialCard
    lateinit var tileCard: LinearLayout
    private lateinit var tileText: TextView
    private lateinit var tileActions: LinearLayout
    private lateinit var colonistBar: LinearLayout
    private lateinit var alertBar: LinearLayout
    private val speedButtons = ArrayList<TextView>()
    private var chipIds: List<Int> = emptyList()
    private val chips = ArrayList<TextView>()

    var speed = 1
    private var acc = 0.0
    private var lastFrameNs = 0L
    private var hudAcc = 0.0
    private var slowAcc = 0.0
    private var lastLogEntry: LogEntry? = null
    private var bannerUntil = 0L
    private var overShown = false
    private var lastSaved = -1L
    private var started = false

    private val speedMult = intArrayOf(0, 1, 3, 6)
    private val saveFile get() = File(filesDir, "colony.sav")

    // ------------------------------------------------------------------ lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = UiKit(this)
        prefs = Prefs(this)
        // The main menu starts the game with one of these; the default is to carry on from the save.
        val action = intent.getStringExtra(EXTRA_ACTION) ?: ACTION_CONTINUE
        val loaded = if (action == ACTION_CONTINUE) loadSave() else null
        if (loaded == null && action != ACTION_CONTINUE) saveFile.delete()
        game = loaded ?: Game(System.currentTimeMillis()).also { it.startNewColony() }
        tutorial = if (action == ACTION_CONTINUE) prefs.loadTutorial() else TutorialState()
        if (action != ACTION_CONTINUE) prefs.saveTutorial(tutorial)
        if (!prefs.tutorialOn && action != ACTION_TUTORIAL) tutorial.hide()
        hookAutosave(game)
        buildUi()
        immersive()
        refreshBattleChip()
        renderTutorial(force = true)
        when {
            loaded == null && action == ACTION_CONTINUE -> root.post { dialogs.newColony(firstRun = true) }
            loaded == null && action == ACTION_NEW -> root.post { dialogs.newColony(firstRun = false) }
            game.pendingBattle != null -> root.post { beginBattleUi() }
        }
        started = true
    }

    // ------------------------------------------------------------------ tutorial
    /** Moves the tutorial past lessons the colony already satisfies; called with the HUD, a few times a second. */
    private fun tutorialTick() {
        if (tutorial.update(game)) prefs.saveTutorial(tutorial)
        renderTutorial()
    }

    private var shownTutorial = ""
    private fun renderTutorial(force: Boolean = false) {
        if (!::tutorialCard.isInitialized) return
        val key = "${tutorial.active}:${tutorial.index}"
        if (!force && key == shownTutorial) return
        shownTutorial = key
        tutorialCard.render(tutorial)
    }

    fun tutorialNext() { tutorial.next(); tutorial.update(game); prefs.saveTutorial(tutorial); renderTutorial(true) }
    fun tutorialSkipLesson() { tutorial.skipLesson(); tutorial.update(game); prefs.saveTutorial(tutorial); renderTutorial(true) }
    fun tutorialHide() { tutorial.hide(); prefs.saveTutorial(tutorial); renderTutorial(true) }
    fun tutorialActive() = tutorial.active

    fun toggleTutorial() {
        if (tutorial.active) tutorial.hide() else tutorial.show()
        prefs.saveTutorial(tutorial); renderTutorial(true)
    }

    /** Saves the colony and goes back to the main menu. */
    fun backToMenu() {
        save()
        startActivity(android.content.Intent(this, MenuActivity::class.java))
        finish()
    }

    private var errorCount = 0
    fun reportError(e: Throwable) {
        errorCount++
        try {
            File(filesDir, "error.txt").writeText("${e.javaClass.name}: ${e.message}\n" + e.stackTrace.take(25).joinToString("\n") { "  at $it" })
        } catch (_: Throwable) {}
        if (errorCount < 6) toast("Error: ${e.javaClass.simpleName} (see Menu → Last error)")
    }

    fun lastError(): String? = try { File(filesDir, "error.txt").takeIf { it.exists() }?.readText() } catch (_: Throwable) { null }

    private fun loadSave(): Game? {
        try {
            if (saveFile.exists()) return SaveGame.read(saveFile.readBytes())
        } catch (e: Throwable) {
            saveFile.delete()
        }
        return null
    }

    fun hookAutosave(g: Game) {
        g.autosaveHook = {
            val stamp = g.tick / 6000
            if (stamp != lastSaved) { lastSaved = stamp; save() }
        }
    }

    fun save() {
        if (game.gameOver) { saveFile.delete(); return }
        try {
            val tmp = File(filesDir, "colony.sav.tmp")
            tmp.writeBytes(SaveGame.write(game))
            tmp.renameTo(saveFile)
        } catch (_: Throwable) {
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
        if (started) save()
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
            panels.panelKind.isNotEmpty() -> panels.closePanel()
            tileCard.visibility == View.VISIBLE -> { tileCard.visibility = View.GONE; view.selectedCell = -1 }
            view.tool !== Tool.Select -> setTool(Tool.Select)
            view.selectedId >= 0 -> select(null)
            else -> super.onBackPressed()
        }
    }

    // ------------------------------------------------------------------ loop
    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            // A fight asked for from a menu starts even while the game is paused.
            if (game.pendingBattle != null) try { beginBattleUi() } catch (e: Throwable) { reportError(e) }
            val dt = if (lastFrameNs == 0L) 0.0 else (frameTimeNanos - lastFrameNs) / 1e9
            lastFrameNs = frameTimeNanos
            if (speed > 0 && !game.gameOver) {
                acc += min(dt, 0.1) * 30.0 * speedMult[speed]
                var n = acc.toInt()
                acc -= n
                if (n > 70) n = 70
                try {
                    repeat(n) { game.step() }
                    // A caravan was attacked: the battle map takes over from here.
                    if (game.pendingBattle != null) beginBattleUi()
                } catch (e: Throwable) {
                    // Never let a rare simulation bug take the app down; pause and report.
                    speed = 0; refreshSpeed()
                    toast("Simulation error: ${e.javaClass.simpleName}. Paused.")
                }
            }
            view.invalidate()
            hudAcc += dt
            if (hudAcc > 0.25) {
                hudAcc = 0.0
                try { refreshHud() } catch (e: Throwable) { reportError(e) }
            }
            // A decided battle goes back to the world.
            game.battle?.outcome?.let { try { endBattleUi() } catch (e: Throwable) { reportError(e) } }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    // ------------------------------------------------------------------ UI
    private fun buildUi() {
        root = FrameLayout(this)
        view = GameView(this)
        view.game = game
        view.onTileTap = { x, y -> ui.guard { onTileTap(x, y) } }
        view.onArea = { x0, y0, x1, y1 -> ui.guard { onArea(x0, y0, x1, y1) } }
        root.addView(view, ui.fl(-1, -1))
        panels = Panels(this)
        dialogs = Dialogs(this)

        // Top-left HUD.
        val top = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = ui.bg(ui.panel, 12); setPadding(ui.dp(10), ui.dp(5), ui.dp(6), ui.dp(5))
        }
        status = ui.label("", 12f, ui.text, true)
        row1.addView(status, ui.lin(0, -2, 1f))
        for ((idx, s) in listOf("II", "▶", "▶▶", "▶▶▶").withIndex()) {
            val b = ui.button(s, 12f) { speed = idx; refreshSpeed() }
            b.setPadding(ui.dp(9), ui.dp(3), ui.dp(9), ui.dp(3))
            speedButtons.add(b)
            row1.addView(b, ui.lin(-2, -2, 0f, 3, 0, 0, 0))
        }
        top.addView(row1, ui.lin(-1, -2))
        resources2 = ui.label("", 11f).apply { background = ui.bg(ui.panel, 10); setPadding(ui.dp(10), ui.dp(3), ui.dp(10), ui.dp(3)) }
        top.addView(resources2, ui.lin(-1, -2, 0f, 0, 3, 0, 0))
        colonistBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(ui.hscroll(colonistBar), ui.lin(-1, -2, 0f, 0, 3, 0, 0))
        banner = ui.label("", 12f, Color.WHITE, true).apply {
            background = ui.bg(0xDD3A2A14.toInt(), 10); setPadding(ui.dp(10), ui.dp(5), ui.dp(10), ui.dp(5)); visibility = View.GONE
        }
        top.addView(banner, ui.lin(-2, -2, 0f, 0, 3, 0, 0))
        root.addView(top, ui.fl(ui.dp(470), -2, Gravity.TOP or Gravity.START, 6, 5, 0, 0))

        alertBar = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
        root.addView(alertBar, ui.fl(ui.dp(170), -2, Gravity.TOP or Gravity.END, 0, 5, 6, 0))

        // Bottom toolbar.
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = ui.bg(ui.panel, 14); setPadding(ui.dp(6), ui.dp(5), ui.dp(6), ui.dp(5))
        }
        toolChip = ui.button("", 12f) { setTool(Tool.Select) }.apply { visibility = View.GONE; setTextColor(ui.accent) }
        bar.addView(toolChip, ui.lin(-2, -2, 0f, 0, 0, 6, 0))
        rotChip = ui.button("Rotate ⟳", 12f) { view.buildRot = !view.buildRot; view.invalidate() }.apply { visibility = View.GONE }
        bar.addView(rotChip, ui.lin(-2, -2, 0f, 0, 0, 6, 0))
        materialChip = ui.button("", 12f) { cycleMaterial() }.apply { visibility = View.GONE }
        bar.addView(materialChip, ui.lin(-2, -2, 0f, 0, 0, 6, 0))
        battleChip = ui.button("", 12f) { confirmRetreat() }.apply { visibility = View.GONE; setTextColor(ui.bad) }
        bar.addView(battleChip, ui.lin(-2, -2, 0f, 0, 0, 6, 0))
        val items = listOf<Pair<String, () -> Unit>>(
            "Architect" to { panels.toggle("architect") },
            "Work" to { panels.toggle("work") },
            "Schedule" to { panels.toggle("schedule") },
            "Research" to { panels.toggle("research") },
            "People" to { panels.toggle("people") },
            "Animals" to { panels.toggle("animals") },
            "Map" to { dialogs.overview() },
            "World" to { dialogs.worldMap() },
            "Trade" to { dialogs.trade() },
            "Log" to { panels.toggle("log") },
            "Menu" to { dialogs.menu() },
        )
        for ((t, a) in items) bar.addView(ui.button(t, 12f) { a() }, ui.lin(-2, -2, 0f, 0, 0, 4, 0))
        root.addView(ui.hscroll(bar), ui.fl(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 0, 0, 5))
        tutorialCard = TutorialCard(this)
        val cardWidth = minOf(resources.displayMetrics.widthPixels - ui.dp(24), ui.dp(520))
        root.addView(tutorialCard, FrameLayout.LayoutParams(cardWidth, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = ui.dp(64) })

        tileCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = ui.bg(ui.panel, 10)
            setPadding(ui.dp(10), ui.dp(6), ui.dp(10), ui.dp(6)); visibility = View.GONE
        }
        tileText = ui.label("", 11f)
        tileActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tileCard.addView(tileText)
        tileCard.addView(ui.hscroll(tileActions), ui.lin(-1, -2, 0f, 0, 4, 0, 0))
        root.addView(tileCard, ui.fl(ui.dp(290), -2, Gravity.BOTTOM or Gravity.START, 6, 0, 0, 52))

        setContentView(root)
        refreshSpeed()
        refreshHud()
    }

    fun refreshSpeed() {
        for ((i, b) in speedButtons.withIndex()) {
            b.background = ui.bg(if (i == speed) 0xFF7A5A1E.toInt() else ui.button, 10, if (i == speed) ui.accent else 0x33FFFFFF)
        }
    }

    // ------------------------------------------------------------------ HUD refresh
    fun refreshHud() {
        tutorialTick()
        val g = game
        val temp = g.outdoorTemp()
        val ev = if (g.tempEventUntil > 0) "  ⚠ ${g.tempEventName}" else ""
        val wx = when (g.weather) { Weather.CLEAR -> "☀"; Weather.CLOUDY -> "☁"; Weather.RAIN -> "🌧"; Weather.FOG -> "🌫"; Weather.SNOW -> "❄"; Weather.THUNDER -> "⛈" }
        status.text = "${g.dateLabel()}  ·  ${temp.toInt()}°C $wx$ev"
        val m = g.map
        val food = (g.totalFoodNutrition() / (0.7f * g.humansOnSide.size.coerceAtLeast(1))).let { String.format("%.1f", it) }
        resources2.text = "Food ${food}d  Wood ${m.countItems(ItemType.WOOD)}  Stone ${m.countItems(ItemType.STONE)}  Steel ${m.countItems(ItemType.STEEL)}  " +
            "Silver ${m.countItems(ItemType.SILVER)}  Comp ${m.countItems(ItemType.COMPONENT)}  Med ${m.countItems { it.cat == ItemCat.MEDICINE && it.potency > 0f }}"
        refreshColonistBar()
        refreshAlerts()
        refreshBanner()
        panels.refresh()
        if (g.gameOver && !overShown) { overShown = true; dialogs.gameOver() }
    }

    private fun refreshColonistBar() {
        val cols = game.humansOnSide.filter { !it.prisoner }
        val ids = cols.map { it.id }
        if (ids != chipIds) {
            colonistBar.removeAllViews(); chips.clear(); chipIds = ids
            for (p in cols) {
                val tv = ui.label("", 10.5f, Color.WHITE, true).apply { setPadding(ui.dp(7), ui.dp(3), ui.dp(7), ui.dp(3)) }
                tv.setOnClickListener {
                    if (view.selectedId == p.id) view.centerOn(p.x.toFloat(), p.y.toFloat())
                    else { select(p); view.centerOn(p.x.toFloat(), p.y.toFloat()) }
                }
                chips.add(tv)
                colonistBar.addView(tv, ui.lin(-2, -2, 0f, 0, 0, 4, 0))
            }
        }
        for ((i, p) in cols.withIndex()) {
            if (i >= chips.size) break
            val tv = chips[i]
            val c = when {
                p.downed -> 0xFF8A2C2C.toInt()
                p.breakUntil > 0 -> 0xFF9A4A1A.toInt()
                p.mood < 0.3f -> 0xFF7A5A1E.toInt()
                else -> 0xFF2F4A2A.toInt()
            }
            tv.background = ui.bg(c, 8, if (p.id == view.selectedId) Color.WHITE else 0x33FFFFFF)
            val state = when {
                p.downed -> "DOWN"
                p.breakUntil > 0 -> "BREAK"
                p.drafted -> "DRAFTED"
                else -> p.job?.type?.label ?: "Idle"
            }
            tv.text = "${p.name.substringBefore(' ')}  ${(p.mood * 100).toInt()}%\n$state"
        }
    }

    /** An alert on the colony bar. [offerId] is set for a ransom offer, which opens its dialog instead of centring the view. */
    private class Alert(val text: String, val level: Int, val x: Int = -1, val y: Int = -1, val offerId: Int = -1)

    private fun computeAlerts(): List<Alert> {
        val g = game
        val out = ArrayList<Alert>()
        val cols = g.colonists
        if (g.raidActive) { val r = g.hostiles.firstOrNull(); out += Alert("Raiders!", 3, r?.x ?: -1, r?.y ?: -1) }
        if (g.map.fires.isNotEmpty()) { val f = g.map.fires.keys.first(); out += Alert("Fire! (${g.map.fires.size})", 3, f % g.map.w, f / g.map.w) }
        val starving = cols.firstOrNull { it.food < 0.1f }
        if (starving != null) out += Alert("${starving.name.substringBefore(' ')} is starving", 3, starving.x, starving.y)
        else if (g.totalFoodNutrition() < 0.7f * cols.size.coerceAtLeast(1)) out += Alert("Low food", 2)
        val down = cols.firstOrNull { it.downed }
        if (down != null) out += Alert("${down.name.substringBefore(' ')} is down", 3, down.x, down.y)
        val bleeding = cols.firstOrNull { it.bleeding > 0.00002f && !it.downed }
        if (bleeding != null) out += Alert("${bleeding.name.substringBefore(' ')} is bleeding", 3, bleeding.x, bleeding.y)
        val br = cols.firstOrNull { it.breakUntil > 0 }
        if (br != null) out += Alert("${br.name.substringBefore(' ')}: mental break", 3, br.x, br.y)
        val sick = cols.firstOrNull { p -> p.hediffs.any { it.kind.category == 0 && it.severity > 0.4f } }
        if (sick != null) out += Alert("${sick.name.substringBefore(' ')} is very sick", 2, sick.x, sick.y)
        val cold = cols.firstOrNull { it.temp < it.comfyMin() - 8f }
        if (cold != null) out += Alert("${cold.name.substringBefore(' ')} is freezing", 2, cold.x, cold.y)
        val hot = cols.firstOrNull { it.temp > it.comfyMax() + 8f }
        if (hot != null) out += Alert("${hot.name.substringBefore(' ')} is overheating", 2, hot.x, hot.y)
        if (g.bedCount() < cols.size) out += Alert("Need ${cols.size - g.bedCount()} more bed(s)", 1)
        if (g.map.zones.values.none { it.kind == ZoneKind.STOCKPILE }) out += Alert("No stockpile zone", 1)
        if (g.researchCurrent == null && g.map.buildings().any { it != null && it.built && it.def == BuildDef.RESEARCH_BENCH }) out += Alert("No research selected", 1)
        if (g.power.nets > 0 && g.power.consumed > g.power.produced + 1f && g.power.stored <= 0f) out += Alert("Power shortage", 2)
        if (g.solarFlareUntil > g.tick) out += Alert("Solar flare", 2)
        if (g.toxicFalloutUntil > g.tick) out += Alert("Toxic fallout", 3)
        val idle = cols.count { it.job?.type == io.github.teamomuito.colony.sim.JobType.IDLE && !it.downed && it.priority.any { x -> x in 1..2 } }
        if (idle >= 2 && g.hour in 7..20) out += Alert("$idle colonists idle", 0)
        if (g.trader() != null) { val t = g.pawnById(g.trader()!!.pawnId); out += Alert("Trader here", 1, t?.x ?: -1, t?.y ?: -1) }
        val esc = g.prisoners.firstOrNull { it.escaping }
        if (esc != null) out += Alert("Prisoner escaping!", 3, esc.x, esc.y)
        for (o in g.ransomOffers) out += Alert("Ransom ${o.prisonerName}: ${o.price} silver", 2, offerId = o.id)
        val crops = g.map.plant.count { it?.type?.crop == true }
        if (crops > 0 && g.outdoorTemp() < 2f) out += Alert("Crops may freeze", 2)
        return out.sortedByDescending { it.level }.take(7)
    }

    private fun showRansomOffer(offerId: Int) {
        val o = game.ransomOffers.firstOrNull { it.id == offerId } ?: return refreshAlerts()
        val faction = game.world.factions.getOrNull(o.factionId)?.name ?: "A faction"
        val days = ((o.expires - game.tick).coerceAtLeast(0L) / TICKS_PER_DAY.toLong()).toInt()
        AlertDialog.Builder(this)
            .setTitle("Ransom offer")
            .setMessage("$faction will pay ${o.price} silver for ${o.prisonerName}, a prisoner in your colony. " +
                "The offer ends in ${if (days <= 0) "less than a day" else "$days day(s)"}. Declining annoys them a little.")
            .setPositiveButton("Accept") { _, _ -> toast(game.acceptRansom(offerId) ?: "${o.prisonerName} was ransomed for ${o.price} silver.") }
            .setNegativeButton("Decline") { _, _ -> game.declineRansom(offerId); toast("You declined the ransom for ${o.prisonerName}.") }
            .setNeutralButton("Later", null)
            .setOnDismissListener { refreshAlerts() }
            .show()
    }

    private fun refreshAlerts() {
        alertBar.removeAllViews()
        // The pawn panel sits on the same side of the screen.
        alertBar.visibility = if (view.selectedId >= 0) View.GONE else View.VISIBLE
        for (a in computeAlerts()) {
            val color = when (a.level) { 3 -> 0xDD8A2828.toInt(); 2 -> 0xDD8A6420.toInt(); 1 -> 0xDD4A5A2A.toInt(); else -> 0xDD3A3A3A.toInt() }
            val tv = ui.chip(a.text, color)
            tv.setOnClickListener {
                if (a.offerId >= 0) showRansomOffer(a.offerId)
                else if (a.x >= 0) view.centerOn(a.x.toFloat(), a.y.toFloat())
            }
            alertBar.addView(tv, ui.lin(-2, -2, 0f, 0, 2, 0, 0))
        }
    }

    private fun refreshBanner() {
        val newest = game.log.lastOrNull()
        if (newest !== lastLogEntry) {
            lastLogEntry = newest
            if (newest != null) {
                banner.text = newest.text
                banner.setTextColor(when (newest.level) { 3 -> ui.bad; 2 -> ui.warn; 1 -> ui.good; else -> Color.WHITE })
                banner.visibility = View.VISIBLE
                bannerUntil = SystemClock.uptimeMillis() + 6500
                if (newest.level >= 3 && speed > 1) { speed = 1; refreshSpeed() }
            }
        }
        if (banner.visibility == View.VISIBLE && SystemClock.uptimeMillis() > bannerUntil) banner.visibility = View.GONE
    }

    fun toast(s: String) {
        banner.text = s
        banner.setTextColor(ui.warn)
        banner.visibility = View.VISIBLE
        bannerUntil = SystemClock.uptimeMillis() + 3500
    }

    // ------------------------------------------------------------------ selection and tools
    fun select(p: Pawn?) {
        view.selectedId = p?.id ?: -1
        panels.showPawn(p)
        if (p != null) { tileCard.visibility = View.GONE; view.selectedCell = -1 }
    }

    /** Materials the current build tool may use right now (research done). */
    private fun usableMaterials(def: io.github.teamomuito.colony.sim.BuildDef): List<io.github.teamomuito.colony.sim.ItemType> =
        (def.stuff ?: emptyList()).filter { m -> io.github.teamomuito.colony.sim.Materials.of(m)?.research.let { it == null || it in game.researchDone } }

    private fun updateMaterialChip() {
        val t = view.tool as? Tool.Build ?: return
        val options = usableMaterials(t.def)
        val chosen = view.buildMaterial ?: t.def.stuff?.first() ?: return
        materialChip.text = "Material: ${chosen.label} (${game.map.countItems(chosen)} in stock) ⟳"
        if (options.size < 2) materialChip.alpha = 0.6f else materialChip.alpha = 1f
    }

    /** Each tap picks the next material the colony has researched; the next placed structure uses it. */
    private fun cycleMaterial() {
        val t = view.tool as? Tool.Build ?: return
        val options = usableMaterials(t.def)
        if (options.isEmpty()) return
        val cur = view.buildMaterial ?: t.def.stuff?.first()
        val next = options[(options.indexOf(cur) + 1).let { if (it < 0 || it >= options.size) 0 else it }]
        view.buildMaterial = next
        updateMaterialChip()
        view.invalidate()
    }

    fun setTool(t: Tool) {
        view.tool = t
        toolChip.visibility = if (t === Tool.Select) View.GONE else View.VISIBLE
        toolChip.text = "${t.label}  ✕"
        rotChip.visibility = if (t is Tool.Build && t.def.w != t.def.h) View.VISIBLE else View.GONE
        val stuff = (t as? Tool.Build)?.def?.stuff
        if (stuff == null || view.buildMaterial !in stuff) view.buildMaterial = null
        materialChip.visibility = if (stuff != null) View.VISIBLE else View.GONE
        updateMaterialChip()
        tileCard.visibility = View.GONE
        view.selectedCell = -1
        view.invalidate()
    }

    private fun onTileTap(x: Int, y: Int) {
        val g = game
        if (!g.map.inB(x, y)) return
        panels.closePanel()
        val p = g.pawnAt(x, y)
        val sel = g.pawnById(view.selectedId)
        if (p != null && !(sel != null && sel.drafted && p.hostile)) { select(p); return }
        if (sel != null && sel.faction == Faction.PLAYER && sel.drafted) {
            if (p != null && p.hostile) g.orderAttack(sel, p) else g.orderMove(sel, x, y)
            return
        }
        if (sel != null) select(null)
        showTile(x, y)
    }

    private fun showTile(x: Int, y: Int) {
        val g = game
        val m = g.map
        val i = m.idx(x, y)
        view.selectedCell = i
        val sb = StringBuilder()
        sb.append(m.terrain[i].label)
        if (m.terrain[i] == io.github.teamomuito.colony.sim.Terrain.ROCK) sb.append(" (${m.rockType[i].label}${if (m.ore[i] != io.github.teamomuito.colony.sim.Ore.NONE) ", " + m.ore[i].label + " ore" else ""})")
        if (m.terrain[i].fertility > 0f) sb.append("  ·  fertility ${(m.terrain[i].fertility * 100).toInt()}%")
        m.floor[i]?.let { sb.append("\n${it.label}") }
        if (m.conduit[i]) sb.append("\nPower conduit")
        m.building[i]?.let { b ->
            sb.append("\n${b.displayName}${if (b.built && b.quality != io.github.teamomuito.colony.sim.Quality.NORMAL) " (${b.quality.label})" else ""}")
            if (!b.built) {
                var done = 0; var tot = 0
                for (k in b.cost.indices) { done += b.delivered[k]; tot += b.cost[k].second }
                sb.append(" (blueprint $done/$tot materials)")
            } else {
                sb.append("  HP ${b.hp.toInt()}/${b.maxHp.toInt()}")
                if (b.def.fuelCap > 0f) sb.append("\nFuel ${b.fuel.toInt()}/${b.def.fuelCap.toInt()}")
                if (b.def.consumesPower) sb.append(if (b.powered) "\nPowered" else "\nNo power")
                if (b.def.sleeps && b.ownerId >= 0) sb.append("\nOwner: ${g.pawnById(b.ownerId)?.name ?: "?"}")
                if (b.def.sleeps && b.prisonerBed) sb.append("\nPrisoner bed")
                if (b.def == BuildDef.BATTERY) sb.append("\nCharge ${b.charge.toInt()}/600")
                if (b.def == BuildDef.MORTAR) sb.append("\nShells ${b.shells}/5")
            }
        }
        m.plant[i]?.let { pl ->
            sb.append("\n${pl.type.label}")
            if (pl.type.crop || pl.type.isTree) sb.append(" ${(pl.growth * 100).toInt()}% grown")
        }
        m.items[i]?.let {
            if (it.corpseOf != null) sb.append("\nCorpse: ${it.corpseOf} (${(it.rot * 100).toInt()}% decayed)")
            else sb.append("\n${it.count} × ${it.type.label}${if (it.type.isGear) " (${it.quality.label})" else ""}${if (it.rot > 0.05f && it.type.spoilDays > 0f) ", ${(it.rot * 100).toInt()}% spoiled" else ""}")
        }
        m.zoneAt(i)?.let { sb.append("\n${it.name}") }
        if (m.filth[i] > 0) sb.append("\nFilth x${m.filth[i]}")
        if (m.fires.containsKey(i)) sb.append("\nON FIRE")
        when (m.desig[i].toInt()) {
            Desig.MINE -> sb.append("\nMarked: mine")
            Desig.CUT -> sb.append("\nMarked: cut")
            Desig.HARVEST -> sb.append("\nMarked: harvest")
            Desig.DECON -> sb.append("\nMarked: deconstruct")
            Desig.REPAIR -> sb.append("\nMarked: repair")
        }
        val t = m.tempAt(i, g.outdoorTemp())
        sb.append("\n${if (m.roomIndoorAt(i)) "Indoors" else "Outdoors"}, ${t.toInt()}°C")
        if (m.roomIndoorAt(i) && !m.roomDirty) {
            val r = m.roomId[i]
            sb.append("\nRoom: ${m.roomSize[r]} cells, ${io.github.teamomuito.colony.sim.impressLabel(m.roomImpress[r])}")
        }
        tileText.text = sb.toString()
        tileActions.removeAllViews()
        val b = m.building[i]
        if (b != null && b.built && (b.def.workbench || b.def.sleeps || b.def.fuelCap > 0f || b.def.hp > 0)) {
            tileActions.addView(ui.button("Open", 11f) { dialogs.building(b) }, ui.lin(-2, -2, 0f, 0, 0, 4, 0))
        }
        if (m.zoneAt(i) != null) tileActions.addView(ui.button("Zone settings", 11f) { dialogs.zone(m.zoneAt(i)!!) }, ui.lin(-2, -2, 0f, 0, 0, 4, 0))
        if (m.items[i] != null || b != null) tileActions.addView(ui.button("Forbid / allow", 11f) { g.toggleForbidden(i); showTile(x, y) }, ui.lin(-2, -2, 0f, 0, 0, 4, 0))
        tileCard.visibility = View.VISIBLE
    }

    private fun onArea(x0: Int, y0: Int, x1: Int, y1: Int) {
        val g = game
        val t = view.tool
        if (t === Tool.Select) return
        var n = 0
        when (t) {
            Tool.Stockpile -> { g.paintZone(x0, y0, x1, y1, ZoneKind.STOCKPILE); n = 1 }
            Tool.Dumping -> { g.paintZone(x0, y0, x1, y1, ZoneKind.DUMPING); n = 1 }
            is Tool.Growing -> { g.paintZone(x0, y0, x1, y1, ZoneKind.GROWING, t.crop); n = 1 }
            Tool.Hunt, Tool.Tame, Tool.Slaughter -> {
                for (p in g.pawns) {
                    if (!p.alive || !p.isAnimal || p.x !in x0..x1 || p.y !in y0..y1) continue
                    when (t) {
                        Tool.Hunt -> if (p.faction == Faction.WILD) { p.huntMark = !p.huntMark; if (p.huntMark) p.tameMark = false; n++ }
                        Tool.Tame -> if (p.faction == Faction.WILD) { p.tameMark = !p.tameMark; if (p.tameMark) p.huntMark = false; n++ }
                        else -> if (p.faction == Faction.PLAYER) { p.slaughterMark = !p.slaughterMark; n++ }
                    }
                }
                if (n == 0) toast("No suitable animals there")
            }
            Tool.Forbid -> {
                var anyAllowed = false
                for (y in y0..y1) for (x in x0..x1) if (g.map.inB(x, y)) { val i = g.map.idx(x, y); if (g.map.items[i]?.forbidden == false || g.map.building[i]?.forbidden == false) anyAllowed = true }
                for (y in y0..y1) for (x in x0..x1) if (g.map.inB(x, y)) {
                    val i = g.map.idx(x, y)
                    g.map.items[i]?.let { it.forbidden = anyAllowed; n++ }
                    g.map.building[i]?.let { it.forbidden = anyAllowed; n++ }
                }
            }
            is Tool.Area -> {
                for (y in y0..y1) for (x in x0..x1) if (g.map.inB(x, y)) g.map.areas[t.index][g.map.idx(x, y)] = t.add
                n = 1
            }
            else -> {
                val stepX = (t as? Tool.Build)?.let { if (view.buildRot) it.def.h else it.def.w } ?: 1
                val stepY = (t as? Tool.Build)?.let { if (view.buildRot) it.def.w else it.def.h } ?: 1
                for (y in y0..y1 step stepY) for (x in x0..x1 step stepX) {
                    if (!g.map.inB(x, y)) continue
                    val ok = when (t) {
                        Tool.Mine -> g.designate(x, y, Desig.MINE)
                        Tool.Cut -> g.designate(x, y, Desig.CUT)
                        Tool.Harvest -> g.designate(x, y, Desig.HARVEST)
                        Tool.Deconstruct -> g.designate(x, y, Desig.DECON)
                        Tool.Repair -> g.designate(x, y, Desig.REPAIR)
                        Tool.CancelOrders -> {
                            g.clearDesignation(x, y)
                            if (g.map.building[g.map.idx(x, y)]?.built == false) g.designate(x, y, Desig.DECON) else true
                        }
                        Tool.ClearZone -> { g.setZone(x, y, ZoneKind.NONE); true }
                        is Tool.Build -> g.placeBlueprint(t.def, x, y, view.buildRot, view.buildMaterial)
                        else -> false
                    }
                    if (ok) n++
                }
                if (n == 0 && t is Tool.Build) toast("Can't build ${t.def.label.lowercase()} there")
            }
        }
    }

    // ------------------------------------------------------------------ caravan battles
    /** Puts the colony's battle map on screen, paused, so the player can draft and order before anything happens. */
    fun beginBattleUi() {
        val plan = game.pendingBattle ?: return
        val bg = game.beginBattle()
        swapTo(bg)
        speed = 0; refreshSpeed()
        refreshBattleChip()
        toast("${plan.label}! Draft your people and give orders. Press play when ready.")
    }

    /** Takes the player back to the world map once the battle has a result. */
    fun endBattleUi() {
        val bg = game
        val world = bg.parent ?: return
        val outcome = bg.battle?.outcome ?: return
        world.resolveBattle(bg)
        swapTo(world)
        refreshBattleChip()
        toast(when (outcome) {
            io.github.teamomuito.colony.sim.BattleOutcome.VICTORY -> "Victory!"
            io.github.teamomuito.colony.sim.BattleOutcome.DEFEAT -> "Defeat. The caravan was lost."
            io.github.teamomuito.colony.sim.BattleOutcome.RETREAT -> "The caravan withdrew."
        })
    }

    fun refreshBattleChip() {
        val b = game.battle
        battleChip.visibility = if (b != null) View.VISIBLE else View.GONE
        if (b != null) battleChip.text = "⚔ ${b.label}  ·  Retreat"
    }

    private fun confirmRetreat() {
        if (game.battle?.outcome != null) return
        AlertDialog.Builder(this).setMessage("Retreat? Your people walk to the edge of the map. Downed people are carried out if someone can take them; anyone else is left behind.")
            .setPositiveButton("Retreat") { _, _ -> game.requestRetreat(); toast("Withdrawing") }
            .setNegativeButton("Keep fighting", null).show()
    }

    // ------------------------------------------------------------------ several colonies
    class ColonyEntry(val tile: Int, val name: String, val file: String)

    private val coloniesFile get() = File(filesDir, "colonies.txt")

    fun colonies(): List<ColonyEntry> = try {
        if (!coloniesFile.exists()) emptyList() else coloniesFile.readLines().mapNotNull { l ->
            val parts = l.split("|")
            if (parts.size == 3 && File(filesDir, parts[2]).exists()) ColonyEntry(parts[0].toInt(), parts[1], parts[2]) else null
        }
    } catch (_: Throwable) { emptyList() }

    private fun writeColonies(list: List<ColonyEntry>) {
        try { coloniesFile.writeText(list.joinToString("\n") { "${it.tile}|${it.name.replace('|', ' ')}|${it.file}" }) } catch (_: Throwable) {}
    }

    /** Found a colony with the caravan; the old one is archived and can be switched back to. */
    fun settleWith(c: io.github.teamomuito.colony.sim.Caravan, name: String): String? {
        if (game.battle != null) return "Finish the battle first."
        val oldTile = game.world.homeTile
        val oldName = game.colonyName
        val st = game.settle(c, name) ?: return "This tile can't be settled."
        val file = "colony_${System.currentTimeMillis()}.sav"
        File(filesDir, file).writeBytes(st.archivedOld)
        writeColonies(colonies() + ColonyEntry(oldTile, oldName, file))
        swapTo(st.game)
        return null
    }

    fun switchColony(e: ColonyEntry) {
        if (game.battle != null) { toast("Finish the battle first."); return }
        try {
            val target = SaveGame.read(File(filesDir, e.file).readBytes())
            val file = "colony_${System.currentTimeMillis()}.sav"
            File(filesDir, file).writeBytes(SaveGame.write(game))
            val rest = colonies().filter { it.file != e.file }
            writeColonies(rest + ColonyEntry(game.world.homeTile, game.colonyName, file))
            File(filesDir, e.file).delete()
            swapTo(target)
        } catch (t: Throwable) { reportError(t) }
    }

    private fun swapTo(g: Game) {
        game = g
        hookAutosave(game)
        view.game = game
        view.selectedId = -1
        view.selectedCell = -1
        view.recenter()
        overShown = false
        lastLogEntry = null
        chipIds = emptyList()
        select(null)
        panels.closePanel()
        setTool(Tool.Select)
        speed = 1; refreshSpeed()
        save()
        refreshHud()
    }

    // ------------------------------------------------------------------ whole-game control
    fun restart(newGame: Game) {
        saveFile.delete()
        game = newGame
        // A new colony starts the tutorial again, if the player wants it.
        if (prefs.tutorialOn) tutorial.restart() else tutorial.hide()
        prefs.saveTutorial(tutorial)
        renderTutorial(true)
        hookAutosave(game)
        view.game = game
        view.selectedId = -1
        view.selectedCell = -1
        view.recenter()
        overShown = false
        lastLogEntry = null
        chipIds = emptyList()
        select(null)
        panels.closePanel()
        setTool(Tool.Select)
        speed = 1; refreshSpeed()
        refreshHud()
    }
}
