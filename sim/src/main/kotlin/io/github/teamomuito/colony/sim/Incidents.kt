package io.github.teamomuito.colony.sim

import kotlin.math.max
import kotlin.math.min

/*
 * Incidents are what happens to a colony between its own decisions: raids, visitors, disease, weather, animals, fires,
 * drops from the sky. The system has five separate parts, each one a place to change behaviour without touching the others.
 *
 *  1. Definitions: [IncidentRegistry] lists every [IncidentDef]. A definition says which channel it belongs to, how
 *     much it weighs in each situation, and what it does when it happens. The effects themselves live in the sim files
 *     they change (Events.kt, World.kt, Trade.kt, Factions.kt) and are called from [IncidentDef.run].
 *  2. Eligibility: [eligibleNow] asks whether a definition may happen now at all: its minimum day, its refire cooldown,
 *     whether the colony is still recovering from a raid, and the definition's own condition on [IncidentContext].
 *  3. Selection: [selectIncident] weighs the eligible definitions and picks one with the game's seeded RNG.
 *  4. Scheduling: [IncidentChannel] has a timer per channel (kept in Game as nextRaid, nextMisc, ...). [runIncidentChannels]
 *     runs once an hour, and each due channel picks one incident from its own definitions.
 *  5. Execution: [executeIncident] records when the incident happened, which the cooldowns read, and runs it.
 *
 * [IncidentContext] is the colony state the rules read: wealth, population, difficulty, storyteller, season, biome,
 * recent raids, colony infrastructure. Values that scan the map are computed on first use only.
 */

enum class IncidentCategory { RAID, VISITOR, WANDERER, DISEASE, ANIMAL, FIRE, ENVIRONMENT, RESOURCE }

/**
 * A timing channel. Each one has its own timer and spacing; a due timer lets the channel choose one incident.
 * [fireChance] is the chance that a due timer produces anything at all. Entries are in the order they run each hour.
 */
enum class IncidentChannel(val minDays: Int, val maxDays: Int, val fireChance: Float = 1f) {
    RAID(3, 6),
    WANDERER(4, 10, 0.8f),
    POD(3, 8),
    TRADER(6, 13),
    TEMP(8, 16, 0.75f),
    MISC(2, 6),
}

/** Every incident the sim can produce. Names are saved with the incident history, so keep them stable. */
enum class Incident {
    RAID_ATTACK, WANDERER, CARGO_POD, TRADE_CARAVAN, HEAT_WAVE, COLD_SNAP,
    MANHUNTER_PACK, INFESTATION, DISEASE_OUTBREAK, SOLAR_FLARE, ECLIPSE, TOXIC_FALLOUT, SHORT_CIRCUIT, BLIGHT,
    ANIMAL_JOINS, THRUMBO, REFUGEES, METEORITE, AURORA, PSYCHIC_WAVE, MECH_CRASH, VOLCANIC_WINTER, THUNDERSTORM, WILDFIRE,
}

/** Days after a raid ends during which no new threat incident may start. */
const val RAID_QUIET_DAYS = 1

/** Colony state read by eligibility and weight rules. Built once per selection, so every rule sees the same colony. */
class IncidentContext internal constructor(private val g: Game) {
    val tick = g.tick
    val day = g.day
    val difficulty = g.difficulty
    val storyteller = g.storyteller
    val biome = g.map.biome
    val season = g.season
    /** The rule the heat-wave and cold-snap channels have always used. */
    val hotSeason = season == Season.SUMMER || biome == Biome.DESERT || biome == Biome.TROPICAL
    /** Hot and dry: where wildfires catch. */
    val dry = season == Season.SUMMER || biome == Biome.DESERT || biome == Biome.ARID
    val raining = g.weather == Weather.RAIN || g.weather == Weather.THUNDER

    /** Population: living colonists, and prisoners held. */
    val colonists = g.colonists.size
    val prisoners = g.prisoners.size

    /** Recent threats: a raid is on, or one ended within [RAID_QUIET_DAYS]. */
    val raidActive = g.raidActive
    val quietAfterRaid = !g.raidActive && g.raidLastEnded >= 0 && tick - g.raidLastEnded < RAID_QUIET_DAYS * TICKS_PER_DAY
    val tempEventActive = g.tempEventUntil != 0L

