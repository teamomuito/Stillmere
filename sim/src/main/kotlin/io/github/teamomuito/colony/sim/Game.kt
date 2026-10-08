package io.github.teamomuito.colony.sim

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class LogEntry(val tick: Long, val text: String, val level: Int) // level: 0 info, 1 good, 2 warning, 3 bad
class Corpse(val name: String, val x: Int, val y: Int, val tick: Long, val colonist: Boolean)
class Shot(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val expires: Long, val hit: Boolean)

class Game(val seed: Long, val map: GameMap = GameMap.generate(MAP_SIZE, MAP_SIZE, seed)) {
    var rng = Rng(seed + 1)
    val finder = Pathfinder(map)
    var tick = 6L * TICKS_PER_HOUR
    val pawns = ArrayList<Pawn>()
    val corpses = ArrayList<Corpse>()
    val shots = ArrayList<Shot>()
    val log = ArrayList<LogEntry>()
    val reservations = HashMap<Int, Int>()
    val unreachable = HashMap<Long, Long>()
    var nextPawnId = 1

    val researchDone = HashSet<Research>()
    val researchProgress = HashMap<Research, Float>()
    var researchCurrent: Research? = null

    var tempOffset = 0f
    var tempEventUntil = 0L
    var tempEventName = ""
    var nextRaid = 0L
    var nextWanderer = 0L
    var nextPod = 0L
    var nextTempEvent = 0L
    var raidActive = false
    var raidStartCount = 0
    var raidEnds = 0L
    var raidsSurvived = 0
    var gameOver = false
    var won = false
    var colonyName = "New Arrivals"
    var shipBuilt = false
    var homeX = MAP_SIZE / 2
    var homeY = MAP_SIZE / 2

    init {
        nextRaid = 5L * TICKS_PER_DAY + rng.int(TICKS_PER_DAY)
        nextWanderer = 3L * TICKS_PER_DAY + rng.int(3 * TICKS_PER_DAY)
        nextPod = 2L * TICKS_PER_DAY + rng.int(4 * TICKS_PER_DAY)
        nextTempEvent = 8L * TICKS_PER_DAY + rng.int(6 * TICKS_PER_DAY)
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
        return when {
            h < 5f -> 0f
            h < 7f -> (h - 5f) / 2f
            h < 18f -> 1f
            h < 20f -> (20f - h) / 2f
            else -> 0f
        }
    }

    fun outdoorTemp(): Float {
        val h = (tick % TICKS_PER_DAY) / TICKS_PER_HOUR.toFloat()
        val diurnal = sin(((h - 9f) / 24f) * 2f * PI.toFloat()) * 7f
        val s = season
        val next = Season.entries[(s.ordinal + 1) % 4]
        val blend = (dayOfSeason - 1) / DAYS_PER_SEASON.toFloat()
        val base = s.baseTemp + (next.baseTemp - s.baseTemp) * blend * 0.5f
        return base + diurnal + tempOffset
    }

    fun dateLabel() = "${hour.toString().padStart(2, '0')}:00  Day $dayOfSeason of ${season.label}, $year"

    // ------------------------------------------------------------------ logging
    fun say(text: String, level: Int = 0) {
        log.add(LogEntry(tick, text, level))
        if (log.size > 200) log.removeAt(0)
    }

    // ------------------------------------------------------------------ pawns
    val colonists get() = pawns.filter { it.colonist && it.alive }
    val hostiles get() = pawns.filter { it.hostile && it.alive }
    fun pawnAt(x: Int, y: Int): Pawn? = pawns.firstOrNull { it.alive && it.x == x && it.y == y }
    fun pawnById(id: Int): Pawn? = pawns.firstOrNull { it.id == id }

