package io.github.teamomuito.colony

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import io.github.teamomuito.colony.sim.Biome
import io.github.teamomuito.colony.sim.Bill
import io.github.teamomuito.colony.sim.BillMode
import io.github.teamomuito.colony.sim.Building
import io.github.teamomuito.colony.sim.Desig
import io.github.teamomuito.colony.sim.Difficulty
import io.github.teamomuito.colony.sim.Faction
import io.github.teamomuito.colony.sim.Game
import io.github.teamomuito.colony.sim.GameMap
import io.github.teamomuito.colony.sim.ItemCat
import io.github.teamomuito.colony.sim.ItemType
import io.github.teamomuito.colony.sim.Pawn
import io.github.teamomuito.colony.sim.PlantType
import io.github.teamomuito.colony.sim.Quality
import io.github.teamomuito.colony.sim.Recipe
import io.github.teamomuito.colony.sim.Scenario
import io.github.teamomuito.colony.sim.SkillType
import io.github.teamomuito.colony.sim.Storyteller
import io.github.teamomuito.colony.sim.Zone
import io.github.teamomuito.colony.sim.ZoneKind
import io.github.teamomuito.colony.sim.assignBed
import io.github.teamomuito.colony.sim.availableSurgeries
import io.github.teamomuito.colony.sim.queueSurgery
import io.github.teamomuito.colony.sim.buyItem
import io.github.teamomuito.colony.sim.buyPrice
import io.github.teamomuito.colony.sim.sellItem
import io.github.teamomuito.colony.sim.sellableStacks
import io.github.teamomuito.colony.sim.sellPrice
import io.github.teamomuito.colony.sim.setPrisonerBed
import io.github.teamomuito.colony.sim.trader
import kotlin.math.min

class Dialogs(val a: MainActivity) {
    internal val ui get() = a.ui
    internal val game get() = a.game

