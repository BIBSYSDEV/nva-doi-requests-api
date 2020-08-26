package no.unit.nva.doi.requests.service.impl;

import static no.unit.nva.doi.requests.contants.ServiceConstants.PUBLICATIONS_TABLE_NAME_ENV_VARIABLE;
import static no.unit.nva.doi.requests.util.MockEnvironment.mockEnvironment;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.amazonaws.services.dynamodbv2.document.Index;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.RangeKeyCondition;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import no.unit.nva.doi.requests.exception.DynamoDBException;
import no.unit.nva.doi.requests.model.DoiRequestSummary;
import no.unit.nva.doi.requests.service.DoiRequestsService;
import no.unit.nva.doi.requests.util.DoiRequestsDynamoDBLocal;
import no.unit.nva.doi.requests.util.PublicationGenerator;
import no.unit.nva.model.DoiRequest;
import no.unit.nva.model.DoiRequestStatus;
import no.unit.nva.model.Publication;
import nva.commons.exceptions.commonexceptions.ConflictException;
import nva.commons.exceptions.commonexceptions.NotFoundException;
import nva.commons.utils.Environment;
import nva.commons.utils.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

public class DynamoDBDoiRequestsServiceTest extends DoiRequestsDynamoDBLocal {

    public static final String GARBAGE_JSON = "ʕ•ᴥ•ʔ";
    private final Instant mockedNow = Instant.now();
    private DynamoDBDoiRequestsService service;
    private Environment environment;

    public DynamoDBDoiRequestsServiceTest() {

    }

    @BeforeEach
    public void setUp() {
        initializeDatabase();
        environment = mockEnvironment();
        Clock clock = Clock.fixed(mockedNow, ZoneId.systemDefault());
        service = new DynamoDBDoiRequestsService(client, JsonUtils.objectMapper, environment, clock);
    }

    @Test
    public void constructorCreatesInstanceOnValidInput() {
        DynamoDBDoiRequestsService dynamoDBDoiRequestsService =
            new DynamoDBDoiRequestsService(client, JsonUtils.objectMapper, mockEnvironment());
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
        insertPublication(PublicationGenerator.getPublicationWithDoiRequest());
        insertPublication(PublicationGenerator.getPublicationWithDoiRequest());
        insertPublication(PublicationGenerator.getPublicationWithDoiRequest());

        List<DoiRequestSummary> doiRequestSummaries = service.findDoiRequestsByStatusAndOwner(
            PublicationGenerator.PUBLISHER_ID,
            DoiRequestStatus.REQUESTED, PublicationGenerator.OWNER);

        assertEquals(3, doiRequestSummaries.size());
    }

    @Test
    public void findDoiRequestsByStatusAndOwnerReturnsAllButOneResultsWhenUserOwnsAllButOneDoiRequests()
        throws Exception {
        insertPublication(PublicationGenerator.getPublicationWithDoiRequest());
        insertPublication(PublicationGenerator.getPublicationWithDoiRequest());

        Publication publicationOwnedByAnother = PublicationGenerator.getPublicationWithDoiRequest();
        publicationOwnedByAnother.setOwner("another_owner");
        insertPublication(publicationOwnedByAnother);

        List<DoiRequestSummary> doiRequestSummaries = service.findDoiRequestsByStatusAndOwner(
            PublicationGenerator.PUBLISHER_ID,
            DoiRequestStatus.REQUESTED, PublicationGenerator.OWNER);

        assertEquals(2, doiRequestSummaries.size());
    }

