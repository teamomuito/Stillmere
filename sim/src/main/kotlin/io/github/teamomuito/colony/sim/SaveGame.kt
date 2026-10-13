package io.github.teamomuito.colony.sim

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Binary save format. Jobs and reservations are not saved; pawns simply re-think after loading. */
object SaveGame {
    /**
     * 15 added building materials. 14 is still read (its buildings simply have the default material).
     *
     * 26 to 31 renamed some text that is saved: backstory titles inside [Pawn.backstory], thought labels, one incident name, the
     * temporary event name, and (version 28) the pirate camp texts: site names in [World.sites] and the labels of pending fights in
     * [BattlePlan.label]. Saves before each rename are translated when read (see [LegacyNames]). Three kinds of saved text are never
     * translated, because each one is a record of a past event: graveyard entries in [Game.graveyard], which keep the death cause they
     * were written with (only new deaths write the current causes), log lines in [Game.log], and caravan last-event notes in
     * [Caravan.lastEvent]. The layout did not change. Decision recorded in docs/fidelity/names.md (section 2).
     *
     * 32 adds, at the end of each pawn record: the outdoors and indoors needs, the zero-rest counter, the break timers and
     * cooldown, and drug tolerance (see [writePawn] and docs/fidelity/needs-mood.md). Earlier saves read these as their defaults.
     *
     * 33 adds, at the end of each pawn record, the XP each skill learned today (12 floats, the daily learning cap; see [writePawn] and
     * docs/fidelity/skills-traits.md). Earlier saves read these as zero, which is the value at the start of a day.
     *
     * 34 adds, right after the incident history, the five most recent random incidents by name, most recent first
     * ([Game.lastRandomIncidents]; see docs/fidelity/threats.md). Earlier saves read an empty list.
     *
     * 35 keeps the layout but changes what a saved rot means: the share of its spoil time that has passed. Raw meat now spoils in
     * 2 days (was 4) and corpses in 2.5 (was 2.2), so the rot of items read from older saves (map items and job stacks, see
     * [migrateRot]) and the rot of food an eat job holds (see [heldRotFromOld]) is rescaled to keep the same time spent
     * (docs/fidelity/economy.md).
     *
     * 36 adds, at the end of each pawn record, the burn size of a fire on the pawn and the tick until which a melee hit keeps it
     * staggered (see [writePawn] and docs/fidelity/combat.md). Earlier saves read both as zero: not burning, not staggered.
     *
     * 37 changes no field and no layout. It marks the saves written after new colonies moved to a 250 by 250 map (docs/fidelity/map.md).
     * The width and height have been written since version 14 and are read as before, so every save keeps the size it stores.
     *
     * 38 adds one flag per map cell, the built roof (docs/fidelity/roofs.md), after the snow of each cell. A save before 38 has no
     * flags: on reading, every cell of each room that was indoor under the old rule gets a roof, so no room changes status and
     * each cell is roofed as before ([GameMap.adoptLegacyRoofs]).
     *
     * 39 changes no field and no layout. It marks the saves that may hold grass: the grass terrain ([Terrain.GRASS]) and the grass
     * cover ([PlantType.GRASS]) are new values of the terrain byte and of the plant type, appended to their enums (docs/fidelity/grass.md).
     * Saves before 39 never hold them, so nothing is read differently. An older build refuses a version-39 save with its usual
     * "Unsupported save version" error, instead of failing on a value it does not have.
     */
    /** The version [write] writes by default. Tests compare against it, so they do not repeat the number. */
    internal const val VERSION = 39

    /** Spoil times before version 35, which saved rot is rescaled from. */
    private const val OLD_MEAT_SPOIL_DAYS = 4f
    private const val OLD_CORPSE_ROT_DAYS = 2.2f

    /** Saves older than this count ticks in the old base of 1,000 ticks per hour. Their times are converted when read. */
    private const val TIME_BASE_VERSION = 25

    /** Times at or past this are "never" markers (Long.MAX_VALUE in battle maps). They are left alone. */
    private const val NEVER = Long.MAX_VALUE / 4

    private fun Long.fromOldBase(version: Int): Long =
        if (version >= TIME_BASE_VERSION || this <= 0L || this >= NEVER) this else Math.round(this * TIME_SCALE.toDouble())
    private fun Int.fromOldBase(version: Int): Int = if (version >= TIME_BASE_VERSION) this else Math.round(this * TIME_SCALE)
    private fun Float.fromOldBase(version: Int): Float = if (version >= TIME_BASE_VERSION) this else this * TIME_SCALE
    private fun DataInputStream.tickAt(version: Int): Long = readLong().fromOldBase(version)
    private const val OLDEST_READABLE = 14

    /** Goods off the map: one entry per lot. Before version 22 these were plain kind-and-count pairs. */
    private fun DataOutputStream.writeStock(s: Stock, version: Int) {
        if (version < 22) {
            // Older saves kept only kind and count.
            val totals = s.totals()
            writeInt(totals.size)
            for ((t, n) in totals) { writeInt(t.ordinal); writeInt(n) }
            return
        }
        val e = s.entries()
        writeInt(e.size)
        for ((lot, n) in e) { writeInt(lot.type.ordinal); writeInt(lot.quality.ordinal); writeInt(lot.condition); writeInt(n) }
    }

    private fun DataInputStream.readStock(version: Int): Stock {
        val s = Stock()
        repeat(readInt()) {
            if (version >= 22) {
                val t = ItemType.entries[readInt()]; val q = Quality.entries[readInt()]; val c = readInt()
                s.add(Lot(t, q, c), readInt())
            } else s.add(ItemType.entries[readInt()], readInt())
        }
        return s
    }

    private fun DataOutputStream.writePlan(p: BattlePlan, version: Int) {
        writeInt(p.caravanId); writeUTF(p.label); writeInt(p.kind); writeFloat(p.points); writeBoolean(p.fortified)
        writeInt(p.aftermath.ordinal); writeInt(p.settlement); writeInt(p.site); writeBoolean(p.wasHostile); writeLong(p.startTick)
        writeBoolean(p.retreating); writeInt(p.outcome?.ordinal ?: -1); writeInt(p.lostOnRetreat)
        writeInt(p.carrierOf.size); for ((d, c) in p.carrierOf) { writeInt(d); writeInt(c) }
        if (version >= 17) writeInt(p.enemyFaction)
    }

