package io.github.teamomuito.colony.sim

import kotlin.math.max

/** What a won fight does to the world, beyond the loot. Stored as data so it survives a save. */
enum class BattleAftermath { AMBUSH, SITE, SETTLEMENT_GUARDS, SETTLEMENT_ASSAULT, RESCUE_CAPTIVES }

enum class BattleOutcome(val label: String) { VICTORY("victory"), DEFEAT("defeat"), RETREAT("retreat") }

/**
 * A caravan fight. It is created when a caravan is attacked (or attacks), and it becomes a real battle map once the
 * player begins it. The caravan's members and cargo are not on the battle map; members are moved onto it, cargo stays
 * with the caravan until the fight is over.
 */
class BattlePlan(
    val caravanId: Int,
    val label: String,
    /** 0 for people, 1 for manhunting animals. */
    val kind: Int,
    val points: Float,
    val fortified: Boolean,
    val aftermath: BattleAftermath,
    /** Index into the world's settlements, or -1. */
    val settlement: Int,
    /** Id of the world site, or -1. */
    val site: Int,
    val wasHostile: Boolean,
    val startTick: Long,
    /** The faction the enemy belongs to, so a captured enemy can be ransomed to them. -1 if none. */
    var enemyFaction: Int = -1,
) {
    var retreating = false
    var outcome: BattleOutcome? = null
    var lostOnRetreat = 0
    /** Downed member id -> the standing member carrying it out. */
    val carrierOf = LinkedHashMap<Int, Int>()
}

/** Battles last at most this long; after that the colony withdraws. */
const val BATTLE_LIMIT = 15_000L

/** The world asks for a fight; nothing happens until the app begins it, so the world freezes meanwhile. */
fun Game.startFight(
    c: Caravan, label: String, kind: Int, points: Float, fortified: Boolean, aftermath: BattleAftermath,
    settlement: Int = -1, site: Int = -1, wasHostile: Boolean = false, enemyFaction: Int = -1,
) {
    c.inBattle = true
    pendingBattle = BattlePlan(c.id, label, kind, points, fortified, aftermath, settlement, site, wasHostile, tick, enemyFaction)
    say("${c.name} is attacked by $label!", 3)
}

/** Builds the battle map for the pending fight and moves the caravan's people onto it. Returns the battle game. */
fun Game.beginBattle(): Game {
    val plan = pendingBattle ?: error("no fight is pending")
    val c = caravans.first { it.id == plan.caravanId }
    val bg = createBattleGame(c, plan)
    pendingBattle = null
    return bg
}

/** Called by the battle game every tick: decides whether the fight is over. */
internal fun Game.battleTick() {
    val b = battle ?: return
    if (b.outcome != null) return
    battleMembers.removeAll { it.dead }
    // A carried pawn leaves with the one carrying it.
    for ((downed, carrier) in b.carrierOf) {
        val carrierPawn = battleMembers.firstOrNull { it.id == carrier } ?: continue
        if (carrierPawn in pawns) continue
        val d = battleMembers.firstOrNull { it.id == downed } ?: continue
        if (d in pawns) { pawns.remove(d); d.carriedBy = -1 }
    }
    if (battleMembers.isEmpty()) { b.outcome = BattleOutcome.DEFEAT; return }
    if (b.retreating) {
        if (battleMembers.none { it in pawns }) b.outcome = BattleOutcome.RETREAT
        return
    }
    if (pawns.none { it.hostile && it.alive && !it.downed && !it.retreating }) { b.outcome = BattleOutcome.VICTORY; return }
    // Out of time: the colony withdraws the way a manual retreat would, carrying and leaving people as usual.
    if (tick - b.startTick > BATTLE_LIMIT) { requestRetreat(); return }
}

/**
 * The player withdraws. Standing members walk to the nearest edge (the same exit raiders use). Downed members are
 * carried by a standing one; a downed member with nobody free to carry it is left behind.
 */
fun Game.requestRetreat() {
    val b = battle ?: return
    if (b.outcome != null || b.retreating) return
    b.retreating = true
    val standing = battleMembers.filter { it.alive && !it.downed && !it.isAnimal && it in pawns }.toMutableList()
    for (d in battleMembers.filter { it.alive && it.downed && it in pawns }.toList()) {
        val carrier = standing.firstOrNull { it.carrying < 0 && distance(it.x, it.y, d.x, d.y) <= 6f }
        if (carrier != null) {
            carrier.carrying = d.id; d.carriedBy = carrier.id; d.clearPath(); d.job = null
            b.carrierOf[d.id] = carrier.id
        } else {
            // Left on the field: resolveBattle decides whether the enemy takes them captive.
            pawns.remove(d); releaseAll(d); d.clearPath(); d.job = null; d.abandoned = true; b.lostOnRetreat++
            say("${d.name} was left behind.", 3)
        }
    }
    for (p in battleMembers) if (p in pawns) p.retreating = true
    say("The colony is withdrawing!", 2)
}

/**
 * Applies a finished battle to the world: members and cargo go back to the caravan, the caravan leaves or stays, and
 * the fight's aftermath happens. The battle game is not used afterwards.
 */
