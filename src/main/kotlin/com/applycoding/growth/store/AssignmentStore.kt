package com.applycoding.growth.store

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.BillingMode
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement
import software.amazon.awssdk.services.dynamodb.model.KeyType
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType
import java.net.URI
import java.time.Instant

/**
 * Sticky assignment log in DynamoDB (DynamoDB Local in dev and tests), keyed by
 * pk = "user#<id>", sk = "exp#<key>". The first write wins via a conditional put, so the
 * variant a user first saw is preserved even if weights change later.
 */
@Component
class AssignmentStore(
    @Value("\${dynamodb.endpoint}") endpoint: String,
    @Value("\${dynamodb.table:assignments}") private val table: String,
) {
    private val ddb: DynamoDbClient = DynamoDbClient.builder()
        .endpointOverride(URI.create(endpoint))
        .region(Region.EU_WEST_1)
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
        .build()

    init {
        try {
            ddb.createTable(
                CreateTableRequest.builder().tableName(table)
                    .keySchema(KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build())
                    .attributeDefinitions(AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build())
                    .billingMode(BillingMode.PAY_PER_REQUEST).build(),
            )
        } catch (_: ResourceInUseException) {
        }
    }

    /** Returns the stored variant, writing [variant] if none exists yet. */
    fun getOrPut(userId: String, experimentKey: String, variant: String): Pair<String, Boolean> {
        val key = mapOf("pk" to s("user#$userId"), "sk" to s("exp#$experimentKey"))
        return try {
            ddb.putItem {
                it.tableName(table).item(key + mapOf("variant" to s(variant), "assigned_at" to s(Instant.now().toString())))
                    .conditionExpression("attribute_not_exists(pk)")
            }
            variant to true
        } catch (_: ConditionalCheckFailedException) {
            val existing = ddb.getItem { it.tableName(table).key(key).consistentRead(true) }.item()["variant"]!!.s()
            existing to false
        }
    }

    fun get(userId: String, experimentKey: String): String? =
        ddb.getItem { it.tableName(table).key(mapOf("pk" to s("user#$userId"), "sk" to s("exp#$experimentKey"))).consistentRead(true) }
            .item()["variant"]?.s()

    private fun s(v: String) = AttributeValue.builder().s(v).build()
}