    private fun DataInputStream.readPlan(version: Int): BattlePlan {
        // The label is live text (the foe's name in the fight prompt), so a pirate label is translated below version 28 (names.md section 2).
        val p = BattlePlan(readInt(), readUTF().let { if (version < 28) LegacyNames.battleLabel(it, version) else it }, readInt(), readFloat(), readBoolean(), BattleAftermath.entries[readInt()], readInt(), readInt(), readBoolean(), tickAt(version))
        p.retreating = readBoolean()
        val oc = readInt(); p.outcome = if (oc >= 0) BattleOutcome.entries[oc] else null
        p.lostOnRetreat = readInt()
        repeat(readInt()) { p.carrierOf[readInt()] = readInt() }
        if (version >= 17) p.enemyFaction = readInt()
        return p
    }
    private fun DataOutputStream.opt(s: String?) { writeBoolean(s != null); if (s != null) writeUTF(s) }

    /** An item stack held by a job (carried to a drop cell, or a corpse on its way to a grave). Keeps its id. */
    private fun DataOutputStream.writeJobStack(s: ItemStack) {
        writeInt(s.id); writeInt(s.type.ordinal); writeShort(s.x); writeShort(s.y); writeInt(s.count)
        writeByte(s.quality.ordinal); writeFloat(s.rot); writeFloat(s.hp); writeBoolean(s.forbidden)
        opt(s.corpseOf); writeInt(s.corpseRace?.ordinal ?: -1); writeBoolean(s.corpseColonist); writeInt(s.corpseAge)
    }

    private fun DataInputStream.readJobStack(version: Int): ItemStack {
        val id = readInt(); val t = ItemType.entries[readInt()]
        val x = readShort().toInt(); val y = readShort().toInt(); val n = readInt()
        val s = ItemStack(id, t, n, x, y)
        s.quality = Quality.entries[readByte().toInt()]; s.rot = readFloat(); s.hp = readFloat(); s.forbidden = readBoolean()
        s.corpseOf = opt(); val cr = readInt(); s.corpseRace = if (cr >= 0) Race.entries[cr] else null
        s.corpseColonist = readBoolean(); s.corpseAge = readInt()
        s.migrateRot(version)
        return s
    }

    /**
     * Rot is saved as the share of the spoil time that has passed. Before version 35 raw meat spoiled in 4 days and corpses in
     * 2.2, so the rot is rescaled to the same time spent under the current spoil times (docs/fidelity/economy.md).
     */
    private fun ItemStack.migrateRot(version: Int) {
        if (version >= 35) return
        rot = when {
            corpseOf != null -> rot * (OLD_CORPSE_ROT_DAYS / CORPSE_ROT_DAYS)
            type == ItemType.MEAT -> rot * (OLD_MEAT_SPOIL_DAYS / ItemType.MEAT.spoilDays)
            else -> rot
        }
    }

    /**
     * The rot of the food an eat job holds ([Job.heldRot]) is a share of the same spoil time, so raw meat gets the same rescale.
     * No other food changed its spoil time. [food] is the eat job's food type, which is read before this value.
     */
    private fun heldRotFromOld(rot: Float, food: ItemType?, version: Int): Float =
        if (version >= 35 || food != ItemType.MEAT) rot else rot * (OLD_MEAT_SPOIL_DAYS / ItemType.MEAT.spoilDays)

    /** A job exactly as it was: what it is doing, its targets, progress, what it holds, and the stack it carries. */
    private fun DataOutputStream.writeJob(j: Job) {
        writeInt(j.type.ordinal); writeInt(j.tx); writeInt(j.ty); writeInt(j.stage); writeInt(j.timer); writeFloat(j.work)
        writeInt(j.targetPawn); writeInt(j.key); writeInt(j.amount); writeInt(j.dx); writeInt(j.dy); writeInt(j.bench); writeInt(j.billIndex)
        writeInt(j.item?.ordinal ?: -1); writeInt(j.aux); writeInt(j.ingIdx); writeInt(j.collected)
        writeInt(j.held.size); for ((t, n, q) in j.held) { writeInt(t.ordinal); writeInt(n); writeInt(q.ordinal) }
        writeFloat(j.heldRot)
        val st = j.stack
        writeBoolean(st != null)
        if (st != null) writeJobStack(st)
    }

    private fun DataInputStream.readJob(version: Int): Job {
        val type = JobType.entries[readInt()]
        val j = Job(type, readInt(), readInt())
        j.stage = readInt(); j.timer = readInt().fromOldBase(version); j.work = readFloat()
        j.targetPawn = readInt(); j.key = readInt(); j.amount = readInt(); j.dx = readInt(); j.dy = readInt(); j.bench = readInt(); j.billIndex = readInt()
        val item = readInt(); j.item = if (item >= 0) ItemType.entries[item] else null
        j.aux = readInt(); j.ingIdx = readInt(); j.collected = readInt()
        repeat(readInt()) { val t = ItemType.entries[readInt()]; val n = readInt(); j.held.add(Triple(t, n, Quality.entries[readInt()])) }
        j.heldRot = heldRotFromOld(readFloat(), j.item, version)
        if (readBoolean()) j.stack = readJobStack(version)
        return j
    }
    private fun DataInputStream.opt(): String? = if (readBoolean()) readUTF() else null

