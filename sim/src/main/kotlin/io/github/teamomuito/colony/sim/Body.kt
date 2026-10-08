package io.github.teamomuito.colony.sim

enum class PartTag { TORSO, NECK, HEAD, BRAIN, EYE, EAR, NOSE, JAW, ARM, HAND, LEG, FOOT, HEART, LUNG, STOMACH, LIVER, KIDNEY }

class PartDef(
    val label: String, val tag: PartTag, val hp: Float, val coverage: Float, val parent: Int = -1,
    /** Apparel cover bit: 1 head, 2 torso, 4 arms, 8 legs */
    val cover: Int = 0,
) {
    val inner get() = parent >= 0
    val vital get() = tag == PartTag.HEART || tag == PartTag.BRAIN || tag == PartTag.TORSO || tag == PartTag.NECK || tag == PartTag.HEAD
}

object Bodies {
    val HUMAN: List<PartDef> = listOf(
        PartDef("torso", PartTag.TORSO, 40f, 0.40f, -1, 2),
        PartDef("neck", PartTag.NECK, 25f, 0.03f, -1, 2),
        PartDef("head", PartTag.HEAD, 25f, 0.07f, -1, 1),
        PartDef("left arm", PartTag.ARM, 30f, 0.07f, -1, 4),
        PartDef("right arm", PartTag.ARM, 30f, 0.07f, -1, 4),
        PartDef("left hand", PartTag.HAND, 20f, 0.02f, -1, 4),
        PartDef("right hand", PartTag.HAND, 20f, 0.02f, -1, 4),
        PartDef("left leg", PartTag.LEG, 30f, 0.10f, -1, 8),
        PartDef("right leg", PartTag.LEG, 30f, 0.10f, -1, 8),
        PartDef("left foot", PartTag.FOOT, 25f, 0.02f, -1, 8),
        PartDef("right foot", PartTag.FOOT, 25f, 0.02f, -1, 8),
        PartDef("heart", PartTag.HEART, 15f, 0f, 0, 2),
        PartDef("left lung", PartTag.LUNG, 15f, 0f, 0, 2),
        PartDef("right lung", PartTag.LUNG, 15f, 0f, 0, 2),
        PartDef("stomach", PartTag.STOMACH, 20f, 0f, 0, 2),
        PartDef("liver", PartTag.LIVER, 20f, 0f, 0, 2),
        PartDef("left kidney", PartTag.KIDNEY, 15f, 0f, 0, 2),
        PartDef("right kidney", PartTag.KIDNEY, 15f, 0f, 0, 2),
        PartDef("brain", PartTag.BRAIN, 10f, 0f, 2, 1),
        PartDef("left eye", PartTag.EYE, 10f, 0f, 2, 1),
        PartDef("right eye", PartTag.EYE, 10f, 0f, 2, 1),
        PartDef("left ear", PartTag.EAR, 12f, 0f, 2, 1),
        PartDef("right ear", PartTag.EAR, 12f, 0f, 2, 1),
        PartDef("nose", PartTag.NOSE, 10f, 0f, 2, 1),
        PartDef("jaw", PartTag.JAW, 20f, 0f, 2, 1),
    )

    val QUAD: List<PartDef> = listOf(
        PartDef("torso", PartTag.TORSO, 40f, 0.46f),
        PartDef("neck", PartTag.NECK, 20f, 0.04f),
        PartDef("head", PartTag.HEAD, 22f, 0.08f),
        PartDef("front left leg", PartTag.LEG, 22f, 0.10f),
        PartDef("front right leg", PartTag.LEG, 22f, 0.10f),
        PartDef("hind left leg", PartTag.LEG, 22f, 0.11f),
        PartDef("hind right leg", PartTag.LEG, 22f, 0.11f),
        PartDef("heart", PartTag.HEART, 14f, 0f, 0),
        PartDef("left lung", PartTag.LUNG, 14f, 0f, 0),
        PartDef("right lung", PartTag.LUNG, 14f, 0f, 0),
        PartDef("stomach", PartTag.STOMACH, 18f, 0f, 0),
        PartDef("liver", PartTag.LIVER, 18f, 0f, 0),
        PartDef("left kidney", PartTag.KIDNEY, 14f, 0f, 0),
        PartDef("right kidney", PartTag.KIDNEY, 14f, 0f, 0),
        PartDef("brain", PartTag.BRAIN, 9f, 0f, 2),
        PartDef("left eye", PartTag.EYE, 8f, 0f, 2),
        PartDef("right eye", PartTag.EYE, 8f, 0f, 2),
        PartDef("jaw", PartTag.JAW, 16f, 0f, 2),
    )

