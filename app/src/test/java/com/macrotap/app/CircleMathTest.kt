package com.macrotap.app

import kotlin.math.hypot
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CircleMathTest {
    private val w = 1080
    private val h = 2400

    @Test fun pointsAlwaysInsideCircleAndScreen() {
        val rng = Random(1)
        repeat(20000) {
            val (x, y) = CircleMath.randomPointInCircle(500f, 900f, 120f, w, h, rng)
            assertTrue(hypot(x - 500f, y - 900f) <= 120.01f)
            assertTrue(x in 0f..(w - 1f) && y in 0f..(h - 1f))
        }
    }

    @Test fun eachCallGivesFreshPoint() {
        val rng = Random(2)
        val pts = HashSet<Pair<Float, Float>>()
        repeat(1000) { pts.add(CircleMath.randomPointInCircle(500f, 900f, 120f, w, h, rng)) }
        assertTrue(pts.size > 990)
    }

    @Test fun distributionIsUniformOverArea() {
        val rng = Random(3)
        val n = 40000
        var inner = 0
        repeat(n) {
            val (x, y) = CircleMath.randomPointInCircle(500f, 900f, 200f, w, h, rng)
            if (hypot(x - 500f, y - 900f) <= 100f) inner++
        }
        assertEquals(0.25, inner.toDouble() / n, 0.01)
    }

    @Test fun circleOverEdgeStaysOnScreen() {
        val rng = Random(4)
        repeat(5000) {
            val (x, y) = CircleMath.randomPointInCircle(5f, 5f, 150f, w, h, rng)
            assertTrue(x in 0f..(w - 1f) && y in 0f..(h - 1f))
            assertTrue(hypot(x - 5f, y - 5f) <= 150.01f)
        }
    }

    @Test fun zeroRadiusTapsCentre() {
        assertEquals(Pair(300f, 400f), CircleMath.randomPointInCircle(300f, 400f, 0f, w, h))
    }
}