fun Game.resolveBattle(bg: Game) {
    val plan = bg.battle ?: return
    val out = plan.outcome ?: return
    tick = max(tick, bg.tick)
    nextPawnId = max(nextPawnId, bg.nextPawnId)
    pendingBattle = null
    val c = caravans.firstOrNull { it.id == plan.caravanId } ?: return
    c.inBattle = false
    val back = bg.battleMembers.filter { it.alive }.toMutableList()
    // Members abandoned on the field during a retreat. Everyone else either stayed or walked off the edge.
    val left = back.filter { it.abandoned }
    for (p in back) {
        p.drafted = false; p.retreating = false; p.job = null; releaseAll(p); p.clearPath()
        p.moveCd = 0; p.carriedBy = -1; p.carrying = -1; p.abandoned = false
    }
    back.removeAll { it in left }
    when (out) {
        BattleOutcome.DEFEAT -> {
            caravans.remove(c)
            say("${c.name} was wiped out by ${plan.label}. Its cargo is lost.", 3)
        }
        BattleOutcome.RETREAT -> {
            c.members.clear(); c.members.addAll(back)
            c.route.clear(); c.progress = 0f
            c.lastEvent = "Retreated from ${plan.label}."
            say("${c.name} withdrew from ${plan.label}.", 2)
            // The enemy takes the people left behind. Animals simply wander off.
            val captor = world.factions.firstOrNull { it.id == plan.enemyFaction }
            val taken = left.filter { !it.isAnimal }
            if (captor != null) takeCaptive(c.tile, captor, taken, plan.points)
            else for (lost in taken) {
                // No one took them: left in the wild, they are lost, and the colony records it as a death.
                recordBattleDeath(lost, "lost in the wilds after ${plan.label}")
                say("${lost.name} was lost in the wilds.", 3)
            }
        }
        BattleOutcome.VICTORY -> {
            // Battlefield loot: what the dead and the enemy left behind. Corpses are not taken.
            for (s in bg.map.items.values) if (s.corpseOf == null) c.inventory.add(s.lot(), s.count)
            // Enemies still down are taken prisoner.
            for (e in bg.pawns.toList()) if (e.faction == Faction.ENEMY && e.downed && e.alive && !e.isAnimal) {
                makePrisoner(e, -1); back.add(e)
            }
            if (plan.kind == 0) c.inventory.add(ItemType.SILVER, rng.range(20, 90))
            // Nobody took them, so the people left on the field walk home with the rest.
            back.addAll(left)
            c.members.clear(); c.members.addAll(back)
            c.lastEvent = "Defeated ${plan.label}."
            say("${c.name} defeated ${plan.label}.", 1)
            afterVictory(c, plan)
        }
    }
}

private fun Game.afterVictory(c: Caravan, plan: BattlePlan) {
    when (plan.aftermath) {
        BattleAftermath.AMBUSH -> {}
        BattleAftermath.SETTLEMENT_GUARDS -> c.lastEvent = "Driven off from ${world.settlements.getOrNull(plan.settlement)?.name ?: "the settlement"}."
        BattleAftermath.SITE -> {
            val site = world.sites.firstOrNull { it.id == plan.site } ?: return
            world.sites.remove(site)
            val q = openQuestAt(site.id)
            if (q != null && q.state == QuestState.ACCEPTED) {
                q.state = QuestState.COMPLETED; q.resolveAt(tick)
                c.inventory.add(ItemType.SILVER, q.reward)
                adjustGoodwill(patronOf(q), CAMP_BOUNTY_GOODWILL)
                say("The ${site.name} is cleared. Reward: ${q.reward} silver.", 1)
            } else {
                // Nobody asked for this camp, or the request was never accepted: no bounty.
                q?.let { it.state = QuestState.EXPIRED; it.resolveAt(tick) }
                say("The ${site.name} is cleared. Nobody asked for it, so there is no reward.", 1)
            }
            for (f in world.factions) if (!f.permanentEnemy && world.goodwill[f.id] in 1..99 && world.relation[f.id][site.factionId] == -1) adjustGoodwill(f, 6, false)
        }
        BattleAftermath.RESCUE_CAPTIVES -> {
            val site = world.sites.firstOrNull { it.id == plan.site } ?: return
            world.sites.remove(site)
            val q = openQuestAt(site.id) ?: return
            val n = releaseCaptives(q, c)
            q.state = QuestState.COMPLETED; q.resolveAt(tick)
            say("The ${site.name} is cleared. $n ${if (n == 1) "colonist is" else "colonists are"} rescued.", 1)
        }
        BattleAftermath.SETTLEMENT_ASSAULT -> {
            val s = world.settlements.getOrNull(plan.settlement) ?: return
            var looted = 0
            for ((lot, n) in s.stock.entries()) { val take = (n * 0.7f).toInt(); if (take > 0) { s.stock.remove(lot, take); c.inventory.add(lot, take); looted += take } }
            val silver = (s.silver * 0.8f).toInt() + (if (plan.wasHostile) 150 else 0)
            c.inventory.add(ItemType.SILVER, silver)
            s.stock.clear(); s.silver = 0
            s.destroyedUntil = tick + 30L * TICKS_PER_DAY
            say("${s.name} falls. The caravan takes $looted goods and $silver silver.", 1)
        }
    }
}

/** A colonist or animal of the colony died on a battle map: record it in the world the battle came from. */
internal fun Game.recordBattleDeath(p: Pawn, cause: String) {
    if (p.faction != Faction.PLAYER || p.isAnimal || p.prisoner) return
    graveyard.add("${p.name} - $cause (day ${day + 1})")
    for (o in colonists) if (Trait.PSYCHOPATH !in o.traits) o.addThought("Colonist died: ${p.name.substringBefore(' ')}", -0.1f, tick, 4 * TICKS_PER_DAY)
}
