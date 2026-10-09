package io.github.teamomuito.colony.sim

import kotlin.math.max
import kotlin.math.min

/**
 * Outdoor temperature and the heat exchange between rooms, walls, doors and the outdoors. Pure functions of their arguments,
 * with no randomness and no clock. Constants and their sources are recorded in `docs/fidelity/temperature-rooms.md`.
 */
object Climate {
    /** Peak swing of the day/night curve in degrees C. UNVERIFIED (custom). */
    const val DIURNAL_SWING = 7f
    const val DIURNAL_SWING_DRY = 10f
    /** The hour of the coolest temperature. The curve is a sine, so the peak is 12 hours later. UNVERIFIED (custom). */
    const val COLDEST_HOUR = 3f

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

    /** Day/night swing at an hour of the day (0 to 24). `StrictMath` so every JVM and Android give the same bits. */
    fun diurnal(b: Biome, hourOfDay: Float): Float {
        val swing = if (b == Biome.DESERT || b == Biome.ARID) DIURNAL_SWING_DRY else DIURNAL_SWING
        val phase = ((hourOfDay - COLDEST_HOUR - 6f) / 24f) * 2.0 * StrictMath.PI
        return (StrictMath.sin(phase) * swing).toFloat()
    }

    fun outdoorTemp(b: Biome, tick: Long, tempOffset: Float, weather: Weather): Float {
        val hour = (tick % TICKS_PER_DAY) / TICKS_PER_HOUR.toFloat()
        val day = (tick / TICKS_PER_DAY).toInt()
        return seasonalBase(b, day) + diurnal(b, hour) + tempOffset + weatherOffset(weather)
    }
}

/** Heat exchange. Temperatures are per room, as in vanilla (every cell of a room shares one temperature). */
object Thermal {
    /** Fraction of the gap closed per slow tick, per unit of conductance and per room cell. Calibrated so a 5x5 room with one door closes about 4%. */
    const val EXCHANGE = 0.048f
    /** A single step never moves a room by more than this fraction of the gap; keeps the explicit scheme stable. */
    const val MAX_RATE = 0.5f
    /** Degrees added per slow tick per unit of building heat, divided by room cells. */
    const val HEAT_GAIN = 1.2f
    /** Heaters stop at this temperature and coolers stop below it. */
    const val SETPOINT = 21f
    const val MIN_TEMP = -60f
    const val MAX_TEMP = 80f

    /** Conductance of one wall edge. Equal for every material: no per-material wall insulation is applied (see the fidelity doc). */
    fun wallConductance(@Suppress("UNUSED_PARAMETER") material: ItemType?): Float = 1f
    /** A door edge leaks more than a wall. UNVERIFIED (custom ratio). */
    const val DOOR_CONDUCTANCE = 3f
    /** A rock edge insulates well. UNVERIFIED (custom). */
    const val ROCK_CONDUCTANCE = 0.3f

    /** Temperature of the rock mass around underground rooms. UNVERIFIED (custom). */
    fun groundTemp(outdoor: Float): Float = (outdoor + 14f) / 2f

    /** Does a heater (positive [heat]) or cooler (negative) work at room temperature [temp]? */
    fun thermostatAllows(heat: Float, temp: Float): Boolean = if (heat > 0f) temp < SETPOINT else temp > SETPOINT

    /**
     * New room temperatures after one slow tick. [linkA]/[linkB]/[linkC] describe heat paths: room A exchanges with room B
     * (or [OUTDOORS] / [GROUND]) through an edge of conductance C. Rooms that are not [indoor] sit at the outdoor temperature.
     */
    fun step(
        temp: FloatArray, size: IntArray, indoor: BooleanArray, heat: FloatArray,
        linkA: IntArray, linkB: IntArray, linkC: FloatArray, outdoor: Float,
    ): FloatArray {
        val n = temp.size
        val pull = FloatArray(n)
        val leak = FloatArray(n)
        val ground = groundTemp(outdoor)
        for (k in linkA.indices) {
            val a = linkA[k]; val b = linkB[k]; val c = linkC[k]
            val tb = when {
                b == OUTDOORS -> outdoor
                b == GROUND -> ground
                indoor[b] -> temp[b]
                else -> outdoor
            }
            leak[a] += c
            pull[a] += c * tb
        }
        val out = FloatArray(n)
        for (r in 0 until n) {
            if (!indoor[r]) { out[r] = outdoor; continue }
            val sz = max(1, size[r])
            val rate = min(MAX_RATE, EXCHANGE * leak[r] / sz)
            val mean = if (leak[r] > 0f) pull[r] / leak[r] else temp[r]
            val t = temp[r] + (mean - temp[r]) * rate + heat[r] / sz * HEAT_GAIN
            out[r] = t.coerceIn(MIN_TEMP, MAX_TEMP)
        }
        return out
    }

    const val OUTDOORS = -1
    const val GROUND = -2
}
