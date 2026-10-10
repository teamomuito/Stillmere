package io.github.teamomuito.colony

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.teamomuito.colony.sim.BuildDef
import io.github.teamomuito.colony.sim.Cap
import io.github.teamomuito.colony.sim.Faction
import io.github.teamomuito.colony.sim.Game
import io.github.teamomuito.colony.sim.HOURS_PER_DAY
import io.github.teamomuito.colony.sim.ItemType
import io.github.teamomuito.colony.sim.TICKS_PER_DAY
import io.github.teamomuito.colony.sim.TICKS_PER_HOUR
import io.github.teamomuito.colony.sim.JobType
import io.github.teamomuito.colony.sim.Pawn
import io.github.teamomuito.colony.sim.PlantType
import io.github.teamomuito.colony.sim.Quality
import io.github.teamomuito.colony.sim.Research
import io.github.teamomuito.colony.sim.SkillType
import io.github.teamomuito.colony.sim.Trait
import io.github.teamomuito.colony.sim.WorkType
import io.github.teamomuito.colony.sim.assignBed
import io.github.teamomuito.colony.sim.dropWeapon
import io.github.teamomuito.colony.sim.releasePrisoner
import io.github.teamomuito.colony.sim.skillLabel
import io.github.teamomuito.colony.sim.stripApparel
import io.github.teamomuito.colony.sim.partDamage
import io.github.teamomuito.colony.sim.partEff
import io.github.teamomuito.colony.sim.partMissing
import io.github.teamomuito.colony.sim.partMax
import io.github.teamomuito.colony.sim.hediff
import io.github.teamomuito.colony.sim.skill
import kotlin.math.abs
import kotlin.math.min

class Panels(private val a: MainActivity) {
    private val ui get() = a.ui
    private val game get() = a.game

    var panelKind = ""
    private var panel: FrameLayout? = null
    private var panelBody: LinearLayout? = null
    private var panelScroll: ScrollView? = null
    private var workCells = ArrayList<Triple<TextView, Pawn, WorkType>>()
    private var sig = ""

    // pawn panel
    private var pawnBox: LinearLayout? = null
    private var pawnBody: LinearLayout? = null
    private var pawnScroll: ScrollView? = null
    private var pawnTab = 0
    private var pawnTick = 0

    // ------------------------------------------------------------------ generic host
    fun closePanel() {
        panel?.let { a.root.removeView(it) }
        panel = null; panelBody = null; panelScroll = null; panelKind = ""
        workCells.clear(); sig = ""
    }