    @Test
    public void findByDoiRequestStatusThrowsExceptionOnIndexError() {
        Index index = mock(Index.class);
        when(index.query(anyString(), any(), any(RangeKeyCondition.class))).thenThrow(RuntimeException.class);

        DynamoDBDoiRequestsService failingService = new DynamoDBDoiRequestsService(
            JsonUtils.objectMapper, getTable(), index);
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

    @Test
    public void fetchDoiRequestByPublicationIdReturnsDoiRequestSummary()
        throws JsonProcessingException, NotFoundException {
        Publication publication = PublicationGenerator.getPublicationWithDoiRequest();
        insertPublication(publication);
        Optional<DoiRequestSummary> result = service.fetchDoiRequest(publication.getIdentifier());
        assertThat(result.isPresent(), is(true));
    }

    @Test
    public void createDoiRequestAddsDoiRequestToPublication()
        throws JsonProcessingException, ConflictException, NotFoundException {
        Publication publication = PublicationGenerator.getPublicationWithoutDoiRequest();
        insertPublication(publication);

        DoiRequestSummary expectedDoiRequestSummary = expectedDoiRequestSummary(publication);

        service.createDoiRequest(publication.getIdentifier());
        DoiRequestSummary actualDoiRequestSummary = service
            .fetchDoiRequest(publication.getIdentifier())
            .orElseThrow();

        assertThat(actualDoiRequestSummary, is(equalTo(expectedDoiRequestSummary)));
    }

    @Test
    public void createDoiRequestThrowsConflictExceptionWhenCreatingDoiRequestForPublicationWithDoiRequest()
        throws JsonProcessingException {
        Publication publication = PublicationGenerator.getPublicationWithDoiRequest();
        insertPublication(publication);

        Executable action = () -> service.createDoiRequest(publication.getIdentifier());
        ConflictException exception = assertThrows(ConflictException.class, action);

        assertThat(exception.getMessage(), containsString(DoiRequestsService.DOI_ALREADY_EXISTS_ERROR));
    }

    @Test
    public void createDoiRequestThrowsNotFoundExceptionWhenCreatingDoiRequestForNonExistingPublication() {
        Publication publication = PublicationGenerator.getPublicationWithDoiRequest();

        Executable action = () -> service.createDoiRequest(publication.getIdentifier());
        NotFoundException exception = assertThrows(NotFoundException.class, action);

        assertThat(exception.getMessage(), containsString(publication.getIdentifier().toString()));
    }

    @Test
    public void createDoiRequestThrowsRuntimeExceptionOnSerializationError() throws JsonProcessingException {

        final String exceptionMessage = "This is the exception message";
        ObjectMapper objectMapper = spy(JsonUtils.objectMapper);
        when(objectMapper.writeValueAsString(any(Publication.class)))
            .thenThrow(new RuntimeException(exceptionMessage));

        DynamoDBDoiRequestsService serviceWithFailingJsonObjectMapper =
            new DynamoDBDoiRequestsService(client, objectMapper, environment);

        Publication publication = PublicationGenerator.getPublicationWithoutDoiRequest();
        insertPublication(publication);

        Executable action = () -> serviceWithFailingJsonObjectMapper.createDoiRequest(publication.getIdentifier());
        RuntimeException exception = assertThrows(RuntimeException.class, action);

        assertThat(exception.getMessage(), containsString(exceptionMessage));
    }

    private DoiRequestSummary expectedDoiRequestSummary(Publication publication) {
        DoiRequest expectedDoiRequest = new DoiRequest.Builder()
            .withDate(mockedNow)
            .withStatus(DoiRequestStatus.REQUESTED)
            .build();
        DoiRequestSummary expectedDoiRequestSummary = new DoiRequestSummary(
            publication.getIdentifier(), publication.getOwner(), expectedDoiRequest,
            publication.getEntityDescription());
        return expectedDoiRequestSummary;
    }

    private Table getTable() {
        return getTable(environment.readEnv(PUBLICATIONS_TABLE_NAME_ENV_VARIABLE));
    }

    private void insertPublication(Publication publication) throws JsonProcessingException {
        String tableName = environment.readEnv(PUBLICATIONS_TABLE_NAME_ENV_VARIABLE);
        super.insertPublication(tableName, publication);
    }
}
