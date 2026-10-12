package io.github.teamomuito.colony.sim

import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

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
 *     runs once an hour, and each due channel picks one incident from its own definitions. Raids follow their storyteller's
 *     on and off cycle when it has one ([RaidCycle], [nextChannelTime]).
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
    ANIMAL_JOINS, SKYHORN_VISIT, REFUGEES, METEORITE, AURORA, PSYCHIC_WAVE, MECH_CRASH, VOLCANIC_WINTER, THUNDERSTORM, WILDFIRE,
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
        IncidentDef(Incident.SKYHORN_VISIT, IncidentCategory.ANIMAL, IncidentChannel.MISC, friendly = true, minDay = 9, weight = { 0.5f },
            run = { skyhornPasses() }),
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
        val w = def.weight(ctx) * recentWeight(def.id)
        if (w > 0f) options += def to w
    }
    if (options.isEmpty()) return null
    var r = rng.float() * options.sumOf { it.second.toDouble() }.toFloat()
    for ((def, w) in options) { r -= w; if (r <= 0f) return def }
    return options.last().first
}

/**
 * Runs [def]: records when it happened, which the cooldowns read, and then makes it happen. It is also added to the
 * list of recent random incidents, most recent first, which keeps five (QuestTuning.cs:14; StoryState.cs:140-147).
 */
internal fun Game.executeIncident(def: IncidentDef) {
    incidentLast[def.id] = tick
    def.run(this)
    lastRandomIncidents.add(0, def.id)
    while (lastRandomIncidents.size > RECENT_INCIDENT_WEIGHTS.size) lastRandomIncidents.removeAt(lastRandomIncidents.size - 1)
}

/** Selects and runs one incident of [ch], if any is eligible. */
internal fun Game.rollIncident(ch: IncidentChannel, friendlyOnly: Boolean = false): Incident? {
    val def = selectIncident(ch, incidentContext(), friendlyOnly) ?: return null
    executeIncident(def)
    return def.id
}

/** Days between a channel's incidents. The chaos setting widens or narrows the spread around the middle. */
internal fun Game.chaosInterval(ch: IncidentChannel): Int = chaosInterval(ch.minDays, ch.maxDays)

/**
 * When [ch] is next due. A raid follows its storyteller's cycle when there is one: the first cycle hit at least the cycle's
 * minimum spacing after the time the timer was due (not after the hourly check, which can be up to an hour later, and would
 * skip a hit that sits exactly at the spacing). A timer still in the future counts from now. Every other channel keeps its
 * uniform chaos spacing from now.
 */
internal fun Game.nextChannelTime(ch: IncidentChannel): Long {
    val cycle = if (ch == IncidentChannel.RAID) storyteller.raidCycle() else null
    if (cycle == null) return tick + chaosInterval(ch)
    val due = timerOf(ch).coerceAtMost(tick)
    return nextRaidCycleTick(cycle, maxOf(tick, due + cycle.spacingTicks))
}

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
        setTimerOf(ch, nextChannelTime(ch))
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

// ---------------------------------------------------------------------------------------------- raid points
//
// Raid points follow the shape of vanilla's (RimWorld/StorytellerUtility.cs:185-188): wealth points and colonist points,
// scaled by the difficulty and the storyteller, then clamped between a seeded floor and a cap. Every value cites its
// source in docs/fidelity/threats.md. The constants below are the vanilla ones (D); the rest is Stillmere's own.

/** Wealth to points on a home map (StorytellerUtility.cs:18-25). Flat outside the last point. */
internal val wealthPointsCurve = PiecewiseCurve(0f to 0f, 14000f to 0f, 400000f to 2400f, 700000f to 3600f, 1000000f to 4200f)

/** Points per free colonist by wealth (StorytellerUtility.cs:43-49). */
internal val colonistPointsCurve = PiecewiseCurve(0f to 15f, 10000f to 15f, 400000f to 140f, 1000000f to 200f)

/** The ceiling of the raid points floor by days passed, at the default ceiling of 70 (Difficulty.cs:458-462). */
internal val pointsFloorCeilingCurve = PiecewiseCurve(12f to 35f, 35f to 70f)

/** The lowest value of the raid points floor (StorytellerUtility.cs:12). */
internal const val POINTS_FLOOR_MIN = 35f

/** The highest raid points (StorytellerUtility.cs:14). */
internal const val POINTS_CAP = 10000f

/** Buildings count at half their value in the wealth that raid points read (StorytellerUtility.cs:16; Verse/Map.cs:422). */
internal const val BUILDING_WEALTH_WEIGHT = 0.5f

