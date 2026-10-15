package com.applycoding.growth.events

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

@Configuration
class TopicConfig {
    @Bean
    fun experimentEvents(): NewTopic = TopicBuilder.name(EventPublisher.TOPIC).partitions(3).replicas(1).build()
}