    /** Insect-like bodies are simple: torso, head, legs. */
    val BUG: List<PartDef> = listOf(
        PartDef("body", PartTag.TORSO, 30f, 0.6f),
        PartDef("head", PartTag.HEAD, 20f, 0.15f),
        PartDef("legs", PartTag.LEG, 25f, 0.25f),
        PartDef("heart", PartTag.HEART, 12f, 0f, 0),
        PartDef("brain", PartTag.BRAIN, 8f, 0f, 1),
    )
}

enum class Cap { CONSCIOUSNESS, MOVING, MANIPULATION, SIGHT, HEARING, TALKING, EATING, BREATHING, PUMPING, FILTRATION }

class Injury(
    var part: Int,
    var kind: DamageKind,
    var severity: Float,
    var bleed: Float = 0f,
    var tended: Boolean = false,
    var tendQuality: Float = 0f,
    var infection: Float = 0f,
    var infectable: Boolean = true,
    var permanent: Boolean = false,
    var missing: Boolean = false,
    var scar: Boolean = false,
    var implant: String = "",
    var age: Int = 0,
)

enum class HediffKind(
    val label: String, val category: Int, val progressPerDay: Float, val immunityPerDay: Float,
    val lethal: Boolean, val cons: Float = 0f, val move: Float = 0f, val manip: Float = 0f, val eat: Float = 0f,
    val pain: Float = 0f, val mood: Float = 0f, val needsTend: Boolean = false, val contagious: Boolean = false,
) {
    // category: 0 illness, 1 environment, 2 drug high, 3 drug tolerance/addiction, 4 other
    FLU("Flu", 0, 0.9f, 0.8f, true, cons = 0.25f, move = 0.2f, manip = 0.15f, eat = 0.3f, pain = 0.15f, needsTend = true, contagious = true),
    PLAGUE("Plague", 0, 0.75f, 0.45f, true, cons = 0.35f, move = 0.3f, manip = 0.2f, pain = 0.2f, needsTend = true),
    MALARIA("Malaria", 0, 0.7f, 0.5f, true, cons = 0.35f, move = 0.25f, manip = 0.2f, pain = 0.15f, needsTend = true),
    SLEEPING_SICKNESS("Sleeping sickness", 0, 0.5f, 0.35f, true, cons = 0.45f, move = 0.3f, needsTend = true),
    FOOD_POISONING("Food poisoning", 0, 1.4f, 2.0f, false, cons = 0.1f, move = 0.1f, eat = 0.2f, pain = 0.1f),
    GUT_WORMS("Gut worms", 0, 0.12f, 0.1f, false, eat = 0.25f, pain = 0.1f),
    MUSCLE_PARASITES("Muscle parasites", 0, 0.12f, 0.1f, false, move = 0.25f, manip = 0.15f, pain = 0.1f),
    HYPOTHERMIA("Hypothermia", 1, 0f, 0f, true, cons = 0.3f, move = 0.2f, manip = 0.2f, pain = 0f),
    HEATSTROKE("Heatstroke", 1, 0f, 0f, true, cons = 0.35f, move = 0.2f, manip = 0.15f),
    MALNUTRITION("Malnutrition", 1, 0f, 0f, true, cons = 0.2f, move = 0.2f, manip = 0.1f, pain = 0.1f),
    TOXIC_BUILDUP("Toxic buildup", 1, 0f, 0f, true, cons = 0.25f, move = 0.1f, manip = 0.1f),
    BLOOD_LOSS("Blood loss", 4, 0f, 0f, true),
    ALCOHOL_HIGH("Drunk", 2, 0f, 0f, false, cons = 0.0f, move = 0.1f, manip = 0.15f),
    SMOKELEAF_HIGH("Smokeleaf high", 2, 0f, 0f, false, cons = 0.0f, manip = 0f),
    PSYCHITE_HIGH("Psychite buzz", 2, 0f, 0f, false),
    ALCOHOL_TOLERANCE("Alcohol tolerance", 3, 0f, 0f, false),
    ALCOHOL_ADDICTION("Alcohol addiction", 3, 0f, 0f, false),
    SMOKELEAF_ADDICTION("Smokeleaf addiction", 3, 0f, 0f, false),
    PSYCHITE_ADDICTION("Psychite addiction", 3, 0f, 0f, false),
    WITHDRAWAL("Withdrawal", 3, 0f, 0f, false, cons = 0.15f, move = 0.1f, manip = 0.15f, pain = 0.1f),
    ANESTHESIA("Anesthetic", 4, 0f, 0f, false, cons = 1f),
}

class Hediff(val kind: HediffKind, var severity: Float, var immunity: Float = 0f) {
    var tended = false
    var tendQuality = 0f
    var age = 0
    var part = -1
    var duration = 0
}

object Diseases {
    val ILLNESSES = listOf(HediffKind.FLU, HediffKind.PLAGUE, HediffKind.MALARIA, HediffKind.SLEEPING_SICKNESS, HediffKind.GUT_WORMS, HediffKind.MUSCLE_PARASITES)
}
