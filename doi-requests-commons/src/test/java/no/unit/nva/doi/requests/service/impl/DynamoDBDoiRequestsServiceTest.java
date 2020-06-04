package no.unit.nva.doi.requests.service.impl;

import static no.unit.nva.doi.requests.service.impl.DynamoDBDoiRequestsService.INDEX_NAME;
import static no.unit.nva.doi.requests.service.impl.DynamoDBDoiRequestsService.TABLE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import no.unit.nva.doi.requests.util.DoiRequestsDynamoDBLocal;
import no.unit.nva.doi.requests.util.PublicationGenerator;
import no.unit.nva.model.DoiRequestStatus;
import no.unit.nva.model.Publication;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.utils.Environment;
import nva.commons.utils.JsonUtils;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;

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
            JsonUtils.objectMapper,
            db.getTable(),
            db.getIndex()
        );
    }

    @Test
    public void constructorCreatesInstanceOnValidInput() throws Exception {
        when(environment.readEnv(TABLE_NAME)).thenReturn(TABLE_NAME);
        when(environment.readEnv(INDEX_NAME)).thenReturn(INDEX_NAME);
        DynamoDBDoiRequestsService dynamoDBDoiRequestsService =
            new DynamoDBDoiRequestsService(client, JsonUtils.objectMapper, environment);
        assertNotNull(dynamoDBDoiRequestsService);
    }

    @Test
    public void findDoiRequestsByStatusAndOwnerReturnsEmptyListWhenNoDoiRequests() throws Exception {
        List<DoiRequestSummary> doiRequestSummaries = service.findDoiRequestsByStatusAndOwner(
            PublicationGenerator.PUBLISHER_ID,
            DoiRequestStatus.REQUESTED, PublicationGenerator.OWNER);

        assertTrue(doiRequestSummaries.isEmpty());
    }

    @Test
    public void findDoiRequestsByStatusAndOwnerReturnsAllResultsWhenUserOwnsAllDoiRequests() throws Exception {
        insertPublication(PublicationGenerator.getPublication());
        insertPublication(PublicationGenerator.getPublication());
        insertPublication(PublicationGenerator.getPublication());

        List<DoiRequestSummary> doiRequestSummaries = service.findDoiRequestsByStatusAndOwner(
            PublicationGenerator.PUBLISHER_ID,
            DoiRequestStatus.REQUESTED, PublicationGenerator.OWNER);

        assertEquals(3, doiRequestSummaries.size());
    }

    @Test
    public void findDoiRequestsByStatusAndOwnerReturnsAllButOneResultsWhenUserOwnsAllButOneDoiRequests()
        throws Exception {
        insertPublication(PublicationGenerator.getPublication());
        insertPublication(PublicationGenerator.getPublication());

        Publication publicationOwnedByAnother = PublicationGenerator.getPublication();
        publicationOwnedByAnother.setOwner("another_owner");
        insertPublication(publicationOwnedByAnother);

        List<DoiRequestSummary> doiRequestSummaries = service.findDoiRequestsByStatusAndOwner(
            PublicationGenerator.PUBLISHER_ID,
            DoiRequestStatus.REQUESTED, PublicationGenerator.OWNER);

        assertEquals(2, doiRequestSummaries.size());
    }

    @Test
    public void findByDoiRequestStatusThrowsExceptionOnIndexError() throws ApiGatewayException {
        Index index = mock(Index.class);
        when(index.query(anyString(), any(), any(RangeKeyCondition.class))).thenThrow(RuntimeException.class);
        DynamoDBDoiRequestsService failingService = new DynamoDBDoiRequestsService(
            JsonUtils.objectMapper, db.getTable(), index);
        DynamoDBException exception = assertThrows(DynamoDBException.class,
            () -> failingService.findDoiRequestsByStatus(PublicationGenerator.PUBLISHER_ID,
                DoiRequestStatus.REQUESTED));

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
            Item.fromJSON(JsonUtils.objectMapper.writeValueAsString(publication))
        );
    }
}
