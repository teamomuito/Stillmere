package io.github.teamomuito.colony.sim

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class LogEntry(val tick: Long, val text: String, val level: Int) // level: 0 info, 1 good, 2 warning, 3 bad
class Shot(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val expires: Long, val hit: Boolean, val kind: Int = 0)
class Blast(val x: Int, val y: Int, val radius: Float, val expires: Long)

enum class Weather(val label: String) { CLEAR("Clear"), CLOUDY("Overcast"), RAIN("Rain"), FOG("Fog"), SNOW("Snow"), THUNDER("Thunderstorm") }

enum class Storyteller(val label: String, val desc: String, val threat: Float, val chaos: Float) {
    MARLOWE("Marlowe", "Steadily rising threats with rest between. The classic arc.", 1f, 0.2f),
    JUNIPER("Juniper", "Wild and random. Anything can happen at any time.", 1f, 1f),
    ORRIN("Orrin", "A gentle builder. Smaller raids and long quiet spells.", 0.6f, 0.15f),
}

enum class Difficulty(val label: String, val threat: Float, val colonistDamage: Float, val loseOnDeath: Boolean) {
    PEACEFUL("Peaceful", 0.0f, 0.5f, false), EASY("Easy", 0.6f, 0.8f, false), RANDY("Normal", 0.9f, 1f, false), HARD("Challenge", 1.5f, 1.1f, false), EXTREME("Losing is fun", 2.2f, 1.25f, false)
}

enum class Scenario(val label: String, val desc: String) {
    CRASHLANDED("Crashlanded", "Three survivors with a few supplies from the wreck."),
    LOST_TRIBE("The lost tribe", "Five tribals with spears, hides and none of the old science."),
    RICH_EXPLORER("Rich explorer", "A lone explorer with a fortune and a good rifle."),
    SOLO("Naked brutality", "One person, nothing but their clothes."),
}

class Game(val seed: Long, val map: GameMap = GameMap.generateFor(MAP_SIZE, MAP_SIZE, seed, Biome.TEMPERATE)) {
    var rng = Rng(seed + 1)
    val finder = Pathfinder(map)
    var tick = 6L * TICKS_PER_HOUR
    val pawns = ArrayList<Pawn>()
    val shots = ArrayList<Shot>()
    val blasts = ArrayList<Blast>()
    val log = ArrayList<LogEntry>()
    val reservations = HashMap<Int, Int>()
    val unreachable = HashMap<Long, Long>()
    var nextPawnId = 1

    // Settings
    var scenario = Scenario.CRASHLANDED
    var storyteller = Storyteller.MARLOWE
    var difficulty = Difficulty.RANDY
    var colonyName = "New Arrivals"

    // Research
    val researchDone = HashSet<Research>()
    val researchProgress = HashMap<Research, Float>()
    var researchCurrent: Research? = null

    // Weather and temperature
    var weather = Weather.CLEAR
    var weatherUntil = 0L
    var tempOffset = 0f
    var tempEventUntil = 0L
    var tempEventName = ""
    var solarFlareUntil = 0L
    var toxicFalloutUntil = 0L
    var eclipseUntil = 0L

    // Storyteller state
    var nextRaid = 0L
    var nextWanderer = 0L
    var nextPod = 0L
    var nextTempEvent = 0L
    var nextMisc = 0L
    var nextTrader = 0L
    var raidActive = false
    var raidStartCount = 0
    var raidEnds = 0L
    var raidsSurvived = 0
    var raidCounter = 0
    var gameOver = false
    var won = false
    var homeX = MAP_SIZE / 2
    var homeY = MAP_SIZE / 2
    var shipLaunching = 0L
    var windFactor = 0.8f
    var statsKilled = 0
    var statsDays = 0

    // Power
    val power = PowerState()
    // Trading
    val traders = ArrayList<TraderInfo>()
    // World map and caravans
    /** The biome the world was generated around; kept so a later colony on another tile sees the same planet. */
    var worldBiome = map.biome
    var world = World.generate(seed, map.biome)
    val caravans = ArrayList<Caravan>()
    var nextCaravanId = 1
    var silverEarned = 0
    var autosaveHook: (() -> Unit)? = null
    var debugHook: ((String) -> Unit)? = null
    var mentalBreaksEnabled = true
    /** True for battle maps (caravan fights). */
    var encounter = false
    /** A caravan fight that has been asked for but not begun yet; the world waits for it. */
    var pendingBattle: BattlePlan? = null
    /** On a battle map: its plan, and the world it returns to. */
    var battle: BattlePlan? = null
    var parent: Game? = null
    /** Open ransom offers for prisoners, and when each prisoner may be offered again. */
    val ransomOffers = ArrayList<RansomOffer>()
    val ransomCooldown = HashMap<Int, Long>()
    var nextRansomId = 1
    /** On a battle map: the caravan's people, alive, whether or not they are still on the map. */
    val battleMembers = ArrayList<Pawn>()
    var hintBits = 0
    var graveyard = ArrayList<String>()

