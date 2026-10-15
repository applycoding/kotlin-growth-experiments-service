package com.applycoding.growth.domain

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

data class VariantResult(val variant: String, val exposures: Long, val conversions: Long) {
    val rate: Double get() = if (exposures == 0L) 0.0 else conversions.toDouble() / exposures
}

data class Comparison(val control: String, val treatment: String, val absoluteLift: Double, val z: Double, val pValue: Double, val significant: Boolean)

object Stats {
    /** Two proportion z test of treatment vs control (normal approximation). */
    fun compare(control: VariantResult, treatment: VariantResult, alpha: Double = 0.05): Comparison {
        val n1 = control.exposures.toDouble()
        val n2 = treatment.exposures.toDouble()
        if (n1 == 0.0 || n2 == 0.0) return Comparison(control.variant, treatment.variant, 0.0, 0.0, 1.0, false)
        val pooled = (control.conversions + treatment.conversions) / (n1 + n2)
        val se = sqrt(pooled * (1 - pooled) * (1 / n1 + 1 / n2))
        val lift = treatment.rate - control.rate
        val z = if (se == 0.0) 0.0 else lift / se
        val p = 2 * (1 - normalCdf(abs(z)))
        return Comparison(control.variant, treatment.variant, lift, z, p, p < alpha)
    }

    /** Abramowitz and Stegun 7.1.26 approximation, accurate to about 1e-7. */
    fun normalCdf(x: Double): Double {
        val t = 1 / (1 + 0.3275911 * abs(x) / sqrt(2.0))
        val y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-x * x / 2)
        return if (x >= 0) 0.5 * (1 + y) else 0.5 * (1 - y)
    }
}
