package io.github.teamomuito.colony

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.teamomuito.colony.sim.SaveException
import io.github.teamomuito.colony.sim.SaveInfo
import io.github.teamomuito.colony.sim.SaveStore
import java.text.DateFormat
import java.util.Date

/** The save list, the name prompt and the discard confirmation, shared by the main menu and the game menu. */
object SaveUi {
    fun playtime(ms: Long): String {
        val mins = ms / 60_000
        return if (mins >= 60) "${mins / 60}h ${"%02d".format(mins % 60)}m" else "${mins}m"
    }

    /** One line of metadata for a save: what the player needs to choose between them. */
    fun describe(info: SaveInfo): String {
        if (info.damaged) return "Damaged: ${info.problem}"
        val stamp = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(info.savedAtMillis))
        return "${info.colonyName} · day ${info.day + 1} · ${info.colonists} colonist${if (info.colonists == 1) "" else "s"} · played ${playtime(info.playtimeMs)}\nSaved $stamp"
    }

    /**
     * Lists every save. Loading calls [onLoad] with the save; deleting asks first and only ever deletes the one row chosen.
     * Damaged saves are listed with their problem, and can only be deleted.
     */
    fun showList(a: Activity, ui: UiKit, store: SaveStore, title: String, onLoad: (SaveInfo) -> Unit) {
        lateinit var dialog: AlertDialog
        lateinit var box: LinearLayout
        fun render() {
            box.removeAllViews()
            val infos = store.list()
            if (infos.isEmpty()) box.addView(ui.label("No saved games yet. Use Save as… in the game menu to keep one.", 12f, ui.dim))
            for (info in infos) {
                val card = LinearLayout(a).apply {
                    orientation = LinearLayout.VERTICAL
                    background = ui.bg(0xFF26221C.toInt(), 8)
                    setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8))
                }
                val heading = when (info.id) {
                    SaveStore.AUTOSAVE -> "Autosave (Continue)"
                    SaveStore.QUICKSAVE -> "Quicksave"
                    else -> info.name
                }
                card.addView(ui.label(heading, 13f, if (info.damaged) ui.bad else ui.text, true))
                card.addView(ui.label(describe(info), 10.5f, if (info.damaged) ui.bad else ui.dim))
                val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
                if (!info.damaged) row.addView(ui.button("Load", 11f) { dialog.dismiss(); onLoad(info) }, ui.lin(0, -2, 1f, 0, 0, 4, 0))
                row.addView(ui.button("Delete", 11f) { confirmDelete(a, info) { store.delete(info.id); render() } }, ui.lin(0, -2, 1f, 4, 0, 0, 0))
                card.addView(row, ui.lin(-1, -2, 0f, 0, 6, 0, 0))
                box.addView(card, ui.lin(-1, -2, 0f, 0, 6, 0, 0))
            }
        }
        box = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.dp(12), ui.dp(4), ui.dp(12), 0) }
        render()
        val scroll = ScrollView(a).apply { addView(box) }
        dialog = AlertDialog.Builder(a).setTitle(title).setView(scroll).setNegativeButton("Close", null).create()
        dialog.show()
    }

    fun confirmDelete(a: Activity, info: SaveInfo, onYes: () -> Unit) {
        val what = when (info.id) {
            SaveStore.QUICKSAVE -> "the quicksave"
            SaveStore.AUTOSAVE -> "the autosave (the Continue game)"
            else -> "\"${info.name}\""
        }
        AlertDialog.Builder(a).setTitle("Delete save?")
            .setMessage("Delete $what? This cannot be undone. Other saves are not affected.")
            .setPositiveButton("Delete") { _, _ -> onYes() }
            .setNegativeButton("Cancel", null).show()
    }

    /**
     * Asks for a name for a new save, or for the new name of [exceptId]. Invalid names and duplicates are explained
     * and the prompt stays open, so nothing is saved under a name the player didn't accept.
     */
    fun promptName(a: Activity, store: SaveStore, title: String, initial: String, exceptId: String? = null, onName: (String) -> Unit) {
        val field = EditText(a).apply {
            setText(initial); setSelectAllOnFocus(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            filters = arrayOf(android.text.InputFilter.LengthFilter(SaveStore.MAX_NAME))
            setPadding(24, 16, 24, 16)
        }
        val dialog = AlertDialog.Builder(a).setTitle(title).setView(field)
            .setPositiveButton("Save", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val name = store.cleanName(field.text.toString())
                    store.ensureUniqueName(name, exceptId)
                    dialog.dismiss()
                    onName(name)
                } catch (e: SaveException) {
                    field.error = e.message
                }
            }
        }
        dialog.show()
    }

    /** Runs [go] now, or after the player confirms they are happy to lose the progress that has not been saved. */
    fun ifNothingUnsaved(a: Activity, unsaved: Boolean, message: String, go: () -> Unit) {
        if (!unsaved) { go(); return }
        AlertDialog.Builder(a).setTitle("Discard unsaved progress?")
            .setMessage(message)
            .setPositiveButton("Discard") { _, _ -> go() }
            .setNegativeButton("Keep playing", null).show()
    }
}