    /** [version] other than the current one exists only so tests can check that older saves still load. */
    fun write(g: Game, version: Int = VERSION): ByteArray {
        val bytes = ByteArrayOutputStream()
        val o = DataOutputStream(bytes)
        o.writeInt(version)
        o.writeLong(g.seed); o.writeLong(g.tick); o.writeUTF(g.colonyName)
        o.writeInt(g.scenario.ordinal); o.writeInt(g.storyteller.ordinal); o.writeInt(g.difficulty.ordinal)
        o.writeInt(g.nextPawnId); o.writeInt(g.raidCounter); o.writeInt(g.raidsSurvived)
        o.writeLong(g.nextRaid); o.writeLong(g.nextWanderer); o.writeLong(g.nextPod); o.writeLong(g.nextTempEvent); o.writeLong(g.nextMisc); o.writeLong(g.nextTrader)
        o.writeBoolean(g.raidActive); o.writeInt(g.raidStartCount); o.writeLong(g.raidEnds); o.writeLong(g.raidStartedAt)
        o.writeInt(g.weather.ordinal); o.writeLong(g.weatherUntil)
        o.writeFloat(g.tempOffset); o.writeLong(g.tempEventUntil); o.writeUTF(g.tempEventName)
        o.writeLong(g.solarFlareUntil); o.writeLong(g.toxicFalloutUntil); o.writeLong(g.eclipseUntil)
        o.writeInt(g.homeX); o.writeInt(g.homeY); o.writeFloat(g.windFactor)
        o.writeInt(g.statsKilled); o.writeInt(g.silverEarned); o.writeInt(g.hintBits)
        o.writeInt(g.graveyard.size); for (s in g.graveyard) o.writeUTF(s)
        o.writeInt(g.researchCurrent?.ordinal ?: -1)
        o.writeInt(g.researchDone.size); for (r in g.researchDone) o.writeInt(r.ordinal)
        o.writeInt(g.researchProgress.size); for ((r, v) in g.researchProgress) { o.writeInt(r.ordinal); o.writeFloat(v) }
        o.writeInt(g.traders.size)
        for (t in g.traders) {
            o.writeInt(t.pawnId); o.writeUTF(t.name); o.writeLong(t.arrival); o.writeLong(t.leaveAt); o.writeInt(t.silver)
            o.writeStock(t.stock, version)
        }
        if (version >= 20) {
            o.writeLong(g.raidLastEnded)
            o.writeInt(g.incidentLast.size)
            for ((k, v) in g.incidentLast) { o.writeUTF(k.name); o.writeLong(v) }
        }
        if (version >= 34) {
            o.writeInt(g.lastRandomIncidents.size)
            for (k in g.lastRandomIncidents) o.writeUTF(k.name)
        }

        val m = g.map
        o.writeInt(m.w); o.writeInt(m.h); o.writeInt(m.biome.ordinal)
        for (i in 0 until m.size) {
            o.writeByte(m.terrain[i].ordinal); o.writeByte(m.ore[i].ordinal); o.writeByte(m.rockType[i].ordinal)
            o.writeByte(m.floor[i]?.ordinal ?: -1); o.writeByte(m.floorQuality[i].ordinal)
            o.writeInt(m.zoneId[i]); o.writeByte(m.desig[i].toInt()); o.writeByte(m.filth[i].toInt())
            o.writeBoolean(m.natRoof[i]); o.writeBoolean(m.conduit[i]); o.writeByte((m.snow[i] * 100).toInt())
            if (version >= 38) o.writeBoolean(m.roof[i])
        }
        for (a in 0 until 3) { o.writeUTF(m.areaNames[a]); for (c in 0 until m.size) o.writeBoolean(m.areas[a][c]) }
        o.writeInt(m.peekNextZone())
        o.writeInt(m.zones.size)
        for (z in m.zones.values) {
            o.writeInt(z.id); o.writeInt(z.kind); o.writeUTF(z.name); o.writeInt(z.priority); o.writeInt(z.crop.ordinal)
            o.writeBoolean(z.sow); o.writeInt(z.minQuality.ordinal); o.writeInt(z.cells)
            for (b in z.allowed) o.writeBoolean(b)
        }
        val builds = m.buildings()
        o.writeInt(builds.size)
        for (b in builds) {
            o.writeInt(b.def.ordinal); o.writeShort(b.x); o.writeShort(b.y); o.writeBoolean(b.built); o.writeBoolean(b.rot)
            if (version >= 15) o.writeInt(b.material?.ordinal ?: -1)
            for (d in b.delivered) o.writeInt(d)
            o.writeFloat(b.progress); o.writeFloat(b.hp); o.writeInt(b.ownerId); o.writeFloat(b.fuel)
            o.writeByte(b.quality.ordinal); o.writeBoolean(b.forbidden); o.writeFloat(b.charge); o.writeInt(b.shells)
            o.writeBoolean(b.prisonerBed); o.writeInt(b.occupant)
            o.writeInt(b.bills.size)
            for (bill in b.bills) {
                o.writeInt(bill.recipe.ordinal); o.writeInt(bill.mode.ordinal); o.writeInt(bill.target); o.writeInt(bill.done)
                o.writeBoolean(bill.paused); o.writeInt(bill.minSkill)
                val al = bill.allowedItems
                o.writeBoolean(al != null)
                if (al != null) { o.writeInt(al.size); for (t in al) o.writeInt(t.ordinal) }
            }
        }
        val plants = m.plant.filterNotNull()
        o.writeInt(plants.size)
        for (p in plants) { o.writeInt(p.type.ordinal); o.writeShort(p.x); o.writeShort(p.y); o.writeFloat(p.growth); o.writeInt(p.age) }
        o.writeInt(m.items.size)
        for (s in m.items.values) {
            o.writeInt(s.type.ordinal); o.writeShort(s.x); o.writeShort(s.y); o.writeInt(s.count)
            o.writeByte(s.quality.ordinal); o.writeFloat(s.rot); o.writeFloat(s.hp); o.writeBoolean(s.forbidden)
            o.opt(s.corpseOf); o.writeInt(s.corpseRace?.ordinal ?: -1); o.writeBoolean(s.corpseColonist); o.writeInt(s.corpseAge)
        }
        o.writeInt(m.fires.size)
        for ((i, f) in m.fires) { o.writeInt(i); o.writeFloat(f.intensity) }
        o.writeInt(g.worldBiome.ordinal); o.writeInt(g.world.homeTile)

        // A battle map's people are written with its battle, not here.
        val pawns = g.pawns.filter { it.alive && it !in g.battleMembers }
        o.writeInt(pawns.size)
        for (p in pawns) writePawn(o, p, g.tick, version)
        // World state
        for (g2 in g.world.goodwill) o.writeInt(g2)
        o.writeInt(g.world.settlements.size)
        for (st in g.world.settlements) {
            o.writeInt(st.silver); o.writeLong(st.stockTick); o.writeLong(st.destroyedUntil)
            o.writeStock(st.stock, version)
            val r = st.request
            o.writeBoolean(r != null)
            if (r != null) { o.writeInt(r.type.ordinal); o.writeInt(r.count); o.writeInt(r.reward); o.writeLong(r.expires) }
        }
        o.writeInt(g.world.nextSiteId)
        o.writeInt(g.world.sites.size)
        for (s2 in g.world.sites) { o.writeInt(s2.id); o.writeInt(s2.tile); o.writeInt(s2.kind); o.writeInt(s2.factionId); o.writeInt(s2.reward); o.writeLong(s2.expires); o.writeFloat(s2.strength); o.writeUTF(s2.name) }
        if (version >= 21) {
            o.writeInt(g.world.nextQuestId)
            o.writeInt(g.world.quests.size)
            for (q in g.world.quests) {
                o.writeInt(q.id); o.writeInt(q.kind.ordinal); o.writeInt(q.factionId); o.writeInt(q.siteId); o.writeInt(q.reward)
                o.writeLong(q.deadline); o.writeInt(q.state.ordinal); o.writeLong(q.resolvedAt)
            }
            o.writeInt(g.world.captives.size)
            for (c in g.world.captives) { o.writeInt(c.questId); writePawn(o, c.pawn, g.tick, version) }
        }
        o.writeInt(g.nextCaravanId)
        o.writeInt(g.caravans.size)
        for (c in g.caravans) {
            o.writeInt(c.id); o.writeUTF(c.name); o.writeInt(c.tile); o.writeInt(c.destination); o.writeFloat(c.progress)
            o.writeBoolean(c.resting); o.writeBoolean(c.goingHome); o.writeUTF(c.lastEvent); o.writeBoolean(c.forageNote)
            o.writeInt(c.route.size); for (t in c.route) o.writeInt(t)
            o.writeStock(c.inventory, version)
            if (version >= 16) o.writeBoolean(c.inBattle)
            val ms = c.members.filter { it.alive }
            o.writeInt(ms.size); for (p in ms) writePawn(o, p, g.tick, version)
        }
        val recent = g.log.takeLast(60)
        o.writeInt(recent.size)
        for (l in recent) { o.writeLong(l.tick); o.writeUTF(l.text); o.writeInt(l.level) }
        // Fights: one that is pending, and the battle map this game is, with the world it came from nested inside.
        if (version >= 16) {
            o.writeBoolean(g.pendingBattle != null)
            g.pendingBattle?.let { o.writePlan(it, version) }
            val bp = g.battle
            o.writeBoolean(bp != null)
            if (bp != null) {
                o.writePlan(bp, version)
                // Only the living are kept: a member killed earlier in the same tick must not come back alive.
                val living = g.battleMembers.filter { it.alive }
                o.writeInt(living.size)
                for (p in living) { o.writeBoolean(p in g.pawns); writePawn(o, p, g.tick, version) }
                val world = write(g.parent!!, version)
                o.writeInt(world.size); o.write(world)
            }
        }
        // Ransom offers and the prisoners they are cooling down for.
        if (version >= 17) {
            o.writeInt(g.nextRansomId)
            o.writeInt(g.ransomOffers.size)
            for (r in g.ransomOffers) { o.writeInt(r.id); o.writeInt(r.prisonerId); o.writeUTF(r.prisonerName); o.writeInt(r.factionId); o.writeInt(r.price); o.writeLong(r.expires) }
            o.writeInt(g.ransomCooldown.size)
            for ((k, v) in g.ransomCooldown) { o.writeInt(k); o.writeLong(v) }
        }
        // The generator state, so a loaded game continues the same random sequence; and how the game stands.
        if (version >= 18) {
            o.writeLong(g.rng.state)
            o.writeBoolean(g.gameOver); o.writeBoolean(g.won); o.writeBoolean(g.encounter); o.writeBoolean(g.mentalBreaksEnabled)
        }
        if (version >= 19) {
            o.writeLong(g.playMs)
            o.writeInt(g.unreachable.size); for ((k, v) in g.unreachable) { o.writeLong(k); o.writeLong(v) }
        }
        o.flush()
        return bytes.toByteArray()
    }