    init {
        nextRaid = 8L * TICKS_PER_DAY + rng.int(4 * TICKS_PER_DAY)
        nextWanderer = 3L * TICKS_PER_DAY + rng.int(3 * TICKS_PER_DAY)
        nextPod = 2L * TICKS_PER_DAY + rng.int(4 * TICKS_PER_DAY)
        nextTempEvent = 8L * TICKS_PER_DAY + rng.int(6 * TICKS_PER_DAY)
        nextMisc = 4L * TICKS_PER_DAY + rng.int(3 * TICKS_PER_DAY)
        nextTrader = 6L * TICKS_PER_DAY + rng.int(4 * TICKS_PER_DAY)
    }

    // ------------------------------------------------------------------ time
    val hour get() = ((tick / TICKS_PER_HOUR) % 24).toInt()
    val day get() = (tick / TICKS_PER_DAY).toInt()
    val season get() = Season.entries[(day / DAYS_PER_SEASON) % 4]
    val year get() = 5500 + day / (DAYS_PER_SEASON * 4)
    val dayOfSeason get() = day % DAYS_PER_SEASON + 1
    val isSleepHour get() = hour >= 22 || hour < 6

    fun daylight(): Float {
        val h = (tick % TICKS_PER_DAY) / TICKS_PER_HOUR.toFloat()
        var l = when {
            h < 5f -> 0f
            h < 7f -> (h - 5f) / 2f
            h < 18f -> 1f
            h < 20f -> (20f - h) / 2f
            else -> 0f
        }
        if (eclipseUntil > tick) l *= 0.15f
        when (weather) { Weather.CLOUDY -> l *= 0.85f; Weather.RAIN, Weather.SNOW -> l *= 0.7f; Weather.THUNDER -> l *= 0.55f; Weather.FOG -> l *= 0.8f; else -> {} }
        return l
    }

    private fun seasonTemp(s: Season): Float {
        val b = map.biome
        return when (s) { Season.SPRING -> b.springT; Season.SUMMER -> b.summerT; Season.FALL -> b.fallT; Season.WINTER -> b.winterT }
    }

    fun outdoorTemp(): Float {
        val h = (tick % TICKS_PER_DAY) / TICKS_PER_HOUR.toFloat()
        val diurnal = sin(((h - 9f) / 24f) * 2f * PI.toFloat()) * (if (map.biome == Biome.DESERT || map.biome == Biome.ARID) 10f else 7f)
        val s = season
        val next = Season.entries[(s.ordinal + 1) % 4]
        val blend = (dayOfSeason - 1) / DAYS_PER_SEASON.toFloat()
        val base = seasonTemp(s) + (seasonTemp(next) - seasonTemp(s)) * blend * 0.5f
        var w = 0f
        when (weather) { Weather.RAIN -> w = -2f; Weather.SNOW -> w = -3f; Weather.THUNDER -> w = -3f; Weather.CLOUDY -> w = -1f; else -> {} }
        return base + diurnal + tempOffset + w
    }

    fun dateLabel() = "${hour.toString().padStart(2, '0')}:00  Day $dayOfSeason of ${season.label}, $year"

    // ------------------------------------------------------------------ logging
    fun say(text: String, level: Int = 0) {
        log.add(LogEntry(tick, text, level))
        if (log.size > 300) log.removeAt(0)
    }

    // ------------------------------------------------------------------ pawns
    val colonists get() = pawns.filter { it.colonist && it.alive }
    val tamedAnimals get() = pawns.filter { it.isAnimal && it.faction == Faction.PLAYER && it.alive }
    val prisoners get() = pawns.filter { it.prisoner && it.alive }
    val hostiles get() = pawns.filter { it.hostile && it.alive }
    val humansOnSide get() = pawns.filter { it.faction == Faction.PLAYER && it.alive && !it.isAnimal }
    fun pawnAt(x: Int, y: Int): Pawn? = pawns.firstOrNull { it.alive && it.x == x && it.y == y && it.carriedBy < 0 }
    fun pawnById(id: Int): Pawn? = pawns.firstOrNull { it.id == id }

