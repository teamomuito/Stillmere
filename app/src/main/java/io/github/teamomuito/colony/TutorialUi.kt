package io.github.teamomuito.colony

import android.view.Gravity
import android.widget.LinearLayout
import io.github.teamomuito.colony.sim.TutorialState

/** The tutorial card, shown along the top of the game while the tutorial is running. */
class TutorialCard(private val a: MainActivity) : LinearLayout(a) {
    private val ui get() = a.ui
    private val header = ui.label("", 11f, ui.accent, true)
    private val body = ui.label("", 13f)
    private val actions = LinearLayout(a).apply { orientation = HORIZONTAL; gravity = Gravity.END }

    init {
        orientation = VERTICAL
        background = ui.bg(0xEE1E1B17.toInt(), 12, 0x44FFFFFF)
        setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8))
        visibility = GONE
        addView(header)
        addView(body, ui.lin(-1, -2, 0f, 0, 3, 0, 4))
        addView(actions, ui.lin(-1, -2, 0f, 0, 0, 0, 0))
    }

    /** Shows [t]'s current lesson, or nothing when the tutorial is off or finished. */
    fun render(t: TutorialState) {
        actions.removeAllViews()
        if (!t.active) { visibility = GONE; return }
        visibility = VISIBLE
        val lesson = t.current
        if (lesson == null) {
            header.text = "TUTORIAL COMPLETE"
            body.text = "You can replay the tutorial from the Menu at any time."
            actions.addView(ui.button("Close", 12f) { a.tutorialHide() })
            return
        }
        header.text = "TUTORIAL  ${t.index + 1} / ${io.github.teamomuito.colony.sim.Tutorial.lessons.size}"
        body.text = lesson.text
        if (lesson.doneWhen == null) actions.addView(ui.button("Next", 12f, selected = true) { a.tutorialNext() }, ui.lin(-2, -2, 0f, 0, 0, 6, 0))
        else actions.addView(ui.button("Skip this step", 12f) { a.tutorialSkipLesson() }, ui.lin(-2, -2, 0f, 0, 0, 6, 0))
        actions.addView(ui.button("Hide tutorial", 12f) { a.tutorialHide() })
    }
}
