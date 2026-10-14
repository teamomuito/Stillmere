package io.github.teamomuito.colony.sim

/**
 * The optional tutorial: a short list of lessons. Some lessons finish by themselves when the colony does the thing
 * (marking trees, building a bed...); the others finish when the player presses Next. This is plain state with no
 * Android dependencies, so the app only has to store [index] and [active] and show [current].
 */
class TutorialLesson(val text: String, val doneWhen: ((Game) -> Boolean)?)

object Tutorial {
    val lessons: List<TutorialLesson> = listOf(
        TutorialLesson("Welcome. You crashed on a hostile frontier with a few survivors. Drag to look around, pinch to zoom, and tap people or things to inspect them.", null),
        TutorialLesson("Every colonist has needs (food, rest, joy) and a mood. Tap a colonist to see theirs, then press Next.", null),
        TutorialLesson("Colonists only work on what you mark. Open Architect → Orders, pick Chop trees, and drag over a few trees.") { g ->
            g.map.desig.any { it.toInt() == Desig.CUT }
        },
        TutorialLesson("Build a bed for each colonist: Architect → Furniture → Bed. Sleeping on the ground makes people miserable.") { g ->
            g.map.buildings().any { it.def.sleeps && !it.prisonerBed }
        },
        TutorialLesson("Hauled items need a home. Make a stockpile: Architect → Zones → Stockpile, then drag over an area.") { g ->
            // A new colony starts with one stockpile already; the player has to make another.
            g.map.zones.values.count { it.kind == ZoneKind.STOCKPILE } >= 2
        },
        TutorialLesson("Cooking happens at a workbench. Build a campfire (Architect → Production), tap it, press Open, and add a cooking bill.") { g ->
            g.map.buildings().any { it.built && it.def.workbench && it.bills.isNotEmpty() }
        },
        TutorialLesson("Research unlocks better buildings. Open Research and start a project.") { g ->
            g.researchCurrent != null || g.researchDone.isNotEmpty()
        },
        TutorialLesson("Raiders come every few days. Tap a colonist and press Draft, then tap the ground to move or an enemy to attack. Walls and sandbags help.", null),
        TutorialLesson("That is the basics. The speed buttons speed up time, and the Menu has saving, How to play and the main menu. Good luck!", null),
    )
}

/** Where a player is in the tutorial. */
class TutorialState(var index: Int = 0, var active: Boolean = true) {
    val finished get() = index >= Tutorial.lessons.size
    val current: TutorialLesson? get() = if (active && !finished) Tutorial.lessons[index] else null

    /** Moves past lessons the colony has already satisfied. Returns true if the lesson changed. */
    fun update(g: Game): Boolean {
        if (!active) return false
        var changed = false
        while (!finished) {
            val done = Tutorial.lessons[index].doneWhen ?: break
            if (!done(g)) break
            index++; changed = true
        }
        return changed
    }

    /** Pressing Next on a lesson that needs no game condition. */
    fun next(): Boolean {
        if (!active || finished || Tutorial.lessons[index].doneWhen != null) return false
        index++
        return true
    }

    /** Skips the lesson the player is on. */
    fun skipLesson() { if (!finished) index++ }

    /** Turns the tutorial off for now, keeping its place. */
    fun hide() { active = false }

    /** Turns it back on where it was. */
    fun show() { active = true }

    fun restart() { index = 0; active = true }
}