    fun newHuman(x: Int, y: Int, faction: Faction = Faction.PLAYER, tribal: Boolean = false): Pawn {
        val first = rng.pick(Names.first)
        val p = Pawn(nextPawnId++, "$first ${rng.pick(Names.last)}", Race.HUMAN, faction)
        p.female = first in Names.female
        p.age = rng.range(19, 55)
        p.birthday = rng.int(DAYS_PER_YEAR)
        val child = rng.pick(Backstories.childhood)
        val adult = rng.pick(Backstories.adulthood)
        p.backstory = "${child.title}, ${adult.title.lowercase()}"
        for (s in SkillType.entries) p.skill[s.ordinal] = if (rng.chance(0.15f)) rng.range(0, 2) else rng.range(1, 8)
        for ((s, v) in child.skills) p.skill[s.ordinal] = max(p.skill[s.ordinal], v + rng.range(-1, 2))
        for ((s, v) in adult.skills) p.skill[s.ordinal] = max(p.skill[s.ordinal], v + rng.range(-1, 2))
        for (w in adult.disabled) p.incapable = p.incapable or (1 shl w.ordinal)
        val n = rng.range(1, 3)
        repeat(n) {
            val s = rng.int(SkillType.entries.size)
            p.passion[s] = if (rng.chance(0.3f)) 2 else 1
            p.skill[s] = min(20, p.skill[s] + 2)
        }
        repeat(rng.range(1, 3)) {
            val t = Trait.entries[rng.int(Trait.entries.size)]
            val clash = p.traits.any { o -> conflicts(o, t) || o == t }
            if (!clash) p.traits.add(t)
        }
        if (p.traits.none { it == Trait.GAY || it == Trait.BISEXUAL || it == Trait.ASEXUAL } && rng.chance(0.16f)) p.traits.add(rng.pick(listOf(Trait.GAY, Trait.BISEXUAL, Trait.ASEXUAL, Trait.BISEXUAL)))
        if (Trait.BRAWLER in p.traits) p.passion[SkillType.MELEE.ordinal] = 2
        if (Trait.NUDIST in p.traits) { /* will undress when the schedule allows */ }
        p.x = x; p.y = y; p.fromX = x; p.fromY = y
        for (w in WorkType.entries) {
            val sk = w.skill()
            if (sk == null) { p.priority[w.ordinal] = if (w == WorkType.FIREFIGHT || w == WorkType.PATIENT) 1 else 3; continue }
            val sv = p.skill[sk.ordinal]
            val pas = p.passion[sk.ordinal]
            p.priority[w.ordinal] = if (pas == 2) 1 else if (pas == 1 || sv >= 9) 2 else 3
            if (p.incapable and (1 shl w.ordinal) != 0) p.priority[w.ordinal] = 0
            if (Trait.BRAWLER in p.traits && w == WorkType.HUNT) p.priority[w.ordinal] = 0
        }
        p.priority[WorkType.HAUL.ordinal] = 4
        p.priority[WorkType.CLEAN.ordinal] = 4
        p.priority[WorkType.HUNT.ordinal] = if (p.priority[WorkType.HUNT.ordinal] == 0) 0 else 4
        p.priority[WorkType.WARDEN.ordinal] = if (p.skill[SkillType.SOCIAL.ordinal] >= 5) 3 else 4
        if (tribal) {
            p.weaponItem = rng.pick(listOf(ItemType.W_SPEAR, ItemType.W_CLUB, ItemType.W_BOW))
            p.apparel.add(Worn(ItemType.A_TRIBAL, Quality.NORMAL, 90f))
        } else if (faction == Faction.PLAYER) {
            p.apparel.add(Worn(ItemType.A_TSHIRT, rng.pick(listOf(Quality.POOR, Quality.NORMAL, Quality.NORMAL, Quality.GOOD)), 60f))
            p.apparel.add(Worn(ItemType.A_PANTS, Quality.NORMAL, 80f))
        }
        p.mood = 0.6f
        recomputeHealth(p)
        pawns.add(p)
        return p
    }

    fun conflicts(a: Trait, b: Trait): Boolean {
        val pairs = listOf(
            Trait.HARD_WORKER to Trait.LAZY, Trait.OPTIMIST to Trait.PESSIMIST, Trait.NIMBLE to Trait.SLOW_WALKER,
            Trait.TOUGH to Trait.WIMP, Trait.FAST_LEARNER to Trait.SLOW_LEARNER, Trait.KIND to Trait.ABRASIVE,
            Trait.SANGUINE to Trait.DEPRESSIVE, Trait.BEAUTIFUL to Trait.UGLY, Trait.TOO_SMART to Trait.SLOW_LEARNER,
            Trait.BRAWLER to Trait.TRIGGER_HAPPY, Trait.PSYCHOPATH to Trait.KIND,
            Trait.GAY to Trait.BISEXUAL, Trait.GAY to Trait.ASEXUAL, Trait.BISEXUAL to Trait.ASEXUAL, Trait.IRON_WILLED to Trait.VOLATILE,
            Trait.JOGGER to Trait.SLOW_WALKER, Trait.GREEDY to Trait.ASCETIC, Trait.CAREFUL_SHOOTER to Trait.TRIGGER_HAPPY,
        )
        return pairs.any { (x, y) -> (a == x && b == y) || (a == y && b == x) }
    }

    fun newAnimal(race: Race, x: Int, y: Int, faction: Faction = Faction.WILD): Pawn {
        val p = Pawn(nextPawnId++, if (faction == Faction.PLAYER) rng.pick(Names.animal) else race.label, race, faction)
        p.x = x; p.y = y; p.fromX = x; p.fromY = y
        p.age = rng.range(1, 8)
        p.female = rng.chance(0.5f)
        p.food = 0.65f + rng.float() * 0.3f
        p.tame = faction == Faction.PLAYER
        p.animalProductTimer = rng.int(TICKS_PER_DAY)
        recomputeHealth(p)
        pawns.add(p)
        return p
    }

    fun newMech(race: Race, x: Int, y: Int, raidId: Int): Pawn {
        val p = Pawn(nextPawnId++, race.label, race, Faction.ENEMY)
        p.raidId = raidId
        p.skill[SkillType.SHOOTING.ordinal] = 8
        p.skill[SkillType.MELEE.ordinal] = 10
        p.x = x; p.y = y; p.fromX = x; p.fromY = y
        recomputeHealth(p)
        pawns.add(p)
        return p
    }

