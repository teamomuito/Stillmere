package io.github.teamomuito.colony.sim

import java.util.EnumMap
import kotlin.math.max
import kotlin.math.min

enum class Faction { PLAYER, ENEMY, WILD, VISITOR }

enum class JobType(val label: String) {
    IDLE("Idle"), WANDER("Wandering"), EAT("Eating"), SLEEP("Sleeping"), MINE("Mining"), CUT("Cutting plants"),
    SOW("Sowing"), HARVEST("Harvesting"), HAUL("Hauling"), BUILD("Building"), DECONSTRUCT("Deconstructing"),
    REPAIR("Repairing"), BILL("Crafting"), RESEARCH("Researching"), TEND("Doctoring"), REFUEL("Refuelling"), MOVE("Moving"),
    FLEE("Fleeing"), ATTACK("Fighting"), BREAK("Mental break"), RAID("Raiding"), LEAVE("Leaving"),
    WEAR("Changing clothes"), EQUIP("Equipping"), BUTCHER("Butchering"), HUNT("Hunting"), TAME("Taming"),
    SLAUGHTER("Slaughtering"), RESCUE("Rescuing"), CAPTURE("Capturing"), WARDEN("Talking to prisoner"),
    FEED_PRISONER("Feeding prisoner"), FEED_ANIMAL("Feeding animal"), SHEAR("Gathering from animal"), FEED_BABY("Feeding a baby"),
    CLEAN("Cleaning"), FIREFIGHT("Fighting fire"), JOY("Relaxing"), REST("Resting"), SURGERY("Operating"),
    SOCIAL("Chatting"), GRAZE("Grazing"), WAIT("Waiting"), TRADE("Trading"), CHASE("Chasing"), HUNT_PREY("Hunting"),
    EXTINGUISH("Putting out fire"), DELIVER("Delivering"), BURY("Burying"), SMOKE("Taking a drug"),
    // Appended last: saves store job types by ordinal.
    TRAIN("Training animal"),
}

/** Most tricks a tamed animal can learn. */
const val MAX_TRICKS = 2

class Job(val type: JobType, var tx: Int = -1, var ty: Int = -1) {
    var stage = 0
    var timer = 0
    var work = 0f
    var targetPawn = -1
    var key = -1
    var amount = 0
    var dx = -1
    var dy = -1
    var bench = -1
    var billIndex = -1
    var item: ItemType? = null
    var aux = 0
    var ingIdx = 0
    var collected = 0
    val held = ArrayList<Triple<ItemType, Int, Quality>>()
    var heldRot = 0f
    var stack: ItemStack? = null
}

class Thought(val label: String, val mood: Float, var expires: Long, var stack: Int = 1)

class Worn(val type: ItemType, var quality: Quality, var hp: Float)

class Pawn(val id: Int, var name: String, val race: Race, var faction: Faction) {
    var x = 0
    var y = 0
    var fromX = 0
    var fromY = 0
    var moveCd = 0
    var moveTotal = 1

    // Status
    var downed = false
    var dead = false
    var deathTick = 0L
    var drafted = false
    var prisoner = false
    var hostileFlag = false
    var age = 25
    var female = false
    var mood = 0.6f
    var breakUntil = 0L
    var breakKind = 0
    var temp = 20f
    var wanderer = false
    var wfaction = -1
    var ally = false
    var dormant = false
    var pathKey = -1
    var attackCd = 0
    var burstLeft = 0
    var warmup = 0
    /** Under fire until this tick: the pawn's own ranged shots are less accurate. */
    var suppressedUntil = 0L
    /** Burn size of the fire on this pawn; 0 when not burning. Saved from version 36 (docs/fidelity/combat.md). */
    var burnSize = 0f
    /** Tick until which a melee hit keeps this pawn staggered and it takes no action. Saved from version 36. */
    var staggerUntil = 0L
    /** Tricks a tamed animal has learned, 0 to [MAX_TRICKS]. Trained animals hit harder. */
    var trained = 0
    /** The target this pawn is currently aiming at; -1 for none. Not saved: it is re-chosen after a load. */
    var fightTarget = -1
    var carriedBy = -1
    /** Left on a battle map during a retreat. The enemy may take an abandoned person captive. */
    var abandoned = false
    var carrying = -1
    var raidId = 0
    var retreating = false
    var homeTile = -1
    var escapeTick = 0L
    var raidMode = 0 // 0 rush, 1 tunnel, 2 shelling camp, 3 lander drop
    var campX = -1
    var campY = -1
    var escaping = false