    /** Colony state. */
    val powered = g.power.nets > 0
    val traders = g.traders.size
    val wealth by lazy { g.map.wealth() }
    val threatPoints by lazy { g.threatPoints() }
    val batteryReady by lazy { g.map.buildings().any { it != null && it.built && it.def == BuildDef.BATTERY && it.charge > 150f } }
    val wallCount by lazy { g.map.buildings().count { it != null && it.built && it.def.isWall } }
    val cropCount by lazy { (0 until g.map.size).count { g.map.plant[it]?.type?.crop == true } }

    /** Factions: at least one that trades with you and is not hostile. */
    val tradeFactionAvailable by lazy { g.world.factions.any { it.trades && g.standing(it) != Standing.HOSTILE } }

    /** Map conditions and animals. */
    val wildTameable by lazy {
        g.pawns.any { it.alive && it.isAnimal && it.faction == Faction.WILD && !it.race.predator && it.race.dangerous < 0.5f && !it.manhunter && it.race.tameDifficulty < 1.2f }
    }
    val flammableCells by lazy { (0 until g.map.size).count { g.cellFlammability(it) > 0.3f } }
    val mountainCells by lazy { (0 until g.map.size).count { g.map.natRoof[it] && g.map.terrain[it] == Terrain.ROCK } }

    /** When [id] last happened, or null if it never has. */
    fun lastHappened(id: Incident): Long? = g.incidentLast[id]
}

/** One kind of incident. [run] makes it happen; everything else decides whether and how likely it is. */
class IncidentDef(
    val id: Incident,
    val category: IncidentCategory,
    val channel: IncidentChannel,
    /** Good or neutral for the colony. Peaceful games and the early days allow only these, and they are never held back by a recent raid. */
    val friendly: Boolean = false,
    /** First day the incident can happen. */
    val minDay: Int = 0,
    /** Days before the same incident may happen again. 0 means no limit. */
    val cooldownDays: Int = 0,
    val eligible: (IncidentContext) -> Boolean = { true },
    val weight: (IncidentContext) -> Float = { 1f },
    val run: Game.() -> Unit,
)

