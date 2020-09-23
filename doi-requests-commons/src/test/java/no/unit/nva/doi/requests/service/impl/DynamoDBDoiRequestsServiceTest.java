package no.unit.nva.doi.requests.service.impl;

import static no.unit.nva.doi.requests.contants.ServiceConstants.PUBLICATIONS_TABLE_NAME_ENV_VARIABLE;
import static no.unit.nva.doi.requests.util.MockEnvironment.mockEnvironment;
import static no.unit.nva.model.DoiRequestStatus.APPROVED;
import static no.unit.nva.model.DoiRequestStatus.REQUESTED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
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
import com.amazonaws.services.dynamodbv2.document.RangeKeyCondition;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import no.unit.nva.doi.requests.api.model.requests.CreateDoiRequest;
import no.unit.nva.doi.requests.api.model.responses.DoiRequestSummary;
import no.unit.nva.doi.requests.exception.DynamoDBException;
import no.unit.nva.doi.requests.service.DoiRequestsService;
import no.unit.nva.doi.requests.util.DoiRequestsDynamoDBLocal;
import no.unit.nva.doi.requests.util.PublicationGenerator;
import no.unit.nva.model.DoiRequest;
import no.unit.nva.model.DoiRequestMessage;
import no.unit.nva.model.DoiRequestStatus;
import no.unit.nva.model.Publication;
import nva.commons.exceptions.ForbiddenException;
import nva.commons.exceptions.commonexceptions.ConflictException;
import nva.commons.exceptions.commonexceptions.NotFoundException;
import nva.commons.utils.Environment;
import nva.commons.utils.JsonUtils;
import nva.commons.utils.SingletonCollector;
import nva.commons.utils.log.LogUtils;
import nva.commons.utils.log.TestAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

public class DynamoDBDoiRequestsServiceTest extends DoiRequestsDynamoDBLocal {

    public static final String DEFAULT_MESSAGE = "defaultMessage";
    public static final String INVALID_USERNAME = "invalidUsername";
    public static final DoiRequestStatus INITIAL_DOI_REQUEST_STATUS = REQUESTED;
    public static final DoiRequestStatus NEW_DOI_REQUEST_STATUS = APPROVED;
    private final Instant mockedNow = Instant.now();
    private DynamoDBDoiRequestsService service;
    private Environment environment;
    private Clock clock;

    public DynamoDBDoiRequestsServiceTest() {

    }

    @BeforeEach
    public void setUp() {
        initializeDatabase();
        environment = mockEnvironment();
        clock = Clock.fixed(mockedNow, ZoneId.systemDefault());
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
            PublicationGenerator.PUBLISHER_ID, REQUESTED, PublicationGenerator.OWNER);