    private fun writePawn(o: DataOutputStream, p: Pawn, now: Long, version: Int) {
        o.writeInt(p.id); o.writeUTF(p.name); o.writeInt(p.race.ordinal); o.writeInt(p.faction.ordinal)
        o.writeShort(p.x); o.writeShort(p.y)
        o.writeBoolean(p.downed); o.writeBoolean(p.drafted); o.writeBoolean(p.prisoner); o.writeBoolean(p.hostileFlag)
        o.writeInt(p.age); o.writeBoolean(p.female); o.writeFloat(p.mood)
        o.writeLong(p.breakUntil); o.writeInt(p.breakKind)
        o.writeFloat(p.food); o.writeFloat(p.rest); o.writeFloat(p.joy); o.writeFloat(p.bloodLoss)
        o.writeInt(p.careLevel); o.writeInt(p.foodPolicy); o.writeBoolean(p.allowDrugs); o.writeInt(p.outfit); o.writeInt(p.drugPolicy)
        for (s in p.skill) o.writeInt(s)
        for (s in p.xp) o.writeFloat(s)
        for (s in p.passion) o.writeInt(s)
        for (s in p.priority) o.writeInt(s)
        o.writeInt(p.traits.size); for (t in p.traits) o.writeInt(t.ordinal)
        o.writeUTF(p.backstory); o.writeInt(p.incapable)
        o.writeInt(p.weaponItem?.ordinal ?: -1); o.writeInt(p.weaponQuality.ordinal)
        o.writeInt(p.apparel.size); for (w in p.apparel) { o.writeInt(w.type.ordinal); o.writeInt(w.quality.ordinal); o.writeFloat(w.hp) }
        o.writeInt(p.injuries.size)
        for (i in p.injuries) {
            o.writeInt(i.part); o.writeInt(i.kind.ordinal); o.writeFloat(i.severity); o.writeFloat(i.bleed)
            o.writeBoolean(i.tended); o.writeFloat(i.tendQuality); o.writeFloat(i.infection); o.writeBoolean(i.infectable)
            o.writeBoolean(i.permanent); o.writeBoolean(i.missing); o.writeBoolean(i.scar); o.writeUTF(i.implant); o.writeInt(i.age); o.writeFloat(i.immune)
        }
        o.writeInt(p.hediffs.size)
        for (h in p.hediffs) { o.writeInt(h.kind.ordinal); o.writeFloat(h.severity); o.writeFloat(h.immunity); o.writeBoolean(h.tended); o.writeFloat(h.tendQuality); o.writeInt(h.age); o.writeInt(h.part); o.writeInt(h.duration) }
        val th = p.thoughts.filter { it.expires > now }
        o.writeInt(th.size)
        for (t in th) { o.writeUTF(t.label); o.writeFloat(t.mood); o.writeLong(t.expires - now) }
        o.writeInt(p.opinion.size); for ((k, v) in p.opinion) { o.writeInt(k); o.writeInt(v) }
        o.writeInt(p.spouse); o.writeInt(p.lover)
        for (s in p.schedule) o.writeInt(s)
        o.writeInt(p.bedId); o.writeInt(p.homeTile)
        o.writeBoolean(p.tame); o.writeInt(p.master); o.writeInt(p.animalProductTimer); o.writeBoolean(p.manhunter)
        o.writeBoolean(p.huntMark); o.writeBoolean(p.tameMark); o.writeBoolean(p.slaughterMark)
        o.writeFloat(p.resistance); o.writeBoolean(p.escaping); o.writeInt(p.recruitMode)
        o.writeInt(p.raidId); o.writeInt(p.raidMode); o.writeInt(p.campX); o.writeInt(p.campY); o.writeBoolean(p.retreating)
        o.writeLong(p.escapeTick); o.writeBoolean(p.wanderer); o.writeLong(p.lastSocial); o.writeInt(p.wfaction); o.writeBoolean(p.ally); o.writeInt(p.birthday); o.writeInt(p.ageDays); o.writeInt(p.mother); o.writeInt(p.father); o.writeLong(p.pregnantUntil); o.writeInt(p.pregnantBy); o.writeBoolean(p.dormant)
        o.writeInt(p.herdLeader)
        o.writeBoolean(p.refugee); o.writeInt(p.areaRestriction)
        o.writeInt(p.implants.size); for ((k, v) in p.implants) { o.writeInt(k); o.writeInt(v.ordinal) }
        if (version >= 18) {
            o.writeInt(p.carryType?.ordinal ?: -1); o.writeInt(p.carryCount); o.writeInt(p.carryQuality.ordinal)
            o.writeInt(p.surgeries.size)
            for (s in p.surgeries) { o.writeInt(s.kind.ordinal); o.writeInt(s.part); o.writeInt(s.implant?.ordinal ?: -1) }
        }
        if (version >= 19) {
            // Where the pawn is in its movement, its path, what it has claimed, and the job it is doing.
            o.writeInt(p.carriedBy); o.writeInt(p.carrying); o.writeInt(p.attackCd); o.writeInt(p.warmup); o.writeInt(p.burstLeft)
            o.writeInt(p.moveCd); o.writeInt(p.moveTotal); o.writeInt(p.fromX); o.writeInt(p.fromY)
            val joy = p.lastJoyKind.toByteArray(); o.writeInt(joy.size); o.write(joy)
            o.writeFloat(p.recruitProgress)
            // Read by the next mood update, which runs before the comfort tick that would refresh them.
            o.writeFloat(p.temp); o.writeBoolean(p.dark)
            if (version >= 21) o.writeBoolean(p.abandoned)
            if (version >= 23) o.writeLong(p.suppressedUntil)
            if (version >= 24) o.writeInt(p.trained)
            o.writeInt(p.pathKey); o.writeInt(p.pathI)
            val path = p.path
            if (path == null) o.writeInt(-1) else { o.writeInt(path.size); for (c in path) o.writeInt(c) }
            o.writeInt(p.reserved.size); for (k in p.reserved) o.writeInt(k)
            val j = p.job
            o.writeBoolean(j != null)
            if (j != null) o.writeJob(j)
        }
        if (version >= 32) {
            o.writeFloat(p.outdoorsNeed); o.writeFloat(p.indoorsNeed); o.writeInt(p.ticksAtZeroRest)
            o.writeInt(p.moodBelowExtremeTicks); o.writeInt(p.moodBelowMajorTicks); o.writeInt(p.moodBelowMinorTicks); o.writeInt(p.breakCooldown)
            o.writeInt(p.drugTolerance.size)
            for ((drug, tolerance) in p.drugTolerance) { o.writeInt(drug.ordinal); o.writeFloat(tolerance) }
        }
        if (version >= 33) for (x in p.xpToday) o.writeFloat(x)
        if (version >= 36) { o.writeFloat(p.burnSize); o.writeLong(p.staggerUntil) }
    }