    fun newRaider(x: Int, y: Int, weaponItem: ItemType?, raidId: Int, armor: List<ItemType> = emptyList()): Pawn {
        val p = Pawn(nextPawnId++, rng.pick(Names.raider) + " " + rng.pick(Names.raider), Race.HUMAN, Faction.ENEMY)
        p.weaponItem = weaponItem
        p.weaponQuality = rng.pick(listOf(Quality.POOR, Quality.NORMAL, Quality.NORMAL, Quality.GOOD))
        p.raidId = raidId
        p.skill[SkillType.SHOOTING.ordinal] = rng.range(2, 9)
        p.skill[SkillType.MELEE.ordinal] = rng.range(3, 10)
        p.apparel.add(Worn(ItemType.A_TRIBAL, Quality.NORMAL, 70f))
        for (a in armor) p.apparel.add(Worn(a, Quality.NORMAL, 120f))
        p.x = x; p.y = y; p.fromX = x; p.fromY = y
        recomputeHealth(p)
        pawns.add(p)
        return p
    }

    // ------------------------------------------------------------------ scenario
    private fun findStartSpot(): Pair<Int, Int> {
        val cx = map.w / 2
        val cy = map.h / 2
        var sx = cx; var sy = cy
        loop@ for (r in 0 until 30) for (y in cy - r..cy + r) for (x in cx - r..cx + r) {
            if (!map.inB(x, y)) continue
            val i = map.idx(x, y)
            if (map.terrain[i] == Terrain.SOIL || map.terrain[i] == Terrain.RICH_SOIL || (map.biome == Biome.DESERT || map.biome == Biome.ARID) && map.terrain[i] == Terrain.SAND) {
                var ok = true
                for (yy in y - 3..y + 3) for (xx in x - 3..x + 3) {
                    if (!map.inB(xx, yy)) { ok = false; continue }
                    val t = map.terrain[map.idx(xx, yy)]
                    if (!t.passable || t == Terrain.WATER_SHALLOW) ok = false
                }
                if (ok) { sx = x; sy = y; break@loop }
            }
        }
        return sx to sy
    }

    fun startNewColony(sc: Scenario = scenario, supplied: List<Pawn>? = null) {
        scenario = sc
        val (sx, sy) = findStartSpot()
        homeX = sx; homeY = sy
        for (yy in sy - 4..sy + 4) for (xx in sx - 4..sx + 4) if (map.inB(xx, yy)) map.plant[map.idx(xx, yy)] = null

        val count = when (sc) { Scenario.CRASHLANDED -> 3; Scenario.LOST_TRIBE -> 5; Scenario.RICH_EXPLORER -> 1; Scenario.SOLO -> 1 }
        val crew = ArrayList<Pawn>()
        if (supplied != null) {
            for ((k, p) in supplied.withIndex()) {
                p.x = sx - 1 + k % 5; p.y = sy + 2 + k / 5; p.fromX = p.x; p.fromY = p.y
                if (!pawns.contains(p)) pawns.add(p)
                crew.add(p)
            }
        } else for (k in 0 until count) crew.add(newHuman(sx - 1 + k, sy + 2, Faction.PLAYER, sc == Scenario.LOST_TRIBE))
        for ((k, p) in crew.withIndex()) {
            p.food = 0.8f; p.rest = 0.85f
            when (sc) {
                Scenario.CRASHLANDED -> {
                    p.weaponItem = listOf(ItemType.W_RIFLE, ItemType.W_REVOLVER, ItemType.W_KNIFE).getOrElse(k) { ItemType.W_CLUB }
                }
                Scenario.LOST_TRIBE -> {}
                Scenario.RICH_EXPLORER -> { p.weaponItem = ItemType.W_RIFLE; p.apparel.add(Worn(ItemType.A_FLAK_VEST, Quality.GOOD, 160f)); p.apparel.add(Worn(ItemType.A_HELMET, Quality.GOOD, 150f)) }
                Scenario.SOLO -> { p.weaponItem = null }
            }
            recomputeHealth(p)
        }
        // A starter stockpile with the supplies.
        val z = map.newZone(ZoneKind.STOCKPILE)
        z.allowed[ItemType.CORPSE_HUMAN.ordinal] = false
        for (yy in sy - 2..sy) for (xx in sx - 2..sx + 1) { map.zoneId[map.idx(xx, yy)] = z.id; z.cells++ }
        val sup = ArrayList<Pair<ItemType, Int>>()
        when (sc) {
            Scenario.CRASHLANDED -> {
                researchDone.addAll(listOf(Research.COMPLEX_FURNITURE))
                sup += ItemType.MEAL_PACKAGED to 30; sup += ItemType.WOOD to 150; sup += ItemType.STEEL to 250; sup += ItemType.COMPONENT to 12
                sup += ItemType.MEDS_HERBAL to 8; sup += ItemType.MEDS_INDUSTRIAL to 4; sup += ItemType.SILVER to 200; sup += ItemType.CLOTH to 60
            }
            Scenario.LOST_TRIBE -> {
                sup += ItemType.PEMMICAN to 60; sup += ItemType.WOOD to 160; sup += ItemType.STEEL to 60; sup += ItemType.LEATHER to 60
                sup += ItemType.MEDS_HERBAL to 6; sup += ItemType.CLOTH to 80; sup += ItemType.RICE to 40
                researchDone.add(Research.BASIC_MELEE); researchDone.add(Research.SMITHING); researchDone.add(Research.BOWS)
            }
            Scenario.RICH_EXPLORER -> {
                researchDone.addAll(listOf(Research.COMPLEX_FURNITURE, Research.SMITHING, Research.STONECUTTING, Research.TAILORING))
                sup += ItemType.MEAL_PACKAGED to 20; sup += ItemType.SILVER to 1800; sup += ItemType.STEEL to 400; sup += ItemType.WOOD to 200
                sup += ItemType.MEDS_INDUSTRIAL to 10; sup += ItemType.COMPONENT to 20; sup += ItemType.GOLD to 60
            }
            Scenario.SOLO -> { sup += ItemType.MEAL_PACKAGED to 6 }
        }
        var k = 0
        for ((t, n) in sup) {
            val cell = k % 8
            map.drop(t, n, sx - 2 + cell % 4, sy - 2 + cell / 4)
            k++
        }
        if (sc == Scenario.LOST_TRIBE) {
            map.drop(ItemType.W_SPEAR, 1, sx - 2, sy); map.drop(ItemType.W_BOW, 1, sx - 1, sy)
        }
        if (sc == Scenario.CRASHLANDED || sc == Scenario.RICH_EXPLORER) {
            // Pets.
            if (rng.chance(0.5f)) { val a = newAnimal(Race.HUSKY, sx + 2, sy + 2, Faction.PLAYER); a.tame = true }
        }
        map.rebuildRooms(outdoorTemp())
        populateWildlife()
        say("Your colonists have arrived. Build beds, grow food, and survive.", 1)
        say("Tip: open Architect to mark trees and rock, and to place buildings and zones.", 0)
    }