    fun toggle(kind: String) {
        val was = panelKind
        closePanel()
        if (was == kind) return
        a.tileCard.visibility = View.GONE
        panelKind = kind
        val wrap = FrameLayout(a).apply { background = ui.bg(ui.panel, 14); setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8)) }
        val metrics = a.resources.displayMetrics
        val body = ui.column()
        val sc = ui.scroll(body)
        wrap.addView(sc)
        panelBody = body; panelScroll = sc
        val h = if (kind == "architect") ViewGroupLP.WRAP else (metrics.heightPixels * 0.52f).toInt()
        a.root.addView(wrap, ui.fl(min(metrics.widthPixels - ui.dp(30), ui.dp(700)), h, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 0, 0, 50))
        panel = wrap
        build(true)
    }

    object ViewGroupLP { const val WRAP = -2 }

    fun refresh() {
        if (panelKind.isNotEmpty()) build(false)
        refreshPawn()
    }

    private fun build(force: Boolean) {
        val body = panelBody ?: return
        when (panelKind) {
            "architect" -> if (force) buildArchitect(body)
            "work" -> if (force) buildWork(body) else for ((tv, p, w) in workCells) tv.text = prioText(p, w)
            "schedule" -> if (force) buildSchedule(body)
            "research" -> buildResearch(body, force)
            "people" -> rebuild(body) { buildPeople(it) }
            "animals" -> rebuild(body) { buildAnimals(it) }
            "log" -> buildLog(body, force)
            "stats" -> rebuild(body) { buildStats(it) }
        }
    }

    private fun rebuild(body: LinearLayout, f: (LinearLayout) -> Unit) {
        val y = panelScroll?.scrollY ?: 0
        body.removeAllViews()
        f(body)
        panelScroll?.post { panelScroll?.scrollTo(0, y) }
    }

    // ------------------------------------------------------------------ architect
    private val orderTools: List<Pair<String, Tool>> get() = listOf(
        "Mine rock" to Tool.Mine, "Chop trees" to Tool.Cut, "Harvest plants" to Tool.Harvest, "Deconstruct" to Tool.Deconstruct,
        "Repair" to Tool.Repair, "Cancel orders" to Tool.CancelOrders, "Hunt animals" to Tool.Hunt, "Tame animals" to Tool.Tame,
        "Slaughter" to Tool.Slaughter, "Forbid / allow" to Tool.Forbid,
    )

    private fun buildArchitect(body: LinearLayout) {
        val tabs = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        val content = ui.column()
        val cats = listOf("Orders", "Zones", "Structure", "Floors", "Furniture", "Production", "Power", "Temperature", "Security", "Joy", "Misc", "Ship")
        fun show(name: String) {
            content.removeAllViews()
            val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            fun add(t: String, locked: String?, act: () -> Unit) {
                val label = if (locked != null) "$t\n🔒 $locked" else t
                val b = ui.button(label, 11.5f) { if (locked != null) a.toast("Needs research: $locked") else { act(); closePanel() } }
                if (locked != null) b.alpha = 0.6f
                row.addView(b, ui.lin(-2, -2, 0f, 0, 0, 6, 0))
            }
            when (name) {
                "Orders" -> for ((t, tool) in orderTools) add(t, null) { a.setTool(tool) }
                "Zones" -> {
                    add("Stockpile", null) { a.setTool(Tool.Stockpile) }
                    add("Dumping", null) { a.setTool(Tool.Dumping) }
                    for (c in listOf(PlantType.RICE, PlantType.POTATO, PlantType.CORN, PlantType.STRAWBERRY, PlantType.COTTON, PlantType.HEALROOT, PlantType.SMOKELEAF, PlantType.PSYCHOID, PlantType.HAYGRASS, PlantType.DEVILSTRAND_CROP).filter { it.research == null || it.research in game.researchDone })
                        add("Grow ${c.label.lowercase()}", null) { a.setTool(Tool.Growing(c)) }
                    add("Remove zone", null) { a.setTool(Tool.ClearZone) }
                    for (k in 0 until 3) {
                        add("Paint ${game.map.areaNames[k]}", null) { a.setTool(Tool.Area(k, true, game.map.areaNames[k])) }
                        add("Erase ${game.map.areaNames[k]}", null) { a.setTool(Tool.Area(k, false, game.map.areaNames[k])) }
                    }
                }
                else -> {
                    val defs = BuildDef.entries.filter { it.category == name && !it.legacy }
                    for (d in defs) {
                        val locked = d.research?.takeIf { it !in game.researchDone }?.label
                        val cost = if (d.cost.isEmpty()) "free" else d.cost.joinToString(", ") { "${it.second} ${shortName(it.first)}" }
                        add("${d.label}${if (d.w * d.h > 1) " ${d.w}×${d.h}" else ""}\n$cost", locked) { a.setTool(Tool.Build(d)) }
                    }
                    if (defs.isEmpty()) row.addView(ui.label("Nothing here yet.", 12f, ui.dim))
                }
            }
            content.addView(ui.hscroll(row))
        }
        var current = "Orders"
        for (c in cats) tabs.addView(ui.button(c, 11.5f) { current = c; show(c) }, ui.lin(-2, -2, 0f, 0, 0, 5, 6))
        body.addView(ui.hscroll(tabs))
        body.addView(content)
        show(current)
    }

    private fun shortName(t: ItemType) = when (t) {
        ItemType.COMPONENT -> "comp"; ItemType.STONE -> "stone"; ItemType.STONE_CHUNK -> "chunk"; ItemType.PLASTEEL -> "plasteel"; else -> t.label.lowercase()
    }

    // ------------------------------------------------------------------ work
    private fun prioText(p: Pawn, w: WorkType): String =
        if (p.incapable and (1 shl w.ordinal) != 0) "X" else if (p.priority[w.ordinal] == 0) "–" else p.priority[w.ordinal].toString()

    private fun buildWork(body: LinearLayout) {
        val cols = game.colonists
        body.addView(ui.label("Work priorities: 1 = first, 4 = last, – = never. Tap to change.", 12f, ui.accent, true))
        val table = ui.column()
        val hdr = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        hdr.addView(ui.label("", 10f), ui.lin(ui.dp(84), -2))
        for (w in WorkType.entries) hdr.addView(ui.label(w.short, 9.5f, ui.dim).apply { gravity = Gravity.CENTER }, ui.lin(ui.dp(46), -2))
        table.addView(hdr)
        workCells.clear()
        for (p in cols) {
            val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(ui.label(p.name.substringBefore(' '), 12f, ui.text, true), ui.lin(ui.dp(84), -2))
            for (w in WorkType.entries) {
                val tv = TextView(a).apply {
                    text = prioText(p, w); textSize = 14f; setTextColor(ui.text); gravity = Gravity.CENTER
                    val skill = w.skill()
                    val passion = if (skill != null) p.passion[skill.ordinal] else 0
                    background = ui.bg(if (passion == 2) 0xFF5A3F1A.toInt() else if (passion == 1) 0xFF45391E.toInt() else ui.button, 8, 0x33FFFFFF)
                    setOnClickListener {
                        val cur = p.priority[w.ordinal]
                        game.setPriority(p, w, if (cur == 0) 1 else if (cur >= 4) 0 else cur + 1)
                        text = prioText(p, w)
                    }
                }
                workCells.add(Triple(tv, p, w))
                row.addView(tv, ui.lin(ui.dp(42), ui.dp(36), 0f, 2, 2, 2, 2))
            }
            table.addView(row)
        }
        body.addView(ui.hscroll(table))
        body.addView(ui.label("Brown cells = passions.", 10.5f, ui.dim))
    }

    // ------------------------------------------------------------------ schedule
    private val schedColors = intArrayOf(0xFF4A4A4A.toInt(), 0xFFB07A28.toInt(), 0xFF4C9A4C.toInt(), 0xFF3A5A9A.toInt())
    private val schedNames = arrayOf("Anything", "Work", "Joy", "Sleep")

    private fun buildSchedule(body: LinearLayout) {
        body.addView(ui.label("Daily schedule. Tap a cell to cycle: Anything · Work · Joy · Sleep", 12f, ui.accent, true))
        val legend = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        for (k in 0..3) legend.addView(ui.chip(schedNames[k], schedColors[k], 10f), ui.lin(-2, -2, 0f, 0, 2, 4, 4))
        body.addView(legend)
        val table = ui.column()
        val hdr = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        hdr.addView(ui.label("", 10f), ui.lin(ui.dp(76), -2))
        for (h in 0 until 24) hdr.addView(ui.label(h.toString(), 9f, ui.dim).apply { gravity = Gravity.CENTER }, ui.lin(ui.dp(22), -2))
        table.addView(hdr)
        fun cellFor(p: Pawn, h: Int): TextView = TextView(a).apply {
            background = ui.bg(schedColors[p.schedule[h]], 3)
            setOnClickListener { p.schedule[h] = (p.schedule[h] + 1) % 4; background = ui.bg(schedColors[p.schedule[h]], 3) }
        }
        val rows = ArrayList<Pair<Pawn, List<TextView>>>()
        for (p in game.colonists) {
            val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(ui.label(p.name.substringBefore(' '), 11f, ui.text, true), ui.lin(ui.dp(76), -2))
            val cells = (0 until 24).map { h -> cellFor(p, h).also { row.addView(it, ui.lin(ui.dp(20), ui.dp(24), 0f, 1, 1, 1, 1)) } }
            rows.add(p to cells)
            table.addView(row)
        }
        // The "everyone" row.
        val all = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        all.addView(ui.label("Everyone", 11f, ui.accent, true), ui.lin(ui.dp(76), -2))
        for (h in 0 until 24) {
            val t = TextView(a).apply {
                text = "▾"; gravity = Gravity.CENTER; textSize = 10f; setTextColor(ui.text); background = ui.bg(0xFF2A2620.toInt(), 3)
                setOnClickListener {
                    val first = game.colonists.firstOrNull() ?: return@setOnClickListener
                    val v = (first.schedule[h] + 1) % 4
                    for ((p, cells) in rows) { p.schedule[h] = v; cells[h].background = ui.bg(schedColors[v], 3) }
                }
            }
            all.addView(t, ui.lin(ui.dp(20), ui.dp(24), 0f, 1, 4, 1, 1))
        }
        table.addView(all)
        body.addView(ui.hscroll(table))
    }

    // ------------------------------------------------------------------ research
    private fun buildResearch(body: LinearLayout, force: Boolean) {
        val g = game
        val s = "${g.researchCurrent?.name}${g.researchDone.size}${((g.researchProgress[g.researchCurrent] ?: 0f) / 50f).toInt()}"
        if (!force && s == sig) return
        sig = s
        rebuild(body) { host ->
            val benches = g.map.buildings().filter { it.built && (it.def == BuildDef.RESEARCH_BENCH || it.def == BuildDef.HI_TECH_BENCH) }
            host.addView(ui.label(if (benches.isEmpty()) "Build a research bench to start researching" else "Research · ${g.researchDone.size}/${Research.entries.size} complete", 12f, ui.accent, true))
            val order = Research.entries.sortedWith(compareBy({ it.tier }, { it.cost }))
            for (r in order) {
                val done = r in g.researchDone
                val avail = g.researchAvailable(r)
                val prog = (g.researchProgress[r] ?: 0f) / r.cost
                val needsHi = r.tier >= 3
                val state = when {
                    done -> "Done"
                    !avail -> "Needs " + r.needs.filter { it !in g.researchDone }.joinToString { it.label }
                    g.researchCurrent == r -> "In progress ${(prog * 100).toInt()}%"
                    else -> "${(prog * 100).toInt()}% · ${r.cost.toInt()} points" + if (needsHi) " · hi-tech bench" else ""
                }
                val col = ui.column()
                col.addView(ui.label(r.label, 13f, if (done) ui.good else if (avail) ui.text else ui.dim, true))
                col.addView(ui.label("${r.unlocks}\n$state", 10.5f, ui.dim))
                if (avail && !done) col.addView(ui.bar(prog, ui.accent, 150))
                val wrap = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                wrap.addView(col, ui.lin(0, -2, 1f))
                if (avail && !done) {
                    val active = g.researchCurrent == r
                    wrap.addView(ui.button(if (active) "Stop" else "Start", 11.5f, selected = active) { g.startResearch(if (active) null else r); sig = ""; build(true) })
                }
                host.addView(wrap, ui.lin(-1, -2, 0f, 0, 6, 0, 0))
            }
        }
    }

    // ------------------------------------------------------------------ people / animals / log / stats
    private fun buildPeople(host: LinearLayout) {
        val g = game
        host.addView(ui.label("Colonists", 13f, ui.accent, true))
        for (p in g.colonists) host.addView(personRow(p), ui.lin(-1, -2, 0f, 0, 4, 0, 0))
        val pr = g.prisoners
        if (pr.isNotEmpty()) {
            host.addView(ui.label("Prisoners", 13f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 10, 0, 0))
            for (p in pr) host.addView(personRow(p), ui.lin(-1, -2, 0f, 0, 4, 0, 0))
        }
        val vis = g.pawns.filter { it.alive && it.faction == Faction.VISITOR && !it.isAnimal }
        if (vis.isNotEmpty()) {
            host.addView(ui.label("Visitors", 13f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 10, 0, 0))
            for (p in vis) host.addView(personRow(p), ui.lin(-1, -2, 0f, 0, 4, 0, 0))
        }
    }

    private fun personRow(p: Pawn): View {
        val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = ui.bg(0xFF26221C.toInt(), 8); setPadding(ui.dp(8), ui.dp(4), ui.dp(8), ui.dp(4)) }
        val info = ui.column()
        val act = when { p.downed -> "Downed"; p.drafted -> "Drafted"; else -> p.job?.type?.label ?: "Idle" }
        info.addView(ui.label("${p.name}  ·  ${p.age}", 12.5f, ui.text, true))
        info.addView(ui.label("$act  ·  HP ${p.hp.toInt()}  ·  mood ${(p.mood * 100).toInt()}%${if (p.prisoner) "  ·  resistance ${String.format("%.1f", p.resistance)}" else ""}", 10.5f, ui.dim))
        row.addView(info, ui.lin(0, -2, 1f))
        row.addView(ui.button("View", 11f) { closePanel(); a.select(p); a.view.centerOn(p.x.toFloat(), p.y.toFloat()) }, ui.lin(-2, -2, 0f, 4, 0, 0, 0))
        if (p.prisoner) {
            row.addView(ui.button(if (p.recruitMode == 0) "Recruit" else "Hold", 11f, selected = p.recruitMode == 0) { p.recruitMode = if (p.recruitMode == 0) 1 else 0; build(true) }, ui.lin(-2, -2, 0f, 4, 0, 0, 0))
            row.addView(ui.button("Release", 11f) { game.releasePrisoner(p); build(true) }, ui.lin(-2, -2, 0f, 4, 0, 0, 0))
        }
        return row
    }

    private fun buildAnimals(host: LinearLayout) {
        val g = game
        host.addView(ui.label("Your animals", 13f, ui.accent, true))
        val tame = g.tamedAnimals
        if (tame.isEmpty()) host.addView(ui.label("None yet. Use the Tame tool on wild animals.", 11.5f, ui.dim))
        for (p in tame) {
            val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = ui.bg(0xFF26221C.toInt(), 8); setPadding(ui.dp(8), ui.dp(4), ui.dp(8), ui.dp(4)) }
            val info = ui.column()
            info.addView(ui.label("${p.name} the ${p.race.label.lowercase()}", 12.5f, ui.text, true))
            info.addView(ui.label("HP ${p.hp.toInt()} · food ${(p.food * 100).toInt()}%${if (p.race.product != null) " · makes ${p.race.product!!.label.lowercase()}" else ""}", 10.5f, ui.dim))
            row.addView(info, ui.lin(0, -2, 1f))
            row.addView(ui.button("Slaughter", 11f, selected = p.slaughterMark) { p.slaughterMark = !p.slaughterMark; build(true) }, ui.lin(-2, -2, 0f, 4, 0, 0, 0))
            row.addView(ui.button("View", 11f) { closePanel(); a.select(p); a.view.centerOn(p.x.toFloat(), p.y.toFloat()) }, ui.lin(-2, -2, 0f, 4, 0, 0, 0))
            host.addView(row, ui.lin(-1, -2, 0f, 0, 4, 0, 0))
        }
        host.addView(ui.label("Wildlife nearby", 13f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 10, 0, 0))
        val wild = g.pawns.filter { it.alive && it.isAnimal && it.faction == Faction.WILD }.sortedBy { abs(it.x - g.homeX) + abs(it.y - g.homeY) }.take(14)
        for (p in wild) {
            val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = ui.bg(0xFF26221C.toInt(), 8); setPadding(ui.dp(8), ui.dp(4), ui.dp(8), ui.dp(4)) }
            val info = ui.column()
            info.addView(ui.label(p.race.label + if (p.manhunter) "  (manhunter!)" else "", 12.5f, if (p.manhunter) ui.bad else ui.text, true))
            info.addView(ui.label("${p.race.meat} meat · tame difficulty ${String.format("%.1f", p.race.tameDifficulty)}${if (p.race.predator) " · predator" else ""}", 10.5f, ui.dim))
            row.addView(info, ui.lin(0, -2, 1f))
            row.addView(ui.button("Hunt", 11f, selected = p.huntMark) { p.huntMark = !p.huntMark; if (p.huntMark) p.tameMark = false; build(true) }, ui.lin(-2, -2, 0f, 4, 0, 0, 0))
            row.addView(ui.button("Tame", 11f, selected = p.tameMark) { p.tameMark = !p.tameMark; if (p.tameMark) p.huntMark = false; build(true) }, ui.lin(-2, -2, 0f, 4, 0, 0, 0))
            row.addView(ui.button("Find", 11f) { closePanel(); a.view.centerOn(p.x.toFloat(), p.y.toFloat()) }, ui.lin(-2, -2, 0f, 4, 0, 0, 0))
            host.addView(row, ui.lin(-1, -2, 0f, 0, 4, 0, 0))
        }
    }

    private var logSig = 0
    private fun buildLog(body: LinearLayout, force: Boolean) {
        val s = game.log.size * 31 + (game.log.lastOrNull()?.tick ?: 0L).toInt()
        if (!force && s == logSig) return
        logSig = s
        rebuild(body) { host ->
            for (l in game.log.asReversed().take(90)) {
                val day = l.tick / TICKS_PER_DAY.toLong() + 1
                val hour = (l.tick / TICKS_PER_HOUR.toLong() % HOURS_PER_DAY).toInt()
                val color = when (l.level) { 3 -> ui.bad; 2 -> ui.warn; 1 -> ui.good; else -> ui.text }
                host.addView(ui.label("Day $day ${hour.toString().padStart(2, '0')}:00  ${l.text}", 11.5f, color))
            }
        }
    }

    private fun buildStats(host: LinearLayout) {
        val g = game
        host.addView(ui.label("Colony statistics", 13f, ui.accent, true))
        host.addView(ui.mono(buildString {
            appendLine("Days survived:   ${g.day}")
            appendLine("Raids repelled:  ${g.raidsSurvived}")
            appendLine("Enemies killed:  ${g.statsKilled}")
            appendLine("Colonists:       ${g.colonists.size}")
            appendLine("Prisoners:       ${g.prisoners.size}")
            appendLine("Animals:         ${g.tamedAnimals.size}")
            appendLine("Colony wealth:   ${g.map.wealth().toInt()}")
            appendLine("Silver earned:   ${g.silverEarned}")
            appendLine("Research done:   ${g.researchDone.size}/${Research.entries.size}")
            appendLine("Power:           ${g.power.produced.toInt()}W made, ${g.power.consumed.toInt()}W used")
            appendLine("Biome:           ${g.map.biome.label}")
            appendLine("Storyteller:     ${g.storyteller.label}")
            appendLine("Difficulty:      ${g.difficulty.label}")
        }, 12f))
        if (g.graveyard.isNotEmpty()) {
            host.addView(ui.label("Graveyard", 13f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 10, 0, 0))
            for (s in g.graveyard) host.addView(ui.label("† $s", 11.5f, ui.dim))
        }
    }

    // ================================================================== pawn panel
    fun showPawn(p: Pawn?) {
        pawnBox?.let { a.root.removeView(it) }
        pawnBox = null; pawnBody = null; pawnScroll = null
        if (p == null) return
        closePanel()
        val box = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; background = ui.bg(ui.panel, 12); setPadding(ui.dp(8), ui.dp(6), ui.dp(8), ui.dp(6)) }
        val tabs = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        val names = if (p.isAnimal) listOf("Info") else listOf("Needs", "Health", "Gear", "Social", "Skills", "Policy")
        for ((i, n) in names.withIndex()) tabs.addView(ui.button(n, 10.5f, selected = i == pawnTab) { pawnTab = i; showPawn(a.game.pawnById(a.view.selectedId)) }.apply { setPadding(ui.dp(8), ui.dp(4), ui.dp(8), ui.dp(4)) }, ui.lin(-2, -2, 0f, 0, 0, 3, 0))
        box.addView(ui.hscroll(tabs))
        val body = ui.column()
        val sc = ui.scroll(body)
        box.addView(sc, ui.lin(-1, 0, 1f, 0, 4, 0, 0))
        val actions = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        box.addView(ui.hscroll(actions), ui.lin(-1, -2, 0f, 0, 4, 0, 0))
        buildActions(actions, p)
        a.root.addView(box, ui.fl(ui.dp(300), ui.dp(300), Gravity.END or Gravity.CENTER_VERTICAL, 0, 20, 6, 38))
        pawnBox = box; pawnBody = body; pawnScroll = sc
        if (p.isAnimal && pawnTab > 0) pawnTab = 0
        fillPawn(p)
    }

    private fun refreshPawn() {
        val id = a.view.selectedId
        if (id < 0) return
        val p = game.pawnById(id)
        if (p == null || !p.alive) { a.select(null); return }
        if (++pawnTick % 4 == 0) fillPawn(p)
    }

    private fun buildActions(actions: LinearLayout, p: Pawn) {
        if (p.colonist && p.faction == Faction.PLAYER) {
            actions.addView(ui.button(if (p.drafted) "Undraft" else "Draft", 11.5f, selected = p.drafted) {
                val sel = game.pawnById(a.view.selectedId) ?: return@button
                game.setDrafted(sel, !sel.drafted); showPawn(sel)
            }, ui.lin(-2, -2, 0f, 0, 0, 4, 0))
            actions.addView(ui.button("Rename", 11.5f) { a.dialogs.rename(p) }, ui.lin(-2, -2, 0f, 0, 0, 4, 0))
        }
        if (p.prisoner) {
            actions.addView(ui.button(if (p.recruitMode == 0) "Recruit" else "Hold", 11.5f, selected = p.recruitMode == 0) { p.recruitMode = if (p.recruitMode == 0) 1 else 0; showPawn(p) }, ui.lin(-2, -2, 0f, 0, 0, 4, 0))
            actions.addView(ui.button("Release", 11.5f) { game.releasePrisoner(p); showPawn(p) }, ui.lin(-2, -2, 0f, 0, 0, 4, 0))
        }
        if (p.isAnimal) {
            if (p.faction == Faction.WILD) {
                actions.addView(ui.button("Hunt", 11.5f, selected = p.huntMark) { p.huntMark = !p.huntMark; if (p.huntMark) p.tameMark = false; showPawn(p) }, ui.lin(-2, -2, 0f, 0, 0, 4, 0))
                actions.addView(ui.button("Tame", 11.5f, selected = p.tameMark) { p.tameMark = !p.tameMark; if (p.tameMark) p.huntMark = false; showPawn(p) }, ui.lin(-2, -2, 0f, 0, 0, 4, 0))
            } else if (p.faction == Faction.PLAYER) {
                actions.addView(ui.button("Slaughter", 11.5f, selected = p.slaughterMark) { p.slaughterMark = !p.slaughterMark; showPawn(p) }, ui.lin(-2, -2, 0f, 0, 0, 4, 0))
                actions.addView(ui.button("Rename", 11.5f) { a.dialogs.rename(p) }, ui.lin(-2, -2, 0f, 0, 0, 4, 0))
            }
        }
        actions.addView(ui.button("Center", 11.5f) { a.view.centerOn(p.x.toFloat(), p.y.toFloat()) }, ui.lin(-2, -2, 0f, 0, 0, 4, 0))
        actions.addView(ui.button("Close", 11.5f) { a.select(null) }, ui.lin(-2, -2, 0f, 0, 0, 0, 0))
    }

    private fun barRow(host: LinearLayout, label: String, v: Float, color: Int, text: String) {
        val r = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        r.addView(ui.label(label, 11f, ui.dim), ui.lin(ui.dp(58), -2))
        r.addView(ui.bar(v, color, 110))
        r.addView(ui.label("  $text", 11f), ui.lin(-2, -2))
        host.addView(r, ui.lin(-1, -2, 0f, 0, 2, 0, 0))
    }

    private fun fillPawn(p: Pawn) {
        val body = pawnBody ?: return
        val y = pawnScroll?.scrollY ?: 0
        body.removeAllViews()
        body.addView(ui.label(p.name, 14f, ui.accent, true))
        if (p.isAnimal) fillAnimal(body, p)
        else when (pawnTab) {
            0 -> fillNeeds(body, p)
            1 -> fillHealth(body, p)
            2 -> fillGear(body, p)
            3 -> fillSocial(body, p)
            4 -> fillSkills(body, p)
            else -> fillPolicy(body, p)
        }
        pawnScroll?.post { pawnScroll?.scrollTo(0, y) }
    }

    private fun activity(p: Pawn): String = when {
        p.dead -> "Dead"
        p.downed -> "Downed"
        p.breakUntil > 0 -> "Mental break"
        p.drafted -> "Drafted"
        else -> p.job?.type?.label ?: "Idle"
    }

    private fun fillAnimal(body: LinearLayout, p: Pawn) {
        body.addView(ui.label("${p.race.label}${if (p.faction == Faction.PLAYER) " (tame)" else " (wild)"}", 11.5f, ui.dim))
        body.addView(ui.label("Doing: ${activity(p)}", 11.5f))
        barRow(body, "Health", p.hp / 100f, ui.good, "${p.hp.toInt()}")
        barRow(body, "Food", p.food, 0xFFE0A030.toInt(), "${(p.food * 100).toInt()}%")
        body.addView(ui.label("Moves ${"%.0f".format(100f * p.cap[Cap.MOVING.ordinal])}%  ·  Bite ${p.race.weapon.damage.toInt()} dmg", 11f, ui.dim))
        if (p.race.product != null) body.addView(ui.label("Produces ${p.race.product!!.label.lowercase()}", 11f, ui.dim))
        if (p.faction == Faction.PLAYER) {
            body.addView(ui.label("Restrict to area", 12f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 6, 0, 2))
            val ar = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            ar.addView(ui.button("Anywhere", 10.5f, selected = p.areaRestriction == 0) { p.areaRestriction = 0; fillPawn(p) }, ui.lin(-2, -2, 0f, 0, 0, 3, 0))
            for (k in 0 until 3) ar.addView(ui.button(game.map.areaNames[k], 10.5f, selected = p.areaRestriction == k + 1) { p.areaRestriction = k + 1; fillPawn(p) }, ui.lin(-2, -2, 0f, 0, 0, 3, 0))
            body.addView(ui.hscroll(ar))
        }
        if (p.huntMark) body.addView(ui.label("Marked for hunting", 11f, ui.warn))
        if (p.tameMark) body.addView(ui.label("Marked for taming", 11f, ui.good))
        if (p.slaughterMark) body.addView(ui.label("Marked for slaughter", 11f, ui.bad))
        if (p.injuries.isNotEmpty()) body.addView(ui.label("Wounds: ${p.injuries.count { !it.scar }}${if (p.untended) " (untended)" else ""}", 11f, ui.warn))
    }

    private fun fillNeeds(body: LinearLayout, p: Pawn) {
        body.addView(ui.label("${p.age} (${p.stage.label.lowercase()}) · ${if (p.female) "female" else "male"}${if (p.pregnantUntil > 0L) " · pregnant (${(100 - (p.pregnantUntil - game.tick) * 100 / (30L * io.github.teamomuito.colony.sim.TICKS_PER_DAY)).coerceIn(0, 100)}%)" else ""}${if (p.mother >= 0) game.pawnById(p.mother)?.let { " · child of ${it.name.substringBefore(' ')}" } ?: "" else ""}${if (p.prisoner) " · prisoner" else if (p.faction == Faction.VISITOR) " · visitor" else ""}", 11f, ui.dim))
        if (p.backstory.isNotEmpty()) body.addView(ui.label(p.backstory, 11f, ui.dim))
        body.addView(ui.label("Doing: ${activity(p)}", 11.5f), ui.lin(-2, -2, 0f, 0, 3, 0, 3))
        barRow(body, "Health", p.hp / 100f, ui.good, "${p.hp.toInt()}")
        barRow(body, "Mood", p.mood, ui.moodColor(p.mood), "${(p.mood * 100).toInt()}%")
        barRow(body, "Food", p.food, 0xFFE0A030.toInt(), "${(p.food * 100).toInt()}%")
        barRow(body, "Rest", p.rest, 0xFF6A9AE0.toInt(), "${(p.rest * 100).toInt()}%")
        barRow(body, "Joy", p.joy, 0xFFB77AE0.toInt(), "${(p.joy * 100).toInt()}%")
        body.addView(ui.label("Temp ${p.temp.toInt()}°C  (comfortable ${p.comfyMin().toInt()}°–${p.comfyMax().toInt()}°)", 11f, if (p.temp < p.comfyMin() || p.temp > p.comfyMax()) ui.warn else ui.dim), ui.lin(-2, -2, 0f, 0, 3, 0, 0))
        body.addView(ui.label("Mood factors", 12f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 8, 0, 2))
        val all = (p.situ + p.thoughts.filter { it.expires > game.tick }).sortedBy { it.mood }
        if (all.isEmpty()) body.addView(ui.label("Nothing notable.", 11f, ui.dim))
        body.addView(ui.label("Baseline +55%", 11f, ui.dim))
        for (t in all) {
            val v = (t.mood * 100).toInt()
            body.addView(ui.label("${if (v >= 0) "+" else ""}$v%  ${t.label}", 11f, if (t.mood < 0) ui.bad else ui.good))
        }
        if (p.breakUntil > 0) body.addView(ui.label("In a mental break", 11.5f, ui.bad, true))
    }

    private fun fillHealth(body: LinearLayout, p: Pawn) {
        barRow(body, "Health", p.hp / 100f, ui.good, "${p.hp.toInt()}")
        barRow(body, "Blood", 1f - p.bloodLoss, 0xFFD02030.toInt(), "${((1f - p.bloodLoss) * 100).toInt()}%")
        barRow(body, "Pain", p.pain, 0xFFE08030.toInt(), "${(p.pain * 100).toInt()}%")
        body.addView(ui.label("Capacities", 12f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 6, 0, 2))
        for (c in Cap.entries) {
            val v = p.cap[c.ordinal]
            body.addView(ui.label("${c.name.lowercase().replaceFirstChar { it.uppercase() }}  ${(v * 100).toInt()}%", 11f, if (v < 0.5f) ui.bad else if (v < 0.9f) ui.warn else ui.dim))
        }
        body.addView(ui.label("Conditions", 12f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 8, 0, 2))
        var any = false
        for (h in p.hediffs) {
            any = true
            val extra = if (h.kind.category == 0) " · immunity ${(h.immunity * 100).toInt()}%${if (h.tended) " · treated" else ""}" else ""
            body.addView(ui.label("${h.kind.label} ${(h.severity * 100).toInt()}%$extra", 11f, if (h.severity > 0.6f) ui.bad else ui.warn))
        }
        for (inj in p.injuries.sortedByDescending { if (it.missing) 999f else it.severity }) {
            any = true
            val part = p.race.body[inj.part].label
            val desc = when {
                inj.missing -> "$part: missing"
                inj.scar -> "$part: scar"
                else -> {
                    val b = StringBuilder("$part: ${inj.kind.label.lowercase()} ${String.format("%.1f", inj.severity)}")
                    if (inj.bleed > 0.00001f) b.append(if (inj.tended) " · bandaged" else " · bleeding")
                    if (inj.tended) b.append(" ${(inj.tendQuality * 100).toInt()}%")
                    if (inj.infection > 0f) b.append(" · INFECTED ${(inj.infection * 100).toInt()}%")
                    b.toString()
                }
            }
            body.addView(ui.label(desc, 11f, if (inj.infection > 0f) ui.bad else if (inj.missing || inj.scar) ui.dim else if (inj.tended) ui.warn else ui.bad))
        }
        if (!any) body.addView(ui.label("Healthy.", 11f, ui.good))
        for ((idx, imp) in p.implants) body.addView(ui.label("${imp.label} (${p.race.body[idx].label})", 11f, ui.good))
        body.addView(ui.label("Surgery", 12f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 8, 0, 2))
        for (o in p.surgeries.toList()) {
            val r = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            r.addView(ui.label(o.label(p), 11f), ui.lin(0, -2, 1f))
            r.addView(ui.button("Cancel", 10.5f) { p.surgeries.remove(o); fillPawn(p) })
            body.addView(r)
        }
        if (!p.isAnimal) body.addView(ui.button("Add operation…", 11f) { a.dialogs.surgery(p) }, ui.lin(-2, -2, 0f, 0, 3, 0, 0))
        body.addView(ui.label("Medical care", 12f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 8, 0, 2))
        val care = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        for ((i, n) in listOf("None", "Herbal+", "Best").withIndex()) care.addView(ui.button(n, 11f, selected = p.careLevel == i) { p.careLevel = i; fillPawn(p) }, ui.lin(-2, -2, 0f, 0, 0, 4, 0))
        body.addView(care)
        val doctors = game.colonists.filter { it.priority[WorkType.DOCTOR.ordinal] > 0 }
        if (doctors.isEmpty()) body.addView(ui.label("Nobody is set to Doctor!", 11f, ui.bad))
    }

    private fun fillGear(body: LinearLayout, p: Pawn) {
        val w = p.weapon
        body.addView(ui.label("Weapon", 12f, ui.accent, true))
        if (p.weaponItem != null) {
            body.addView(ui.label("${w.label} (${p.weaponQuality.label})", 12f))
            body.addView(ui.label("${w.damage.toInt()} dmg${if (w.burst > 1) " x${w.burst}" else ""} · range ${w.range.toInt()} · ${w.kind.label.lowercase()}", 10.5f, ui.dim))
            body.addView(ui.button("Drop weapon", 11f) { game.dropWeapon(p); fillPawn(p) }, ui.lin(-2, -2, 0f, 0, 3, 0, 0))
        } else body.addView(ui.label("Unarmed", 11.5f, ui.dim))
        body.addView(ui.label("Apparel", 12f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 8, 0, 2))
        if (p.apparel.isEmpty()) body.addView(ui.label("Naked", 11.5f, ui.warn))
        for (wn in p.apparel.toList()) {
            val ap = wn.type.apparel!!
            val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(ui.label("${wn.type.label} (${wn.quality.label}) ${(wn.hp / ap.hp * 100).toInt()}%", 11f), ui.lin(0, -2, 1f))
            row.addView(ui.button("Take off", 10.5f) { game.stripApparel(p, wn); fillPawn(p) })
            body.addView(row)
        }
        body.addView(ui.label("Insulation: cold +${p.insulationCold().toInt()}° · heat ${p.insulationHeat().toInt()}°", 11f, ui.dim), ui.lin(-2, -2, 0f, 0, 6, 0, 0))
        body.addView(ui.label("Armor: sharp ${(p.armorFor(2, true) * 100).toInt()}% · blunt ${(p.armorFor(2, false) * 100).toInt()}% (torso)", 11f, ui.dim))
        body.addView(ui.label("Colonists swap to better gear on their own from stockpiles.", 10.5f, ui.dim), ui.lin(-2, -2, 0f, 0, 4, 0, 0))
    }

    private fun fillSocial(body: LinearLayout, p: Pawn) {
        if (p.spouse >= 0) body.addView(ui.label("Spouse: ${game.pawnById(p.spouse)?.name ?: "?"}", 12f, ui.good))
        if (p.lover >= 0) body.addView(ui.label("Partner: ${game.pawnById(p.lover)?.name ?: "?"}", 12f, ui.good))
        body.addView(ui.label("Opinions", 12f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 6, 0, 2))
        var n = 0
        for (o in game.humansOnSide) {
            if (o === p) continue
            val v = p.opinion[o.id] ?: 0
            body.addView(ui.label("${o.name}: ${if (v >= 0) "+" else ""}$v", 11f, if (v < -10) ui.bad else if (v > 10) ui.good else ui.dim))
            n++
        }
        if (n == 0) body.addView(ui.label("Nobody else around.", 11f, ui.dim))
        body.addView(ui.label("Colonists chat during free time (Joy hours) and when idle. Relationships grow from friendly talk.", 10.5f, ui.dim), ui.lin(-2, -2, 0f, 0, 6, 0, 0))
    }

    private fun fillSkills(body: LinearLayout, p: Pawn) {
        for (s in SkillType.entries) {
            val lv = p.skill[s.ordinal]
            val pas = when (p.passion[s.ordinal]) { 2 -> " 🔥🔥"; 1 -> " 🔥"; else -> "" }
            val r = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            r.addView(ui.label(s.label, 11f, ui.dim), ui.lin(ui.dp(92), -2))
            r.addView(ui.bar(lv / 20f, ui.accent, 70))
            r.addView(ui.label("  $lv$pas", 11f))
            body.addView(r, ui.lin(-1, -2, 0f, 0, 2, 0, 0))
        }
        body.addView(ui.label("Traits", 12f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 8, 0, 2))
        if (p.traits.isEmpty()) body.addView(ui.label("None", 11f, ui.dim))
        for (t in p.traits) body.addView(ui.label("${t.label}: ${t.desc}", 11f))
        var dis = ""
        for (w in WorkType.entries) if (p.incapable and (1 shl w.ordinal) != 0) dis += w.label + ", "
        if (dis.isNotEmpty()) body.addView(ui.label("Incapable of: ${dis.trimEnd(' ', ',')}", 11f, ui.bad), ui.lin(-2, -2, 0f, 0, 4, 0, 0))
        if (p.backstory.isNotEmpty()) body.addView(ui.label("Backstory: ${p.backstory}", 11f, ui.dim), ui.lin(-2, -2, 0f, 0, 6, 0, 0))
    }

    private fun fillPolicy(body: LinearLayout, p: Pawn) {
        body.addView(ui.label("Food", 12f, ui.accent, true))
        val food = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        for ((i, n) in listOf("Anything", "No raw", "Meals only").withIndex()) food.addView(ui.button(n, 10.5f, selected = p.foodPolicy == i) { p.foodPolicy = i; fillPawn(p) }, ui.lin(-2, -2, 0f, 0, 0, 3, 0))
        body.addView(ui.hscroll(food))
        body.addView(ui.label("Outfit", 12f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 8, 0, 2))
        val outfit = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        for ((i, n) in listOf("Anything", "Worker (no armor)", "Soldier (armor)", "Nothing").withIndex()) outfit.addView(ui.button(n, 10.5f, selected = p.outfit == i) { p.outfit = i; fillPawn(p) }, ui.lin(-2, -2, 0f, 0, 0, 3, 0))
        body.addView(ui.hscroll(outfit))
        body.addView(ui.label("Drugs", 12f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 8, 0, 2))
        val drugs = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        for ((i, n) in listOf("None", "Social only", "Whenever bored").withIndex()) drugs.addView(ui.button(n, 10.5f, selected = (if (p.allowDrugs) 2 else p.drugPolicy) == i) { p.drugPolicy = i; p.allowDrugs = false; fillPawn(p) }, ui.lin(-2, -2, 0f, 0, 0, 3, 0))
        body.addView(ui.hscroll(drugs))
        body.addView(ui.label("Schedule (tap to change)", 12f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 8, 0, 2))
        val strip = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        for (h in 0 until 24) {
            val cell = TextView(a).apply { background = ui.bg(schedColors[p.schedule[h]], 3) }
            cell.setOnClickListener { p.schedule[h] = (p.schedule[h] + 1) % 4; cell.background = ui.bg(schedColors[p.schedule[h]], 3) }
            strip.addView(cell, ui.lin(ui.dp(10), ui.dp(24), 0f, 1, 0, 1, 0))
        }
        body.addView(strip)
        val leg = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        for (k in 0..3) leg.addView(ui.chip(schedNames[k], schedColors[k], 9f), ui.lin(-2, -2, 0f, 0, 3, 3, 0))
        body.addView(ui.hscroll(leg))
        body.addView(ui.label("Restrict to area", 12f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 8, 0, 2))
        val ar = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        ar.addView(ui.button("Anywhere", 10.5f, selected = p.areaRestriction == 0) { p.areaRestriction = 0; fillPawn(p) }, ui.lin(-2, -2, 0f, 0, 0, 3, 0))
        for (k in 0 until 3) ar.addView(ui.button(game.map.areaNames[k], 10.5f, selected = p.areaRestriction == k + 1) { p.areaRestriction = k + 1; fillPawn(p) }, ui.lin(-2, -2, 0f, 0, 0, 3, 0))
        body.addView(ui.hscroll(ar))
        body.addView(ui.label("Beds", 12f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 8, 0, 2))
        val bed = if (p.bedId >= 0) game.map.building[p.bedId] else null
        body.addView(ui.label(if (bed != null) "Sleeps in the ${bed.def.label.lowercase()} at (${bed.x},${bed.y})" else "No assigned bed", 11f, ui.dim))
    }
}