    fun read(data: ByteArray): Game {
        val i = DataInputStream(ByteArrayInputStream(data))
        val version = i.readInt()
        require(version in OLDEST_READABLE..VERSION) { "Unsupported save version" }
        val seed = i.readLong(); val tick = i.tickAt(version); val name = i.readUTF()
        val scenario = Scenario.entries[i.readInt()]; val story = Storyteller.entries[i.readInt()]; val diff = Difficulty.entries[i.readInt()]
        val nextPawn = i.readInt(); val raidCounter = i.readInt(); val survived = i.readInt()
        val nr = i.tickAt(version); val nw = i.tickAt(version); val np = i.tickAt(version); val nt = i.tickAt(version); val nm = i.tickAt(version); val ntr = i.tickAt(version)
        val raidActive = i.readBoolean(); val raidStart = i.readInt(); val raidEnds = i.tickAt(version); val rsa = i.tickAt(version)
        val weather = Weather.entries[i.readInt()]; val weatherUntil = i.tickAt(version)
        val tempOffset = i.readFloat(); val tempUntil = i.tickAt(version); val tempName = i.readUTF()
        val flare = i.tickAt(version); val toxic = i.tickAt(version); val eclipse = i.tickAt(version)
        val hx = i.readInt(); val hy = i.readInt(); val wind = i.readFloat()
        val killed = i.readInt(); val earned = i.readInt(); val hints = i.readInt()
        // Graveyard entries are read exactly as they were saved, in every version. Each one is a record of a past death, so the
        // causes that earlier versions wrote stay as they were. Only new deaths write the current causes. Decision recorded in
        // docs/fidelity/names.md (section 2). LegacyNames does not translate graveyard entries.
        val grave = List(i.readInt()) { i.readUTF() }
        val cur = i.readInt()
        val done = List(i.readInt()) { Research.entries[i.readInt()] }
        val prog = List(i.readInt()) { Research.entries[i.readInt()] to i.readFloat() }
        val traderList = List(i.readInt()) {
            val t = TraderInfo(i.readInt(), i.readUTF(), i.tickAt(version), i.tickAt(version))
            t.silver = i.readInt()
            t.stock.addAll(i.readStock(version))
            t
        }
        var raidEnded = -1L
        val incidents: MutableMap<Incident, Long> = java.util.EnumMap(Incident::class.java)
        if (version >= 20) {
            raidEnded = i.tickAt(version)
            repeat(i.readInt()) { val name = i.readUTF().let { if (version < 28) LegacyNames.incidentName(it, version) else it }; val at = i.tickAt(version); Incident.entries.firstOrNull { it.name == name }?.let { incidents[it] = at } }
        }
        var recentRandom: List<Incident> = emptyList()
        if (version >= 34) {
            recentRandom = List(i.readInt()) { i.readUTF() }.mapNotNull { name -> Incident.entries.firstOrNull { it.name == name } }
        }

        val w = i.readInt(); val h = i.readInt()
        val map = GameMap(w, h)
        map.biome = Biome.entries[i.readInt()]
        for (c in 0 until map.size) {
            map.terrain[c] = Terrain.entries[i.readByte().toInt()]
            map.ore[c] = Ore.entries[i.readByte().toInt()]
            map.rockType[c] = RockType.entries[i.readByte().toInt()]
            val f = i.readByte().toInt()
            map.floor[c] = if (f >= 0) BuildDef.entries[f] else null
            map.floorQuality[c] = Quality.entries[i.readByte().toInt()]
            map.zoneId[c] = i.readInt(); map.desig[c] = i.readByte(); map.filth[c] = i.readByte()
            map.natRoof[c] = i.readBoolean(); map.conduit[c] = i.readBoolean(); map.snow[c] = i.readByte() / 100f
            if (version >= 38) map.roof[c] = i.readBoolean()
        }
        for (a in 0 until 3) { map.areaNames[a] = i.readUTF(); for (c in 0 until map.size) map.areas[a][c] = i.readBoolean() }
        map.setNextZone(i.readInt())
        map.zoneVersion++
        repeat(i.readInt()) {
            val z = Zone(i.readInt(), i.readInt())
            z.name = i.readUTF(); z.priority = i.readInt(); z.crop = PlantType.entries[i.readInt()]
            z.sow = i.readBoolean(); z.minQuality = Quality.entries[i.readInt()]; z.cells = i.readInt()
            for (k in z.allowed.indices) z.allowed[k] = i.readBoolean()
            map.zones[z.id] = z
        }
        repeat(i.readInt()) {
            val def = BuildDef.entries[i.readInt()]
            val x = i.readShort().toInt(); val y = i.readShort().toInt()
            val b = Building(def, x, y, i.readBoolean())
            b.rot = i.readBoolean()
            if (version >= 15) { val mi = i.readInt(); if (mi >= 0) b.material = ItemType.entries[mi] }
            for (k in b.delivered.indices) b.delivered[k] = i.readInt()
            b.progress = i.readFloat(); b.hp = i.readFloat(); b.ownerId = i.readInt(); b.fuel = i.readFloat()
            b.quality = Quality.entries[i.readByte().toInt()]; b.forbidden = i.readBoolean(); b.charge = i.readFloat(); b.shells = i.readInt()
            b.prisonerBed = i.readBoolean(); b.occupant = i.readInt()
            repeat(i.readInt()) {
                val bill = Bill(Recipe.entries[i.readInt()])
                bill.mode = BillMode.entries[i.readInt()]; bill.target = i.readInt(); bill.done = i.readInt()
                bill.paused = i.readBoolean(); bill.minSkill = i.readInt()
                if (i.readBoolean()) { val set = java.util.EnumSet.noneOf(ItemType::class.java); repeat(i.readInt()) { set.add(ItemType.entries[i.readInt()]) }; bill.allowedItems = set }
                b.bills.add(bill)
            }
            map.setBuilding(b)
        }
        repeat(i.readInt()) {
            val t = PlantType.entries[i.readInt()]
            val x = i.readShort().toInt(); val y = i.readShort().toInt()
            val pl = Plant(t, x, y, i.readFloat()); pl.age = i.readInt().fromOldBase(version)
            map.plant[map.idx(x, y)] = pl
        }
        repeat(i.readInt()) {
            val t = ItemType.entries[i.readInt()]
            val x = i.readShort().toInt(); val y = i.readShort().toInt()
            val s = ItemStack(map.nextId(), t, i.readInt(), x, y)
            s.quality = Quality.entries[i.readByte().toInt()]; s.rot = i.readFloat(); s.hp = i.readFloat(); s.forbidden = i.readBoolean()
            s.corpseOf = i.opt(); val cr = i.readInt(); s.corpseRace = if (cr >= 0) Race.entries[cr] else null
            s.corpseColonist = i.readBoolean(); s.corpseAge = i.readInt()
            s.migrateRot(version)
            map.items[map.idx(x, y)] = s
        }
        repeat(i.readInt()) { val c = i.readInt(); map.fires[c] = Fire(i.readFloat()) }

        val wBiome = Biome.entries[i.readInt()]; val wHome = i.readInt()
        val g = Game(seed, map)
        g.rng = Rng(seed + tick)
        g.tick = tick; g.colonyName = name
        g.scenario = scenario; g.storyteller = story; g.difficulty = diff
        g.nextPawnId = nextPawn; g.raidCounter = raidCounter; g.raidsSurvived = survived
        g.nextRaid = nr; g.nextWanderer = nw; g.nextPod = np; g.nextTempEvent = nt; g.nextMisc = nm; g.nextTrader = ntr
        g.raidActive = raidActive; g.raidStartCount = raidStart; g.raidEnds = raidEnds; g.raidStartedAt = rsa
        g.weather = weather; g.weatherUntil = weatherUntil
        g.tempOffset = tempOffset; g.tempEventUntil = tempUntil; g.tempEventName = LegacyNames.tempEventName(tempName, version)
        g.solarFlareUntil = flare; g.toxicFalloutUntil = toxic; g.eclipseUntil = eclipse
        g.homeX = hx; g.homeY = hy; g.windFactor = wind
        g.statsKilled = killed; g.silverEarned = earned; g.hintBits = hints
        g.graveyard.addAll(grave)
        g.researchCurrent = if (cur >= 0) Research.entries[cur] else null
        g.researchDone.addAll(done)
        for ((r, v) in prog) g.researchProgress[r] = v
        g.traders.addAll(traderList)
        g.raidLastEnded = raidEnded
        g.incidentLast.putAll(incidents)
        g.lastRandomIncidents.addAll(recentRandom.take(RECENT_INCIDENT_WEIGHTS.size))

        repeat(i.readInt()) { g.pawns.add(readPawn(i, tick, version)) }
        run {
            g.worldBiome = wBiome
            g.world = World.generate(seed, wBiome)
            g.world.homeTile = wHome
            for (k in g.world.goodwill.indices) g.world.goodwill[k] = i.readInt()
            repeat(i.readInt()) { idx ->
                val st = g.world.settlements.getOrNull(idx)
                val silver = i.readInt(); val stockTick = i.tickAt(version); val destroyed = i.tickAt(version)
                val stock = i.readStock(version)
                val req = if (i.readBoolean()) SettlementRequest(ItemType.entries[i.readInt()], i.readInt(), i.readInt(), i.tickAt(version)) else null
                if (st != null) { st.silver = silver; st.stockTick = stockTick; st.destroyedUntil = destroyed; st.stock.addAll(stock); st.request = req }
            }
            g.world.nextSiteId = i.readInt()
            // A site name is live text. A pirate camp was renamed in version 28, so a save below 28 is translated (names.md section 2).
            repeat(i.readInt()) { g.world.sites.add(Site(i.readInt(), i.readInt(), i.readInt(), i.readInt(), i.readInt(), i.tickAt(version), i.readFloat(), i.readUTF().let { if (version < 28) LegacyNames.siteName(it, version) else it })) }
            if (version >= 21) {
                g.world.nextQuestId = i.readInt()
                repeat(i.readInt()) {
                    val q = Quest(i.readInt(), QuestKind.entries[i.readInt()], i.readInt(), i.readInt(), i.readInt(), i.tickAt(version))
                    q.state = QuestState.entries[i.readInt()]; q.resolvedAt = i.tickAt(version)
                    g.world.quests.add(q)
                }
                repeat(i.readInt()) { val qid = i.readInt(); g.world.captives.add(Captive(readPawn(i, tick, version), qid)) }
            }
            g.nextCaravanId = i.readInt()
            repeat(i.readInt()) {
                val c = Caravan(i.readInt(), i.readUTF(), i.readInt())
                c.destination = i.readInt(); c.progress = i.readFloat().fromOldBase(version); c.resting = i.readBoolean(); c.goingHome = i.readBoolean()
                // lastEvent is a record of a past battle, so it is read as saved and not translated (names.md section 2).
                c.lastEvent = i.readUTF(); c.forageNote = i.readBoolean()
                repeat(i.readInt()) { c.route.add(i.readInt()) }
                c.inventory.addAll(i.readStock(version))
                if (version >= 16) c.inBattle = i.readBoolean()
                repeat(i.readInt()) { c.members.add(readPawn(i, tick, version)) }
                g.caravans.add(c)
            }
        }
        // Log lines are read exactly as they were saved, in every version, like graveyard entries: each one records a past event.
        // LegacyNames does not translate them. Decision recorded in docs/fidelity/names.md (section 2).
        g.log.clear()
        repeat(i.readInt()) { g.log.add(LogEntry(i.tickAt(version), i.readUTF(), i.readInt())) }
        if (version >= 16) {
            if (i.readBoolean()) g.pendingBattle = i.readPlan(version)
            if (i.readBoolean()) {
                val plan = i.readPlan(version)
                val members = ArrayList<Pair<Pawn, Boolean>>()
                repeat(i.readInt()) { val onMap = i.readBoolean(); members.add(readPawn(i, tick, version) to onMap) }
                val worldBytes = ByteArray(i.readInt()); i.readFully(worldBytes)
                val world = read(worldBytes)
                g.battle = plan
                g.parent = world
                g.world = world.world
                for ((p, onMap) in members) { g.battleMembers.add(p); if (onMap) g.pawns.add(p) }
                // Who carries whom is part of the fight's state, so it is put back here.
                for ((d, carrier) in plan.carrierOf) {
                    g.pawns.firstOrNull { it.id == d }?.carriedBy = carrier
                    g.pawns.firstOrNull { it.id == carrier }?.carrying = d
                }
                for ((p, _) in members) g.recomputeHealth(p)
            }
        }
        if (version >= 17) {
            g.nextRansomId = i.readInt()
            repeat(i.readInt()) { g.ransomOffers.add(RansomOffer(i.readInt(), i.readInt(), i.readUTF(), i.readInt(), i.readInt(), i.tickAt(version))) }
            repeat(i.readInt()) { g.ransomCooldown[i.readInt()] = i.tickAt(version) }
        }
        if (version >= 18) {
            g.rng.restore(i.readLong())
            g.gameOver = i.readBoolean(); g.won = i.readBoolean(); g.encounter = i.readBoolean(); g.mentalBreaksEnabled = i.readBoolean()
        }
        if (version >= 19) {
            g.playMs = i.readLong()
            repeat(i.readInt()) { g.unreachable[i.readLong()] = i.tickAt(version) }
        }
        // Before version 38 there is no roof grid: it is rebuilt from the old indoor rule, before the rooms are, so no room changes status.
        if (version < 38) map.adoptLegacyRoofs()
        map.rebuildRooms(g.outdoorTemp())
        for (p in g.pawns) g.recomputeHealth(p)
        for (c in g.caravans) for (p in c.members) g.recomputeHealth(p)
        g.restoreJobState()
        return g
    }