    /** Found a new colony from a caravan's people and cargo, continuing the old game's calendar and research. */
    fun startSettlement(from: Game, members: List<Pawn>, cargo: Map<ItemType, Int>, name: String) {
        scenario = from.scenario; storyteller = from.storyteller; difficulty = from.difficulty
        colonyName = name
        tick = from.tick
        nextPawnId = from.nextPawnId
        researchDone.addAll(from.researchDone)
        for ((r, v) in from.researchProgress) researchProgress[r] = v
        researchCurrent = from.researchCurrent
        graveyard.addAll(from.graveyard)
        statsKilled = from.statsKilled; silverEarned = from.silverEarned; raidsSurvived = from.raidsSurvived
        val (sx, sy) = findStartSpot()
        homeX = sx; homeY = sy
        for (yy in sy - 4..sy + 4) for (xx in sx - 4..sx + 4) if (map.inB(xx, yy)) map.plant[map.idx(xx, yy)] = null
        for ((k, p) in members.withIndex()) {
            p.x = sx - 1 + k % 5; p.y = sy + 2 + k / 5; p.fromX = p.x; p.fromY = p.y; p.moveCd = 0
            p.job = null; p.bedId = -1; p.homeTile = -1; p.reserved.clear(); p.clearPath()
            for (b in 0 until 3) { }
            pawns.add(p)
            recomputeHealth(p)
        }
        val z = map.newZone(ZoneKind.STOCKPILE)
        z.allowed[ItemType.CORPSE_HUMAN.ordinal] = false
        for (yy in sy - 2..sy) for (xx in sx - 2..sx + 1) { map.zoneId[map.idx(xx, yy)] = z.id; z.cells++ }
        var k = 0
        for ((t, n) in cargo) { if (n > 0) { val cell = k % 8; map.drop(t, n, sx - 2 + cell % 4, sy - 2 + cell / 4); k++ } }
        map.rebuildRooms(outdoorTemp())
        populateWildlife()
        nextRaid = tick + 6L * TICKS_PER_DAY
        nextTrader = tick + 8L * TICKS_PER_DAY
        say("$name is founded. Build beds, grow food, and survive.", 1)
    }

    // ------------------------------------------------------------------ reservations
    fun reserve(p: Pawn, key: Int): Boolean {
        val holder = reservations[key]
        if (holder != null && holder != p.id) {
            val other = pawnById(holder)
            if (other != null && other.alive && other.reserved.contains(key)) return false
        }
        reservations[key] = p.id
        if (!p.reserved.contains(key)) p.reserved.add(key)
        return true
    }

    fun isFree(p: Pawn, key: Int): Boolean {
        val holder = reservations[key] ?: return true
        if (holder == p.id) return true
        val other = pawnById(holder) ?: return true
        return !(other.alive && other.reserved.contains(key))
    }

    fun releaseAll(p: Pawn) {
        for (k in p.reserved) if (reservations[k] == p.id) reservations.remove(k)
        p.reserved.clear()
    }

    // ------------------------------------------------------------------ player API
    private fun cell(x: Int, y: Int) = if (map.inB(x, y)) map.idx(x, y) else -1

