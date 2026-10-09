package io.github.teamomuito.colony.sim

/**
 * Ransom: a faction offers silver for one of its people held as a prisoner. It is an offer on the colony's side, like a
 * settlement request: it has a price and an expiry, the player accepts or declines, and it can be withdrawn when the
 * prisoner or the relationship no longer allows it. It needs the comms console, the same as other diplomacy.
 */
class RansomOffer(val id: Int, val prisonerId: Int, val prisonerName: String, val factionId: Int, val price: Int, val expires: Long)

const val RANSOM_OFFER_DAYS = 6
/** After an offer is declined or expires, the same prisoner is not offered again for this long. */
const val RANSOM_COOLDOWN_DAYS = 4
const val RANSOM_DECLINE_GOODWILL = -4
const val RANSOM_ACCEPT_GOODWILL = 10
const val RANSOM_MAX_OPEN = 3

/** Why [name] cannot be ransomed to [factionId] right now, or null if they can. */
fun Game.ransomBlocker(p: Pawn?, name: String, factionId: Int): String? {
    if (p == null) return "$name is no longer here"
    if (!p.alive) return "$name died"
    if (!p.prisoner) return "$name is no longer a prisoner"
    if (p.escaping) return "$name is trying to escape"
    if (p.faction != Faction.PLAYER || p.isAnimal || p.race.mech) return "$name cannot be ransomed"
    if (p !in pawns) return "$name is no longer here"
    val f = world.factions.getOrNull(factionId) ?: return "no faction claims $name"
    if (f.permanentEnemy) return "${f.name} do not pay ransoms"
    if (hostileTo(f)) return "${f.name} are hostile to you"
    return null
}

/** The faction's price for one of its people: better relations pay more. */
fun Game.ransomPrice(f: WorldFaction): Int {
    val mult = when (standing(f)) {
        Standing.WARY -> 0.7f
        Standing.FRIENDLY -> 1.2f
        Standing.ALLY -> 1.5f
        else -> 1f
    }
    return ((150f * mult) / 10).toInt() * 10
}

/** The offer for [prisonerId], if one is open. */
fun Game.ransomOfferFor(prisonerId: Int): RansomOffer? = ransomOffers.firstOrNull { it.prisonerId == prisonerId }

/** Runs once an hour: withdraws offers that no longer hold, and lets factions make new offers. */
internal fun Game.ransomTick() {
    val it = ransomOffers.iterator()
    while (it.hasNext()) {
        val o = it.next()
        val p = pawnById(o.prisonerId)
        val reason = if (tick > o.expires) "expired" else ransomBlocker(p, o.prisonerName, o.factionId)
        if (reason == null) continue
        it.remove()
        ransomCooldown[o.prisonerId] = tick + RANSOM_COOLDOWN_DAYS * TICKS_PER_DAY
        if (reason == "expired") say("The ransom offer for ${o.prisonerName} expired.", 0)
        else say("The ransom offer for ${o.prisonerName} was withdrawn: $reason.", 2)
    }
    if (!hasComms()) return
    for (p in prisoners) {
        if (ransomOffers.size >= RANSOM_MAX_OPEN) break
        if (ransomOfferFor(p.id) != null) continue
        if ((ransomCooldown[p.id] ?: 0L) > tick) continue
        if (ransomBlocker(p, p.name, p.wfaction) != null) continue
        if (!rng.chance(0.3f)) continue
        val f = world.factions[p.wfaction]
        val o = RansomOffer(nextRansomId++, p.id, p.name, f.id, ransomPrice(f), tick + RANSOM_OFFER_DAYS.toLong() * TICKS_PER_DAY)
        ransomOffers.add(o)
        say("${f.name} offer ${o.price} silver to ransom ${p.name}. Accept or decline in the alerts.", 2)
    }
}

/**
 * The player accepts: silver is dropped at home, the prisoner walks out to their people, and the faction is pleased.
 * Returns an error to show, or null when the ransom went through.
 */
fun Game.acceptRansom(offerId: Int): String? {
    val o = ransomOffers.firstOrNull { it.id == offerId } ?: return "That offer is no longer open."
    if (!hasComms()) return "The comms console has to be powered to finish a ransom."
    val p = pawnById(o.prisonerId)
    ransomBlocker(p, o.prisonerName, o.factionId)?.let { reason ->
        ransomOffers.remove(o)
        return "The ransom is off: $reason."
    }
    val f = world.factions[o.factionId]
    ransomOffers.remove(o)
    map.drop(ItemType.SILVER, o.price, homeX, homeY)
    dismissPrisoner(p!!, goodwill = RANSOM_ACCEPT_GOODWILL)
    say("${o.prisonerName} was ransomed to ${f.name} for ${o.price} silver.", 1)
    return null
}

/** The player declines. The faction is annoyed, and the prisoner is not offered again for a while. */
fun Game.declineRansom(offerId: Int): String? {
    val o = ransomOffers.firstOrNull { it.id == offerId } ?: return "That offer is no longer open."
    ransomOffers.remove(o)
    ransomCooldown[o.prisonerId] = tick + RANSOM_COOLDOWN_DAYS * TICKS_PER_DAY
    world.factions.getOrNull(o.factionId)?.let { adjustGoodwill(it, RANSOM_DECLINE_GOODWILL) }
    say("You declined the ransom for ${o.prisonerName}.", 0)
    return null
}
