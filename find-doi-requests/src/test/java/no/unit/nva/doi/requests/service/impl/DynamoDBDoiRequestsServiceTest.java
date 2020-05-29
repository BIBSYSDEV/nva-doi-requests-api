package no.unit.nva.doi.requests.service.impl;

import static no.unit.nva.doi.requests.service.impl.DynamoDBDoiRequestsService.INDEX_NAME;
import static no.unit.nva.doi.requests.service.impl.DynamoDBDoiRequestsService.TABLE_NAME;
import static no.unit.nva.model.DoiRequestStatus.REQUESTED;
import static nva.commons.utils.JsonUtils.objectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static util.PublicationGenerator.OWNER;
import static util.PublicationGenerator.PUBLISHER_ID;
import static util.PublicationGenerator.getPublication;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.document.Index;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.RangeKeyCondition;
import com.amazonaws.services.dynamodbv2.local.embedded.DynamoDBEmbedded;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Optional;
import no.unit.nva.doi.requests.exception.DynamoDBException;
import no.unit.nva.doi.requests.model.DoiRequestSummary;
import no.unit.nva.model.Publication;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.utils.Environment;
import org.junit.Rule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;
import org.mockito.Mockito;
import util.DoiRequestsDynamoDBLocal;

@EnableRuleMigrationSupport
public class DynamoDBDoiRequestsServiceTest {

    public static final String GARBAGE_JSON = "ʕ•ᴥ•ʔ";

    @Rule
    public DoiRequestsDynamoDBLocal db =  new DoiRequestsDynamoDBLocal();

    private DynamoDBDoiRequestsService service;
    private Environment environment;
    private AmazonDynamoDB client;

    /**
     * Set up environment.
     */
    @BeforeEach
    public void setUp() {
        client = DynamoDBEmbedded.create().amazonDynamoDB();
        environment = mock(Environment.class);
        service = new DynamoDBDoiRequestsService(
            objectMapper,
            db.getTable(),
            db.getIndex()
        );
    }

    @Test
    public void constructorCreatesInstanceOnValidInput() throws Exception {
        when(environment.readEnv(TABLE_NAME)).thenReturn(TABLE_NAME);
        when(environment.readEnv(INDEX_NAME)).thenReturn(INDEX_NAME);
        DynamoDBDoiRequestsService dynamoDBDoiRequestsService =
            new DynamoDBDoiRequestsService(client, objectMapper, environment);
        assertNotNull(dynamoDBDoiRequestsService);
    }

    @Test
    public void findDoiRequestsByStatusAndOwnerReturnsEmptyListWhenNoDoiRequests() throws Exception {
        List<DoiRequestSummary> doiRequestSummaries = service.findDoiRequestsByStatusAndOwner(PUBLISHER_ID,
            REQUESTED, OWNER);

        assertTrue(doiRequestSummaries.isEmpty());
    }

    @Test
    public void findDoiRequestsByStatusAndOwnerReturnsAllResultsWhenUserOwnsAllDoiRequests() throws Exception {
        insertPublication(getPublication());
        insertPublication(getPublication());
        insertPublication(getPublication());

        List<DoiRequestSummary> doiRequestSummaries = service.findDoiRequestsByStatusAndOwner(PUBLISHER_ID,
            REQUESTED, OWNER);

        assertEquals(3, doiRequestSummaries.size());
    }

    @Test
    public void findDoiRequestsByStatusAndOwnerReturnsAllButOneResultsWhenUserOwnsAllButOneDoiRequests()
        throws Exception {
        insertPublication(getPublication());
        insertPublication(getPublication());

        Publication publicationOwnedByAnother = getPublication();
        publicationOwnedByAnother.setOwner("another_owner");
        insertPublication(publicationOwnedByAnother);

        List<DoiRequestSummary> doiRequestSummaries = service.findDoiRequestsByStatusAndOwner(PUBLISHER_ID,
            REQUESTED, OWNER);

        assertEquals(2, doiRequestSummaries.size());
    }

    @Test
    public void findByDoiRequestStatusThrowsExceptionOnIndexError() throws ApiGatewayException {
        Index index = mock(Index.class);
        when(index.query(anyString(), any(), any(RangeKeyCondition.class))).thenThrow(RuntimeException.class);
        DynamoDBDoiRequestsService failingService = new DynamoDBDoiRequestsService(objectMapper, db.getTable(), index);
        DynamoDBException exception = assertThrows(DynamoDBException.class,
            () -> failingService.findDoiRequestsByStatus(PUBLISHER_ID, REQUESTED));

        assertEquals(DynamoDBDoiRequestsService.ERROR_READING_FROM_TABLE, exception.getMessage());
    }

    @Test
    public void toDoiDoiRequestSummaryThrowsExceptionOnInvalidJsonItem() {
        Item item = mock(Item.class);
        when(item.toJSON()).thenReturn(GARBAGE_JSON);
        Optional<DoiRequestSummary> doiRequestSummary = service.toDoiRequestSummary(item);
        assertTrue(doiRequestSummary.isEmpty());
    }

    private void insertPublication(Publication publication) throws JsonProcessingException {
        db.getTable().putItem(
            Item.fromJSON(objectMapper.writeValueAsString(publication))
        );
    }
}
