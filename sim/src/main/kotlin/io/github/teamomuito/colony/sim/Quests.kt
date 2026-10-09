package io.github.teamomuito.colony.sim

/*
 * Quests are offers and obligations between the colony and the factions of the world map.
 *
 *  - CLEAR_CAMP: a friendly faction asks you to destroy a camp near one of its settlements. It is offered, and the player
 *    accepts or declines. Clearing an accepted camp pays its bounty and raises the patron's goodwill; a camp cleared
 *    without an accepted quest pays nothing. An accepted quest that runs out of time costs goodwill.
 *  - RESCUE_CAPTIVES: colonists left behind in a retreat are taken captive by the enemy and held in a camp. They are
 *    real pawns with their own identity, health and gear, kept in [Captive] until the camp is cleared (they rejoin the
 *    caravan) or the deadline passes (they are lost).
 *
 * Every quest ends in exactly one terminal state, and the site it refers to is removed then.
 */

enum class QuestKind(val label: String) { CLEAR_CAMP("clear a camp"), RESCUE_CAPTIVES("rescue captives") }

enum class QuestState { OFFERED, ACCEPTED, COMPLETED, FAILED, EXPIRED, DECLINED }

class Quest(
    val id: Int,
    val kind: QuestKind,
    /** The faction that offered the quest, or that holds the captives. */
    val factionId: Int,
    /** The world site this quest is about. */
    val siteId: Int,
    val reward: Int,
    val deadline: Long,
) {
    var state = QuestState.OFFERED
        internal set
    var resolvedAt = -1L
        internal set

    val open get() = state == QuestState.OFFERED || state == QuestState.ACCEPTED
}

/** A colonist held by the enemy at a camp. The pawn keeps its id, name, health, injuries and gear. */
class Captive(val pawn: Pawn, val questId: Int)

const val CAMP_QUEST_DAYS = 14
const val RESCUE_QUEST_DAYS = 8
const val CAMP_BOUNTY_GOODWILL = 8
const val CAMP_FAILED_GOODWILL = -5

fun Game.questById(id: Int): Quest? = world.quests.firstOrNull { it.id == id }

/** The open quest about [siteId], if any. */
fun Game.openQuestAt(siteId: Int): Quest? = world.quests.firstOrNull { it.siteId == siteId && it.open }

/** Offers a camp-clearing quest from [patron] about a camp at [site]. The camp is already on the world map. */
internal fun Game.offerCampQuest(patron: WorldFaction, site: Site): Quest {
    val q = Quest(world.nextQuestId++, QuestKind.CLEAR_CAMP, patron.id, site.id, site.reward, site.expires)
    world.quests.add(q)
    return q
}

/** Accepts an offered quest. Only offers can be accepted; the camp then pays its bounty when cleared. */
fun Game.acceptQuest(id: Int): String? {
    val q = questById(id) ?: return "That quest no longer exists."
    if (q.state != QuestState.OFFERED) return "That quest is no longer open."
    q.state = QuestState.ACCEPTED
    val f = patronOf(q)
    say("You accepted ${f.name}'s request. Clear the camp by day ${(q.deadline / TICKS_PER_DAY).toInt() + 1} for ${q.reward} silver.", 1)
    return null
}

/** Declines an offer. The camp disappears from the map; nothing else changes. */
fun Game.declineQuest(id: Int): String? {
    val q = questById(id) ?: return "That quest no longer exists."
    if (q.state != QuestState.OFFERED) return "Only an open offer can be declined."
    q.state = QuestState.DECLINED
    q.resolveAt(tick)
    removeSite(q.siteId)
    return null
}

/**
 * Runs once a day: quests whose deadline has passed end. An offer quietly expires. An accepted camp quest costs goodwill
 * with its patron. Captives of a rescue quest that runs out are lost for good.
 */
