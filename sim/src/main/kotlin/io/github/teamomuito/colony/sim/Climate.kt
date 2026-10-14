package io.github.teamomuito.colony.sim

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/**
 * Outdoor temperature and the heat exchange between rooms, walls, doors and the outdoors. Pure functions of their arguments,
 * with no randomness and no clock. Constants and their sources are recorded in `docs/fidelity/temperature-rooms.md`.
 */
object Climate {
    /**
     * Peak swing of the day/night curve in degrees C, the same for every biome. D: Verse/TemperatureTuning.cs:20 (the daily
     * amplitude, 7), the same amplitude as the cosine at Verse/GenTemperature.cs:194-202, which has no biome term. The reference's
     * temperature code does have biome terms: a constant outdoor temperature (Verse/MapTemperature.cs:29-31, 45-47) and the vacuum
     * factor (Verse/GenTemperature.cs:451). Neither is a daily swing, and Stillmere models neither.
     */
    const val DIURNAL_SWING = 7f
    /** The hour of the coolest temperature. The curve is a sine, so the peak is 12 hours later. D: the coolest point of the base game's cosine is at day fraction 0.18 (Verse/GenTemperature.cs:194-202, phase 0.32), which is 4.32 h at longitude 0 (RimWorld/GenDate.cs:145-147). Round 22; was 3. */
    const val COLDEST_HOUR = 4.32f

    fun weatherOffset(w: Weather): Float = when (w) {
        Weather.RAIN -> -2f; Weather.SNOW -> -3f; Weather.THUNDER -> -3f; Weather.CLOUDY -> -1f; else -> 0f
    }

    fun seasonTemp(b: Biome, s: Season): Float = when (s) {
        Season.SPRING -> b.springT; Season.SUMMER -> b.summerT; Season.FALL -> b.fallT; Season.WINTER -> b.winterT
    }

    /** Season/day part of the outdoor temperature, without the day/night swing, events or weather. */
    fun seasonalBase(b: Biome, day: Int): Float {
        val s = Season.entries[(day / DAYS_PER_SEASON) % SEASONS_PER_YEAR]
        val next = Season.entries[(s.ordinal + 1) % SEASONS_PER_YEAR]
        val blend = (day % DAYS_PER_SEASON) / DAYS_PER_SEASON.toFloat()
        return seasonTemp(b, s) + (seasonTemp(b, next) - seasonTemp(b, s)) * blend * 0.5f
    }

    /** Day/night swing at an hour of the day (0 to 24), the same in every biome. `StrictMath` so every JVM and Android give the same bits. */
    fun diurnal(hourOfDay: Float): Float {
        val phase = ((hourOfDay - COLDEST_HOUR - 6f) / 24f) * 2.0 * StrictMath.PI
        return (StrictMath.sin(phase) * DIURNAL_SWING).toFloat()
    }

    fun outdoorTemp(b: Biome, tick: Long, tempOffset: Float, weather: Weather): Float {
        val hour = (tick % TICKS_PER_DAY) / TICKS_PER_HOUR.toFloat()
        val day = (tick / TICKS_PER_DAY).toInt()
        return seasonalBase(b, day) + diurnal(hour) + tempOffset + weatherOffset(weather)
    }
}

/**
 * The roof shares of each room, as fractions of its cells: unroofed ([open]), under a built thin roof ([thin]), and under a
 * natural thick roof ([thick]). The three add up to 1 for each room (docs/fidelity/heat-exchange.md).
 */
class RoofShares(val open: FloatArray, val thin: FloatArray, val thick: FloatArray) {
    companion object {
        val NONE = RoofShares(FloatArray(0), FloatArray(0), FloatArray(0))
    }
}

/**
 * Heat exchange. Temperatures are per room, as in vanilla (every cell of a room shares one temperature). Each slow step a room
 * moves toward the temperatures it is linked to, and the per-tick rates of vanilla are integrated exactly over the step. The
 * constants and their sources (D: decompiled RimWorld, file and line) are in docs/fidelity/heat-exchange.md.
 */
