package no.unit.nva.doi.requests.util;

import static no.unit.nva.doi.requests.contants.DatabaseConstants.DOI_REQUEST_FIELD_NAME;
import static no.unit.nva.doi.requests.contants.DatabaseConstants.DOI_REQUEST_INDEX_HASH_KEY;
import static no.unit.nva.doi.requests.contants.DatabaseConstants.DOI_REQUEST_INDEX_SORT_KEY;
import static no.unit.nva.doi.requests.contants.DatabaseConstants.TABLE_HASH_KEY;
import static no.unit.nva.doi.requests.contants.DatabaseConstants.TABLE_SORT_KEY;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.local.embedded.DynamoDBEmbedded;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.GlobalSecondaryIndex;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.Projection;
import com.amazonaws.services.dynamodbv2.model.ProjectionType;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import no.unit.nva.model.Publication;
import nva.commons.utils.JsonUtils;
import org.junit.jupiter.api.AfterEach;

public class DoiRequestsDynamoDBLocal {

    public static final String CREATED_DATE = "createdDate";
    public static final String ENTITY_DESCRIPTION = "entityDescription";
    public static final String STATUS = "status";
    public static final String OWNER = "owner";

    public static final String NVA_RESOURCES_TABLE_NAME = "nva_resources";
    public static final String BY_DOI_REQUEST_INDEX_NAME = "ByDoiRequest";

    protected AmazonDynamoDB client;

    protected Table getTable(String tableName) {
        return new DynamoDB(client).getTable(tableName);
    }

    protected void initializeDatabase() {
        client = DynamoDBEmbedded.create().amazonDynamoDB();
        createPublicationsTable(client);
    }

    @AfterEach
    protected void after() {
        if (client != null) {
            client.shutdown();
        }
    }

    protected void createPublicationsTable(AmazonDynamoDB ddb) {
        List<AttributeDefinition> attributeDefinitions = tableAndIndexKeyFields();
        List<KeySchemaElement> keySchema = tableKey();
        List<KeySchemaElement> byDoiRequestKeySchema = indexKey();
        Projection byDoiRequestProjection = byDoiRequestTableProjection();

        List<GlobalSecondaryIndex> globalSecondaryIndexes = byDoiRequestSecondaryIndex(byDoiRequestKeySchema,
            byDoiRequestProjection);

        CreateTableRequest createTableRequest =
            new CreateTableRequest()
                .withTableName(NVA_RESOURCES_TABLE_NAME)
                .withAttributeDefinitions(attributeDefinitions)
                .withKeySchema(keySchema)
                .withGlobalSecondaryIndexes(globalSecondaryIndexes)
                .withBillingMode(BillingMode.PAY_PER_REQUEST);

        ddb.createTable(createTableRequest);
    }

    protected void insertPublication(String tableName, Publication publication) throws JsonProcessingException {
        getTable(tableName).putItem(
            Item.fromJSON(JsonUtils.objectMapper.writeValueAsString(publication))
        );
    }

    private List<GlobalSecondaryIndex> byDoiRequestSecondaryIndex(List<KeySchemaElement> byDoiRequestKeySchema,
                                                                  Projection byDoiRequestProjection) {
        return Collections.singletonList(
            new GlobalSecondaryIndex()
                .withIndexName(BY_DOI_REQUEST_INDEX_NAME)
                .withKeySchema(byDoiRequestKeySchema)
                .withProjection(byDoiRequestProjection)
        );
    }

    private Projection byDoiRequestTableProjection() {
        return new Projection()
            .withProjectionType(ProjectionType.INCLUDE)
            .withNonKeyAttributes(TABLE_HASH_KEY,
                CREATED_DATE,
                TABLE_SORT_KEY,
                ENTITY_DESCRIPTION,
                DOI_REQUEST_FIELD_NAME,
                STATUS,
                OWNER);
    }

    private List<KeySchemaElement> indexKey() {
        return Arrays.asList(
            new KeySchemaElement(DOI_REQUEST_INDEX_HASH_KEY, KeyType.HASH),
            new KeySchemaElement(DOI_REQUEST_INDEX_SORT_KEY, KeyType.RANGE)
        );
    }

    private List<KeySchemaElement> tableKey() {
        return Arrays.asList(
            new KeySchemaElement(TABLE_HASH_KEY, KeyType.HASH),
            new KeySchemaElement(TABLE_SORT_KEY, KeyType.RANGE)
        );
    }

    private List<AttributeDefinition> tableAndIndexKeyFields() {
        return Arrays.asList(
            new AttributeDefinition(TABLE_HASH_KEY, ScalarAttributeType.S),
            new AttributeDefinition(TABLE_SORT_KEY, ScalarAttributeType.S),
            new AttributeDefinition(DOI_REQUEST_INDEX_HASH_KEY, ScalarAttributeType.S),
            new AttributeDefinition(DOI_REQUEST_INDEX_SORT_KEY, ScalarAttributeType.S)
        );
    }
}
