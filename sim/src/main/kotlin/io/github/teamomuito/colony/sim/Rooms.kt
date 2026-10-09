package io.github.teamomuito.colony.sim

import kotlin.math.max
import kotlin.math.min

/**
 * Per-room stats. Every constant here is custom to Stillmere (conf C) except where the fidelity doc says otherwise; see
 * `docs/fidelity/temperature-rooms.md`. Pure functions of the map, so they can be checked by hand.
 */
object RoomRules {
    /** Beauty of a cell with no floor laid. */
    const val BARE_FLOOR_BEAUTY = -0.2f
    /** Beauty of a growing plant (not a tree) on a room cell. */
    const val PLANT_BEAUTY = 0.4f
    /** Beauty lost per filth level on a cell. */
    const val FILTH_BEAUTY = 0.9f
    /** Beauty of a cell under natural (mountain) roof. */
    const val NATURAL_ROOF_BEAUTY = -0.1f
    /** Rooms of at least this many cells get the full beauty weight. */
    const val FULL_WEIGHT_CELLS = 14f
    const val SPACE_CAP_CELLS = 60
    const val SPACE_WEIGHT = 0.18f
    const val BEAUTY_WEIGHT = 6f
    const val CLEAN_WEIGHT = 2.5f
    const val WEALTH_DIVISOR = 450f
    const val WEALTH_CAP = 9f
    /** Items count for this share of their market value toward room wealth. */
    const val ITEM_WEALTH_SHARE = 0.5f

    /** Impressiveness from the room's average beauty, cell count, wealth and cleanliness (cleanliness is 0 or negative). */
    fun impressiveness(beauty: Float, cells: Int, wealth: Float, cleanliness: Float): Float {
        val sz = max(1, cells)
        val space = min(sz, SPACE_CAP_CELLS)
        return beauty * BEAUTY_WEIGHT * min(1f, sz / FULL_WEIGHT_CELLS) + space * SPACE_WEIGHT +
            min(wealth / WEALTH_DIVISOR, WEALTH_CAP) + cleanliness * CLEAN_WEIGHT
    }
}

/** Fill [GameMap.roomBeauty], [GameMap.roomClean], [GameMap.roomWealth], [GameMap.roomImpress] and [GameMap.roomRole] for every indoor room. */
fun GameMap.computeRoomStats() {
    val m = this
    val n = m.roomTemp.size
    if (n == 0) return
    val beauty = FloatArray(n)
    val filth = FloatArray(n)
    val wealth = FloatArray(n)
    val beds = IntArray(n); val ownedBeds = IntArray(n); val tables = IntArray(n); val chairs = IntArray(n); val joy = IntArray(n)
    val hosp = IntArray(n); val prison = IntArray(n); val bench = IntArray(n); val kitchen = IntArray(n)
    for (i in 0 until m.size) {
        val r = m.roomId[i]
        if (r < 0 || !m.roomIndoor[r]) continue
        var b = 0f
        val fl = m.floor[i]
        b += fl?.beauty ?: RoomRules.BARE_FLOOR_BEAUTY
        val bd = m.building[i]
        if (bd != null && bd.built && bd.x == i % m.w && bd.y == i / m.w) {
            b += bd.beauty * bd.quality.mult
            wealth[r] += bd.def.totalCost
            when {
                bd.def.sleeps -> { beds[r]++; if (bd.ownerId >= 0) ownedBeds[r]++; if (bd.def.medical) hosp[r]++; if (bd.prisonerBed) prison[r]++ }
                bd.def == BuildDef.TABLE -> tables[r]++
                bd.def == BuildDef.CHAIR || bd.def == BuildDef.STOOL -> chairs[r]++
                bd.def.joy > 0f -> joy[r]++
                bd.def == BuildDef.STOVE_FUEL || bd.def == BuildDef.STOVE_ELEC -> kitchen[r]++
                bd.def.workbench -> bench[r]++
            }
        }
        val s = m.items[i]
        if (s != null) wealth[r] += s.type.value * s.count * RoomRules.ITEM_WEALTH_SHARE
        val pl = m.plant[i]
        if (pl != null && !pl.type.isTree) b += RoomRules.PLANT_BEAUTY
        b -= m.filth[i] * RoomRules.FILTH_BEAUTY
        b += if (m.terrain[i] == Terrain.ROCK) 0f else if (m.natRoof[i]) RoomRules.NATURAL_ROOF_BEAUTY else 0f
        beauty[r] += b
        filth[r] += m.filth[i]
    }
    for (r in 0 until n) {
        if (!m.roomIndoor[r]) continue
        val sz = max(1, m.roomSize[r])
        m.roomBeauty[r] = beauty[r] / sz
        m.roomClean[r] = -filth[r] / sz
        m.roomWealth[r] = wealth[r]
        m.roomImpress[r] = RoomRules.impressiveness(m.roomBeauty[r], sz, wealth[r], m.roomClean[r])
        m.roomRole[r] = when {
            prison[r] > 0 -> 5
            hosp[r] > 0 -> 4
            ownedBeds[r] + beds[r] >= 3 -> 2
            beds[r] > 0 -> 1
            tables[r] > 0 && chairs[r] > 0 -> 3
            joy[r] > 0 -> 6
            kitchen[r] > 0 -> 8
            bench[r] > 0 -> 7
            else -> 0
        }
    }
}