/**
 * The share of a counted pawn's points that depends on its health: the rest is fixed. Vanilla applies it to every pawn that
 * counts, colonists and tamed animals alike (StorytellerUtility.cs:41, and the lerp at 173 inside the loop at 140-184).
 */
internal const val HEALTH_BLEND = 0.65f

/** The points a counted pawn keeps at [health] (0 to 1): the fixed part plus the health part (StorytellerUtility.cs:173). */
internal fun healthBlend(health: Float): Float = 1f - HEALTH_BLEND + HEALTH_BLEND * health

/** Points per unit of combat power for each tamed animal that is not downed (StorytellerUtility.cs:37, 151-153). */
internal const val ANIMAL_POINTS_PER_COMBAT_POWER = 0.08f

/** Stillmere's stand-in for an animal's combat power: its danger times this (C, UNVERIFIED; vanilla's power is XML). */
internal const val ANIMAL_COMBAT_POWER_PER_DANGER = 6f

/** A tamed animal that counts toward raid points: its danger rating and its health (0 to 1). */
internal class AnimalThreat(val danger: Float, val health: Float)

/** Wealth for raid points on a home map: items, plus half the value of the buildings (Verse/Map.cs:422). */
internal fun Game.threatWealth(): Float {
    var items = 0f
    for (s in map.items.values) items += s.type.value * s.count * (if (s.type.isGear) s.quality.mult else 1f)
    var buildings = 0f
    for (b in map.buildings()) if (b.built) buildings += b.def.totalCost * b.quality.mult
    return items + BUILDING_WEALTH_WEIGHT * buildings
}

/**
 * Points from wealth, colonists and tamed animals, before the difficulty and storyteller scales. [colonistHealth] has one
 * entry per colonist; [animals] one per tamed animal that is not downed. Colonists and animals are both blended by health
 * (see [healthBlend]).
 */
internal fun basePoints(wealth: Float, colonistHealth: List<Float>, animals: List<AnimalThreat>): Float {
    val perColonist = colonistPointsCurve.eval(wealth)
    var pts = wealthPointsCurve.eval(wealth)
    for (h in colonistHealth) pts += perColonist * healthBlend(h)
    for (a in animals) pts += ANIMAL_POINTS_PER_COMBAT_POWER * (a.danger * ANIMAL_COMBAT_POWER_PER_DANGER) * healthBlend(a.health)
    return pts
}

/** The scaled points, held between the floor and the cap. The floor is not scaled by difficulty (StorytellerUtility.cs:188). */
internal fun clampPoints(raw: Float, floor: Float): Float = min(max(raw, floor), POINTS_CAP)

/**
 * The lowest raid points this hour: a value from [POINTS_FLOOR_MIN] up to the day's ceiling. It is drawn from a seed made of
 * the game seed and the game hour, so it is the same for every call in the hour and changes each hour (StorytellerUtility.cs:
 * 98-101). The draw does not touch the main RNG, so calling it never moves another roll.
 */
internal fun Game.raidPointsFloor(): Float {
    val ceiling = pointsFloorCeilingCurve.eval(tick.toFloat() / TICKS_PER_DAY)
    val u = Rng(mixSeed(seed, tick / TICKS_PER_HOUR, POINTS_FLOOR_SALT)).float()
    return POINTS_FLOOR_MIN + (ceiling - POINTS_FLOOR_MIN) * u
}

/** Colony threat in points: how big the next raid or hunt may be. Used by raids and manhunter packs. */
internal fun Game.threatPoints(): Float {
    val wealth = threatWealth()
    val health = colonists.map { it.healthFraction() }
    val animals = tamedAnimals.filter { !it.downed }.map { AnimalThreat(it.race.dangerous, it.healthFraction()) }
    val raw = basePoints(wealth, health, animals) * difficulty.threat * storyteller.threat * min(1f, 0.34f + day / 34f)
    return clampPoints(raw, raidPointsFloor())
}

/** The start value of [mixSeed]'s state (C; Stillmere's own choice, no vanilla value is reproduced). */
private const val MIX_START = 0x2545F4914F6CDD1DL

/** The first multiplier of [mixSeed] (C). Close to the usual 64-bit finaliser constant, with its top bit cleared. */
private const val MIX_MULTIPLIER_1 = 0x3F58476D1CE4E5B9L

/** The second multiplier of [mixSeed] (C). Close to the usual 64-bit finaliser constant, with its top bit cleared. */
private const val MIX_MULTIPLIER_2 = 0x14D049BB133111EBL

/**
 * A 64-bit hash of its parts, in the style of a splitmix finaliser. It makes the seeds for draws that must not move the
 * main RNG (the raid floor, the raid cycles). Same parts, same result. The start value, the multipliers and the shifts (30,
 * 27 and 31) are Stillmere's (C); see docs/fidelity/threats.md.
 */
