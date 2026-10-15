package com.applycoding.growth.api

import com.applycoding.growth.domain.Assignment
import com.applycoding.growth.domain.Experiment
import com.applycoding.growth.domain.ExperimentService
import com.applycoding.growth.domain.Results
import com.applycoding.growth.domain.Variant
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.execution.ErrorType
import org.springframework.stereotype.Controller

data class VariantInput(val name: String, val weight: Int)

@Controller
class GraphqlController(private val svc: ExperimentService) {
    @QueryMapping fun experiments(): List<Experiment> = svc.all()
    @QueryMapping fun experiment(@Argument key: String): Experiment? = svc.get(key)
    @QueryMapping fun results(@Argument key: String, @Argument metric: String): Results = svc.results(key, metric)

    @MutationMapping
    fun createExperiment(@Argument key: String, @Argument description: String, @Argument variants: List<VariantInput>, @Argument trafficPercent: Int?): Experiment =
        svc.create(key, description, variants.map { Variant(it.name, it.weight) }, trafficPercent ?: 100)

    @MutationMapping fun startExperiment(@Argument key: String): Experiment = svc.start(key)
    @MutationMapping fun stopExperiment(@Argument key: String): Experiment = svc.stop(key)
    @MutationMapping fun assign(@Argument key: String, @Argument userId: String): Assignment = svc.assign(key, userId)
    @MutationMapping fun trackConversion(@Argument key: String, @Argument userId: String, @Argument metric: String): Boolean = svc.convert(key, userId, metric)

    @GraphQlExceptionHandler
    fun badInput(ex: IllegalArgumentException, env: DataFetchingEnvironment): GraphQLError =
        GraphqlErrorBuilder.newError(env).errorType(ErrorType.BAD_REQUEST).message(ex.message ?: "bad request").build()

    @GraphQlExceptionHandler
    fun notFound(ex: NoSuchElementException, env: DataFetchingEnvironment): GraphQLError =
        GraphqlErrorBuilder.newError(env).errorType(ErrorType.NOT_FOUND).message(ex.message ?: "not found").build()
}
