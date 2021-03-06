package no.unit.nva.doi.requests.util;

import static no.unit.nva.doi.requests.contants.DatabaseConstants.DOI_REQUEST_INDEX_HASH_KEY;
import static no.unit.nva.doi.requests.contants.DatabaseConstants.DOI_REQUEST_INDEX_SORT_KEY;
import static no.unit.nva.doi.requests.contants.DatabaseConstants.TABLE_HASH_KEY;
import static no.unit.nva.doi.requests.contants.DatabaseConstants.TABLE_SORT_KEY;
import static nva.commons.utils.JsonUtils.objectMapper;
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
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import no.unit.nva.model.Publication;
import nva.commons.utils.JsonUtils;
import org.junit.jupiter.api.AfterEach;

public class DoiRequestsDynamoDBLocal {

    public static final String NVA_RESOURCES_TABLE_NAME = "nva_resources";
    public static final String BY_DOI_REQUEST_INDEX_NAME = "ByDoiRequest";
    public static final Pattern REMOVE_STARTING_AND_ENDING_QUOTES = Pattern.compile("^\"(.*)\"$");
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
            Item.fromJSON(objectMapper.writeValueAsString(publication))
        );
    }

    protected Publication getPublication(String tableName, UUID publicationId, Instant modifiedDate)
        throws IOException {
        String modifiedDateString = serializeForQueryValue(modifiedDate);
        Item item = getTable(tableName).getItem(
            TABLE_HASH_KEY, publicationId.toString(),
            TABLE_SORT_KEY, modifiedDateString
        );
        return objectMapper.readValue(item.toJSON(), Publication.class);
    }

    private <T> String serializeForQueryValue(T serializable) throws JsonProcessingException {
        return removeDoubleQuotesFromString(serializable);
    }

    private <T> String removeDoubleQuotesFromString(T serializable) throws JsonProcessingException {
        String serialized = JsonUtils.objectMapper.writeValueAsString(serializable);
        Matcher matcher = REMOVE_STARTING_AND_ENDING_QUOTES.matcher(serialized);
        if (matcher.find()) {
            serialized = matcher.group(1);
        }
        return serialized;
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
        return new Projection().withProjectionType(ProjectionType.ALL);
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