/** Every incident definition, in a fixed order so selection is the same for the same seed. */
internal object IncidentRegistry {
    val all: List<IncidentDef> = listOf(
        IncidentDef(Incident.RAID_ATTACK, IncidentCategory.RAID, IncidentChannel.RAID,
            eligible = { !it.raidActive && !it.quietAfterRaid },
            run = { launchRaid() }),
        IncidentDef(Incident.WANDERER, IncidentCategory.WANDERER, IncidentChannel.WANDERER, friendly = true,
            eligible = { it.colonists < 14 },
            run = { spawnWanderer() }),
        IncidentDef(Incident.CARGO_POD, IncidentCategory.RESOURCE, IncidentChannel.POD, friendly = true,
            eligible = { it.colonists > 0 },
            run = { spawnPod() }),
        IncidentDef(Incident.TRADE_CARAVAN, IncidentCategory.VISITOR, IncidentChannel.TRADER, friendly = true,
            eligible = { it.traders == 0 && it.tradeFactionAvailable },
            run = { spawnTrader() }),
        IncidentDef(Incident.HEAT_WAVE, IncidentCategory.ENVIRONMENT, IncidentChannel.TEMP,
            eligible = { it.hotSeason }, run = { heatWave() }),
        IncidentDef(Incident.COLD_SNAP, IncidentCategory.ENVIRONMENT, IncidentChannel.TEMP,
            eligible = { !it.hotSeason }, run = { coldSnap() }),

        IncidentDef(Incident.MANHUNTER_PACK, IncidentCategory.ANIMAL, IncidentChannel.MISC, minDay = 11, weight = { 2f },
            run = { manhunterPack() }),
        IncidentDef(Incident.INFESTATION, IncidentCategory.ANIMAL, IncidentChannel.MISC, minDay = 11, weight = { 2f },
            eligible = { it.mountainCells > 0 }, run = { infestation() }),
        IncidentDef(Incident.DISEASE_OUTBREAK, IncidentCategory.DISEASE, IncidentChannel.MISC, cooldownDays = 6, weight = { 1.2f },
            eligible = { it.colonists > 0 }, run = { outbreak() }),
        IncidentDef(Incident.SOLAR_FLARE, IncidentCategory.ENVIRONMENT, IncidentChannel.MISC, weight = { if (it.powered) 1.5f else 0f },
            run = { solarFlare() }),
        IncidentDef(Incident.ECLIPSE, IncidentCategory.ENVIRONMENT, IncidentChannel.MISC, run = { eclipse() }),
        IncidentDef(Incident.TOXIC_FALLOUT, IncidentCategory.ENVIRONMENT, IncidentChannel.MISC, minDay = 11,
            run = { toxicFallout() }),
        IncidentDef(Incident.SHORT_CIRCUIT, IncidentCategory.RESOURCE, IncidentChannel.MISC, weight = { if (it.batteryReady) 1.5f else 0f },
            eligible = { it.batteryReady }, run = { shortCircuit() }),
        IncidentDef(Incident.BLIGHT, IncidentCategory.RESOURCE, IncidentChannel.MISC, cooldownDays = 4,
            eligible = { it.cropCount > 0 }, run = { blight() }),
        IncidentDef(Incident.ANIMAL_JOINS, IncidentCategory.ANIMAL, IncidentChannel.MISC, friendly = true, weight = { 1.2f },
            eligible = { it.wildTameable }, run = { animalJoins() }),
        IncidentDef(Incident.THRUMBO, IncidentCategory.ANIMAL, IncidentChannel.MISC, friendly = true, minDay = 9, weight = { 0.5f },
            run = { thrumboPasses() }),
        IncidentDef(Incident.REFUGEES, IncidentCategory.VISITOR, IncidentChannel.MISC, minDay = 10, weight = { 1.2f },
            eligible = { !it.raidActive }, run = { refugees() }),
        IncidentDef(Incident.METEORITE, IncidentCategory.RESOURCE, IncidentChannel.MISC, friendly = true, run = { meteorite() }),
        IncidentDef(Incident.AURORA, IncidentCategory.ENVIRONMENT, IncidentChannel.MISC, friendly = true,
            weight = { if (it.biome == Biome.TUNDRA || it.biome == Biome.BOREAL) 1.2f else 0.3f },
            run = { aurora() }),
        IncidentDef(Incident.PSYCHIC_WAVE, IncidentCategory.ENVIRONMENT, IncidentChannel.MISC, minDay = 7, weight = { 0.9f },
            run = { psychicWave() }),
        IncidentDef(Incident.MECH_CRASH, IncidentCategory.RAID, IncidentChannel.MISC, minDay = 25, weight = { 0.9f },
            run = { mechCrash() }),
        IncidentDef(Incident.VOLCANIC_WINTER, IncidentCategory.ENVIRONMENT, IncidentChannel.MISC, minDay = 17, weight = { 0.6f },
            eligible = { !it.tempEventActive }, run = { volcanicWinter() }),
        IncidentDef(Incident.THUNDERSTORM, IncidentCategory.ENVIRONMENT, IncidentChannel.MISC, weight = { 1f },
            run = { thunderstorm() }),
        IncidentDef(Incident.WILDFIRE, IncidentCategory.FIRE, IncidentChannel.MISC, cooldownDays = 4,
            weight = { if (it.season == Season.SUMMER) 1.2f else 0.5f },
            eligible = { it.dry && !it.raining && it.flammableCells > 40 }, run = { wildfire() }),
    )

    private val byChannel: Map<IncidentChannel, List<IncidentDef>> = all.groupBy { it.channel }
    fun forChannel(ch: IncidentChannel): List<IncidentDef> = byChannel[ch].orEmpty()
    fun def(id: Incident): IncidentDef = all.first { it.id == id }
}

/** The colony's current situation, for rules that must see the same snapshot. */
fun Game.incidentContext(): IncidentContext = IncidentContext(this)

/** Whether [def] may happen now: its day, its cooldown, the quiet after a raid, and its own condition. */
internal fun Game.eligibleNow(def: IncidentDef, ctx: IncidentContext = incidentContext()): Boolean {
    if (ctx.day < def.minDay) return false
    if (def.channel == IncidentChannel.MISC && !def.friendly && ctx.quietAfterRaid) return false
    val last = incidentLast[def.id]
    if (last != null && def.cooldownDays > 0 && tick - last < def.cooldownDays * TICKS_PER_DAY) return false
    return def.eligible(ctx)
}

/** Picks one eligible incident of [ch] with weights, using the game's seeded RNG. Null when nothing may happen. */
internal fun Game.selectIncident(ch: IncidentChannel, ctx: IncidentContext = incidentContext(), friendlyOnly: Boolean = false): IncidentDef? {
    val options = ArrayList<Pair<IncidentDef, Float>>()
    for (def in IncidentRegistry.forChannel(ch)) {
        if (friendlyOnly && !def.friendly) continue
        if (!eligibleNow(def, ctx)) continue
        val w = def.weight(ctx)
        if (w > 0f) options += def to w
    }
    if (options.isEmpty()) return null
    var r = rng.float() * options.sumOf { it.second.toDouble() }.toFloat()
    for ((def, w) in options) { r -= w; if (r <= 0f) return def }
    return options.last().first
}