    // Needs
    var food = 0.85f
    var rest = 0.9f
    var joy = 0.6f
    var dark = false
    var lastSocial = 0L
    var lastJoyKind = ""
    var hunger = 1f
    /** Outdoors and indoors needs, 1 is full. They move slowly, so they are saved (version 32). */
    var outdoorsNeed = 1f
    var indoorsNeed = 1f
    /** Comfort of the seat under the pawn, recomputed each slow update (not saved). */
    var comfortNeed = 0f
    /** Ticks spent at zero rest. Past [NeedRules.COLLAPSE_AFTER_TICKS] the pawn may collapse into sleep (saved, version 32). */
    var ticksAtZeroRest = 0
    /** Ticks the mood has stayed below each break threshold (saved, version 32). */
    var moodBelowExtremeTicks = 0
    var moodBelowMajorTicks = 0
    var moodBelowMinorTicks = 0
    /** Awake ticks left before another mental break can happen (saved, version 32). */
    var breakCooldown = 0
    /** Drug tolerance per drug, 0 to 1. Kept in drug order so it saves the same way every time (saved, version 32). */
    val drugTolerance = EnumMap<ItemType, Float>(ItemType::class.java)

    // Health
    val injuries = ArrayList<Injury>()
    val hediffs = ArrayList<Hediff>()
    var bloodLoss = 0f
    val parts: List<PartDef> get() = race.body
    /**
     * Set whenever an input of the capacities and pain changes: injuries, hediffs, blood loss, implants or pregnancy. The
     * capacities and pain are a cache of those inputs. A read of [cap] or [pain] refreshes the cache while this is set, so a
     * read never returns a value the inputs no longer give. Not saved: SaveGame recomputes the cache when it loads a pawn.
     */
    var healthDirty = true
    internal val capCache = FloatArray(Cap.entries.size) { 1f }
    internal var painCache = 0f
    val cap: FloatArray get() { if (healthDirty) refreshHealthCache(this); return capCache }
    /** Pain from the cache. Assigning a value sets the cache until the next refresh; tests use this to set a state directly. */
    var pain: Float
        get() { if (healthDirty) refreshHealthCache(this); return painCache }
        set(value) { painCache = value }
    var careLevel = 2 // 0 none, 1 herbal, 2 any medicine
    var fullHealthCache = 1f
    val implants = HashMap<Int, Implant>()
    val surgeries = ArrayList<SurgeryOrder>()
    var refugee = false

    // Skills and traits
    val skill = IntArray(SkillType.entries.size)
    val xp = FloatArray(SkillType.entries.size)
    /** XP learned today per skill, for the daily cap. Reset at the start of each day. Saved from version 33. */
    val xpToday = FloatArray(SkillType.entries.size)
    val passion = IntArray(SkillType.entries.size)
    val priority = IntArray(WorkType.entries.size) { 3 }
    val traits = ArrayList<Trait>()
    var backstory = ""
    var incapable = 0 // bitmask of WorkType ordinals

    // Gear
    var weaponItem: ItemType? = null
    var weaponQuality = Quality.NORMAL
    val apparel = ArrayList<Worn>()

    // Mind
    val thoughts = ArrayList<Thought>()
    val situ = ArrayList<Thought>()
    val opinion = HashMap<Int, Int>()
    var spouse = -1
    var lover = -1
    var schedule = IntArray(24) { if (it >= 22 || it < 6) 3 else if (it in 19..21) 2 else 0 } // 0 anything, 1 work, 2 joy, 3 sleep
    var areaRestriction = 0
    var foodPolicy = 0 // 0 anything, 1 no raw, 2 meals only
    var outfit = 0 // 0 anything, 1 worker (no armor), 2 soldier (armor first), 3 none
    var drugPolicy = 0 // 0 none, 1 social only, 2 whenever bored
    var allowDrugs = false