    fun designate(x: Int, y: Int, kind: Int): Boolean {
        var i = cell(x, y); if (i < 0) return false
        if (kind == Desig.DECON || kind == Desig.REPAIR) map.building[i]?.let { i = map.idx(it.x, it.y) }
        when (kind) {
            Desig.MINE -> if (map.terrain[i] == Terrain.ROCK) map.desig[i] = kind.toByte() else return false
            Desig.CUT -> if (map.plant[i] != null && !(map.plant[i]!!.type.crop && map.zoneKind(i) == ZoneKind.GROWING)) map.desig[i] = kind.toByte() else return false
            Desig.HARVEST -> if (map.plant[i] != null && map.plant[i]!!.mature && !map.plant[i]!!.type.isTree) map.desig[i] = kind.toByte() else return false
            Desig.REPAIR -> if (map.building[i]?.built == true) map.desig[i] = kind.toByte() else return false
            Desig.DECON -> {
                val b = map.building[i]
                if (b != null) {
                    if (!b.built) removeBlueprint(i) else map.desig[i] = kind.toByte()
                } else if (map.floor[i] != null) map.desig[i] = kind.toByte() else return false
            }
            else -> map.desig[i] = 0
        }
        return true
    }

    fun clearDesignation(x: Int, y: Int) { val i = cell(x, y); if (i >= 0) map.desig[i] = 0 }

    /** Whether the whole footprint (rotated or not) is buildable. */
    /** The material a new structure of [def] would be made of, or null if the choice is not valid. */
    fun materialFor(def: BuildDef, material: ItemType?): ItemType? {
        val options = def.stuff ?: return null
        val m = material ?: options.first()
        if (m !in options) return null
        if (Materials.of(m)?.research?.let { it !in researchDone } == true) return null
        return m
    }

    fun canBuildAt(def: BuildDef, x: Int, y: Int, rot: Boolean = false, material: ItemType? = null): Boolean {
        if (def.stuff != null && materialFor(def, material) == null) return false
        if (def.stuff == null && material != null) return false
        val fw = if (rot) def.h else def.w; val fh = if (rot) def.w else def.h
        for (yy in y until y + fh) for (xx in x until x + fw) if (!canBuildCell(def, xx, yy)) return false
        return true
    }

    private fun canBuildCell(def: BuildDef, x: Int, y: Int): Boolean {
        val i = cell(x, y); if (i < 0) return false
        val t = map.terrain[i]
        if (!t.passable || t == Terrain.WATER_SHALLOW) return false
        if (def.research != null && def.research !in researchDone) return false
        if (def == BuildDef.CONDUIT) return map.building[i] == null && !map.conduitAt(i)
        if (map.building[i] != null) return false
        if (def.isFloor) return map.floor[i] != def
        if (def == BuildDef.HYDROPONICS && map.plant[i] != null) return false
        if (def.isShip && def == BuildDef.SHIP_COMPUTER && map.buildings().any { it != null && it.def == BuildDef.SHIP_COMPUTER }) return false
        if (def.workbench && def != BuildDef.CAMPFIRE && def != BuildDef.CRAFTING_SPOT && map.plant[i]?.type?.isTree == true) return false
        return true
    }

    /** Places a blueprint. [material] chooses what a material-based structure is made of, for this order only. */
    fun placeBlueprint(def: BuildDef, x: Int, y: Int, rot: Boolean = false, material: ItemType? = null): Boolean {
        if (!canBuildAt(def, x, y, rot, material)) return false
        val b = Building(def, x, y, false)
        b.rot = rot
        if (def.stuff != null) b.material = materialFor(def, material)
        b.hp = b.maxHp
        for (yy in y until y + b.fh) for (xx in x until x + b.fw) {
            val i = map.idx(xx, yy)
            if (map.plant[i] != null) map.plant[i] = null
            map.desig[i] = 0
        }
        map.setBuilding(b)
        return true
    }

    private fun removeBlueprint(i: Int) {
        val b = map.building[i] ?: return
        if (!b.built) for ((k, c) in b.cost.withIndex()) if (b.delivered[k] > 0) map.drop(c.first, b.delivered[k], b.x, b.y)
        map.removeBuilding(b)
    }

    fun setZone(x: Int, y: Int, kind: Int, crop: PlantType = PlantType.RICE, zone: Zone? = null): Zone? {
        val i = cell(x, y); if (i < 0) return null
        if (kind == ZoneKind.NONE) {
            val z = map.zoneAt(i)
            if (z != null) { z.cells--; if (z.cells <= 0) map.zones.remove(z.id) }
            map.zoneId[i] = 0
            return null
        }
        val t = map.terrain[i]
        if (!t.passable || t == Terrain.WATER_SHALLOW) return null
        val b = map.building[i]
        if (b != null && b.def.blocksMove && !(b.def == BuildDef.HYDROPONICS)) return null
        if (kind == ZoneKind.GROWING && t.fertility <= 0f && b?.def != BuildDef.HYDROPONICS) return null
        val old = map.zoneAt(i)
        if (old != null) { if (old === zone) return zone; old.cells--; if (old.cells <= 0) map.zones.remove(old.id) }
        val z = zone ?: map.newZone(kind).also {
            it.crop = crop
            if (kind == ZoneKind.STOCKPILE) it.allowed[ItemType.CORPSE_HUMAN.ordinal] = false
            if (kind == ZoneKind.DUMPING) { it.allowed.fill(false); it.allowed[ItemType.CORPSE_HUMAN.ordinal] = true; it.priority = 0 }
        }
        map.zoneId[i] = z.id
        z.cells++
        return z
    }