/** Runs [def]: records when it happened, which the cooldowns read, and then makes it happen. */
internal fun Game.executeIncident(def: IncidentDef) {
    incidentLast[def.id] = tick
    def.run(this)
}

/** Selects and runs one incident of [ch], if any is eligible. */
internal fun Game.rollIncident(ch: IncidentChannel, friendlyOnly: Boolean = false): Incident? {
    val def = selectIncident(ch, incidentContext(), friendlyOnly) ?: return null
    executeIncident(def)
    return def.id
}

/** Days between a channel's incidents. The chaos setting widens or narrows the spread around the middle. */
internal fun Game.chaosInterval(ch: IncidentChannel): Int = chaosInterval(ch.minDays, ch.maxDays)

internal fun Game.chaosInterval(minDays: Int, maxDays: Int): Int {
    val lo = minDays * TICKS_PER_DAY
    val hi = maxDays * TICKS_PER_DAY
    val chaos = storyteller.chaos
    // Orrin builds at a slower pace: his incidents are spaced further apart.
    val pace = if (storyteller == Storyteller.ORRIN) 1.5f else 1f
    val base = rng.range(lo, hi)
    return (base * pace * (1f - 0.5f * chaos + chaos * rng.float())).toInt().coerceAtLeast(TICKS_PER_DAY / 2)
}

internal fun Game.timerOf(ch: IncidentChannel): Long = when (ch) {
    IncidentChannel.RAID -> nextRaid
    IncidentChannel.WANDERER -> nextWanderer
    IncidentChannel.POD -> nextPod
    IncidentChannel.TRADER -> nextTrader
    IncidentChannel.TEMP -> nextTempEvent
    IncidentChannel.MISC -> nextMisc
}

internal fun Game.setTimerOf(ch: IncidentChannel, at: Long) {
    when (ch) {
        IncidentChannel.RAID -> nextRaid = at
        IncidentChannel.WANDERER -> nextWanderer = at
        IncidentChannel.POD -> nextPod = at
        IncidentChannel.TRADER -> nextTrader = at
        IncidentChannel.TEMP -> nextTempEvent = at
        IncidentChannel.MISC -> nextMisc = at
    }
}

/** Whether a channel may fire at all right now. A closed channel keeps its timer, so it fires as soon as it reopens. */
internal fun Game.channelOpen(ch: IncidentChannel): Boolean = when (ch) {
    IncidentChannel.RAID -> difficulty != Difficulty.PEACEFUL && !raidActive
    IncidentChannel.TEMP -> tempEventUntil == 0L
    else -> true
}

/**
 * Runs once an hour. Each due, open channel reschedules itself, then (with its fire chance) picks one incident.
 * Misc incidents in peaceful games and the first days are friendly ones, and only sometimes happen.
 */
internal fun Game.runIncidentChannels() {
    for (ch in IncidentChannel.entries) {
        if (tick < timerOf(ch) || !channelOpen(ch)) continue
        setTimerOf(ch, tick + chaosInterval(ch))
        if (ch == IncidentChannel.MISC) {
            val hostileAllowed = difficulty != Difficulty.PEACEFUL && day >= 4
            if (!hostileAllowed && !rng.chance(0.5f)) continue
            rollIncident(ch, friendlyOnly = !hostileAllowed)
            continue
        }
        if (ch.fireChance < 1f && !rng.chance(ch.fireChance)) continue
        rollIncident(ch)
    }
    if (storyteller == Storyteller.JUNIPER && difficulty != Difficulty.PEACEFUL && rng.chance(0.03f)) rollIncident(IncidentChannel.MISC)
}

/** Colony threat in points: how big the next raid or hunt may be. Used by raids and manhunter packs. */
internal fun Game.threatPoints(): Float {
    val cols = colonists.size
    val animalsPower = tamedAnimals.sumOf { (it.race.dangerous * 6f).toDouble() }.toFloat()
    var pts = cols * 17f + map.wealth() / 95f + animalsPower
    pts *= difficulty.threat * storyteller.threat
    pts *= min(1f, 0.34f + day / 34f)
    return max(pts, 22f * difficulty.threat)
}
