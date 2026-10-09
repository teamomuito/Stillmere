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

    fun loadTutorial(): TutorialState = TutorialState(p.getInt("tut_index", 0), p.getBoolean("tut_active", true))

    fun saveTutorial(t: TutorialState) {
        p.edit().putInt("tut_index", t.index).putBoolean("tut_active", t.active).apply()
    }
}