    fun newColonist(x: Int, y: Int, weapon: Weapon, wanderer: Boolean = false): Pawn {
        val p = Pawn(nextPawnId++, rng.pick(Names.first) + " " + rng.pick(Names.last), true)
        p.age = rng.range(19, 52)
        p.wanderer = wanderer
        for (s in SkillType.entries) p.skill[s.ordinal] = if (rng.chance(0.2f)) rng.range(0, 3) else rng.range(2, 12)
        repeat(rng.range(1, 3)) {
            val s = rng.int(SkillType.entries.size)
            p.passion[s] = if (rng.chance(0.3f)) 2 else 1
            p.skill[s] = min(20, p.skill[s] + 2)
        }
        val t = Trait.entries[rng.int(Trait.entries.size)]
        p.traits.add(t)
        if (rng.chance(0.4f)) {
            val t2 = Trait.entries[rng.int(Trait.entries.size)]
            val clash = (t2 == Trait.LAZY && t == Trait.HARD_WORKER) || (t2 == Trait.HARD_WORKER && t == Trait.LAZY) ||
                (t2 == Trait.OPTIMIST && t == Trait.PESSIMIST) || (t2 == Trait.PESSIMIST && t == Trait.OPTIMIST)
            if (t2 != t && !clash) p.traits.add(t2)
        }
        p.weapon = weapon
        // Do what you're good at first.
        for (w in WorkType.entries) {
            val sk = p.skill[w.skill().ordinal]
            val pas = p.passion[w.skill().ordinal]
            p.priority[w.ordinal] = if (pas == 2) 1 else if (pas == 1 || sk >= 9) 2 else 3
        }
        p.priority[WorkType.HAUL.ordinal] = 4
        p.x = x; p.y = y; p.fromX = x; p.fromY = y
        p.mood = p.moodWithThoughts(tick)
        pawns.add(p)
        return p
    }

    fun newRaider(x: Int, y: Int, weapon: Weapon, raidId: Int): Pawn {
        val p = Pawn(nextPawnId++, rng.pick(Names.raider) + " " + rng.pick(Names.raider), false)
        p.hostile = true
        p.weapon = weapon
        p.raidId = raidId
        p.skill[SkillType.SHOOTING.ordinal] = rng.range(2, 9)
        p.skill[SkillType.MELEE.ordinal] = rng.range(3, 10)
        p.x = x; p.y = y; p.fromX = x; p.fromY = y
        pawns.add(p)
        return p
    }