        assertTrue(doiRequestSummaries.isEmpty());
    }

    @Test
    public void findDoiRequestsByStatusAndOwnerReturnsAllResultsWhenUserOwnsAllDoiRequests() throws Exception {
        insertPublication(PublicationGenerator.getPublicationWithDoiRequest());
        insertPublication(PublicationGenerator.getPublicationWithDoiRequest());
        insertPublication(PublicationGenerator.getPublicationWithDoiRequest());

        List<DoiRequestSummary> doiRequestSummaries = service.findDoiRequestsByStatusAndOwner(
            PublicationGenerator.PUBLISHER_ID, REQUESTED, PublicationGenerator.OWNER);

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
            PublicationGenerator.PUBLISHER_ID, REQUESTED, PublicationGenerator.OWNER);

        assertEquals(2, doiRequestSummaries.size());
    }

    @Test
    public void findByDoiRequestStatusThrowsExceptionOnIndexError() {
        Index index = mock(Index.class);
        when(index.query(anyString(), any(), any(RangeKeyCondition.class))).thenThrow(RuntimeException.class);

        DynamoDBDoiRequestsService failingService = new DynamoDBDoiRequestsService(
            JsonUtils.objectMapper, getTable(), index);
        DynamoDBException exception = assertThrows(DynamoDBException.class,
            () -> failingService.findDoiRequestsByStatus(PublicationGenerator.PUBLISHER_ID, REQUESTED));

        assertEquals(DynamoDBDoiRequestsService.ERROR_READING_FROM_TABLE, exception.getMessage());
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
    public void fetchDoiRequestByPublicationThrowsExceptionWhenIndexSearchFails() {
        Publication publication = PublicationGenerator.getPublicationWithDoiRequest();

        var expectedMessage = "Index search failed";
        var table = mock(Table.class);
        var index = indexThrowingException(expectedMessage);

        service = new DynamoDBDoiRequestsService(JsonUtils.objectMapper, table, index);
        Executable indexSearchFailure = () -> service.findDoiRequestsByStatus(
            publication.getPublisher().getId(), REQUESTED);
        DynamoDBException exception = assertThrows(DynamoDBException.class, indexSearchFailure);

        assertThat(exception.getCause().getMessage(), is(equalTo(expectedMessage)));
    }

    @Test
    public void createDoiRequestAddsDoiRequestToPublication()
        throws JsonProcessingException, ConflictException, NotFoundException, ForbiddenException {
        Publication publication = PublicationGenerator.getPublicationWithoutDoiRequest();
        insertPublication(publication);

        DoiRequestSummary expectedDoiRequestSummary = expectedDoiRequestSummary(publication);

        service.createDoiRequest(createDoiRequestWithMessage(publication), publication.getOwner());
        DoiRequestSummary actualDoiRequestSummary = service
            .fetchDoiRequest(publication.getIdentifier())
            .orElseThrow();

        assertThat(actualDoiRequestSummary, is(equalTo(expectedDoiRequestSummary)));
    }

    @Test
    public void createDoiRequestWithoutMessageDoesNotCreateEmptyMessage()
        throws IOException, ConflictException, NotFoundException, ForbiddenException {
        Publication publication = PublicationGenerator.getPublicationWithoutDoiRequest(clock);
        insertPublication(publication);

        var doiRequestWithoutMessage = createDoiRequestWithoutMessage(publication);
        service.createDoiRequest(doiRequestWithoutMessage, publication.getOwner());

        DoiRequest doiRequest = getPublicationDirectlyFromTable(publication.getIdentifier()).getDoiRequest();

        assertThat(doiRequest.getMessages(), is(empty()));
    }

    @Test
    public void createDoiRequestAddsMessageToPublicationsDoiRequest()
        throws IOException, ConflictException, NotFoundException, ForbiddenException {
        Publication publication = PublicationGenerator.getPublicationWithoutDoiRequest(clock);
        insertPublication(publication);

        CreateDoiRequest createDoiRequest = createDoiRequestWithMessage(publication);
        service.createDoiRequest(createDoiRequest, publication.getOwner());

        Publication updatedPublication = getPublicationDirectlyFromTable(publication.getIdentifier());

        DoiRequestMessage actualDoiRequestMessage = extractDoiRequestMessageFromPublication(updatedPublication);

        DoiRequestMessage expectedMessage = expectedDoiRequestMessage(publication);
        assertThat(actualDoiRequestMessage, is(equalTo(expectedMessage)));
    }

    @Test
    public void createDoiRequestThrowsConflictExceptionWhenCreatingDoiRequestForPublicationWithDoiRequest()
        throws JsonProcessingException {
        Publication publication = PublicationGenerator.getPublicationWithDoiRequest();
        insertPublication(publication);

        Executable action = () -> service.createDoiRequest(createDoiRequestWithMessage(publication),
            publication.getOwner());
        ConflictException exception = assertThrows(ConflictException.class, action);

        assertThat(exception.getMessage(), containsString(DoiRequestsService.DOI_ALREADY_EXISTS_ERROR));
    }

    @Test
    public void createDoiRequestThrowsNotFoundExceptionWhenCreatingDoiRequestForNonExistingPublication() {
        Publication publication = PublicationGenerator.getPublicationWithDoiRequest();

        Executable action = () -> service.createDoiRequest(createDoiRequestWithMessage(publication),
            publication.getOwner());
        NotFoundException exception = assertThrows(NotFoundException.class, action);

        assertThat(exception.getMessage(), containsString(publication.getIdentifier().toString()));
    }

    @Test
    public void createDoiRequestThrowsForbiddenExceptionWhenCreatingDoiRequestForNonExistingPublication()
        throws JsonProcessingException {
        TestAppender testAppender = LogUtils.getTestingAppender(DynamoDBDoiRequestsService.class);
        Publication publication = PublicationGenerator.getPublicationWithDoiRequest();
        insertPublication(publication);

        Executable action = () -> service.createDoiRequest(createDoiRequestWithMessage(publication), INVALID_USERNAME);
        assertThrows(ForbiddenException.class, action);

        assertThatServiceLogsCauseOfForbiddenError(testAppender, publication);
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

        Executable action = () -> serviceWithFailingJsonObjectMapper.createDoiRequest(
            createDoiRequestWithMessage(publication),
            publication.getOwner());
        RuntimeException exception = assertThrows(RuntimeException.class, action);

        assertThat(exception.getMessage(), containsString(exceptionMessage));
    }

    @Test
    public void updateDoiRequestPersistsUpdatedDoiRequestWhenInputIsValidAndUserisAuthorized()
        throws NotFoundException, ForbiddenException, IOException {

        Publication publication = PublicationGenerator.getPublicationWithDoiRequest(clock);
        assertThat(publication.getDoiRequest().getStatus(), is(equalTo(INITIAL_DOI_REQUEST_STATUS)));
        insertPublication(publication);

        service.updateDoiRequest(publication.getIdentifier(), NEW_DOI_REQUEST_STATUS, publication.getOwner());

        DoiRequestSummary doiRequestSummary = service.fetchDoiRequest(publication.getIdentifier()).orElseThrow();
        DoiRequest actualDoiRequest = doiRequestSummary.getDoiRequest();

        assertThat(actualDoiRequest.getStatus(), is(equalTo(NEW_DOI_REQUEST_STATUS)));

        assertThatModifiedDateIsUpdated(publication, doiRequestSummary);
    }

    private void assertThatModifiedDateIsUpdated(Publication publication, DoiRequestSummary doiRequestSummary) {
        var newModifiedDate = doiRequestSummary.getModifiedDate();
        var oldModifiedDate = publication.getModifiedDate();
        assertThat(newModifiedDate, is(greaterThan(oldModifiedDate)));
    }

    private Index indexThrowingException(String expectedMessage) {
        Index index = mock(Index.class);
        when(index.query(anyString(), any(), any(RangeKeyCondition.class))).then(
            invocation -> {
                throw new RuntimeException(expectedMessage);
            });
        return index;
    }

    private void assertThatServiceLogsCauseOfForbiddenError(TestAppender testAppender, Publication publication) {
        String logMessage = testAppender.getMessages();
        String expectedLogMessage = String.format(DynamoDBDoiRequestsService.WRONG_OWNER_ERROR, INVALID_USERNAME,
            publication.getOwner());
        assertThat(logMessage, containsString(expectedLogMessage));
    }

    private DoiRequestMessage extractDoiRequestMessageFromPublication(Publication updatedPublication) {
        return updatedPublication.getDoiRequest().getMessages()
            .stream().collect(SingletonCollector.collect());
    }

    private DoiRequestMessage expectedDoiRequestMessage(Publication publication) {
        DoiRequestMessage expectedMessage = new DoiRequestMessage();
        expectedMessage.setAuthor(publication.getOwner());
        expectedMessage.setText(DEFAULT_MESSAGE);
        expectedMessage.setTimestamp(mockedNow);
        return expectedMessage;
    }

    private CreateDoiRequest createDoiRequestWithMessage(Publication publication) {
        CreateDoiRequest createDoiRequest = new CreateDoiRequest();
        createDoiRequest.setPublicationId(publication.getIdentifier().toString());
        createDoiRequest.setMessage(DEFAULT_MESSAGE);
        return createDoiRequest;
    }

    private CreateDoiRequest createDoiRequestWithoutMessage(Publication publication) {
        CreateDoiRequest createDoiRequest = new CreateDoiRequest();
        createDoiRequest.setPublicationId(publication.getIdentifier().toString());
        return createDoiRequest;
    }

    private DoiRequestSummary expectedDoiRequestSummary(Publication publication) {
        DoiRequestMessage message = createDoiRequestMessage(publication);
        DoiRequest expectedDoiRequest = createDoiRequestObject(message);
        return createDoiRequestSummary(publication, expectedDoiRequest);
    }

    private DoiRequestMessage createDoiRequestMessage(Publication publication) {
        return new DoiRequestMessage.Builder()
            .withTimestamp(mockedNow)
            .withText(DEFAULT_MESSAGE)
            .withAuthor(publication.getOwner())
            .build();
    }

    private DoiRequestSummary createDoiRequestSummary(Publication publication, DoiRequest expectedDoiRequest) {
        var publicationWithExpectedDoiRequest = publication.copy().withDoiRequest(expectedDoiRequest).build();
        return DoiRequestSummary.fromPublication(publicationWithExpectedDoiRequest);
    }

    private DoiRequest createDoiRequestObject(DoiRequestMessage message) {
        return new DoiRequest.Builder()
            .withDate(mockedNow)
            .withMessages(Collections.singletonList(message))
            .withStatus(REQUESTED)
            .build();
    }

    private Table getTable() {
        return getTable(environment.readEnv(PUBLICATIONS_TABLE_NAME_ENV_VARIABLE));
    }

    private void insertPublication(Publication publication) throws JsonProcessingException {
        String tableName = environment.readEnv(PUBLICATIONS_TABLE_NAME_ENV_VARIABLE);
        super.insertPublication(tableName, publication);
    }

    private Publication getPublicationDirectlyFromTable(UUID publicationId) throws IOException {
        String tableName = environment.readEnv(PUBLICATIONS_TABLE_NAME_ENV_VARIABLE);
        return super.getPublication(tableName, publicationId, mockedNow);
    }
}
