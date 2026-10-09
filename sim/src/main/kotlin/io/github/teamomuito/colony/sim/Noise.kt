package io.github.teamomuito.colony.sim

import kotlin.math.floor

/** Deterministic value noise. */
class Noise(private val seed: Int) {
    private fun hash(x: Int, y: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041.toInt()
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xffff) / 65535f
    }

    private fun smooth(t: Float) = t * t * (3 - 2 * t)

    fun at(x: Float, y: Float): Float {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = smooth(x - x0)
        val fy = smooth(y - y0)
        val a = hash(x0, y0)
        val b = hash(x0 + 1, y0)
        val c = hash(x0, y0 + 1)
        val d = hash(x0 + 1, y0 + 1)
        return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy
    }

    fun fractal(x: Float, y: Float, scale: Float): Float {
        var sum = 0f
        var amp = 1f
        var total = 0f
        var s = scale
        for (i in 0 until 3) {
            sum += at(x / s, y / s) * amp
            total += amp
            amp *= 0.5f
            s *= 0.5f
        }
        return sum / total
    }
}

/**
 * Small seeded RNG. It is the same generator as java.util.Random, so every seeded result is unchanged, but its state is
 * one Long that can be saved and restored, so a loaded game continues the exact same sequence.
 */
class Rng(seed: Long) {
    /** The 48-bit generator state. Save this to resume exactly where the game was. */
    var state: Long = (seed xor MULTIPLIER) and MASK
        private set

    fun restore(s: Long) { state = s and MASK }

    private fun next(bits: Int): Int {
        state = (state * MULTIPLIER + 0xBL) and MASK
        return (state ushr (48 - bits)).toInt()
    }

    fun float(): Float = next(24) / (1 shl 24).toFloat()

    fun int(n: Int): Int {
        if (n <= 0) return 0
        var u = next(31)
        if (n and (n - 1) == 0) return ((n * u.toLong()) shr 31).toInt()
        var v = u % n
        while (u - v + (n - 1) < 0) { u = next(31); v = u % n }
        return v
    }

    fun range(a: Int, b: Int) = a + int(b - a + 1)
    fun chance(p: Float) = float() < p
    fun <T> pick(list: List<T>): T = list[int(list.size)]

    private companion object {
        const val MULTIPLIER = 0x5DEECE66DL
        const val MASK = (1L shl 48) - 1
    }
}