internal fun mixSeed(vararg parts: Long): Long {
    var h = MIX_START
    for (p in parts) {
        h = h xor p
        h = (h xor (h ushr 30)) * MIX_MULTIPLIER_1
        h = (h xor (h ushr 27)) * MIX_MULTIPLIER_2
        h = h xor (h ushr 31)
    }
    return h
}

/** The salt that separates the raid floor's draws from every other seeded draw (C; any distinct value would do). */
private const val POINTS_FLOOR_SALT = 0x5E1A_0001L

/** The salt that separates each raid cycle's draws from the floor's and from each other (C; any distinct value would do). */
private const val RAID_CYCLE_SALT = 0x5E1A_0002L

// ---------------------------------------------------------------------------------------------- storyteller cycles

/**
 * Ticks in one step of the cycle clock. The storyteller's check runs every such step (Storyteller.cs:51), and its test that
 * a check is due is at Storyteller.cs:216, inside the Storyteller class.
 */
internal const val TICKS_PER_INTERVAL = 1000L

/** Cycle steps in a day: 60, so a day is 60,000 ticks (Storyteller.cs:49, in the Storyteller class). */
internal const val CYCLE_STEPS_PER_DAY = 60L

/**
 * An on and off cycle for raids (RimWorld/IncidentCycleUtility.cs). Each cycle is an on window of [onDays] followed by an off
 * window of [offDays]. Within an on window the raids come at random intervals, [minRaids] to [maxRaids] of them (rounded
 * randomly), at least [minSpacingDays] apart. Cycles begin at [minDays]. The values are Stillmere's (C, UNVERIFIED); the
 * vanilla values are XML.
 */
internal class RaidCycle(
    val minDays: Int, val onDays: Int, val offDays: Int, val minSpacingDays: Int, val minRaids: Int, val maxRaids: Int,
) {
    internal val origin = minDays * CYCLE_STEPS_PER_DAY
    internal val onLen = onDays * CYCLE_STEPS_PER_DAY
    internal val offLen = offDays * CYCLE_STEPS_PER_DAY
    internal val period = onLen + offLen
    /** The least gap between two hits, in intervals. */
    internal val spacing = minSpacingDays * CYCLE_STEPS_PER_DAY
    /** The same least gap, in ticks, for the timer of a channel. */
    internal val spacingTicks = minSpacingDays * TICKS_PER_DAY.toLong()
}

/** The raid cycle of each storyteller, or null for one that keeps the raid timer (Juniper, "wild and random"). */
internal fun Storyteller.raidCycle(): RaidCycle? = when (this) {
    Storyteller.MARLOWE -> RaidCycle(minDays = 8, onDays = 10, offDays = 6, minSpacingDays = 2, minRaids = 3, maxRaids = 4)
    Storyteller.ORRIN -> RaidCycle(minDays = 8, onDays = 8, offDays = 12, minSpacingDays = 2, minRaids = 2, maxRaids = 4)
    Storyteller.JUNIPER -> null
}

/**
 * The raid hits of [cycle], in order, from every cycle that starts at or before [limit] (in intervals). Each cycle's draw
 * is seeded by the game seed and the cycle index, so the hits do not depend on how often they are asked for.
 */
internal fun raidCycleHits(cycle: RaidCycle, seed: Long, limit: Long, fromCycle: Long = 0L): List<Long> {
    val out = ArrayList<Long>()
    var prevLast: Long? = null
    var i = fromCycle
    while (true) {
        val start = cycle.origin + i * cycle.period
        if (start > limit) break
        val hits = placeCycleHits(cycle, Rng(mixSeed(seed, RAID_CYCLE_SALT, i)), start, prevLast)
        out += hits
        if (hits.isNotEmpty()) prevLast = hits.last()
        i++
    }
    return out
}

/**
 * The hits of one on window starting at [start]: a rounded random count, placed at random intervals inside the window and
 * spaced apart. A draw whose last hit lands past the window is tried again, up to [CYCLE_PLACEMENT_DRAWS] times. If no draw
 * fits, the whole window is dropped and has no raids, not even the ones that fell inside it. Vanilla does the same: it clears
 * its hit list before it gives up, and logs an error (IncidentCycleUtility.cs:64-69).
 */
