package io.github.teamomuito.colony.sim

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
}

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
    var raidMode = 0 // 0 assault, 1 sapper, 2 siege, 3 pod
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

    // Health
    val injuries = ArrayList<Injury>()
    val hediffs = ArrayList<Hediff>()
    var bloodLoss = 0f
    val parts: List<PartDef> get() = race.body
    var healthDirty = true
    val cap = FloatArray(Cap.entries.size) { 1f }
    var pain = 0f
    var careLevel = 2 // 0 none, 1 herbal, 2 any medicine
    var fullHealthCache = 1f
    val implants = HashMap<Int, Implant>()
    val surgeries = ArrayList<SurgeryOrder>()
    var refugee = false

    // Skills and traits
    val skill = IntArray(SkillType.entries.size)
    val xp = FloatArray(SkillType.entries.size)
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
        if (Trait.HARD_WORKER in traits) f *= 1.25f
        if (Trait.LAZY in traits) f *= 0.75f
        if (rest < 0.2f) f *= 0.85f
        if (dark) f *= 0.85f
        f *= max(0.1f, cap[Cap.CONSCIOUSNESS.ordinal])
        f *= min(1f, 0.2f + cap[Cap.MANIPULATION.ordinal] * 0.8f)
        return f
    }

    fun moveSpeedTicks(): Int {
        var t = race.moveTicks.toFloat()
        if (Trait.NIMBLE in traits) t -= 2f
        if (Trait.JOGGER in traits) t -= 3f
        if (Trait.SLOW_WALKER in traits) t += 2f
        t /= max(0.2f, cap[Cap.MOVING.ordinal])
        if (carryCount > 0) t += 1f
        return max(3, t.toInt())
    }

    fun gainXp(s: SkillType, amount: Float) {
        val i = s.ordinal
        var mult = when (passion[i]) { 2 -> 2.0f; 1 -> 1.4f; else -> 1.0f }
        if (Trait.FAST_LEARNER in traits) mult *= 1.4f
        if (Trait.SLOW_LEARNER in traits) mult *= 0.6f
        xp[i] += amount * mult
        val need = 2500f + skill[i] * 400f
        if (xp[i] >= need && skill[i] < 20) { xp[i] -= need; skill[i]++ }
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

    fun weaponDamageMult() = if (weaponItem != null) weaponQuality.mult else 1f

    fun armorFor(coverBit: Int, sharp: Boolean): Float {
        var armor = if (race.mech) race.armor * (if (sharp) 1f else 0.6f) else 0f
        for (w in apparel) {
            val a = w.type.apparel ?: continue
            if (a.cover and coverBit == 0) continue
            val v = if (sharp) a.armorSharp else a.armorBlunt
            armor += v * w.quality.mult * (0.5f + 0.5f * (w.hp / a.hp).coerceIn(0f, 1f))
        }
        return min(0.9f, armor)
    }

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
        Story("Vatgrown soldier", mapOf(SkillType.SHOOTING to 5, SkillType.MELEE to 3)),
        Story("Farm kid", mapOf(SkillType.PLANTS to 5, SkillType.ANIMALS to 3)),
        Story("Urchin", mapOf(SkillType.SOCIAL to 3, SkillType.CRAFTING to 2)),
        Story("Medical student", mapOf(SkillType.MEDICINE to 5, SkillType.INTELLECTUAL to 3)),
        Story("Tribal child", mapOf(SkillType.ANIMALS to 4, SkillType.CRAFTING to 4, SkillType.MELEE to 2)),
        Story("Orphan", mapOf(SkillType.SOCIAL to 2, SkillType.COOKING to 3, SkillType.SHOOTING to 2)),
        Story("Wasteland scavenger", mapOf(SkillType.MINING to 3, SkillType.CONSTRUCTION to 3, SkillType.SHOOTING to 3)),
        Story("Pit fighter", mapOf(SkillType.MELEE to 6, SkillType.MEDICINE to 2)),
        Story("Cargo ship brat", mapOf(SkillType.CONSTRUCTION to 3, SkillType.INTELLECTUAL to 3, SkillType.SOCIAL to 3)),
        Story("Herder's child", mapOf(SkillType.ANIMALS to 6, SkillType.PLANTS to 3)),
        Story("Space-born tinker", mapOf(SkillType.CONSTRUCTION to 4, SkillType.INTELLECTUAL to 4)),
    )
    val adulthood = listOf(
        Story("Colonial guard", mapOf(SkillType.SHOOTING to 8, SkillType.MELEE to 4), listOf(WorkType.ART)),
        Story("Field medic", mapOf(SkillType.MEDICINE to 8, SkillType.SHOOTING to 2)),
        Story("Farmhand", mapOf(SkillType.PLANTS to 8, SkillType.CONSTRUCTION to 3), listOf(WorkType.RESEARCH)),
        Story("Cook", mapOf(SkillType.COOKING to 9, SkillType.SOCIAL to 2)),
        Story("Miner", mapOf(SkillType.MINING to 9, SkillType.CONSTRUCTION to 4)),
        Story("Artist", mapOf(SkillType.ARTISTIC to 10, SkillType.SOCIAL to 4), listOf(WorkType.MINE)),
        Story("Engineer", mapOf(SkillType.INTELLECTUAL to 8, SkillType.CRAFTING to 6, SkillType.CONSTRUCTION to 6)),
        Story("Hunter", mapOf(SkillType.SHOOTING to 7, SkillType.ANIMALS to 5, SkillType.COOKING to 3)),
        Story("Scholar", mapOf(SkillType.INTELLECTUAL to 11, SkillType.SOCIAL to 3), listOf(WorkType.MINE)),
        Story("Smith", mapOf(SkillType.CRAFTING to 9, SkillType.MELEE to 4)),
        Story("Drifter", mapOf(SkillType.SHOOTING to 3, SkillType.SOCIAL to 3, SkillType.COOKING to 3)),
        Story("Beast tamer", mapOf(SkillType.ANIMALS to 10, SkillType.MELEE to 3)),
        Story("Pirate deckhand", mapOf(SkillType.SHOOTING to 6, SkillType.MELEE to 6, SkillType.SOCIAL to 2), listOf(WorkType.RESEARCH)),
        Story("Trader", mapOf(SkillType.SOCIAL to 10, SkillType.INTELLECTUAL to 4), listOf(WorkType.MINE)),
        Story("Surgeon", mapOf(SkillType.MEDICINE to 11, SkillType.INTELLECTUAL to 5)),
        Story("Bartender", mapOf(SkillType.COOKING to 6, SkillType.SOCIAL to 8, SkillType.CRAFTING to 2)),
        Story("Carpenter", mapOf(SkillType.CONSTRUCTION to 10, SkillType.CRAFTING to 6), listOf(WorkType.ART)),
        Story("Mercenary", mapOf(SkillType.SHOOTING to 9, SkillType.MELEE to 7)),
        Story("Prospector", mapOf(SkillType.MINING to 8, SkillType.SHOOTING to 3, SkillType.CONSTRUCTION to 3)),
        Story("Gardener", mapOf(SkillType.PLANTS to 10, SkillType.ARTISTIC to 4)),
        Story("Tailor", mapOf(SkillType.CRAFTING to 9, SkillType.ARTISTIC to 4)),
        Story("Warden", mapOf(SkillType.SOCIAL to 8, SkillType.MELEE to 5)),
    )
}
