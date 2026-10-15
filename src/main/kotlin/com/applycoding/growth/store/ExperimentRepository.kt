package com.applycoding.growth.store

import com.applycoding.growth.domain.Experiment
import com.applycoding.growth.domain.Variant
import com.applycoding.growth.domain.VariantResult
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet

/** Experiment definitions and aggregated metrics live in Postgres. */
@Repository
class ExperimentRepository(private val jdbc: JdbcTemplate, private val mapper: ObjectMapper) {
    private fun map(rs: ResultSet) = Experiment(
        key = rs.getString("key"),
        description = rs.getString("description"),
        salt = rs.getString("salt"),
        status = Experiment.Status.valueOf(rs.getString("status")),
        variants = mapper.readValue<List<Variant>>(rs.getString("variants")),
        trafficPercent = rs.getInt("traffic_percent"),
    )

    fun create(e: Experiment): Experiment {
        try {
            jdbc.update(
                "INSERT INTO experiments(key, description, salt, status, variants, traffic_percent) VALUES (?,?,?,?,?::jsonb,?)",
                e.key, e.description, e.salt, e.status.name, mapper.writeValueAsString(e.variants), e.trafficPercent,
            )
        } catch (ex: DuplicateKeyException) {
            throw IllegalArgumentException("experiment ${e.key} already exists")
        }
        return e
    }

    fun find(key: String): Experiment? = jdbc.query("SELECT * FROM experiments WHERE key = ?", { rs, _ -> map(rs) }, key).firstOrNull()

    fun all(): List<Experiment> = jdbc.query("SELECT * FROM experiments ORDER BY created_at") { rs, _ -> map(rs) }

    fun setStatus(key: String, status: Experiment.Status): Experiment? {
        jdbc.update("UPDATE experiments SET status = ? WHERE key = ?", status.name, key)
        return find(key)
    }

    /** Counts unique exposed and converted users per variant. */
    fun recordExposure(key: String, variant: String, userId: String) {
        jdbc.update("INSERT INTO exposures(experiment_key, variant, user_id) VALUES (?,?,?) ON CONFLICT DO NOTHING", key, variant, userId)
    }

    fun recordConversion(key: String, userId: String, metric: String): Boolean =
        jdbc.update(
            """INSERT INTO conversions(experiment_key, variant, user_id, metric)
               SELECT experiment_key, variant, user_id, ? FROM exposures WHERE experiment_key = ? AND user_id = ?
               ON CONFLICT DO NOTHING""",
            metric, key, userId,
        ) > 0

    fun results(key: String, metric: String): List<VariantResult> = jdbc.query(
        """SELECT e.variant, COUNT(*) AS exposures, COUNT(c.user_id) AS conversions
           FROM exposures e LEFT JOIN conversions c
             ON c.experiment_key = e.experiment_key AND c.user_id = e.user_id AND c.metric = ?
           WHERE e.experiment_key = ? GROUP BY e.variant ORDER BY e.variant""",
        { rs, _ -> VariantResult(rs.getString("variant"), rs.getLong("exposures"), rs.getLong("conversions")) },
        metric, key,
    )
}
