package com.applycoding.growth.domain

import com.applycoding.growth.events.EventPublisher
import com.applycoding.growth.events.ExperimentEvent
import com.applycoding.growth.store.AssignmentStore
import com.applycoding.growth.store.ExperimentRepository
import org.springframework.stereotype.Service
import java.util.UUID

data class Assignment(val experimentKey: String, val userId: String, val variant: String?, val firstExposure: Boolean)
data class Results(val experimentKey: String, val metric: String, val variants: List<VariantResult>, val comparisons: List<Comparison>)

@Service
class ExperimentService(private val repo: ExperimentRepository, private val store: AssignmentStore, private val events: EventPublisher) {
    fun create(key: String, description: String, variants: List<Variant>, trafficPercent: Int): Experiment =
        repo.create(Experiment(key, description, UUID.randomUUID().toString().take(8), Experiment.Status.DRAFT, variants, trafficPercent))

    fun start(key: String) = repo.setStatus(key, Experiment.Status.RUNNING) ?: throw NoSuchElementException(key)
    fun stop(key: String) = repo.setStatus(key, Experiment.Status.STOPPED) ?: throw NoSuchElementException(key)
    fun get(key: String) = repo.find(key)
    fun all() = repo.all()

    fun assign(key: String, userId: String): Assignment {
        val exp = repo.find(key) ?: throw NoSuchElementException("experiment $key not found")
        val computed = Assigner.assign(exp, userId) ?: return Assignment(key, userId, store.get(userId, key), false)
        val (variant, first) = store.getOrPut(userId, key, computed)
        if (first) {
            repo.recordExposure(key, variant, userId)
            events.publish(ExperimentEvent("exposure", key, userId, variant))
        }
        return Assignment(key, userId, variant, first)
    }

    fun convert(key: String, userId: String, metric: String): Boolean {
        val recorded = repo.recordConversion(key, userId, metric)
        if (recorded) events.publish(ExperimentEvent("conversion", key, userId, store.get(userId, key), metric))
        return recorded
    }

    fun results(key: String, metric: String): Results {
        val exp = repo.find(key) ?: throw NoSuchElementException("experiment $key not found")
        val rows = repo.results(key, metric)
        val control = rows.firstOrNull { it.variant == exp.variants.first().name }
        val comps = if (control == null) emptyList() else rows.filter { it !== control }.map { Stats.compare(control, it) }
        return Results(key, metric, rows, comps)
    }
}
