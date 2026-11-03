package com.macrotap.app

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Pure Kotlin (no Android imports) so it can be unit-tested on the JVM. */
object CircleMath {
    /**
     * Uniform random point inside the circle (cx, cy, radius), limited to the screen.
     * angle ~ U(0, 2*PI), distance = radius * sqrt(U(0, 1)) -> uniform over the area.
     * Off-screen points are redrawn (keeps it uniform over the visible part);
     * only if all attempts fail is the centre clamped to the screen.
     */
    fun randomPointInCircle(
        cx: Float, cy: Float, radius: Float, screenW: Int, screenH: Int,
        rng: Random = Random.Default
    ): Pair<Float, Float> {
        val maxX = (screenW - 1).coerceAtLeast(0).toFloat()
        val maxY = (screenH - 1).coerceAtLeast(0).toFloat()
        val r = if (radius > 0f) radius else 0f
        if (r > 0f) {
            repeat(50) {
                val angle = rng.nextDouble() * 2.0 * PI
                val dist = r * sqrt(rng.nextDouble())
                val x = (cx + dist * cos(angle)).toFloat()
                val y = (cy + dist * sin(angle)).toFloat()
                if (x in 0f..maxX && y in 0f..maxY) return Pair(x, y)
            }
        }
        return Pair(cx.coerceIn(0f, maxX), cy.coerceIn(0f, maxY))
    }
}
