package io.github.teamomuito.colony

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import java.io.File

/** The main menu: the app opens here. Every choice starts [MainActivity] with an action, or opens a dialog. */
class MenuActivity : Activity() {
    private lateinit var ui: UiKit
    private lateinit var prefs: Prefs
    private val saveFile get() = File(filesDir, "colony.sav")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = UiKit(this)
        prefs = Prefs(this)
        build()
        if (!prefs.tutorialAsked) root.post { askAboutTutorial() }
    }

    private lateinit var root: LinearLayout

    private fun build() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(0xFF15130F.toInt())
            setPadding(ui.dp(24), ui.dp(24), ui.dp(24), ui.dp(24))
        }
        root.addView(ui.label("COLONY", 34f, ui.accent, true).apply { gravity = Gravity.CENTER; letterSpacing = 0.12f }, ui.lin(-2, -2, 0f, 0, 24, 0, 2))
        root.addView(ui.label("A RimWorld-style colony sim. Survive, build, and leave the rim.", 12f, ui.dim).apply { gravity = Gravity.CENTER }, ui.lin(-2, -2, 0f, 0, 0, 0, 24))

        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val hasSave = saveFile.exists()
        fun item(label: String, enabled: Boolean = true, onClick: () -> Unit) {
            column.addView(ui.button(label, 15f) { if (enabled) ui.guard(onClick) }.apply { alpha = if (enabled) 1f else 0.4f },
                ui.lin(-1, -2, 0f, 0, 0, 0, 8))
        }
        item("Continue", hasSave) { start(MainActivity.ACTION_CONTINUE) }
        item("New colony") { confirmReplace { start(MainActivity.ACTION_NEW) } }
        item("Tutorial colony") { confirmReplace { start(MainActivity.ACTION_TUTORIAL) } }
        item("Settings") { settings() }
        item("How to play") { howToPlay() }
        item("Quit") { finishAffinity() }
        val width = minOf(resources.displayMetrics.widthPixels - ui.dp(48), ui.dp(360))
        root.addView(column, LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT))

        val scroll = ScrollView(this).apply { addView(root); isVerticalScrollBarEnabled = false; isFillViewport = true }
        setContentView(scroll)
    }

    private fun start(action: String) {
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_ACTION, action))
        finish()
    }

    /** A new colony or the tutorial colony replaces the save; ask first if there is one. */
    private fun confirmReplace(go: () -> Unit) {
        if (!saveFile.exists()) { go(); return }
        AlertDialog.Builder(this).setTitle("Replace your colony?")
            .setMessage("Starting a new colony replaces the one you saved.")
            .setPositiveButton("Replace") { _, _ -> go() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun askAboutTutorial() {
        AlertDialog.Builder(this).setTitle("Start with the tutorial?")
            .setMessage("A short, optional walkthrough of the basics. It can be skipped at any step, turned off in Settings, or replayed from the menu.")
            .setPositiveButton("Yes, teach me") { _, _ -> prefs.tutorialOn = true; prefs.tutorialAsked = true }
            .setNegativeButton("No thanks") { _, _ -> prefs.tutorialOn = false; prefs.tutorialAsked = true }
            .setCancelable(false).show()
    }

    private fun settings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.dp(16), ui.dp(8), ui.dp(16), 0) }
        val check = CheckBox(this).apply {
            text = "Show the tutorial in new colonies"
            setTextColor(ui.text)
            isChecked = prefs.tutorialOn
            setOnCheckedChangeListener { _, on -> prefs.tutorialOn = on }
        }
        box.addView(check)
        box.addView(ui.button("Start the tutorial over", 12f) {
            prefs.saveTutorial(io.github.teamomuito.colony.sim.TutorialState())
            toastDone()
        }, ui.lin(-1, -2, 0f, 0, 10, 0, 0))
        AlertDialog.Builder(this).setTitle("Settings").setView(box).setPositiveButton("Done", null).show()
    }

    private fun toastDone() = android.widget.Toast.makeText(this, "The tutorial will start from the beginning.", android.widget.Toast.LENGTH_SHORT).show()

    private fun howToPlay() {
        val text = ui.label(HelpText.text, 12f).apply { setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12)) }
        val scroll = ScrollView(this).apply { addView(text) }
        AlertDialog.Builder(this).setTitle("How to play").setView(scroll).setPositiveButton("Close", null).show()
    }
}