    // Orders
    var job: Job? = null
    var path: IntArray? = null
    var pathI = 0
    var carryType: ItemType? = null
    var carryCount = 0
    var carryQuality = Quality.NORMAL
    var bedId = -1
    val reserved = ArrayList<Int>()
    var doctorOrder = false
    var huntMark = false
    var tameMark = false
    var slaughterMark = false
    var releaseMark = false
    var recruitMode = 0 // 0 none/recruit, 1 convert... (1: just feed and hold)
    var resistance = 0f
    var recruitProgress = 0f
    var rescued = false

    // Animal
    var tame = false
    var master = -1
    var animalProductTimer = 0
    var manhunter = false
    var obedience = 0f
    var herdLeader = -1
    var predatorTarget = -1
    var grazeTimer = 0
    var birthday = 0
    var ageDays = 0
    var mother = -1
    var father = -1
    var pregnantUntil = 0L
    var pregnantBy = -1
    var wild get() = faction == Faction.WILD
        set(_) {}

    val colonist get() = faction == Faction.PLAYER && !race.isAnimal && !prisoner && !ally
    val isAnimal get() = race.isAnimal
    val hostile get() = (hostileFlag || faction == Faction.ENEMY || manhunter) && !dormant
    val alive get() = !dead
    val moving get() = path != null && pathI < (path?.size ?: 0)
    val capacity get() = cap
    val maxHp: Float get() = 100f
    val hp: Float get() = max(0f, 100f * healthFraction())
    val untended get() = injuries.any { !it.tended && !it.scar && !it.missing && it.bleed > 0.00001f } ||
        injuries.any { !it.tended && it.infection > 0f } || hediffs.any { it.kind.needsTend && !it.tended }
    val bleeding get() = injuries.sumOf { (if (it.tended) it.bleed * (1f - it.tendQuality) * 0.06f else it.bleed).toDouble() }.toFloat()
    val needsMedical get() = injuries.any { !it.scar && !it.missing && it.severity > 0.5f } || hediffs.any { it.kind.category == 0 || it.kind.category == 1 && it.severity > 0.3f } || bloodLoss > 0.05f
    val injured get() = needsMedical

    fun healthFraction(): Float {
        var worst = 1f
        var total = 0f
        for (i in injuries) if (!i.scar) total += i.severity
        val parts = race.body
        val hpSum = parts.filter { !it.inner }.sumOf { it.hp.toDouble() }.toFloat() * race.hpScale
        worst = 1f - min(1f, total / (hpSum * 0.9f)) - bloodLoss * 0.5f
        return max(0f, worst)
    }

    fun level(s: SkillType) = skill[s.ordinal]

    fun hasTrait(t: Trait) = t in traits

    fun workSpeed(s: SkillType?): Float {
        var f = if (s == null) 1f else 0.4f + 0.075f * skill[s.ordinal]
        if (!isAnimal && age < 18) f *= if (age < 13) 0.6f else 0.85f
        if (Trait.BRISK_HANDS in traits) f *= 1.25f
        if (Trait.IDLE_STREAK in traits) f *= 0.75f
        if (rest < 0.2f) f *= 0.85f
        if (dark) f *= 0.85f
        f *= max(0.1f, cap[Cap.CONSCIOUSNESS.ordinal])
        f *= min(1f, 0.2f + cap[Cap.MANIPULATION.ordinal] * 0.8f)
        return f
    }

    fun moveSpeedTicks(): Int {
        var t = race.moveTicks.toFloat()
        if (Trait.LIGHT_STEPPED in traits) t -= 2f
        if (Trait.LONG_STRIDER in traits) t -= 3f
        if (Trait.SLOW_WALKER in traits) t += 2f
        t /= max(0.2f, cap[Cap.MOVING.ordinal])
        if (carryCount > 0) t += 1f
        // Written in the old tick unit; the result is in current ticks.
        return max(tk(3), Math.round(t * TIME_SCALE))
    }

    /** Only humanlike pawns have skills (RimWorld/PawnComponentsUtility.cs:77-81). Animals and mechanoids do not learn. */
    val hasSkills get() = !isAnimal && !race.mech