    /** Paint a rectangle with one zone; joins an existing neighbouring zone of the same kind. */
    fun paintZone(x0: Int, y0: Int, x1: Int, y1: Int, kind: Int, crop: PlantType = PlantType.RICE) {
        var z: Zone? = null
        outer@ for (y in y0 - 1..y1 + 1) for (x in x0 - 1..x1 + 1) {
            if (!map.inB(x, y)) continue
            val inside = x in x0..x1 && y in y0..y1
            if (inside) continue
            val ex = map.zoneAt(map.idx(x, y)) ?: continue
            if (ex.kind == kind && (kind != ZoneKind.GROWING || ex.crop == crop)) { z = ex; break@outer }
        }
        for (y in y0..y1) for (x in x0..x1) {
            if (!map.inB(x, y)) continue
            val made = setZone(x, y, kind, crop, z)
            if (z == null && made != null) z = made
        }
    }

    fun deleteZone(zone: Zone) {
        for (i in 0 until map.size) if (map.zoneId[i] == zone.id) map.zoneId[i] = 0
        map.zones.remove(zone.id)
    }

    fun setPriority(p: Pawn, w: WorkType, v: Int) {
        if (p.incapable and (1 shl w.ordinal) != 0) return
        p.priority[w.ordinal] = v.coerceIn(0, 4)
    }

    fun setDrafted(p: Pawn, v: Boolean) {
        if (p.faction != Faction.PLAYER || p.downed || p.prisoner) return
        p.drafted = v
        endJob(p)
        if (!v) say("${p.name} stood down.", 0)
    }

    fun orderMove(p: Pawn, x: Int, y: Int) {
        if (!p.drafted || !map.inB(x, y)) return
        endJob(p)
        p.job = Job(JobType.MOVE, x, y)
    }

    fun orderAttack(p: Pawn, target: Pawn) {
        if (!p.drafted) return
        endJob(p)
        val j = Job(JobType.ATTACK)
        j.targetPawn = target.id
        j.aux = 1
        p.job = j
    }

    fun startResearch(r: Research?) {
        if (r != null && !researchAvailable(r)) return
        researchCurrent = r
    }

    fun researchAvailable(r: Research) = r !in researchDone && r.needs.all { it in researchDone }

    fun shipComplete(): Boolean {
        var core = 0; var engines = 0; var reactor = 0; var casket = 0
        for (b in map.buildings()) {
            if (b == null || !b.built) continue
            when (b.def) {
                BuildDef.SHIP_COMPUTER -> core++
                BuildDef.SHIP_ENGINE -> engines++
                BuildDef.SHIP_REACTOR -> reactor++
                BuildDef.SHIP_CASKET -> casket++
                else -> {}
            }
        }
        return core >= 1 && engines >= 2 && reactor >= 1 && casket >= 1
    }

    fun launchShip(): Boolean {
        if (!shipComplete() || colonists.isEmpty()) return false
        won = true
        gameOver = true
        say("The ship lifts off. You escaped the rim.", 1)
        return true
    }

    fun toggleForbidden(i: Int) {
        map.items[i]?.let { it.forbidden = !it.forbidden }
        map.building[i]?.let { it.forbidden = !it.forbidden }
    }

    // ------------------------------------------------------------------ main tick
    fun step() {
        if (gameOver || pendingBattle != null) return
        tick++
        for (p in pawns.toList()) pawnTick(p)
        if (battle != null) battleTick()
        if (tick % 10 == 0L) { turretsTick(); trapsTick() }
        if (tick % 4 == 0L) fireTick()
        shots.removeAll { it.expires < tick }
        blasts.removeAll { it.expires < tick }
        if (tick % 250 == 0L) slowTick()
        if (tick % TICKS_PER_HOUR == 0L) hourlyTick()
        val gone = pawns.filter { it.dead && tick - it.deathTick > 10 }
        if (gone.isNotEmpty()) pawns.removeAll(gone.toSet())
        if (!encounter && humansOnSide.none { it.colonist } && caravans.none { c -> c.inBattle || c.members.any { it.colonist && it.alive } } && !gameOver) {
            gameOver = true
            say("Everyone is dead. The colony has fallen.", 3)
        }
    }

