package io.github.teamomuito.colony.sim

/**
 * Hunger and mental-break rules, kept out of [Game] so they can be tested on their own. Each constant is listed with
 * its source in `docs/fidelity/needs-mood.md`.
 */
object NeedRules {
    /** Nutrition an adult human uses per day at the base hunger rate. The food bar runs from 0 to 1. */
    const val HUMAN_NUTRITION_PER_DAY = 1.6f

    /**
     * Nutrition a human uses per day, after traits, pregnancy and age. The trait, pregnancy and child multipliers are
     * Stillmere's own (UNVERIFIED). Sleep is applied by the caller.
     */
    fun humanFoodPerDay(gourmand: Boolean, ascetic: Boolean, pregnant: Boolean, age: Int): Float =
        HUMAN_NUTRITION_PER_DAY * (if (gourmand) 1.3f else if (ascetic) 0.9f else 1f) * (if (pregnant) 1.25f else 1f) * (if (age < 13) 0.6f else 1f)
}

/** Mood thresholds for mental breaks. Thresholds follow the base-game values; the chances are Stillmere's own. */
object BreakRules {
    /** Mood below which a minor break can happen. The base-game threshold for a 35% break stat. */
    const val MINOR = 0.35f

    /** Mood below which a major break can happen. */
    const val MAJOR = 0.20f

    /** Mood below which an extreme break can happen. */
    const val EXTREME = 0.05f

    /** 2 for extreme, 1 for major, 0 for minor, or -1 when the mood is above every threshold. */
    fun severity(mood: Float): Int = when {
        mood < EXTREME -> 2
        mood < MAJOR -> 1
        mood < MINOR -> 0
        else -> -1
    }

    /** Chance per check at each severity. These values are UNVERIFIED. */
    fun chance(severity: Int): Float = when (severity) {
        2 -> 0.03f
        1 -> 0.012f
        0 -> 0.0045f
        else -> 0f
    }
}