    internal fun dialog(title: String, build: (LinearLayout, Dialog) -> Unit, wide: Boolean = true): Dialog {
        val body = ui.column()
        body.setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10))
        val holder = ui.column()
        holder.background = ui.bg(0xFF1E1B17.toInt(), 14, 0x44FFFFFF)
        val head = ui.label(title, 15f, ui.accent, true).apply { setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(2)) }
        holder.addView(head)
        holder.addView(ui.scroll(body), ui.lin(-1, 0, 1f))
        val d = Dialog(a, android.R.style.Theme_Material_Dialog_NoActionBar)
        d.setContentView(holder)
        build(body, d)
        // The colony waits while a window is open.
        val prevSpeed = a.speed
        if (prevSpeed > 0) { a.speed = 0; a.refreshSpeed() }
        d.setOnDismissListener { if (a.speed == 0 && prevSpeed > 0 && !a.game.gameOver) { a.speed = prevSpeed; a.refreshSpeed() } }
        d.window?.let { w ->
            w.setBackgroundDrawable(ui.bg(0x00000000, 0))
            val m = a.resources.displayMetrics
            w.setLayout(min(m.widthPixels - ui.dp(30), ui.dp(if (wide) 640 else 480)), (m.heightPixels * 0.86f).toInt())
        }
        d.show()
        return d
    }

    internal fun closeRow(d: Dialog, label: String = "Close"): View =
        ui.button(label, 12f) { d.dismiss() }.also { (it as TextView).gravity = Gravity.CENTER }

    // ================================================================== menu
    fun menu() {
        dialog("Menu", { body, d ->
            fun item(t: String, act: () -> Unit) = body.addView(ui.button(t, 13f) { d.dismiss(); act() }, ui.lin(-1, -2, 0f, 0, 4, 0, 0))
            item("Save game") { a.save(); a.toast("Game saved") }
            item("Colony statistics") { a.panels.toggle("stats") }
            item("Storyteller & difficulty") { settings() }
            item("How to play") { help() }
            a.lastError()?.let { err -> item("Last error (for bug reports)") { dialog("Last error", { b2, d2 -> b2.addView(ui.mono(err, 10f)); b2.addView(closeRow(d2), ui.lin(-1, -2, 0f, 0, 10, 0, 0)) }) } }
            if (game.shipComplete()) item("🚀 Launch the escape ship") {
                AlertDialog.Builder(a).setMessage("Launch the ship and leave the rim for good? This ends the game.")
                    .setPositiveButton("Launch") { _, _ -> if (game.launchShip()) a.refreshHud() }.setNegativeButton("Not yet", null).show()
            }
            item("New colony…") { newColony(false) }
            body.addView(closeRow(d), ui.lin(-1, -2, 0f, 0, 10, 0, 0))
        }, false)
    }

    fun overview() {
        val d = Dialog(a, android.R.style.Theme_Material_Dialog_NoActionBar)
        val box = ui.column()
        box.background = ui.bg(0xFF1E1B17.toInt(), 12, 0x44FFFFFF)
        box.setPadding(ui.dp(8), ui.dp(6), ui.dp(8), ui.dp(8))
        box.addView(ui.label("World map · green = colonists, red = enemies, yellow = tame animals", 11f, ui.dim))
        val v = OverviewView(a, game) { x, y -> a.view.centerOn(x.toFloat(), y.toFloat()); d.dismiss() }
        val side = (a.resources.displayMetrics.heightPixels * 0.78f).toInt()
        box.addView(v, ui.lin(side, side))
        d.setContentView(box)
        val prevSpeed = a.speed
        if (prevSpeed > 0) { a.speed = 0; a.refreshSpeed() }
        d.setOnDismissListener { if (a.speed == 0 && prevSpeed > 0) { a.speed = prevSpeed; a.refreshSpeed() } }
        d.show()
    }

    fun settings() {
        dialog("Storyteller & difficulty", { body, d ->
            body.addView(ui.label("Storyteller", 13f, ui.accent, true))
            val sRow = ui.column()
            fun renderS() {
                sRow.removeAllViews()
                for (s in Storyteller.entries) sRow.addView(ui.button("${s.label}: ${s.desc}", 11.5f, selected = game.storyteller == s) { game.storyteller = s; renderS() }, ui.lin(-1, -2, 0f, 0, 3, 0, 0))
            }
            renderS(); body.addView(sRow)
            body.addView(ui.label("Difficulty", 13f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 10, 0, 0))
            val dRow = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            fun renderD() {
                dRow.removeAllViews()
                for (df in Difficulty.entries) dRow.addView(ui.button(df.label, 11.5f, selected = game.difficulty == df) { game.difficulty = df; renderD() }, ui.lin(-2, -2, 0f, 0, 3, 4, 0))
            }
            renderD(); body.addView(ui.hscroll(dRow))
            body.addView(closeRow(d), ui.lin(-1, -2, 0f, 0, 12, 0, 0))
        }, false)
    }

    fun help() {
        val msg = """
            Survive on a hostile rimworld, build a colony, and eventually build a ship to leave it.

            BASICS
            • Drag to look around, pinch to zoom. Tap people and things for details.
            • Architect → Orders: mine rock, chop trees, hunt, tame, deconstruct.
            • Architect → Zones: stockpiles (where hauled items go), growing zones (crops), dumping.
            • Colonists work by priority (Work tab) and the Schedule (Anything / Work / Joy / Sleep).

            SURVIVAL
            • Build beds (colonists want their own), tables, and a campfire or stove. Add a cooking bill on it.
            • Workbenches have bills: tap the building, then Open. Smith, tailor and craft there.
            • Mood matters: food, sleep, comfort, beauty, recreation. Low mood causes mental breaks.
            • Keep warm: clothes (parkas) and heaters. Rooms need walls and doors; fire and campfires heat them.

            DANGERS
            • Raids come every few days. Draft a colonist, then tap the ground to move or tap an enemy to attack.
            • Walls, doors, sandbags, turrets, traps and mortars help.
            • Wounds bleed and infect; doctors need medicine and a bed. Hospital beds are best.
            • Downed raiders can be captured into prisoner beds and recruited. Toggle a bed as prisoner bed in its menu.

            LONG GAME
            • Research unlocks power, firearms, hydroponics, bionics and finally the ship parts.
            • Traders visit; sell your surplus and buy what you lack.
        """.trimIndent()
        dialog("How to play", { body, d ->
            body.addView(ui.label(msg, 12f))
            body.addView(closeRow(d), ui.lin(-1, -2, 0f, 0, 12, 0, 0))
        })
    }

    fun rename(p: Pawn) {
        val input = EditText(a).apply { setText(p.name); setTextColor(ui.text); setSingleLine() }
        AlertDialog.Builder(a).setTitle("Rename").setView(input)
            .setPositiveButton("OK") { _, _ -> val t = input.text.toString().trim(); if (t.isNotEmpty()) { p.name = t; a.panels.showPawn(p) } }
            .setNegativeButton("Cancel", null).show()
    }

    fun gameOver() {
        val won = game.won
        AlertDialog.Builder(a)
            .setTitle(if (won) "You escaped!" else "Colony lost")
            .setMessage(
                if (won) "The ship leaves the rim behind. You survived ${game.day} days and beat back ${game.raidsSurvived} raids."
                else "Everyone is gone after ${game.day} days. You beat back ${game.raidsSurvived} raids.",
            )
            .setCancelable(false)
            .setPositiveButton("New colony") { _, _ -> newColony(false) }
            .show()
    }

    // ================================================================== new colony wizard
    fun newColony(firstRun: Boolean) {
        var seed = System.currentTimeMillis()
        var scenario = Scenario.CRASHLANDED
        var biome = Biome.TEMPERATE
        var story = Storyteller.MARLOWE
        var diff = Difficulty.RANDY
        var size = Game.MAP_SIZE
        var g = Game(seed, GameMap.generateFor(size, size, seed, biome))
        val crew = ArrayList<Pawn>()
        fun crewSize() = when (scenario) { Scenario.CRASHLANDED -> 3; Scenario.LOST_TRIBE -> 5; Scenario.RICH_EXPLORER -> 1; Scenario.SOLO -> 1 }
        fun roll(): Pawn = g.newHuman(0, 0, Faction.PLAYER, scenario == Scenario.LOST_TRIBE)
        fun resetCrew() { g.pawns.clear(); crew.clear(); repeat(crewSize()) { crew.add(roll()) } }
        resetCrew()

        dialog("New colony", { body, d ->
            val scenarioBox = ui.column(); val biomeBox = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            val sizeBox = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            val storyBox = ui.column(); val diffBox = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            val crewBox = ui.column()
            fun renderCrew() {
                crewBox.removeAllViews()
                for ((i, p) in crew.withIndex()) {
                    val card = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = ui.bg(0xFF26221C.toInt(), 8); setPadding(ui.dp(8), ui.dp(5), ui.dp(8), ui.dp(5)) }
                    val info = ui.column()
                    info.addView(ui.label("${p.name}, ${p.age}", 12.5f, ui.text, true))
                    info.addView(ui.label(p.backstory, 10.5f, ui.dim))
                    val top = SkillType.entries.sortedByDescending { p.skill[it.ordinal] }.take(3).joinToString("  ") { "${it.label} ${p.skill[it.ordinal]}${if (p.passion[it.ordinal] > 0) "🔥" else ""}" }
                    info.addView(ui.label(top, 10.5f, ui.accent))
                    info.addView(ui.label(p.traits.joinToString { it.label }, 10.5f, ui.dim))
                    card.addView(info, ui.lin(0, -2, 1f))
                    card.addView(ui.button("Reroll", 11f) { g.pawns.remove(p); crew[i] = roll(); renderCrew() }, ui.lin(-2, -2, 0f, 4, 0, 0, 0))
                    crewBox.addView(card, ui.lin(-1, -2, 0f, 0, 4, 0, 0))
                }
            }
            fun renderScenario() {
                scenarioBox.removeAllViews()
                for (s in Scenario.entries) scenarioBox.addView(ui.button("${s.label}: ${s.desc}", 11.5f, selected = scenario == s) { scenario = s; resetCrew(); renderScenario(); renderCrew() }, ui.lin(-1, -2, 0f, 0, 3, 0, 0))
            }
            fun renderBiome() {
                biomeBox.removeAllViews()
                for (b in Biome.entries) biomeBox.addView(ui.button(b.label, 11f, selected = biome == b) {
                    biome = b; seed = System.currentTimeMillis(); g = Game(seed, GameMap.generateFor(size, size, seed, biome)); resetCrew(); renderBiome(); renderCrew()
                }, ui.lin(-2, -2, 0f, 0, 3, 4, 0))
            }
            fun renderSize() {
                sizeBox.removeAllViews()
                for ((label, n) in listOf("Small 75" to 75, "Medium 100" to 100, "Large 150" to 150, "Huge 200" to 200)) sizeBox.addView(ui.button(label, 11f, selected = size == n) {
                    size = n; g = Game(seed, GameMap.generateFor(size, size, seed, biome)); resetCrew(); renderSize(); renderCrew()
                }, ui.lin(-2, -2, 0f, 0, 3, 4, 0))
            }
            fun renderStory() {
                storyBox.removeAllViews()
                for (s in Storyteller.entries) storyBox.addView(ui.button("${s.label}: ${s.desc}", 11.5f, selected = story == s) { story = s; renderStory() }, ui.lin(-1, -2, 0f, 0, 3, 0, 0))
            }
            fun renderDiff() {
                diffBox.removeAllViews()
                for (df in Difficulty.entries) diffBox.addView(ui.button(df.label, 11f, selected = diff == df) { diff = df; renderDiff() }, ui.lin(-2, -2, 0f, 0, 3, 4, 0))
            }
            body.addView(ui.label("Scenario", 13f, ui.accent, true)); body.addView(scenarioBox)
            body.addView(ui.label("Biome", 13f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 8, 0, 0)); body.addView(ui.hscroll(biomeBox))
            body.addView(ui.label("Map size", 13f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 8, 0, 0)); body.addView(ui.hscroll(sizeBox))
            body.addView(ui.label("Storyteller", 13f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 8, 0, 0)); body.addView(storyBox)
            body.addView(ui.label("Difficulty", 13f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 8, 0, 0)); body.addView(ui.hscroll(diffBox))
            body.addView(ui.label("Your colonists", 13f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 8, 0, 0)); body.addView(crewBox)
            renderScenario(); renderBiome(); renderSize(); renderStory(); renderDiff(); renderCrew()
            val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(ui.button("Start", 14f, selected = true) {
                g.storyteller = story; g.difficulty = diff
                g.startNewColony(scenario, crew.toList())
                d.dismiss()
                a.restart(g)
            }, ui.lin(0, -2, 1f, 0, 0, 4, 0))
            if (!firstRun) row.addView(ui.button("Cancel", 13f) { d.dismiss() }, ui.lin(0, -2, 1f, 4, 0, 0, 0))
            body.addView(row, ui.lin(-1, -2, 0f, 0, 12, 0, 0))
        })
    }

    // ================================================================== building dialog
    fun building(b: Building) {
        dialog(b.def.label, { body, d ->
            fun render() {
                body.removeAllViews()
                val i = game.map.idx(b.x, b.y)
                if (game.map.building[i] !== b) { d.dismiss(); return }
                body.addView(ui.label("HP ${b.hp.toInt()}/${b.def.hp.toInt()}${if (b.quality != Quality.NORMAL) " · ${b.quality.label}" else ""}${if (b.def.fuelCap > 0f) " · fuel ${b.fuel.toInt()}/${b.def.fuelCap.toInt()}" else ""}${if (b.def.consumesPower) if (b.powered) " · powered" else " · NO POWER" else ""}", 12f, ui.dim))
                val actions = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
                actions.addView(ui.button(if (b.forbidden) "Allow use" else "Forbid", 11.5f, selected = b.forbidden) { b.forbidden = !b.forbidden; render() }, ui.lin(-2, -2, 0f, 0, 6, 4, 0))
                actions.addView(ui.button("Repair", 11.5f) { game.designate(b.x, b.y, Desig.REPAIR); d.dismiss() }, ui.lin(-2, -2, 0f, 0, 6, 4, 0))
                actions.addView(ui.button("Deconstruct", 11.5f) { game.designate(b.x, b.y, Desig.DECON); d.dismiss() }, ui.lin(-2, -2, 0f, 0, 6, 4, 0))
                body.addView(ui.hscroll(actions))
                if (b.def.sleeps) {
                    body.addView(ui.label("Owner: ${game.pawnById(b.ownerId)?.name ?: "nobody"}", 12f), ui.lin(-2, -2, 0f, 0, 10, 0, 2))
                    val owners = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
                    owners.addView(ui.button("Nobody", 11f, selected = b.ownerId < 0) { game.assignBed(b, null); render() }, ui.lin(-2, -2, 0f, 0, 0, 4, 0))
                    for (p in game.humansOnSide) owners.addView(ui.button(p.name.substringBefore(' '), 11f, selected = b.ownerId == p.id) { game.assignBed(b, p); render() }, ui.lin(-2, -2, 0f, 0, 0, 4, 0))
                    body.addView(ui.hscroll(owners))
                    body.addView(ui.button(if (b.prisonerBed) "Prisoner bed: ON" else "Prisoner bed: off", 11.5f, selected = b.prisonerBed) { game.setPrisonerBed(b, !b.prisonerBed); render() }, ui.lin(-2, -2, 0f, 0, 8, 0, 0))
                    if (b.def.medical) body.addView(ui.label("Medical bed: patients heal faster and infections are less likely.", 11f, ui.dim))
                }
                if (b.def.workbench && b.def.fuelCap <= 0f || b.def.workbench) billsSection(body, b) { render() }
                body.addView(closeRow(d), ui.lin(-1, -2, 0f, 0, 12, 0, 0))
            }
            render()
        })
    }

    private fun billsSection(body: LinearLayout, b: Building, rerender: () -> Unit) {
        val recipes = Recipe.entries.filter { b.def in it.benches }
        if (recipes.isEmpty()) return
        body.addView(ui.label("Bills", 13f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 12, 0, 2))
        if (b.bills.isEmpty()) body.addView(ui.label("No bills. Add one so colonists use this workbench.", 11.5f, ui.dim))
        for (bill in b.bills.toList()) {
            val card = ui.column()
            card.background = ui.bg(0xFF26221C.toInt(), 8)
            card.setPadding(ui.dp(8), ui.dp(5), ui.dp(8), ui.dp(5))
            val unlocked = bill.recipe.available(game.researchDone)
            card.addView(ui.label("${bill.recipe.label}${if (bill.paused) " (paused)" else ""}${if (!unlocked) " (locked)" else ""}", 12.5f, ui.text, true))
            card.addView(ui.label(bill.recipe.inputs.joinToString(" + ") { "${it.count} ${it.label.lowercase()}" } + "  →  ${bill.recipe.outCount} ${bill.recipe.out.label.lowercase()}", 10.5f, ui.dim))
            val r1 = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            r1.addView(ui.button(bill.mode.label, 11f) { bill.mode = BillMode.entries[(bill.mode.ordinal + 1) % 3]; rerender() }, ui.lin(-2, -2, 0f, 0, 0, 4, 0))
            if (bill.mode != BillMode.FOREVER) {
                r1.addView(ui.button("−", 12f) { bill.target = (bill.target - 1).coerceAtLeast(1); rerender() }, ui.lin(-2, -2, 0f, 0, 0, 2, 0))
                r1.addView(ui.label(" ${bill.target} ", 13f, ui.accent, true))
                r1.addView(ui.button("+", 12f) { bill.target += 1; rerender() }, ui.lin(-2, -2, 0f, 2, 0, 2, 0))
                r1.addView(ui.button("+10", 11f) { bill.target += 10; rerender() }, ui.lin(-2, -2, 0f, 2, 0, 4, 0))
            }
            r1.addView(ui.label("done ${bill.done}", 11f, ui.dim))
            card.addView(ui.hscroll(r1))
            val r2 = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            r2.addView(ui.button(if (bill.paused) "Resume" else "Pause", 11f) { bill.paused = !bill.paused; rerender() }, ui.lin(-2, -2, 0f, 0, 0, 4, 0))
            r2.addView(ui.button("Skill ≥ ${bill.minSkill}", 11f) { bill.minSkill = (bill.minSkill + 2) % 22; rerender() }, ui.lin(-2, -2, 0f, 0, 0, 4, 0))
            r2.addView(ui.button("Ingredients", 11f) { ingredientFilter(bill) }, ui.lin(-2, -2, 0f, 0, 0, 4, 0))
            r2.addView(ui.button("Delete", 11f) { b.bills.remove(bill); rerender() }, ui.lin(-2, -2, 0f, 0, 0, 0, 0))
            card.addView(ui.hscroll(r2), ui.lin(-1, -2, 0f, 0, 3, 0, 0))
            body.addView(card, ui.lin(-1, -2, 0f, 0, 6, 0, 0))
        }
        body.addView(ui.button("+ Add bill", 12.5f, selected = true) { addBill(b, recipes, rerender) }, ui.lin(-1, -2, 0f, 0, 8, 0, 0))
    }

    private fun addBill(b: Building, recipes: List<Recipe>, rerender: () -> Unit) {
        dialog("Add a bill", { body, d ->
            for (r in recipes) {
                val ok = r.available(game.researchDone)
                val label = "${r.label}${if (!ok) "  🔒 ${r.research?.label}" else ""}\n${r.inputs.joinToString(" + ") { "${it.count} ${it.label.lowercase()}" }} → ${r.outCount} ${r.out.label.lowercase()}"
                val btn = ui.button(label, 11.5f) {
                    if (!ok) a.toast("Needs research: ${r.research?.label}") else {
                        val bill = Bill(r); b.bills.add(bill); d.dismiss(); rerender()
                    }
                }
                if (!ok) btn.alpha = 0.55f
                body.addView(btn, ui.lin(-1, -2, 0f, 0, 3, 0, 0))
            }
            body.addView(closeRow(d), ui.lin(-1, -2, 0f, 0, 10, 0, 0))
        }, false)
    }

    private fun ingredientFilter(bill: Bill) {
        val all = bill.recipe.inputs.flatMap { it.types }.toSet()
        dialog("Allowed ingredients", { body, d ->
            body.addView(ui.label("Choose which items this bill may use.", 11.5f, ui.dim))
            for (t in all) {
                val cb = CheckBox(a).apply { text = t.label; setTextColor(ui.text); isChecked = bill.allowedItems?.contains(t) ?: true }
                cb.setOnCheckedChangeListener { _, on ->
                    val set = bill.allowedItems ?: HashSet(all).also { bill.allowedItems = it }
                    if (on) set.add(t) else set.remove(t)
                    if (set.size == all.size) bill.allowedItems = null
                }
                body.addView(cb)
            }
            body.addView(closeRow(d, "Done"), ui.lin(-1, -2, 0f, 0, 10, 0, 0))
        }, false)
    }

    // ================================================================== zone dialog
    fun zone(z: Zone) {
        dialog(z.name, { body, d ->
            fun render() {
                body.removeAllViews()
                body.addView(ui.label("${if (z.kind == ZoneKind.STOCKPILE) "Stockpile" else if (z.kind == ZoneKind.GROWING) "Growing zone" else "Dumping zone"} · ${z.cells} cells", 12f, ui.dim))
                body.addView(ui.button("Rename", 11.5f) {
                    val input = EditText(a).apply { setText(z.name); setTextColor(ui.text); setSingleLine() }
                    AlertDialog.Builder(a).setTitle("Rename zone").setView(input).setPositiveButton("OK") { _, _ -> if (input.text.isNotBlank()) { z.name = input.text.toString(); render() } }.setNegativeButton("Cancel", null).show()
                }, ui.lin(-2, -2, 0f, 0, 6, 0, 0))
                if (z.kind == ZoneKind.GROWING) {
                    body.addView(ui.label("Crop", 13f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 10, 0, 2))
                    val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
                    for (c in PlantType.entries.filter { it.crop }) row.addView(ui.button(c.label, 11f, selected = z.crop == c) { z.crop = c; render() }, ui.lin(-2, -2, 0f, 0, 0, 4, 0))
                    body.addView(ui.hscroll(row))
                    body.addView(ui.button(if (z.sow) "Sowing: on" else "Sowing: off (harvest only)", 11.5f, selected = z.sow) { z.sow = !z.sow; render() }, ui.lin(-2, -2, 0f, 0, 6, 0, 0))
                } else {
                    body.addView(ui.label("Priority", 13f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 10, 0, 2))
                    val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
                    for ((k, n) in listOf("Low", "Normal", "Preferred", "Important", "Critical").withIndex()) row.addView(ui.button(n, 11f, selected = z.priority == k) { z.priority = k; render() }, ui.lin(-2, -2, 0f, 0, 0, 4, 0))
                    body.addView(ui.hscroll(row))
                    body.addView(ui.label("Minimum quality", 13f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 10, 0, 2))
                    val qrow = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
                    for (q in Quality.entries) qrow.addView(ui.button(q.label, 10.5f, selected = z.minQuality == q) { z.minQuality = q; render() }, ui.lin(-2, -2, 0f, 0, 0, 4, 0))
                    body.addView(ui.hscroll(qrow))
                    body.addView(ui.label("Allowed items", 13f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 10, 0, 2))
                    val bulk = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
                    bulk.addView(ui.button("Allow all", 11f) { z.allowed.fill(true); z.allowed[ItemType.CORPSE_HUMAN.ordinal] = z.kind == ZoneKind.DUMPING; render() }, ui.lin(-2, -2, 0f, 0, 0, 4, 0))
                    bulk.addView(ui.button("Clear all", 11f) { z.allowed.fill(false); render() }, ui.lin(-2, -2, 0f, 0, 0, 4, 0))
                    body.addView(bulk)
                    for (cat in ItemCat.entries) {
                        val items = ItemType.entries.filter { it.cat == cat }
                        if (items.isEmpty()) continue
                        val master = CheckBox(a).apply { text = cat.label; setTextColor(ui.accent); isChecked = items.all { z.allowed[it.ordinal] } }
                        master.setOnClickListener { val on = master.isChecked; for (t in items) z.allowed[t.ordinal] = on; render() }
                        body.addView(master)
                        for (t in items) {
                            val cb = CheckBox(a).apply { text = t.label; setTextColor(ui.text); isChecked = z.allowed[t.ordinal]; textSize = 12f }
                            cb.setOnCheckedChangeListener { _, on -> z.allowed[t.ordinal] = on }
                            body.addView(cb, ui.lin(-2, -2, 0f, 16, 0, 0, 0))
                        }
                    }
                }
                body.addView(ui.button("Delete zone", 12f) { game.deleteZone(z); d.dismiss() }, ui.lin(-1, -2, 0f, 0, 14, 0, 0))
                body.addView(closeRow(d), ui.lin(-1, -2, 0f, 0, 6, 0, 0))
            }
            render()
        })
    }

    fun surgery(p: Pawn) {
        dialog("Surgery: ${p.name}", { body, d ->
            body.addView(ui.label("The patient walks to a bed; a doctor (skill 3+) operates. Hospital beds improve success.", 11.5f, ui.dim))
            val ops = game.availableSurgeries(p)
            if (ops.isEmpty()) body.addView(ui.label("No operations available. Research Prosthetics or Bionics for implants.", 12f, ui.warn))
            for (o in ops) {
                val cost = if (o.implant != null) o.implant!!.cost.joinToString(", ") { "${it.second} ${it.first.label.lowercase()}" } else "no materials"
                body.addView(ui.button("${o.label(p)}\n$cost", 11.5f) { game.queueSurgery(p, o); d.dismiss(); a.panels.showPawn(p) }, ui.lin(-1, -2, 0f, 0, 3, 0, 0))
            }
            body.addView(closeRow(d), ui.lin(-1, -2, 0f, 0, 10, 0, 0))
        }, false)
    }

    // ================================================================== trade
    fun trade() {
        val t = game.trader()
        if (t == null) { a.toast("No trader is here right now."); return }
        dialog("Trade with ${t.name}", { body, d ->
            fun render() {
                val y = (body.parent as? android.widget.ScrollView)?.scrollY ?: 0
                body.removeAllViews()
                val mySilver = game.map.countItems(ItemType.SILVER)
                body.addView(ui.label("Your silver: $mySilver   ·   Trader's silver: ${t.silver}", 12.5f, ui.accent, true))
                body.addView(ui.label("Sell (from your stockpiles)", 13f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 10, 0, 2))
                val mine = game.sellableStacks().filter { it.value.type != ItemType.SILVER }
                    .groupBy { it.value.type }.mapValues { e -> e.value.sumOf { it.value.count } }.toList().sortedBy { it.first.cat.ordinal }
                if (mine.isEmpty()) body.addView(ui.label("Nothing to sell. Items must be in a stockpile.", 11.5f, ui.dim))
                for ((type, n) in mine) {
                    val price = game.sellPrice(type)
                    val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                    row.addView(ui.label("${type.label} ×$n  (${String.format("%.1f", price)} each)", 11.5f), ui.lin(0, -2, 1f))
                    row.addView(ui.button("1", 11f) { game.sellItem(t, type, 1); render() }, ui.lin(-2, -2, 0f, 0, 0, 3, 0))
                    row.addView(ui.button("10", 11f) { game.sellItem(t, type, 10); render() }, ui.lin(-2, -2, 0f, 0, 0, 3, 0))
                    row.addView(ui.button("All", 11f) { game.sellItem(t, type, n); render() }, ui.lin(-2, -2, 0f, 0, 0, 0, 0))
                    body.addView(row, ui.lin(-1, -2, 0f, 0, 3, 0, 0))
                }
                body.addView(ui.label("Buy", 13f, ui.accent, true), ui.lin(-2, -2, 0f, 0, 12, 0, 2))
                if (t.stock.isEmpty()) body.addView(ui.label("The trader has nothing left.", 11.5f, ui.dim))
                for ((type, n) in t.stock.entries.sortedBy { it.key.cat.ordinal }) {
                    val price = game.buyPrice(type)
                    val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                    row.addView(ui.label("${type.label} ×$n  (${String.format("%.1f", price)} each)", 11.5f), ui.lin(0, -2, 1f))
                    row.addView(ui.button("1", 11f) { if (!game.buyItem(t, type, 1)) a.toast("Not enough silver"); render() }, ui.lin(-2, -2, 0f, 0, 0, 3, 0))
                    row.addView(ui.button("10", 11f) { if (!game.buyItem(t, type, 10)) a.toast("Not enough silver"); render() }, ui.lin(-2, -2, 0f, 0, 0, 3, 0))
                    row.addView(ui.button("All", 11f) { if (!game.buyItem(t, type, n)) a.toast("Not enough silver"); render() }, ui.lin(-2, -2, 0f, 0, 0, 0, 0))
                    body.addView(row, ui.lin(-1, -2, 0f, 0, 3, 0, 0))
                }
                body.addView(ui.label("Purchased goods are dropped beside the trader; colonists haul them in.", 10.5f, ui.dim), ui.lin(-2, -2, 0f, 0, 8, 0, 0))
                body.addView(closeRow(d), ui.lin(-1, -2, 0f, 0, 8, 0, 0))
                (body.parent as? android.widget.ScrollView)?.post { (body.parent as? android.widget.ScrollView)?.scrollTo(0, y) }
            }
            render()
        })
    }
}