    // ------------------------------------------------------------------ pawn upkeep
    private fun pawnTick(p: Pawn) {
        if (p.dead) return
        val sleeping = p.job?.type == JobType.SLEEP && p.job?.stage == 1
        if (p.race.mech) { p.food = 1f; p.rest = 1f }
        // Needs.
        val foodRate = if (p.race.isAnimal) 0.55f * p.race.size.let { Math.pow(it.toDouble(), 0.5).toFloat() } * (if (p.faction == Faction.WILD) 0.35f else 1f) else 0.7f * (if (Trait.GOURMAND in p.traits) 1.3f else if (Trait.ASCETIC in p.traits) 0.9f else 1f) * (if (p.pregnantUntil > 0L) 1.25f else 1f) * (if (p.age < 13) 0.6f else 1f)
        p.food = max(0f, p.food - foodRate / TICKS_PER_DAY * (if (sleeping) 0.7f else 1f))
        if (!p.isAnimal) {
            if (!sleeping) p.rest = max(0f, p.rest - 0.95f / TICKS_PER_DAY)
            val joyDrain = 0.42f / TICKS_PER_DAY
            if (p.job?.type != JobType.JOY) p.joy = max(0f, p.joy - joyDrain)
        }
        if (p.food <= 0f && !p.isAnimal || p.food <= 0f && p.isAnimal) starvationTick(p)
        if (tick % 10 == (p.id % 10).toLong()) {
            healthTick(p, 10)
            if (p.dead) return
            if (p.downed) maybeStandUp(p)
        }
        if (p.carriedBy >= 0) {
            val c = pawnById(p.carriedBy)
            if (c == null || !c.alive) { p.carriedBy = -1 } else { p.x = c.x; p.y = c.y; p.fromX = c.fromX; p.fromY = c.fromY; p.moveCd = c.moveCd; p.moveTotal = c.moveTotal; return }
        }
        if (p.downed) {
            // Downed animals and raiders just lie there; downed colonists wait for rescue.
            if (p.faction == Faction.ENEMY && !p.prisoner) enemyDownedTick(p)
            return
        }
        if (p.dormant) { dormantTick(p); return }
        if (p.isBaby) { babyTick(p); return }
        if (p.attackCd > 0) p.attackCd--
        if (map.fires.isNotEmpty() && tick % 3 == (p.id % 3).toLong() && p.moveCd <= 0) stepOutOfFire(p)
        // Drafted animals of the colony fight under the same AI as drafted colonists.
        if (p.isAnimal && p.drafted && p.faction == Faction.PLAYER) { draftedAI(p); return }
        if (p.isAnimal) { animalTick(p); return }
        if (p.drafted && !p.retreating && tick % 60 == (p.id % 60).toLong() && (p.food < 0.08f || p.rest < 0.04f) && pawns.none { it.hostile && it.alive && !it.downed }) {
            setDrafted(p, false)
            say("${p.name} stood down to rest and eat.", 0)
        }
        if (p.colonist && !p.drafted && p.job != null && tick % 10 == (p.id % 10).toLong() && shouldReact(p)) endJob(p)
        if (p.job == null) think(p)
        driveJob(p)
    }

    private fun starvationTick(p: Pawn) {
        if (tick % 10 != (p.id % 10).toLong()) return
        val h = addHediff(p, HediffKind.MALNUTRITION, 0f)
        h.severity = min(1f, h.severity + 10f * 0.28f / TICKS_PER_DAY)
        p.healthDirty = true
        if (h.severity >= 1f) { die(p, "starvation") }
    }

    // ------------------------------------------------------------------ slow tick (every 250)
    private fun slowTick() {
        worldSlowTick()
        if (caravans.isNotEmpty() && pendingBattle == null) caravansTick()
        for (p in pawns) if (p.alive) {
            if ((p.colonist || p.prisoner) && !p.isBaby) moodUpdate(p)
            comfortTick(p)
        }
    }

    // ------------------------------------------------------------------ hourly
    private fun hourlyTick() {
        hourlyEvents()
        alliesHourly()
        if (hour == 0) { factionsDaily(); lifeDaily() }
        autosaveHook?.invoke()
    }

    fun distance(ax: Int, ay: Int, bx: Int, by: Int): Float {
        val dx = (ax - bx).toFloat(); val dy = (ay - by).toFloat()
        return sqrt(dx * dx + dy * dy)
    }

    companion object {
        const val MAP_SIZE = 100
    }
}

/** Pawns standing in flames (or beside a raging fire) hop to a safe neighbouring cell. */
fun Game.stepOutOfFire(p: Pawn) {
    val here = map.idx(p.x, p.y)
    if (!map.fires.containsKey(here)) return
    var best = -1
    var bs = 1e9f
    for (r in 1..3) {
        for (dy in -r..r) for (dx in -r..r) {
            val nx = p.x + dx; val ny = p.y + dy
            if (!map.inB(nx, ny)) continue
            val n = map.idx(nx, ny)
            if (!map.walkable(n) || map.fires.containsKey(n)) continue
            // Prefer cells with no fire around.
            var near = 0
            for (d in 0 until 8) {
                val ax = nx + GameMap.DX8[d]; val ay = ny + GameMap.DY8[d]
                if (map.inB(ax, ay) && map.fires.containsKey(map.idx(ax, ay))) near++
            }
            val sc = near * 3f + (abs(dx) + abs(dy))
            if (sc < bs) { bs = sc; best = n }
        }
        if (best >= 0) break
    }
    if (best >= 0) {
        p.fromX = p.x; p.fromY = p.y
        p.x = map.xOf(best); p.y = map.yOf(best)
        p.moveTotal = 6; p.moveCd = 6
        p.clearPath()
        if (p.job?.type != JobType.FIREFIGHT) { val jb = p.job; if (jb != null && p.colonist) endJob(p) }
    }
}