    fun skillDisabled(s: SkillType): Boolean = SkillRules.skillDisabled(s, incapable)

    /**
     * Learns XP for [s]. The rate is the passion factor, then the trait factors, then the daily cap: once the skill has
     * gained more than the cap today, the rest of today's XP learns at the saturated factor (RimWorld/SkillRecord.cs:371-397).
     */
    fun gainXp(s: SkillType, amount: Float) {
        if (!hasSkills || amount <= 0f || skillDisabled(s)) return
        val i = s.ordinal
        var rate = SkillRules.passionFactor(passion[i])
        if (Trait.EAGER_PUPIL in traits) rate *= 1.4f
        if (Trait.SLOW_STUDY in traits) rate *= 0.6f
        if (xpToday[i] > SkillRules.DAILY_FULL_RATE_XP) rate *= SkillRules.SATURATED_FACTOR
        addXp(i, amount * rate)
    }

    /** Adds XP already scaled by the rate, and records it for today. Negative amounts are decay. */
    private fun addXp(i: Int, gained: Float) {
        xpToday[i] += gained
        val (lvl, x) = SkillRules.applyXp(skill[i], xp[i], gained)
        skill[i] = lvl
        xp[i] = x
    }

    /**
     * The decay interval for every skill: a skill at level 10 or above loses XP, more at higher levels, whether or
     * not it was used (RimWorld/SkillRecord.cs:207-249). Skills that are disabled do not decay.
     */
    fun skillIntervalTick() {
        if (!hasSkills) return
        for (i in skill.indices) {
            val d = SkillRules.decayPerInterval(skill[i])
            if (d != 0f && !skillDisabled(SkillType.entries[i])) addXp(i, d)
        }
    }

    fun addThought(label: String, mood: Float, now: Long, duration: Int) {
        val ex = thoughts.firstOrNull { it.label == label }
        if (ex != null) {
            ex.expires = now + duration
            return
        }
        thoughts.add(Thought(label, mood, now + duration))
    }

    /** Years for humans, mature-adult test for animals. */
    val stage: LifeStage get() = when {
        isAnimal -> if (ageDays < race.matureDays) LifeStage.JUVENILE else LifeStage.ADULT
        age < 3 -> LifeStage.BABY
        age < 13 -> LifeStage.CHILD
        age < 18 -> LifeStage.TEEN
        else -> LifeStage.ADULT
    }
    val isBaby get() = !isAnimal && age < 3
    val isChild get() = !isAnimal && age < 13

    /** Visual and physical size relative to a grown pawn. */
    fun bodyScale(): Float = when (stage) {
        LifeStage.BABY -> 0.45f
        LifeStage.CHILD -> 0.6f + (age - 3) * 0.03f
        LifeStage.TEEN -> 0.9f + (age - 13) * 0.02f
        LifeStage.JUVENILE -> 0.55f + 0.45f * (ageDays.toFloat() / race.matureDays)
        LifeStage.ADULT -> 1f
    }

    fun clearPath() { path = null; pathI = 0 }

    fun interpX(): Float = if (moveCd > 0 && moveTotal > 0) x + (fromX - x) * (moveCd.toFloat() / moveTotal) else x.toFloat()
    fun interpY(): Float = if (moveCd > 0 && moveTotal > 0) y + (fromY - y) * (moveCd.toFloat() / moveTotal) else y.toFloat()

    val weapon: Weapon get() = weaponItem?.weapon ?: race.weapon

    fun weaponDamageMult() = (if (weaponItem != null) weaponQuality.mult else 1f) * (if (isAnimal) 1f + 0.2f * trained else 1f)

    /**
     * A summary of armour for a body region, for the character panel only: the covering layers' ratings added up and capped at
     * 0.9. The hit roll does not use it. Each layer rolls on its own in `dealDamage` (Verse/ArmorUtility.cs:13-56).
     */
    fun armorFor(coverBit: Int, sharp: Boolean): Float {
        var armor = naturalArmor(sharp)
        for (w in apparel) {
            val a = w.type.apparel ?: continue
            if (a.cover and coverBit == 0) continue
            armor += w.armorRating(sharp)
        }
        return min(0.9f, armor)
    }