private fun placeCycleHits(cycle: RaidCycle, rng: Rng, start: Long, prevLast: Long?): List<Long> {
    // The window's end is excluded here. Vanilla accepts a last hit equal to the end (IncidentCycleUtility.cs:77, 109).
    val end = start + cycle.onLen
    val f = cycle.minRaids + (cycle.maxRaids - cycle.minRaids) * rng.float()
    val count = f.toInt() + (if (rng.float() < f - f.toInt()) 1 else 0)
    if (count == 0) return emptyList()
    repeat(CYCLE_PLACEMENT_DRAWS) {
        val hits = LongArray(count) { start + rng.int(cycle.onLen.toInt()) }
        hits.sort()
        spaceHits(hits, cycle.spacing, prevLast)
        if (hits.last() < end) return hits.toList()
    }
    return emptyList()
}

/**
 * The draws tried for one on window before it is dropped: 101. Vanilla's count is 101 too (IncidentCycleUtility.cs:65 lets the
 * first 101 draws through; the 102nd check at :65 gives up).
 */
private const val CYCLE_PLACEMENT_DRAWS = 101

/** Pushes each hit later until it is at least [spacing] after the one before it (the first one after [prev], if given). */
private fun spaceHits(hits: LongArray, spacing: Long, prev: Long?) {
    var before = prev
    for (k in hits.indices) {
        val b = before
        if (b != null && hits[k] - b < spacing) hits[k] = b + spacing
        before = hits[k]
    }
}

/** The first raid hit at or after [atTick], as a tick. Hits are only ever read through here, so the schedule is stateless. */
internal fun Game.nextRaidCycleTick(cycle: RaidCycle, atTick: Long): Long {
    val target = (atTick + TICKS_PER_INTERVAL - 1) / TICKS_PER_INTERVAL
    // A cycle's hits depend on the cycle before only through the spacing, and the cycle before ends at least [RaidCycle.offLen]
    // intervals before this one starts. When the off window is at least the spacing, the walk can begin one cycle before the
    // target. Otherwise it starts at the first cycle.
    val fromCycle = if (cycle.offLen >= cycle.spacing) max(0L, (target - cycle.origin) / cycle.period - 1) else 0L
    val hits = raidCycleHits(cycle, seed, target + cycle.period, fromCycle)
    val hit = hits.firstOrNull { it >= target } ?: return Long.MAX_VALUE
    return hit * TICKS_PER_INTERVAL
}

// ---------------------------------------------------------------------------------------------- recent incidents

/** Weight factors for the most recent random incidents, most recent first (RimWorld/QuestTuning.cs:14). */
internal val RECENT_INCIDENT_WEIGHTS = floatArrayOf(0.01f, 0.3f, 0.5f, 0.7f, 0.9f)

/** The factor an incident's weight takes from how recently it happened. Each place it appears in the list multiplies it. */
internal fun Game.recentWeight(id: Incident): Float {
    var f = 1f
    for (i in lastRandomIncidents.indices) if (lastRandomIncidents[i] == id) f *= RECENT_INCIDENT_WEIGHTS[i]
    return f
}

/**
 * How many manhunters a hunt makes. [points] are the threat points (before [MANHUNTER_POINTS_FACTOR]). Vanilla divides the
 * ambush points by the animal's combat power, rounds the result and holds it between 2 and 100 (AggressiveAnimalIncidentUtility.cs:
 * 107). Stillmere rounds the same way, with its own combat power stand-in and its own upper bound. Every value is in
 * docs/fidelity/threats.md.
 */
internal fun manhuntCount(points: Float, danger: Float): Int {
    val combatPower = MANHUNTER_COMBAT_POWER_PER_DANGER * danger.coerceAtLeast(MANHUNTER_DANGER_FLOOR)
    return round(points * MANHUNTER_POINTS_FACTOR / combatPower).toInt().coerceIn(MANHUNTER_MIN_COUNT, MANHUNTER_MAX_COUNT)
}

/** Ambush points are three quarters of the threat before the animal is chosen (IncidentWorker_Ambush_ManhunterPack.cs:9, 40-43). */
internal const val MANHUNTER_POINTS_FACTOR = 0.75f

/** Stillmere's stand-in for a manhunter's combat power, per unit of danger (C, UNVERIFIED; vanilla's combat power is XML). */
internal const val MANHUNTER_COMBAT_POWER_PER_DANGER = 18f

/** The least danger the count uses, so an animal of almost no danger cannot divide the points by almost nothing (C). */
internal const val MANHUNTER_DANGER_FLOOR = 0.5f

/** The fewest animals in a pack (D: AggressiveAnimalIncidentUtility.cs:12, held by the clamp at :107). */
internal const val MANHUNTER_MIN_COUNT = 2

/** The most animals in a pack: Stillmere's bound (C, scope: vanilla's is 100 at AggressiveAnimalIncidentUtility.cs:14, 107). */
internal const val MANHUNTER_MAX_COUNT = 10