internal fun Game.questsDaily() {
    for (q in world.quests.toList()) {
        if (!q.open || tick <= q.deadline) continue
        when (q.state) {
            QuestState.OFFERED -> {
                q.state = QuestState.EXPIRED
            }
            QuestState.ACCEPTED -> {
                q.state = QuestState.FAILED
                if (q.kind == QuestKind.CLEAR_CAMP) {
                    adjustGoodwill(patronOf(q), CAMP_FAILED_GOODWILL)
                    say("The ${patronOf(q).name} request has run out of time. Relations suffer.", 2)
                } else {
                    losePrisoners(q)
                }
            }
            else -> {}
        }
        q.resolveAt(tick)
        removeSite(q.siteId)
    }
    // Old quests are history; keep the list short so saves stay small.
    val keep = world.quests.filter { it.open || tick - it.resolvedAt < 20L * TICKS_PER_DAY }
    world.quests.retainAll(keep.toSet())
}

internal fun Quest.resolveAt(at: Long) { resolvedAt = at }

/** The faction a quest belongs to. */
internal fun Game.patronOf(q: Quest): WorldFaction = world.factions.first { it.id == q.factionId }

private fun Game.removeSite(siteId: Int) {
    world.sites.removeAll { it.id == siteId }
}

/** Captives of [q] that are still held. */
fun Game.captivesOf(q: Quest): List<Captive> = world.captives.filter { it.questId == q.id }

/** The enemy takes colonists who were left behind: they are held at a new camp near [tile] until someone rescues them. */
internal fun Game.takeCaptive(tile: Int, captor: WorldFaction, people: List<Pawn>, strength: Float) {
    if (people.isEmpty()) return
    val at = freeTileNear(tile) ?: tile
    val site = Site(world.nextSiteId++, at, 0, captor.id, 0, tick + RESCUE_QUEST_DAYS * TICKS_PER_DAY.toLong(), strength, "${captor.name} camp")
    world.sites.add(site)
    val q = Quest(world.nextQuestId++, QuestKind.RESCUE_CAPTIVES, captor.id, site.id, 0, site.expires)
    q.state = QuestState.ACCEPTED
    world.quests.add(q)
    for (p in people) {
        releaseAll(p); p.clearPath(); p.job = null; p.carriedBy = -1; p.carrying = -1
        world.captives.add(Captive(p, q.id))
    }
    val names = people.joinToString { it.name.substringBefore(' ') }
    say("$names ${if (people.size == 1) "was" else "were"} taken captive by ${captor.name}. Rescue ${if (people.size == 1) "them" else "them all"} from the camp by day ${(q.deadline / TICKS_PER_DAY).toInt() + 1}.", 3)
}

/** Captives of a camp that was cleared: they rejoin [c]. Returns how many came back. */
internal fun Game.releaseCaptives(q: Quest, c: Caravan): Int {
    val back = captivesOf(q)
    for (cap in back) {
        cap.pawn.drafted = false; cap.pawn.retreating = false
        c.members.add(cap.pawn)
    }
    world.captives.removeAll { it.questId == q.id }
    return back.size
}

private fun Game.losePrisoners(q: Quest) {
    for (cap in captivesOf(q)) {
        graveyard.add("${cap.pawn.name} - lost in captivity (day ${day + 1})")
        say("${cap.pawn.name} was lost in captivity.", 3)
    }
    world.captives.removeAll { it.questId == q.id }
}

/** A passable tile near [tile] that holds no settlement, camp or river. */
internal fun Game.freeTileNear(tile: Int): Int? {
    val x0 = world.x(tile); val y0 = world.y(tile)
    for (r in 1..3) for (dy in -r..r) for (dx in -r..r) {
        if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != r) continue
        val x = x0 + dx; val y = y0 + dy
        if (!world.inB(x, y)) continue
        val t = world.tile(x, y)
        if (!world.passable(t) || world.river[t] || world.settlementAt(t) != null || world.siteAt(t) != null || t == world.homeTile) continue
        return t
    }
    return null
}