    // ------------------------------------------------------------------ scenario
    fun startNewColony() {
        val cx = map.w / 2
        val cy = map.h / 2
        var sx = cx; var sy = cy
        loop@ for (r in 0 until 30) for (y in cy - r..cy + r) for (x in cx - r..cx + r) {
            if (!map.inB(x, y)) continue
            val i = map.idx(x, y)
            if (map.terrain[i] == Terrain.SOIL || map.terrain[i] == Terrain.RICH_SOIL) {
                var ok = true
                for (yy in y - 3..y + 3) for (xx in x - 3..x + 3) {
                    if (!map.inB(xx, yy)) { ok = false; continue }
                    val t = map.terrain[map.idx(xx, yy)]
                    if (!t.passable || t == Terrain.WATER_SHALLOW) ok = false
                }
                if (ok) { sx = x; sy = y; break@loop }
            }
        }
        for (yy in sy - 4..sy + 4) for (xx in sx - 4..sx + 4) {
            if (map.inB(xx, yy)) map.plant[map.idx(xx, yy)] = null
        }
        homeX = sx; homeY = sy
        val weapons = listOf(Weapon.RIFLE, Weapon.REVOLVER, Weapon.KNIFE)
        for (k in 0 until 3) {
            val p = newColonist(sx - 1 + k, sy + 2, weapons[k])
            p.food = 0.8f; p.rest = 0.85f
        }
        // A starter stockpile with the crash-landed supplies.
        for (yy in sy - 2..sy) for (xx in sx - 2..sx + 1) map.zone[map.idx(xx, yy)] = ZoneKind.STOCKPILE.toByte()
        map.drop(ItemType.MEAL, 20, sx - 2, sy - 2)
        map.drop(ItemType.WOOD, 150, sx - 1, sy - 2)
        map.drop(ItemType.STEEL, 150, sx, sy - 2)
        map.drop(ItemType.RAW_FOOD, 40, sx + 1, sy - 2)
        map.rebuildRooms(outdoorTemp())
        say("Your colonists have crash-landed. Build beds, grow food, and survive.", 1)
        say("Tip: open Architect to mark trees and rock, and to place buildings and zones.", 0)
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
        val i = cell(x, y); if (i < 0) return false
        when (kind) {
            Desig.MINE -> if (map.terrain[i] == Terrain.ROCK) map.desig[i] = kind.toByte() else return false
            Desig.CUT -> if (map.plant[i] != null && !(map.plant[i]!!.type.crop && map.zone[i] == ZoneKind.GROWING.toByte())) map.desig[i] = kind.toByte() else return false
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

    fun canBuildAt(def: BuildDef, x: Int, y: Int): Boolean {
        val i = cell(x, y); if (i < 0) return false
        val t = map.terrain[i]
        if (!t.passable || t == Terrain.WATER_SHALLOW) return false
        if (def.research != null && def.research !in researchDone) return false
        if (map.building[i] != null) return false
        if (def.isFloor) return map.floor[i] == null || map.floor[i] != def
        if (def == BuildDef.SHIP && (shipBuilt || map.building.any { it != null && it.def == BuildDef.SHIP })) return false
        return true
    }

    fun placeBlueprint(def: BuildDef, x: Int, y: Int): Boolean {
        if (!canBuildAt(def, x, y)) return false
        val i = map.idx(x, y)
        // Don't let a blueprint trap somebody inside a wall; they are shoved out when it finishes.
        map.building[i] = Building(def, x, y, false)
        map.desig[i] = 0
        return true
    }

    private fun removeBlueprint(i: Int) {
        val b = map.building[i] ?: return
        if (!b.built && b.delivered > 0) map.drop(b.def.item, b.delivered, b.x, b.y)
        map.building[i] = null
    }

    fun setZone(x: Int, y: Int, kind: Int, crop: PlantType = PlantType.RICE) {
        val i = cell(x, y); if (i < 0) return
        if (kind == ZoneKind.NONE) { map.zone[i] = 0; return }
        val t = map.terrain[i]
        if (!t.passable || t == Terrain.WATER_SHALLOW) return
        if (map.building[i]?.def?.blocksMove == true) return
        if (kind == ZoneKind.GROWING && t.fertility <= 0f) return
        map.zone[i] = kind.toByte()
        if (kind == ZoneKind.GROWING) map.zoneCrop[i] = crop.ordinal.toByte()
    }

    fun setPriority(p: Pawn, w: WorkType, v: Int) { p.priority[w.ordinal] = v.coerceIn(0, 4) }

    fun setDrafted(p: Pawn, v: Boolean) {
        if (!p.colonist || p.downed) return
        p.drafted = v
        endJob(p)
        if (!v) say("${p.name} stood down.", 0)
    }

    fun orderMove(p: Pawn, x: Int, y: Int) {
        if (!p.drafted || !map.inB(x, y)) return
        endJob(p)
        val j = Job(JobType.MOVE, x, y)
        p.job = j
    }

    fun startResearch(r: Research?) {
        if (r != null && (r in researchDone || (r.needs != null && r.needs !in researchDone))) return
        researchCurrent = r
    }

    fun researchAvailable(r: Research) = r !in researchDone && (r.needs == null || r.needs in researchDone)

    fun launchShip(): Boolean {
        val ship = map.building.firstOrNull { it != null && it.def == BuildDef.SHIP && it.built }
        if (ship == null || colonists.isEmpty()) return false
        won = true
        gameOver = true
        say("The ship lifts off. You escaped the rim.", 1)
        return true
    }

    // ------------------------------------------------------------------ main tick
    fun step() {
        if (gameOver) return
        tick++
        for (p in pawns.toList()) pawnTick(p)
        if (tick % 10 == 0L) turretsTick()
        shots.removeAll { it.expires < tick }
        if (tick % 250 == 0L) slowTick()
        if (tick % TICKS_PER_HOUR == 0L) hourlyTick()
        pawns.removeAll { it.dead && tick - it.deathTick > 10 }
        if (colonists.isEmpty() && !gameOver) {
            gameOver = true
            say("Everyone is dead. The colony has fallen.", 3)
        }
    }

    // ------------------------------------------------------------------ pawn upkeep
    private fun pawnTick(p: Pawn) {
        if (p.dead) return
        val sleeping = p.job?.type == JobType.SLEEP && p.job?.stage == 1
        if (p.colonist) {
            p.food = max(0f, p.food - 0.7f / TICKS_PER_DAY * (if (sleeping) 0.7f else 1f))
            if (!sleeping) p.rest = max(0f, p.rest - 0.95f / TICKS_PER_DAY)
            if (p.food <= 0f) hurt(p, 0.004f)
        }
        // Bleeding and healing.
        var bleed = 0f
        val it = p.injuries.iterator()
        while (it.hasNext()) {
            val inj = it.next()
            bleed += inj.bleed
            inj.bleed = max(0f, inj.bleed - 0.0000008f)
            inj.severity -= if (inj.tended) 0.0004f else 0.00015f
            if (inj.severity <= 0f) it.remove()
        }
        if (bleed > 0f) hurt(p, bleed)
        else if (p.hp < p.maxHp && p.food > 0.05f) p.hp = min(p.maxHp, p.hp + if (sleeping) 0.003f else 0.0015f)
        if (p.dead) return

        if (p.downed) {
            if (p.hp > p.maxHp * 0.5f && p.bleeding < 0.0005f) { p.downed = false }
            else return
        } else if (p.hp < p.maxHp * 0.35f) {
            p.downed = true
            p.drafted = false
            endJob(p)
            if (p.colonist) say("${p.name} is down!", 3)
            return
        }

        if (p.attackCd > 0) p.attackCd--
        if (p.colonist && !p.drafted && p.job != null && tick % 10 == (p.id % 10).toLong() && shouldReact(p)) endJob(p)
        if (p.job == null) think(p)
        driveJob(p)
    }

    /** Applies damage that bypasses injuries (bleeding, starvation). */
    fun hurt(p: Pawn, amount: Float) {
        p.hp -= amount
        if (p.hp <= 0f) die(p)
    }

    fun damage(target: Pawn, amount: Float) {
        var a = amount * 1.6f
        if (Trait.TOUGH in target.traits) a *= 0.7f
        target.injuries.add(Injury(a, a * 0.00035f))
        hurt(target, a * 0.7f)
        if (!target.dead && target.job != null && target.colonist && !target.drafted && target.job?.type != JobType.FLEE) {
            // Pain interrupts whatever they were doing.
            endJob(target)
        }
    }

    private fun die(p: Pawn) {
        if (p.dead) return
        p.dead = true
        p.deathTick = tick
        endJob(p)
        releaseAll(p)
        corpses.add(Corpse(p.name, p.x, p.y, tick, p.colonist))
        if (p.colonist) {
            say("${p.name} has died.", 3)
            for (o in colonists) o.addThought("Colonist died", -0.1f, tick, 4 * TICKS_PER_DAY)
        } else if (!p.colonist) {
            say("${p.name} was killed.", 1)
        }
        for (b in map.building) if (b != null && b.ownerId == p.id) b.ownerId = -1
    }

    // ------------------------------------------------------------------ slow tick (every 250)
    private fun slowTick() {
        val out = outdoorTemp()
        if (map.roomDirty) map.rebuildRooms(out)
        // Room temperature drifts to the outdoors, campfires heat.
        val heat = FloatArray(map.roomTemp.size)
        for (b in map.building) {
            if (b == null || !b.built) continue
            if (b.def == BuildDef.CAMPFIRE) {
                if (b.fuel > 0f) {
                    b.fuel = max(0f, b.fuel - 0.25f)
                    val r = map.roomId[map.idx(b.x, b.y)]
                    if (r >= 0 && map.roomIndoor[r]) heat[r] += 10f
                }
            }
        }
        for (r in map.roomTemp.indices) {
            if (!map.roomIndoor[r]) { map.roomTemp[r] = out; continue }
            val sz = max(1, map.roomSize[r])
            map.roomTemp[r] += (out - map.roomTemp[r]) * 0.04f + heat[r] / sz * 1.2f
            map.roomTemp[r] = min(map.roomTemp[r], 60f)
        }
        // Plants grow when it's light and mild, and frost kills crops.
        val light = daylight()
        for (i in 0 until map.size) {
            val pl = map.plant[i] ?: continue
            if (!pl.type.crop) continue
            val t = map.tempAt(i, out)
            if (t < -2f && pl.growth < 1f) {
                if (rng.chance(0.15f)) {
                    map.plant[i] = null
                    if (map.zone[i] == ZoneKind.GROWING.toByte()) say("Frost killed a ${pl.type.label.lowercase()} plant.", 2)
                }
                continue
            }
            if (t < 6f || t > 42f || light <= 0.2f) continue
            val fert = map.terrain[i].fertility
            val perTick = 1f / (pl.type.growDays * TICKS_PER_DAY * 0.55f)
            pl.growth = min(1f, pl.growth + perTick * 250f * max(0.2f, fert))
        }
        // Mood.
        for (p in pawns) if (p.alive && p.colonist) updateMood(p, out)
        // Cleanup designations that no longer make sense.
        for (i in 0 until map.size) {
            val d = map.desig[i].toInt()
            if (d == 0) continue
            if (d == Desig.MINE && map.terrain[i] != Terrain.ROCK) map.desig[i] = 0
            if (d == Desig.CUT && map.plant[i] == null) map.desig[i] = 0
            if (d == Desig.DECON && map.building[i] == null && map.floor[i] == null) map.desig[i] = 0
        }
        corpses.removeAll { tick - it.tick > 2 * TICKS_PER_DAY }
    }

    private fun updateMood(p: Pawn, out: Float) {
        val i = map.idx(p.x, p.y)
        p.temp = map.tempAt(i, out)
        var m = p.moodWithThoughts(tick)
        m += when {
            p.food <= 0.05f -> -0.3f
            p.food < 0.3f -> -0.12f
            else -> 0f
        }
        m += when {
            p.rest < 0.1f -> -0.25f
            p.rest < 0.3f -> -0.1f
            else -> 0f
        }
        m += when {
            p.temp < -10f -> -0.2f
            p.temp < 2f -> -0.08f
            p.temp > 38f -> -0.15f
            p.temp > 30f -> -0.06f
            else -> 0f
        }
        if (p.temp < -18f) hurt(p, 0.15f)
        if (p.temp > 45f) hurt(p, 0.15f)
        m -= min(0.2f, p.injuries.sumOf { it.severity.toDouble() }.toFloat() * 0.004f)
        for (c in corpses) {
            if (abs(c.x - p.x) + abs(c.y - p.y) < 8 && tick - c.tick < TICKS_PER_DAY) {
                m -= if (c.colonist) 0.05f else 0.02f
                break
            }
        }
        p.mood = (p.mood * 0.6f + m.coerceIn(0f, 1f) * 0.4f).coerceIn(0f, 1f)
        p.thoughts.removeAll { it.expires <= tick }

        if (p.breakUntil > 0 && tick >= p.breakUntil) {
            p.breakUntil = 0; p.breakKind = 0; p.hostile = false
            endJob(p)
            say("${p.name} has recovered from the mental break.", 0)
        } else if (p.breakUntil == 0L && p.mood < 0.22f && !p.downed) {
            val berserk = p.mood < 0.08f && rng.chance(0.3f)
            if (rng.chance(if (p.mood < 0.12f) 0.35f else 0.12f)) {
                p.breakUntil = tick + rng.range(2000, 4500)
                p.breakKind = if (berserk) 1 else 0
                p.drafted = false
                endJob(p)
                if (berserk) {
                    p.hostile = true
                    say("${p.name} has gone berserk!", 3)
                } else say("${p.name} is having a mental break and is wandering off.", 2)
                // Everyone remembers.
                for (o in colonists) if (o !== p) o.addThought("Witnessed a mental break", -0.03f, tick, TICKS_PER_DAY)
            }
        }
    }

    // ------------------------------------------------------------------ hourly: storyteller
    private fun hourlyTick() {
        // Raid wrap-up.
        if (raidActive) {
            val alive = pawns.count { it.hostile && it.alive && it.raidId > 0 }
            val standing = pawns.count { it.hostile && it.alive && it.raidId > 0 && !it.downed }
            if (alive == 0) {
                raidActive = false; raidsSurvived++
                say("The raid has been beaten back.", 1)
            } else if (!pawns.any { it.hostile && it.raidId > 0 && it.alive && it.retreating } &&
                (standing * 2 <= raidStartCount || tick > raidEnds)
            ) {
                for (r in pawns) if (r.hostile && r.raidId > 0 && r.alive) { r.retreating = true; endJob(r) }
                say("The raiders are retreating!", 1)
            }
        }
        if (tempEventUntil in 1..tick) {
            tempOffset = 0f; tempEventUntil = 0; say("The $tempEventName has ended.", 0)
        }
        if (tick >= nextRaid && !raidActive) spawnRaid()
        if (tick >= nextWanderer) {
            nextWanderer = tick + rng.range(4 * TICKS_PER_DAY, 10 * TICKS_PER_DAY)
            if (colonists.size < 12) spawnWanderer()
        }
        if (tick >= nextPod) {
            nextPod = tick + rng.range(3 * TICKS_PER_DAY, 8 * TICKS_PER_DAY)
            spawnPod()
        }
        if (tick >= nextTempEvent && tempEventUntil == 0L) {
            nextTempEvent = tick + rng.range(8 * TICKS_PER_DAY, 16 * TICKS_PER_DAY)
            if (season == Season.SUMMER) {
                tempOffset = 16f; tempEventName = "heat wave"
                say("A heat wave is sweeping in!", 2)
            } else {
                tempOffset = -16f; tempEventName = "cold snap"
                say("A cold snap has hit. Keep warm and protect your crops!", 2)
            }
            tempEventUntil = tick + rng.range(2 * TICKS_PER_DAY, 4 * TICKS_PER_DAY)
        }
        // Colonists' mood boost for fresh weather etc. could go here.
        autosaveHook?.invoke()
    }

    var autosaveHook: (() -> Unit)? = null

    private fun edgeCell(side: Int): Pair<Int, Int>? {
        repeat(80) {
            val t = rng.int(MAP_SIZE - 6) + 3
            val (x, y) = when (side) { 0 -> 1 to t; 1 -> map.w - 2 to t; 2 -> t to 1; else -> t to map.h - 2 }
            val i = map.idx(x, y)
            if (map.walkable(i) && map.terrain[i] != Terrain.WATER_SHALLOW) return x to y
        }
        return null
    }

    private fun spawnRaid() {
        val cols = colonists
        val points = max(28f, (cols.size * 20f + map.wealth() / 80f) * min(1f, 0.45f + day / 30f))
        val maxCount = 1 + day / 3 + cols.size / 2
        val side = rng.int(4)
        var left = points
        var count = 0
        val raidId = ++raidCounter
        val costs = listOf(Weapon.CLUB to 20f, Weapon.KNIFE to 22f, Weapon.REVOLVER to 32f, Weapon.RIFLE to 42f)
        val base = edgeCell(side) ?: return
        while ((left > 0f || count == 0) && count < min(14, maxCount)) {
            val (w, c) = rng.pick(costs.filter { it.second <= max(left, 20f) || count == 0 })
            val x = (base.first + rng.range(-2, 2)).coerceIn(1, map.w - 2)
            val y = (base.second + rng.range(-2, 2)).coerceIn(1, map.h - 2)
            if (!map.walkable(map.idx(x, y))) { left -= 1f; continue }
            newRaider(x, y, w, raidId)
            left -= c
            count++
        }
        raidActive = true
        raidStartCount = count
        raidEnds = tick + (1.5f * TICKS_PER_DAY).toInt()
        nextRaid = tick + rng.range((2.5f * TICKS_PER_DAY).toInt(), 5 * TICKS_PER_DAY)
        val dir = arrayOf("west", "east", "north", "south")[side]
        say("RAID! $count raiders approach from the $dir.", 3)
    }

    var raidCounter = 0

    private fun spawnWanderer() {
        val e = edgeCell(rng.int(4)) ?: return
        val p = newColonist(e.first, e.second, if (rng.chance(0.4f)) Weapon.REVOLVER else Weapon.KNIFE, wanderer = true)
        say("${p.name} wanders in and joins your colony.", 1)
    }

    private fun spawnPod() {
        val cols = colonists
        if (cols.isEmpty()) return
        val c = rng.pick(cols)
        val x = (c.x + rng.range(-6, 6)).coerceIn(2, map.w - 3)
        val y = (c.y + rng.range(-6, 6)).coerceIn(2, map.h - 3)
        val (type, n) = when (rng.int(3)) {
            0 -> ItemType.STEEL to rng.range(40, 90)
            1 -> ItemType.MEAL to rng.range(6, 14)
            else -> ItemType.WOOD to rng.range(50, 100)
        }
        if (map.drop(type, n, x, y) < n) say("A cargo pod crashed nearby with ${type.label.lowercase()}.", 1)
    }

    // ------------------------------------------------------------------ turrets
    private fun turretsTick() {
        for (b in map.building) {
            if (b == null || !b.built || b.def != BuildDef.TURRET) continue
            if (b.cooldown > 0) { b.cooldown -= 10; continue }
            var best: Pawn? = null
            var bd = 26f * 26f
            for (h in pawns) {
                if (!h.hostile || !h.alive || h.downed) continue
                val d = ((h.x - b.x) * (h.x - b.x) + (h.y - b.y) * (h.y - b.y)).toFloat()
                if (d < bd && map.lineOfSight(b.x, b.y, h.x, h.y)) { best = h; bd = d }
            }
            if (best != null) {
                b.cooldown = 50
                val hit = rng.chance(0.7f)
                shots.add(Shot(b.x.toFloat(), b.y.toFloat(), best.x.toFloat(), best.y.toFloat(), tick + 6, hit))
                if (hit) damage(best, 11f * (0.8f + rng.float() * 0.4f))
            }
        }
    }

    fun distance(ax: Int, ay: Int, bx: Int, by: Int): Float {
        val dx = (ax - bx).toFloat(); val dy = (ay - by).toFloat()
        return sqrt(dx * dx + dy * dy)
    }

    companion object {
        const val MAP_SIZE = 72
    }
}
