package com.applycoding.growth

import com.applycoding.growth.domain.Stats
import com.applycoding.growth.domain.VariantResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StatsTest {
    @Test
    fun `normal cdf known values`() {
        assertEquals(0.5, Stats.normalCdf(0.0), 1e-7)
        assertEquals(0.975, Stats.normalCdf(1.959964), 1e-6)
        assertEquals(0.025, Stats.normalCdf(-1.959964), 1e-6)
    }

    @Test
    fun `detects a real lift and ignores noise`() {
        val c = VariantResult("control", 10_000, 1_000)
        assertTrue(Stats.compare(c, VariantResult("t", 10_000, 1_150)).significant)
        val noise = Stats.compare(c, VariantResult("t", 10_000, 1_010))
        assertFalse(noise.significant)
        assertEquals(0.001, noise.absoluteLift, 1e-12)
    }

    @Test
    fun `empty arms are not significant`() {
        assertFalse(Stats.compare(VariantResult("c", 0, 0), VariantResult("t", 10, 5)).significant)
    }
}
