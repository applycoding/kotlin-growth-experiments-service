package com.applycoding.growth

import com.applycoding.growth.domain.Assigner
import com.applycoding.growth.domain.Experiment
import com.applycoding.growth.domain.Variant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.abs

class AssignerTest {
    private fun exp(vararg v: Pair<String, Int>, traffic: Int = 100, salt: String = "s1", status: Experiment.Status = Experiment.Status.RUNNING) =
        Experiment("checkout-cta", "test", salt, status, v.map { Variant(it.first, it.second) }, traffic)

    @Test
    fun `same user always gets the same variant`() {
        val e = exp("control" to 50, "treatment" to 50)
        (1..500).forEach { assertEquals(Assigner.assign(e, "u$it"), Assigner.assign(e, "u$it")) }
    }

    @Test
    fun `split follows weights within 2 points over 20k users`() {
        val e = exp("control" to 70, "a" to 20, "b" to 10)
        val counts = (1..20_000).groupingBy { Assigner.assign(e, "user-$it")!! }.eachCount()
        assertTrue(abs(counts["control"]!! / 20_000.0 - 0.70) < 0.02, counts.toString())
        assertTrue(abs(counts["a"]!! / 20_000.0 - 0.20) < 0.02, counts.toString())
        assertTrue(abs(counts["b"]!! / 20_000.0 - 0.10) < 0.02, counts.toString())
    }

    @Test
    fun `traffic ramp keeps already included users`() {
        val at10 = exp("c" to 1, "t" to 1, traffic = 10)
        val at50 = exp("c" to 1, "t" to 1, traffic = 50)
        val users = (1..5000).map { "u$it" }
        val in10 = users.filter { Assigner.assign(at10, it) != null }
        assertTrue(in10.size in 400..600)
        in10.forEach { assertEquals(Assigner.assign(at10, it), Assigner.assign(at50, it)) }
    }

    @Test
    fun `different salts are independent`() {
        val a = exp("c" to 1, "t" to 1, salt = "x")
        val b = exp("c" to 1, "t" to 1, salt = "y")
        val agree = (1..4000).count { Assigner.assign(a, "u$it") == Assigner.assign(b, "u$it") }
        assertTrue(agree in 1800..2200, "agreement $agree")
    }

    @Test
    fun `not running means no assignment`() {
        assertNull(Assigner.assign(exp("c" to 1, "t" to 1, status = Experiment.Status.DRAFT), "u1"))
    }

    @Test
    fun `invalid experiments are rejected`() {
        assertThrows<IllegalArgumentException> { exp("only" to 1) }
        assertThrows<IllegalArgumentException> { exp("a" to 1, "a" to 1) }
        assertThrows<IllegalArgumentException> { exp("a" to 0, "b" to 1) }
        assertThrows<IllegalArgumentException> { Experiment("Bad Key", "", "s", Experiment.Status.DRAFT, listOf(Variant("a", 1), Variant("b", 1))) }
    }

    @Test
    fun `bucket is in range`() {
        (1..1000).forEach { assertTrue(Assigner.bucket("s", "k", "u$it") in 0 until Assigner.BUCKETS) }
    }
}