    /**
     * Natural armour, applied after the worn layers (Verse/ArmorUtility.cs:44-45). Only mechanoids have it here, and the
     * blunt rating at 0.6 of the sharp is kept from Stillmere (UNVERIFIED). See docs/fidelity/combat.md.
     */
    fun naturalArmor(sharp: Boolean): Float = if (race.mech) race.armor * (if (sharp) 1f else 0.6f) else 0f

    fun insulationCold(): Float = apparel.sumOf { ((it.type.apparel?.insCold ?: 0f) * it.quality.mult).toDouble() }.toFloat()
    fun insulationHeat(): Float = apparel.sumOf { ((it.type.apparel?.insHeat ?: 0f)).toDouble() }.toFloat()

    fun comfyMin(): Float = 16f - insulationCold() - (if (race.isAnimal) race.size * 12f else 0f)
    fun comfyMax(): Float = 26f + insulationHeat() + (if (race.isAnimal) 10f else 0f)
}

object Names {
    val first = listOf(
        "Ada", "Bram", "Cleo", "Dax", "Elin", "Finn", "Gwen", "Hale", "Iris", "Jory", "Kai", "Lena", "Milo", "Nora", "Orin",
        "Pia", "Quinn", "Rhea", "Sol", "Tess", "Uri", "Vera", "Wren", "Xan", "Yara", "Zed", "Odette", "Jasper", "Mira", "Tobias",
        "Anselm", "Briar", "Calla", "Dorian", "Esme", "Fenn", "Greta", "Hugo", "Isla", "Joss", "Kestrel", "Linus", "Maren",
        "Nico", "Oona", "Pike", "Rowan", "Sable", "Thea", "Ulric", "Vesper", "Willa", "Yuri", "Zora",
    )
    val female = setOf(
        "Ada", "Cleo", "Elin", "Gwen", "Iris", "Lena", "Nora", "Pia", "Rhea", "Tess", "Vera", "Wren", "Yara", "Odette", "Mira",
        "Briar", "Calla", "Esme", "Greta", "Isla", "Maren", "Oona", "Sable", "Thea", "Vesper", "Willa", "Zora",
    )
    val last = listOf(
        "Voss", "Marek", "Okafor", "Lindqvist", "Tanaka", "Reyes", "Hollis", "Brandt", "Moreau", "Kowal", "Idris", "Calder",
        "Ashby", "Duarte", "Eklund", "Farrow", "Grayson", "Hartley", "Ilves", "Jovanovic", "Kessler", "Larkin", "Mbeki", "Novak",
    )
    val raider = listOf("Skull", "Rat", "Ash", "Fang", "Hook", "Crow", "Slag", "Burr", "Gash", "Knuckle", "Rust", "Ox", "Jag", "Wolf")
    val animal = listOf("Biscuit", "Clover", "Dusty", "Ember", "Fern", "Ginger", "Hazel", "Ivy", "Juniper", "Maple", "Nutmeg", "Olive", "Pepper", "Rusty", "Sage", "Tuft", "Willow")
}