object Thermal {
    /** Wall exchange per tick, per unit of far-side weight and per room cell. D: Verse/RoomTempTracker.cs:22. */
    const val WALL_RATE = 0.00017f
    /** Weight of a far-side cell that has no room: it counts at half the outdoor gap. D: Verse/RoomTempTracker.cs:248. */
    const val HALF_OUTDOOR_WEIGHT = 0.5f
    /** Share of the gap to the far room that a door closes on each pass (per cell of the room). D: RimWorld/BuildingProperties.cs:260. */
    const val DOOR_RATE = 1f
    /** Ticks between door passes while open. D: RimWorld/BuildingProperties.cs:258. */
    const val DOOR_INTERVAL_OPEN = 34f
    /** Ticks between door passes while closed. D: RimWorld/BuildingProperties.cs:256. */
    const val DOOR_INTERVAL_CLOSED = 375f
    /** Minimum ticks a swing door stays open after a pawn approaches it (vanilla, D: RimWorld/Building_Door.cs:411). Stillmere applies it on entry (C). */
    const val DOOR_HOLD_OPEN = 300
    /** Ticks a swing door stays open after the pawn in it leaves. D: RimWorld/Building_Door.cs:47 (110 times the close factor), :367 (re-armed while occupied), :470 (default). */
    const val DOOR_CLOSE_DELAY = 110
    /** Per tick, an unroofed cell's exchange with the outdoors. D: Verse/RoomTempTracker.cs:30, :281. */
    const val ROOF_NO_RATE = 0.0007f
    /** Per tick, a thin (built) roof's exchange with the outdoors. D: Verse/RoomTempTracker.cs:28, :272. */
    const val ROOF_THIN_RATE = 0.00005f
    /** Per tick, a thick roof's pull down toward [DEEP_TEMP]. D: Verse/RoomTempTracker.cs:290-296. */
    const val ROOF_DEEP_RATE = 0.00005f
    /** The deep-ground temperature. D: Verse/TemperatureTuning.cs:11. */
    const val DEEP_TEMP = 15f
    /** An outdoor difference above this is amplified: the excess counts [DIFF_SLOPE] times. D: Verse/RoomTempTracker.cs:257-261. */
    const val DIFF_LIMIT = 100f
    const val DIFF_SLOPE = 5f
    /** Heaters stop at this temperature and coolers stop below it. D: RimWorld/CompProperties_TempControl.cs:9. */
    const val SETPOINT = 21f
    /** Degrees added per slow tick per unit of building heat, divided by room cells. C (unchanged; see docs/fidelity/heat-exchange.md). */
    const val HEAT_GAIN = 1.2f
    /** Room temperature limits. D: Verse/RoomTempTracker.cs:56; Verse/TemperatureTuning.cs:5, 7. */
    const val MIN_TEMP = -273.15f
    const val MAX_TEMP = 1000f

    /** Conductance of one wall edge. Equal for every material: vanilla has no material term (Verse/RoomTempTracker.cs:215-251). */
    fun wallConductance(@Suppress("UNUSED_PARAMETER") material: ItemType?): Float = 1f

    /** Does a heater (positive [heat]) or cooler (negative) work at room temperature [temp]? */
    fun thermostatAllows(heat: Float, temp: Float): Boolean = if (heat > 0f) temp < SETPOINT else temp > SETPOINT

    /** The open-time left after a pawn steps into a swing door: at least [DOOR_HOLD_OPEN] ticks. */
    fun holdAfterEntry(hold: Int): Int = max(hold, DOOR_HOLD_OPEN)

    /** The outdoor difference as vanilla counts it: amplified beyond [DIFF_LIMIT] (Verse/RoomTempTracker.cs:254-262). */
    fun adjustedDifference(d: Float): Float {
        if (abs(d) < DIFF_LIMIT) return d
        val s = sign(d)
        return s * DIFF_LIMIT + DIFF_SLOPE * (d - s * DIFF_LIMIT)
    }

    /** Largest share of the gap to the mean that one door pass closes. Vanilla's limiter keeps a room from overshooting (GenTemperature.cs:421-438). */
    const val MAX_PASS_SHARE = 0.99f

