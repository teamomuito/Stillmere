package io.github.teamomuito.colony

import android.content.Context
import io.github.teamomuito.colony.sim.TutorialState

/** Per-device settings. Tutorial progress lives here too, so a loaded colony keeps its place in the tutorial. */
class Prefs(c: Context) {
    private val p = c.getSharedPreferences("colony_prefs", Context.MODE_PRIVATE)

    /** Whether new colonies start with the tutorial. */
    var tutorialOn: Boolean
        get() = p.getBoolean("tutorial_on", true)
        set(v) { p.edit().putBoolean("tutorial_on", v).apply() }

    /** Whether the first-run question about the tutorial has been answered. */
    var tutorialAsked: Boolean
        get() = p.getBoolean("tutorial_asked", false)
        set(v) { p.edit().putBoolean("tutorial_asked", v).apply() }

    /** The named save this device last played from or saved to, so Save keeps writing to it after a restart. */
    var currentSlot: String?
        get() = p.getString("current_slot", null)
        set(v) { p.edit().putString("current_slot", v).apply() }

    /** The save the autosave was loaded from. Null for a game that started new. */
    var continueOrigin: String?
        get() = p.getString("continue_origin", null)
        set(v) { p.edit().putString("continue_origin", v).apply() }

    fun loadTutorial(): TutorialState = TutorialState(p.getInt("tut_index", 0), p.getBoolean("tut_active", true))

    fun saveTutorial(t: TutorialState) {
        p.edit().putInt("tut_index", t.index).putBoolean("tut_active", t.active).apply()
    }
}

/** The session's settings, kept in the device's preferences. */
class PrefsSettings(private val p: Prefs) : io.github.teamomuito.colony.sim.SessionSettings {
    override var currentSlot: String?
        get() = p.currentSlot
        set(v) { p.currentSlot = v }
    override var continueOrigin: String?
        get() = p.continueOrigin
        set(v) { p.continueOrigin = v }
}