object Backstories {
    class Story(val title: String, val skills: Map<SkillType, Int>, val disabled: List<WorkType> = emptyList(), val traits: List<Trait> = emptyList())
    val childhood = listOf(
        Story("Cradle-line recruit", mapOf(SkillType.MARKSMANSHIP to 5, SkillType.CLOSE_COMBAT to 3)),
        Story("Furrow-born", mapOf(SkillType.HORTICULTURE to 5, SkillType.HUSBANDRY to 3)),
        Story("Street kid", mapOf(SkillType.RAPPORT to 3, SkillType.WORKMANSHIP to 2)),
        Story("Infirmary helper", mapOf(SkillType.HEALING to 5, SkillType.SCHOLARSHIP to 3)),
        Story("Camp foundling", mapOf(SkillType.HUSBANDRY to 4, SkillType.WORKMANSHIP to 4, SkillType.CLOSE_COMBAT to 2)),
        Story("Unclaimed child", mapOf(SkillType.RAPPORT to 2, SkillType.GALLEY to 3, SkillType.MARKSMANSHIP to 2)),
        Story("Ruin picker", mapOf(SkillType.QUARRYING to 3, SkillType.ARCHITECTURE to 3, SkillType.MARKSMANSHIP to 3)),
        Story("Ringside tyke", mapOf(SkillType.CLOSE_COMBAT to 6, SkillType.HEALING to 2)),
        Story("Hold-born ward", mapOf(SkillType.ARCHITECTURE to 3, SkillType.SCHOLARSHIP to 3, SkillType.RAPPORT to 3)),
        Story("Pasture-born", mapOf(SkillType.HUSBANDRY to 6, SkillType.HORTICULTURE to 3)),
        Story("Hull scrapper", mapOf(SkillType.ARCHITECTURE to 4, SkillType.SCHOLARSHIP to 4)),
    )
    val adulthood = listOf(
        Story("Palisade watch", mapOf(SkillType.MARKSMANSHIP to 8, SkillType.CLOSE_COMBAT to 4), listOf(WorkType.ART)),
        Story("Battlefield bandager", mapOf(SkillType.HEALING to 8, SkillType.MARKSMANSHIP to 2)),
        Story("Farmhand", mapOf(SkillType.HORTICULTURE to 8, SkillType.ARCHITECTURE to 3), listOf(WorkType.RESEARCH)),
        Story("Cook", mapOf(SkillType.GALLEY to 9, SkillType.RAPPORT to 2)),
        Story("Miner", mapOf(SkillType.QUARRYING to 9, SkillType.ARCHITECTURE to 4)),
        Story("Artist", mapOf(SkillType.ARTISTRY to 10, SkillType.RAPPORT to 4), listOf(WorkType.MINE)),
        Story("Engineer", mapOf(SkillType.SCHOLARSHIP to 8, SkillType.WORKMANSHIP to 6, SkillType.ARCHITECTURE to 6)),
        Story("Hunter", mapOf(SkillType.MARKSMANSHIP to 7, SkillType.HUSBANDRY to 5, SkillType.GALLEY to 3)),
        Story("Scholar", mapOf(SkillType.SCHOLARSHIP to 11, SkillType.RAPPORT to 3), listOf(WorkType.MINE)),
        Story("Smith", mapOf(SkillType.WORKMANSHIP to 9, SkillType.CLOSE_COMBAT to 4)),
        Story("Roadwalker", mapOf(SkillType.MARKSMANSHIP to 3, SkillType.RAPPORT to 3, SkillType.GALLEY to 3)),
        Story("Stockman", mapOf(SkillType.HUSBANDRY to 10, SkillType.CLOSE_COMBAT to 3)),
        Story("Sloop hand", mapOf(SkillType.MARKSMANSHIP to 6, SkillType.CLOSE_COMBAT to 6, SkillType.RAPPORT to 2), listOf(WorkType.RESEARCH)),
        Story("Trader", mapOf(SkillType.RAPPORT to 10, SkillType.SCHOLARSHIP to 4), listOf(WorkType.MINE)),
        Story("Surgeon", mapOf(SkillType.HEALING to 11, SkillType.SCHOLARSHIP to 5)),
        Story("Bartender", mapOf(SkillType.GALLEY to 6, SkillType.RAPPORT to 8, SkillType.WORKMANSHIP to 2)),
        Story("Carpenter", mapOf(SkillType.ARCHITECTURE to 10, SkillType.WORKMANSHIP to 6), listOf(WorkType.ART)),
        Story("Mercenary", mapOf(SkillType.MARKSMANSHIP to 9, SkillType.CLOSE_COMBAT to 7)),
        Story("Prospector", mapOf(SkillType.QUARRYING to 8, SkillType.MARKSMANSHIP to 3, SkillType.ARCHITECTURE to 3)),
        Story("Gardener", mapOf(SkillType.HORTICULTURE to 10, SkillType.ARTISTRY to 4)),
        Story("Tailor", mapOf(SkillType.WORKMANSHIP to 9, SkillType.ARTISTRY to 4)),
        Story("Warden", mapOf(SkillType.RAPPORT to 8, SkillType.CLOSE_COMBAT to 5)),
    )
}