    /** Expected door passes per tick, when the door is open for the share [open] of the time. The passes come at the open or closed interval. */
    fun doorPassesPerTick(open: Float): Float = open / DOOR_INTERVAL_OPEN + (1f - open) / DOOR_INTERVAL_CLOSED

    /**
     * New room temperatures after one slow step. Link k lets room [linkA][k] exchange with room [linkB][k], or with the outdoors when
     * it is [OUTDOORS]. [linkC] is the weight of a wall, rock or water edge: 1 toward a room, [HALF_OUTDOOR_WEIGHT] toward the
     * outdoors through a far cell with no room. A door link has [linkDoor][k] >= 0, the index of its door in [doorOpen] (the share
     * of the step the door is open). A door to the outdoors closes its share of the gap on each pass; a door between two indoor rooms
     * brings each toward their mean, as vanilla does (docs/fidelity/heat-exchange.md). Rooms that are not [indoor] sit at the outdoor
     * temperature. Wall and outdoor exchanges are exact for constant targets, so the step cannot overshoot whatever its size.
     */
    fun step(
        temp: FloatArray, size: IntArray, indoor: BooleanArray, heat: FloatArray,
        linkA: IntArray, linkB: IntArray, linkC: FloatArray, linkDoor: IntArray,
        doorOpen: FloatArray, roofs: RoofShares, outdoor: Float,
    ): FloatArray {
        val n = temp.size
        val dt = Game.SLOW_TICK.toFloat()
        // Per room: the total exponent of its exchange, and the exponent-weighted sum of its targets.
        val rate = FloatArray(n)
        val pull = FloatArray(n)
        for (k in linkA.indices) {
            val a = linkA[k]
            if (!indoor[a]) continue
            val b = linkB[k]
            val sz = max(1, size[a]).toFloat()
            val d = linkDoor[k]
            if (d >= 0) {
                val passes = doorPassesPerTick(doorOpen[d]) * dt
                val share = min(DOOR_RATE / sz, MAX_PASS_SHARE)
                if (b == OUTDOORS || !indoor[b]) {
                    // The outdoors does not move, so the gap to it closes by half a share per pass: exact for constant targets.
                    val g = (-StrictMath.log(1.0 - share / 2.0)).toFloat() * passes
                    rate[a] += g
                    pull[a] += g * outdoor
                } else {
                    // Both rooms move toward their mean, so each closes its whole share of the gap to the mean.
                    val g = (-StrictMath.log(1.0 - share)).toFloat() * passes
                    rate[a] += g
                    pull[a] += g * 0.5f * (temp[a] + temp[b])
                }
                continue
            }
            val target = if (b == OUTDOORS || !indoor[b]) outdoor else temp[b]
            val g = WALL_RATE * linkC[k] * dt / sz
            rate[a] += g
            pull[a] += g * target
        }
        val out = FloatArray(n)
        for (r in 0 until n) {
            if (!indoor[r]) { out[r] = outdoor; continue }
            val sz = max(1, size[r]).toFloat()
            val t0 = temp[r]
            // The roof exchanges with the outdoors. Its amplified difference acts as a link whose weight follows the gap.
            val roof = ROOF_NO_RATE * roofs.open[r] + ROOF_THIN_RATE * roofs.thin[r]
            if (roof > 0f) {
                val d = outdoor - t0
                val slope = if (d == 0f) 1f else adjustedDifference(d) / d
                val g = roof * slope * dt
                rate[r] += g
                pull[r] += g * outdoor
            }
            // A thick roof pulls a room above the deep-ground temperature down toward it, and never up.
            if (t0 > DEEP_TEMP && roofs.thick[r] > 0f) {
                val g = ROOF_DEEP_RATE * roofs.thick[r] * dt
                rate[r] += g
                pull[r] += g * DEEP_TEMP
            }
            var t = t0
            if (rate[r] > 0f) t = t0 + (pull[r] / rate[r] - t0) * (1f - StrictMath.exp(-rate[r].toDouble()).toFloat())
            out[r] = (t + heat[r] / sz * HEAT_GAIN).coerceIn(MIN_TEMP, MAX_TEMP)
        }
        return out
    }

    const val OUTDOORS = -1
}