    private fun readPawn(i: DataInputStream, now: Long, version: Int): Pawn {
        val id = i.readInt(); val name = i.readUTF()
        val p = Pawn(id, name, Race.entries[i.readInt()], Faction.entries[i.readInt()])
        p.x = i.readShort().toInt(); p.y = i.readShort().toInt(); p.fromX = p.x; p.fromY = p.y
        p.downed = i.readBoolean(); p.drafted = i.readBoolean(); p.prisoner = i.readBoolean(); p.hostileFlag = i.readBoolean()
        p.age = i.readInt(); p.female = i.readBoolean(); p.mood = i.readFloat()
        p.breakUntil = i.tickAt(version); p.breakKind = i.readInt()
        p.food = i.readFloat(); p.rest = i.readFloat(); p.joy = i.readFloat(); p.bloodLoss = i.readFloat()
        p.careLevel = i.readInt(); p.foodPolicy = i.readInt(); p.allowDrugs = i.readBoolean(); p.outfit = i.readInt(); p.drugPolicy = i.readInt()
        for (s in p.skill.indices) p.skill[s] = i.readInt()
        for (s in p.xp.indices) p.xp[s] = i.readFloat()
        for (s in p.passion.indices) p.passion[s] = i.readInt()
        for (s in p.priority.indices) p.priority[s] = i.readInt()
        repeat(i.readInt()) { p.traits.add(Trait.entries[i.readInt()]) }
        p.backstory = i.readUTF().let { if (version < 31) LegacyNames.backstory(it, version) else it }; p.incapable = i.readInt()
        val wi = i.readInt(); p.weaponItem = if (wi >= 0) ItemType.entries[wi] else null; p.weaponQuality = Quality.entries[i.readInt()]
        repeat(i.readInt()) { p.apparel.add(Worn(ItemType.entries[i.readInt()], Quality.entries[i.readInt()], i.readFloat())) }
        repeat(i.readInt()) {
            val inj = Injury(i.readInt(), DamageKind.entries[i.readInt()], i.readFloat(), i.readFloat())
            inj.tended = i.readBoolean(); inj.tendQuality = i.readFloat(); inj.infection = i.readFloat(); inj.infectable = i.readBoolean()
            inj.permanent = i.readBoolean(); inj.missing = i.readBoolean(); inj.scar = i.readBoolean(); inj.implant = i.readUTF(); inj.age = i.readInt().fromOldBase(version); inj.immune = i.readFloat()
            p.injuries.add(inj)
        }
        repeat(i.readInt()) {
            val h = Hediff(HediffKind.entries[i.readInt()], i.readFloat(), i.readFloat())
            h.tended = i.readBoolean(); h.tendQuality = i.readFloat(); h.age = i.readInt().fromOldBase(version); h.part = i.readInt(); h.duration = i.readInt().fromOldBase(version)
            p.hediffs.add(h)
        }
        repeat(i.readInt()) { p.thoughts.add(Thought(i.readUTF().let { if (version < 30) LegacyNames.thoughtLabel(it, version) else it }, i.readFloat(), i.tickAt(version) + now)) }
        repeat(i.readInt()) { p.opinion[i.readInt()] = i.readInt() }
        p.spouse = i.readInt(); p.lover = i.readInt()
        for (s in p.schedule.indices) p.schedule[s] = i.readInt()
        p.bedId = i.readInt(); p.homeTile = i.readInt()
        p.tame = i.readBoolean(); p.master = i.readInt(); p.animalProductTimer = i.readInt().fromOldBase(version); p.manhunter = i.readBoolean()
        p.huntMark = i.readBoolean(); p.tameMark = i.readBoolean(); p.slaughterMark = i.readBoolean()
        p.resistance = i.readFloat(); p.escaping = i.readBoolean(); p.recruitMode = i.readInt()
        p.raidId = i.readInt(); p.raidMode = i.readInt(); p.campX = i.readInt(); p.campY = i.readInt(); p.retreating = i.readBoolean()
        p.escapeTick = i.tickAt(version); p.wanderer = i.readBoolean(); p.lastSocial = i.tickAt(version); p.wfaction = i.readInt(); p.ally = i.readBoolean(); p.birthday = i.readInt(); p.ageDays = i.readInt(); p.mother = i.readInt(); p.father = i.readInt(); p.pregnantUntil = i.tickAt(version); p.pregnantBy = i.readInt(); p.dormant = i.readBoolean()
        p.herdLeader = i.readInt()
        p.refugee = i.readBoolean(); p.areaRestriction = i.readInt()
        repeat(i.readInt()) { p.implants[i.readInt()] = Implant.entries[i.readInt()] }
        if (version >= 18) {
            val carry = i.readInt(); p.carryType = if (carry >= 0) ItemType.entries[carry] else null
            p.carryCount = i.readInt(); p.carryQuality = Quality.entries[i.readInt()]
            repeat(i.readInt()) {
                val kind = SurgeryKind.entries[i.readInt()]; val part = i.readInt(); val imp = i.readInt()
                p.surgeries.add(SurgeryOrder(kind, part, if (imp >= 0) Implant.entries[imp] else null))
            }
        }
        if (version >= 19) {
            p.carriedBy = i.readInt(); p.carrying = i.readInt(); p.attackCd = i.readInt().fromOldBase(version); p.warmup = i.readInt().fromOldBase(version); p.burstLeft = i.readInt()
            p.moveCd = i.readInt().fromOldBase(version); p.moveTotal = i.readInt().fromOldBase(version); p.fromX = i.readInt(); p.fromY = i.readInt()
            p.lastJoyKind = ByteArray(i.readInt()).also { i.readFully(it) }.decodeToString()
            p.recruitProgress = i.readFloat()
            p.temp = i.readFloat(); p.dark = i.readBoolean()
            if (version >= 21) p.abandoned = i.readBoolean()
            if (version >= 23) p.suppressedUntil = i.tickAt(version)
            if (version >= 24) p.trained = i.readInt()
            p.pathKey = i.readInt(); p.pathI = i.readInt()
            val pathLen = i.readInt()
            p.path = if (pathLen < 0) null else IntArray(pathLen) { i.readInt() }
            repeat(i.readInt()) { p.reserved.add(i.readInt()) }
            if (i.readBoolean()) p.job = i.readJob(version)
        }
        if (version >= 32) {
            p.outdoorsNeed = i.readFloat(); p.indoorsNeed = i.readFloat(); p.ticksAtZeroRest = i.readInt()
            p.moodBelowExtremeTicks = i.readInt(); p.moodBelowMajorTicks = i.readInt(); p.moodBelowMinorTicks = i.readInt(); p.breakCooldown = i.readInt()
            repeat(i.readInt()) { val drug = ItemType.entries[i.readInt()]; p.drugTolerance[drug] = i.readFloat() }
        }
        if (version >= 33) for (k in p.xpToday.indices) p.xpToday[k] = i.readFloat()
        if (version >= 36) { p.burnSize = i.readFloat(); p.staggerUntil = i.readLong() }
        return p
    }
}
