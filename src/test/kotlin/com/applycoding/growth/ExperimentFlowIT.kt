package com.applycoding.growth

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureHttpGraphQlTester
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.graphql.test.tester.HttpGraphQlTester
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.ConfluentKafkaContainer
import java.time.Duration

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureHttpGraphQlTester
@Testcontainers
class ExperimentFlowIT {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:16-alpine")
        @Container @JvmStatic val kafka = ConfluentKafkaContainer("confluentinc/cp-kafka:7.6.1")
        @Container @JvmStatic val dynamo = GenericContainer("amazon/dynamodb-local:2.5.2").withExposedPorts(8000)

        @DynamicPropertySource @JvmStatic
        fun props(r: DynamicPropertyRegistry) {
            r.add("spring.datasource.url", postgres::getJdbcUrl)
            r.add("spring.datasource.username", postgres::getUsername)
            r.add("spring.datasource.password", postgres::getPassword)
            r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers)
            r.add("dynamodb.endpoint") { "http://${dynamo.host}:${dynamo.getMappedPort(8000)}" }
        }
    }

    @Autowired lateinit var graphql: HttpGraphQlTester

    @Test
    fun `create, start, assign, convert, results and events`() {
        graphql.document(
            """mutation { createExperiment(key: "cta", description: "Checkout button copy",
                 variants: [{name: "control", weight: 50}, {name: "buy-now", weight: 50}]) { key status } }""",
        ).execute().path("createExperiment.status").entity(String::class.java).isEqualTo("DRAFT")

        // Not running yet: no variant.
        graphql.document("""mutation { assign(key: "cta", userId: "u0") { variant } }""").execute().path("assign.variant").valueIsNull()

        graphql.document("""mutation { startExperiment(key: "cta") { status } }""").execute()
            .path("startExperiment.status").entity(String::class.java).isEqualTo("RUNNING")

        val variants = (1..40).associate { u ->
            "u$u" to graphql.document("""mutation { assign(key: "cta", userId: "u$u") { variant } }""").execute()
                .path("assign.variant").entity(String::class.java).get()
        }
        // Sticky: a second call returns the same variant and is not a first exposure.
        graphql.document("""mutation { assign(key: "cta", userId: "u1") { variant firstExposure } }""").execute()
            .path("assign.variant").entity(String::class.java).isEqualTo(variants["u1"]!!)
            .path("assign.firstExposure").entity(Boolean::class.java).isEqualTo(false)

        (1..10).forEach {
            graphql.document("""mutation { trackConversion(key: "cta", userId: "u$it", metric: "purchase") }""").execute()
                .path("trackConversion").entity(Boolean::class.java).isEqualTo(true)
        }
        // Duplicate conversion is ignored; conversion without exposure is rejected.
        graphql.document("""mutation { trackConversion(key: "cta", userId: "u1", metric: "purchase") }""").execute()
            .path("trackConversion").entity(Boolean::class.java).isEqualTo(false)
        graphql.document("""mutation { trackConversion(key: "cta", userId: "nobody", metric: "purchase") }""").execute()
            .path("trackConversion").entity(Boolean::class.java).isEqualTo(false)

        val exposures = graphql.document("""{ results(key: "cta", metric: "purchase") { variants { variant exposures conversions } comparisons { pValue } } }""")
            .execute().path("results.variants[*].exposures").entityList(Int::class.java).get()
        assertEquals(40, exposures.sum())
        val conversions = graphql.document("""{ results(key: "cta", metric: "purchase") { variants { conversions } } }""")
            .execute().path("results.variants[*].conversions").entityList(Int::class.java).get()
        assertEquals(10, conversions.sum())

        // Kafka got 40 exposure events and 10 conversion events.
        KafkaConsumer(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to "it",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ),
            StringDeserializer(), StringDeserializer(),
        ).use { c ->
            c.subscribe(listOf("experiment-events"))
            val seen = mutableListOf<String>()
            val deadline = System.currentTimeMillis() + 20_000
            while (seen.size < 50 && System.currentTimeMillis() < deadline) c.poll(Duration.ofMillis(500)).forEach { seen += it.value() }
            assertEquals(40, seen.count { it.contains("\"exposure\"") })
            assertEquals(10, seen.count { it.contains("\"conversion\"") })
        }
    }

    @Test
    fun `validation errors are reported as GraphQL errors`() {
        graphql.document("""mutation { createExperiment(key: "bad", description: "x", variants: [{name: "a", weight: 1}]) { key } }""")
            .execute().errors().satisfy { assertTrue(it.any { e -> e.message!!.contains("at least two variants") }) }
        graphql.document("""mutation { assign(key: "missing", userId: "u") { variant } }""")
            .execute().errors().satisfy { assertTrue(it.any { e -> e.message!!.contains("not found") }) }
    }
}
