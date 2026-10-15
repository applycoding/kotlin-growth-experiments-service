package com.applycoding.growth.domain

import java.nio.ByteBuffer
import java.security.MessageDigest

data class Variant(val name: String, val weight: Int)

data class Experiment(
    val key: String,
    val description: String,
    val salt: String,
    val status: Status,
    val variants: List<Variant>,
    val trafficPercent: Int = 100,
) {
    enum class Status { DRAFT, RUNNING, STOPPED }

    init {
        require(key.matches(Regex("^[a-z0-9][a-z0-9_-]{1,63}$"))) { "invalid experiment key" }
        require(variants.size >= 2) { "an experiment needs at least two variants" }
        require(variants.map { it.name }.toSet().size == variants.size) { "variant names must be unique" }
        require(variants.all { it.weight > 0 }) { "weights must be positive" }
        require(trafficPercent in 1..100) { "traffic must be 1..100 percent" }
    }
}

/**
 * Deterministic bucketing: SHA-256 of "salt:key:userId" mapped to 0..9999.
 *
 * The same user always lands in the same variant without storing anything, assignments are
 * independent across experiments (different salt/key), and changing the salt reshuffles.
 * Traffic allocation uses a separate hash so that ramping traffic up keeps existing users in
 * the experiment and in their variant.
 */
object Assigner {
    const val BUCKETS = 10_000

    fun bucket(vararg parts: String): Int {
        val digest = MessageDigest.getInstance("SHA-256").digest(parts.joinToString(":").toByteArray())
        val n = ByteBuffer.wrap(digest, 0, 8).long
        return java.lang.Long.remainderUnsigned(n, BUCKETS.toLong()).toInt()
    }

    /** Returns the variant name, or null when the user is outside the traffic allocation or the experiment is not running. */
    fun assign(exp: Experiment, userId: String): String? {
        require(userId.isNotBlank()) { "userId required" }
        if (exp.status != Experiment.Status.RUNNING) return null
        if (bucket(exp.salt, exp.key, "traffic", userId) >= exp.trafficPercent * BUCKETS / 100) return null
        val total = exp.variants.sumOf { it.weight }
        val point = bucket(exp.salt, exp.key, userId).toLong() * total / BUCKETS
        var acc = 0L
        for (v in exp.variants) {
            acc += v.weight
            if (point < acc) return v.name
        }
        return exp.variants.last().name
    }
}
