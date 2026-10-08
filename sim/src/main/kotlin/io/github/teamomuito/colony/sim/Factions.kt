package io.github.teamomuito.colony.sim

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class Standing(val label: String) { HOSTILE("hostile"), WARY("wary"), NEUTRAL("neutral"), FRIENDLY("friendly"), ALLY("allied") }

fun Game.standing(f: WorldFaction): Standing {
    if (f.permanentEnemy) return Standing.HOSTILE
    val g = world.goodwill[f.id]
    return when { g <= -75 -> Standing.HOSTILE; g < -10 -> Standing.WARY; g < 40 -> Standing.NEUTRAL; g < 75 -> Standing.FRIENDLY; else -> Standing.ALLY }
}

fun Game.hostileTo(f: WorldFaction) = standing(f) == Standing.HOSTILE

/** Change goodwill with a faction; its friends and enemies react a little too. */
fun Game.adjustGoodwill(f: WorldFaction, d: Int, spill: Boolean = true) {
    if (f.permanentEnemy) return
    val before = standing(f)
    world.goodwill[f.id] = (world.goodwill[f.id] + d).coerceIn(-100, 100)
    val after = standing(f)
    if (before != after) say("Relations with ${f.name}: ${after.label}.", if (after.ordinal < before.ordinal) 2 else 1)
    if (spill && abs(d) >= 4) for (o in world.factions) {
        if (o.id == f.id || o.permanentEnemy) continue
        val rel = world.relation[f.id][o.id]
        if (rel != 0) adjustGoodwill(o, (d / 4) * rel, false)
    }
}

/** Slow drift of relations towards neutral, called once a day. */
fun Game.factionsDaily() {
    for (f in world.factions) {
        if (f.permanentEnemy) continue
        val g = world.goodwill[f.id]
        if (day % 5 == 0) {
            if (g < 0) world.goodwill[f.id] = g + 1 else if (g > 50) world.goodwill[f.id] = g - 1
        }
    }
    world.sites.removeAll { it.expires < tick }
    // A faction that likes you may ask for a favour: clear a rival camp.
    if (day >= 6 && world.sites.size < 3 && rng.chance(0.12f)) offerSite()
}

/** A bandit camp appears near a friendly faction and they ask you to clear it. */
private fun Game.offerSite() {
    val pl = world.factions.filter { !it.permanentEnemy && world.goodwill[it.id] > 0 }
    val patron = pl.getOrNull(rng.int(pl.size)) ?: return
    val home = world.settlements.firstOrNull { it.faction.id == patron.id } ?: return
    val foes = world.factions.filter { it.kind == 2 }
    val foe = foes.getOrNull(rng.int(foes.size)) ?: return
    repeat(40) {
        val dx = rng.range(-6, 6); val dy = rng.range(-5, 5)
        val x = world.x(home.tile) + dx; val y = world.y(home.tile) + dy
        if (!world.inB(x, y)) return@repeat
        val t = world.tile(x, y)
        if (!world.passable(t) || world.settlementAt(t) != null || world.siteAt(t) != null || t == world.homeTile || world.river[t]) return@repeat
        val strength = 24f + day * 1.2f + rng.range(0, 20)
        val reward = (strength * 9f).toInt()
        val s = Site(world.nextSiteId++, t, 0, foe.id, reward, tick + 14L * TICKS_PER_DAY, strength, "${foe.name} camp")
        world.sites.add(s)
        say("${patron.name} asks you to destroy a ${foe.name} camp near ${home.name} (reward ${s.reward} silver). It appears on the world map.", 1)
        return
    }
}

/** Which faction raids you next: pirates, or any faction that has turned hostile. */
fun Game.pickRaiders(): WorldFaction {
    val opts = ArrayList<WorldFaction>()
    for (f in world.factions) {
        when {
            f.permanentEnemy -> repeat(3) { opts += f }
            hostileTo(f) -> repeat(2) { opts += f }
            standing(f) == Standing.WARY && rng.chance(0.25f) -> opts += f
        }
    }
    return if (opts.isEmpty()) world.factions.first { it.permanentEnemy } else opts[rng.int(opts.size)]
}

// ---------------------------------------------------------------------- comms: what the player can ask of a faction

/** Silver the faction wants for peace talks. */
fun Game.peaceCost(f: WorldFaction): Int = if (f.permanentEnemy) -1 else 100 + max(0, -world.goodwill[f.id]) * 6

fun Game.silverInStockpiles(): Int = map.countItems(ItemType.SILVER)

fun Game.takeSilver(n: Int): Boolean {
    if (silverInStockpiles() < n) return false
    var left = n
    for (e in map.items.entries.filter { it.value.type == ItemType.SILVER }) { if (left <= 0) break; left -= map.take(e.key, left) }
    return true
}

/** Pay for peace talks: goodwill rises, and a hostile faction becomes merely wary. */
fun Game.peaceTalks(f: WorldFaction): String? {
    val cost = peaceCost(f)
    if (cost < 0) return "${f.name} will not negotiate."
    if (world.goodwill[f.id] > -10) return "Relations are already fine."
    if (!takeSilver(cost)) return "You need $cost silver in your stockpiles."
    adjustGoodwill(f, 35 + rng.range(0, 15))
    return null
}

/** Ask a friendly faction to send a trade caravan to your colony. */
fun Game.requestTraders(f: WorldFaction): String? {
    if (!f.trades) return "${f.name} doesn't trade."
    if (standing(f) == Standing.HOSTILE) return "${f.name} won't deal with you."
    if (traders.isNotEmpty()) return "A trader is already here."
    if (!takeSilver(150)) return "Their escort costs 150 silver."
    spawnTrader(f)
    return null
}

/** Ask an allied faction for fighters: they arrive at your map edge and fight on your side. */
fun Game.requestAid(f: WorldFaction): String? {
    if (standing(f) != Standing.ALLY) return "Only allies will send soldiers."
    if (world.goodwill[f.id] < 80) return "They need goodwill 80 or more."
    adjustGoodwill(f, -25, false)
    val e = edgeCell(rng.int(4)) ?: return "No place to land."
    val n = 4 + day / 15
    repeat(n) {
        val p = newHuman(e.first, e.second, Faction.PLAYER, f.kind == 0)
        p.weaponItem = if (f.kind == 0) ItemType.W_BOW else if (day > 25) ItemType.W_RIFLE else ItemType.W_AUTOPISTOL
        p.wfaction = f.id
        p.ally = true
        p.escapeTick = tick + 2L * TICKS_PER_DAY
        p.name = p.name + " (" + f.name.substringBefore(' ') + ")"
    }
    say("${f.name} sends $n soldiers to fight beside you.", 1)
    return null
}

/** Allies go home when their time is up. */
fun Game.alliesHourly() {
    for (p in pawns) if (p.ally && p.alive && tick > p.escapeTick && !p.retreating) { p.retreating = true; p.drafted = false; endJob(p) }
}
