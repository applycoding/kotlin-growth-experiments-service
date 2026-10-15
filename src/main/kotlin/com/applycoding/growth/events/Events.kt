package com.applycoding.growth.events

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.time.Instant

data class ExperimentEvent(val type: String, val experimentKey: String, val userId: String, val variant: String?, val metric: String? = null, val at: String = Instant.now().toString())

/** Publishes exposure and conversion events, keyed by user so per user ordering is kept within a partition. */
@Component
class EventPublisher(private val kafka: KafkaTemplate<String, String>, private val mapper: ObjectMapper) {
    fun publish(e: ExperimentEvent) {
        kafka.send(TOPIC, e.userId, mapper.writeValueAsString(e))
    }

    companion object {
        const val TOPIC = "experiment-events"
    }
}
