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

/** Small seeded RNG (java.util.Random wrapper with conveniences). */
class Rng(seed: Long) {
    private val r = java.util.Random(seed)
    fun float() = r.nextFloat()
    fun int(n: Int) = if (n <= 0) 0 else r.nextInt(n)
    fun range(a: Int, b: Int) = a + int(b - a + 1)
    fun chance(p: Float) = r.nextFloat() < p
    fun <T> pick(list: List<T>): T = list[int(list.size)]
}
