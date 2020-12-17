package no.unit.nva.doi.requests.service.impl;

import static no.unit.nva.doi.requests.contants.ServiceConstants.PUBLICATIONS_TABLE_NAME_ENV_VARIABLE;
import static no.unit.nva.doi.requests.service.impl.DynamoDbDoiRequestsServiceFactory.EMPTY_CREDENTIALS;
import static no.unit.nva.doi.requests.util.MockEnvironment.mockEnvironment;
import static no.unit.nva.doi.requests.util.PublicationGenerator.getPublicationWithDoiRequest;
import static no.unit.nva.doi.requests.util.PublicationGenerator.getPublicationWithoutDoiRequest;
import static no.unit.nva.model.DoiRequestStatus.APPROVED;
import static no.unit.nva.model.DoiRequestStatus.REQUESTED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import no.unit.nva.doi.requests.api.model.requests.CreateDoiRequest;
import no.unit.nva.doi.requests.exception.DynamoDBException;
import no.unit.nva.doi.requests.service.DoiRequestsService;
import no.unit.nva.doi.requests.util.DoiRequestsDynamoDBLocal;
import no.unit.nva.doi.requests.util.PublicationGenerator;
import no.unit.nva.model.DoiRequest;
import no.unit.nva.model.DoiRequestMessage;
import no.unit.nva.model.DoiRequestStatus;
import no.unit.nva.model.Publication;
import no.unit.nva.model.PublicationStatus;
import no.unit.nva.useraccessmanagement.dao.AccessRight;
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
    public static final List<AccessRight> APPROVE_ACCESS_RIGHT = List.of(AccessRight.APPROVE_DOI_REQUEST);
    private final Instant publicationCreationTime = Instant.parse("1900-01-01T10:00:00.00Z");
    private final Instant publicationModificationTime = Instant.parse("2000-12-03T10:15:30.00Z");
    private DynamoDBDoiRequestsService service;
    private Environment environment;
    private Clock clock;

    public DynamoDBDoiRequestsServiceTest() {

    }

    @BeforeEach
    public void setUp() {
        initializeDatabase();
        environment = mockEnvironment();
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(publicationCreationTime).thenReturn(publicationModificationTime);
        service = DynamoDbDoiRequestsServiceFactory.serviceWithCustomClientWithoutCredentials(client, environment,
            clock)
            .getService(EMPTY_CREDENTIALS);
    }

    @Test
    public void findDoiRequestsByStatusAndOwnerReturnsEmptyListWhenNoDoiRequests() throws Exception {
        List<Publication> publications = service.findDoiRequestsByStatusAndOwner(
            PublicationGenerator.PUBLISHER_ID, REQUESTED, PublicationGenerator.OWNER);

        assertTrue(publications.isEmpty());
    }

    @Test
    public void findDoiRequestsByStatusAndOwnerReturnsAllResultsWhenUserOwnsAllDoiRequests() throws Exception {
        insertPublication(getPublicationWithDoiRequest());
        insertPublication(getPublicationWithDoiRequest());
        insertPublication(getPublicationWithDoiRequest());

        List<Publication> publications = service.findDoiRequestsByStatusAndOwner(
            PublicationGenerator.PUBLISHER_ID, REQUESTED, PublicationGenerator.OWNER);

        assertEquals(3, publications.size());
    }

    @Test
    public void findDoiRequestsByStatusReturnsDoiRequestsOfOnlyPublishedPublications() throws Exception {
        Publication publication = getPublicationWithDoiRequest()
            .copy().withStatus(PublicationStatus.DRAFT).build();
        insertPublication(publication);

        List<Publication> publications = service.findDoiRequestsByStatus(
            PublicationGenerator.PUBLISHER_ID, REQUESTED);

        assertThat(publications, is(empty()));
    }

    @Test
    public void findDoiRequestsByStatusAndOwnerReturnsLatestPublicationForEachPublicationIdentifier() throws Exception {
        Publication publication = getPublicationWithDoiRequest();
        Publication laterPublication = updatedPublication(publication);
        Publication latestPublication = updatedPublication(laterPublication);

        insertPublication(publication);
        insertPublication(laterPublication);
        insertPublication(latestPublication);

        List<Publication> publications = service.findDoiRequestsByStatusAndOwner(
            PublicationGenerator.PUBLISHER_ID, REQUESTED, PublicationGenerator.OWNER);
        assertEquals(1, publications.size());

        Publication actualPublication = publications.get(0);
        assertThat(actualPublication, is(equalTo(latestPublication)));
    }

    @Test
    public void findDoiRequestsByStatusAndOwnerReturnsAllButOneResultsWhenUserOwnsAllButOneDoiRequests()
        throws Exception {
        insertPublication(getPublicationWithDoiRequest());
        insertPublication(getPublicationWithDoiRequest());

        Publication publicationOwnedByAnother = getPublicationWithDoiRequest();
        publicationOwnedByAnother.setOwner("another_owner");
        insertPublication(publicationOwnedByAnother);

        List<Publication> publications = service.findDoiRequestsByStatusAndOwner(
            PublicationGenerator.PUBLISHER_ID, REQUESTED, PublicationGenerator.OWNER);

        assertEquals(2, publications.size());
    }

    @Test
    public void findByDoiRequestStatusThrowsExceptionOnIndexError() {
        Index index = mock(Index.class);
        when(index.query(anyString(), any(), any(RangeKeyCondition.class))).thenThrow(RuntimeException.class);

        DynamoDBDoiRequestsService failingService = new DynamoDBDoiRequestsService(getTable(), index);
        DynamoDBException exception = assertThrows(DynamoDBException.class,
            () -> failingService.findDoiRequestsByStatus(PublicationGenerator.PUBLISHER_ID, REQUESTED));

        assertEquals(DynamoDBDoiRequestsService.ERROR_READING_FROM_TABLE, exception.getMessage());
    }

    @Test
    public void fetchDoiRequestByPublicationIdReturnsDoiRequestSummary()
        throws JsonProcessingException, NotFoundException {
        Publication publication = getPublicationWithDoiRequest();
        insertPublication(publication);
        Optional<Publication> result = service.fetchDoiRequestByPublicationIdentifier(publication.getIdentifier());
        assertThat(result.isPresent(), is(true));
    }

    @Test
    public void fetchDoiRequestByPublicationThrowsExceptionWhenIndexSearchFails() {
        Publication publication = getPublicationWithDoiRequest();

        var expectedMessage = "Index search failed";
        var table = mock(Table.class);
        var index = indexThrowingException(expectedMessage);

        service = new DynamoDBDoiRequestsService(table, index);
        Executable indexSearchFailure = () -> service.findDoiRequestsByStatus(
            publication.getPublisher().getId(), REQUESTED);
        DynamoDBException exception = assertThrows(DynamoDBException.class, indexSearchFailure);

        assertThat(exception.getCause().getMessage(), is(equalTo(expectedMessage)));
    }

    @Test
    public void createDoiRequestAddsDoiRequestToPublication()
        throws JsonProcessingException, ConflictException, NotFoundException, ForbiddenException {
        Publication publication = getPublicationWithoutDoiRequest(clock);
        insertPublication(publication);

        Publication expectedDoiRequestSummary = expectedDoiRequestSummary(publication);

        service.createDoiRequest(createDoiRequestWithMessage(publication), publication.getOwner());
        Publication actualDoiRequestSummary = service
            .fetchDoiRequestByPublicationIdentifier(publication.getIdentifier())
            .orElseThrow();

        assertThat(actualDoiRequestSummary, is(equalTo(expectedDoiRequestSummary)));
    }

    @Test
    public void createDoiRequestWithoutMessageDoesNotCreateEmptyMessage()
        throws IOException, ConflictException, NotFoundException, ForbiddenException {
        Publication publication = getPublicationWithoutDoiRequest(clock);
        insertPublication(publication);

        var doiRequestWithoutMessage = createDoiRequestWithoutMessage(publication);
        service.createDoiRequest(doiRequestWithoutMessage, publication.getOwner());

        DoiRequest doiRequest = getPublicationDirectlyFromTable(publication.getIdentifier()).getDoiRequest();

        assertThat(doiRequest.getMessages(), is(empty()));
    }

    @Test
    public void createDoiRequestSetsModifiedPublicationDateEqualToDoiRequestCreatedAndModifiedDate()
        throws IOException, ConflictException, NotFoundException, ForbiddenException {
        Publication publicationWithoutDoiRequest = getPublicationWithoutDoiRequest(clock);
        insertPublication(publicationWithoutDoiRequest);

        var doiRequestWithoutMessage = createDoiRequestWithoutMessage(publicationWithoutDoiRequest);
        service.createDoiRequest(doiRequestWithoutMessage, publicationWithoutDoiRequest.getOwner());

        Publication actualPublication = getPublicationDirectlyFromTable(publicationWithoutDoiRequest.getIdentifier());

        Instant actualPublicationModifiedDate = actualPublication.getModifiedDate();
        Instant expectedModifiedDate = actualPublication.getDoiRequest().getModifiedDate();
        Instant doiRequestCreatedDate = actualPublication.getDoiRequest().getCreatedDate();

        assertThat(actualPublicationModifiedDate, is(equalTo(expectedModifiedDate)));
        assertThat(actualPublicationModifiedDate, is(equalTo(doiRequestCreatedDate)));
    }

    @Test
    public void createDoiRequestAddsMessageToPublicationsDoiRequest()
        throws IOException, ConflictException, NotFoundException, ForbiddenException {
        Publication publication = getPublicationWithoutDoiRequest(clock);
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
        Publication publication = getPublicationWithDoiRequest();
        insertPublication(publication);

        Executable action = () -> service.createDoiRequest(createDoiRequestWithMessage(publication),
            publication.getOwner());
        ConflictException exception = assertThrows(ConflictException.class, action);

        assertThat(exception.getMessage(), containsString(DoiRequestsService.DOI_ALREADY_EXISTS_ERROR));
    }

    @Test
    public void createDoiRequestThrowsNotFoundExceptionWhenCreatingDoiRequestForNonExistingPublication() {
        Publication publication = getPublicationWithDoiRequest();

        Executable action = () -> service.createDoiRequest(createDoiRequestWithMessage(publication),
            publication.getOwner());
        NotFoundException exception = assertThrows(NotFoundException.class, action);

        assertThat(exception.getMessage(), containsString(publication.getIdentifier().toString()));
    }

    @Test
    public void createDoiRequestThrowsForbiddenExceptionWhenCreatingDoiRequestForNonExistingPublication()
        throws JsonProcessingException {
        TestAppender testAppender = LogUtils.getTestingAppender(DynamoDBDoiRequestsService.class);
        Publication publication = getPublicationWithDoiRequest();
        insertPublication(publication);

        Executable action = () -> service.createDoiRequest(createDoiRequestWithMessage(publication), INVALID_USERNAME);
        assertThrows(ForbiddenException.class, action);

        assertThatServiceLogsCauseOfForbiddenError(testAppender, publication);
    }

    @Test
    public void createDoiRequestThrowsRuntimeExceptionOnSerializationError()
        throws JsonProcessingException, NoSuchFieldException, IllegalAccessException {

        final String exceptionMessage = "This is the exception message";
        ObjectMapper objectMapper = spy(JsonUtils.objectMapper);
        when(objectMapper.writeValueAsString(any(Publication.class)))
            .thenThrow(new RuntimeException(exceptionMessage));

        DynamoDBDoiRequestsService serviceWithFailingJsonObjectMapper = createServiceWithFailingJsonObjectMapper(
            objectMapper);

        Publication publication = getPublicationWithoutDoiRequest(clock);
        insertPublication(publication);

        Executable action = () -> serviceWithFailingJsonObjectMapper.createDoiRequest(
            createDoiRequestWithMessage(publication),
            publication.getOwner());
        RuntimeException exception = assertThrows(RuntimeException.class, action);

        assertThat(exception.getMessage(), containsString(exceptionMessage));
    }

    @Test
    public void updateDoiRequestPersistsUpdatedDoiRequestWhenInputIsValidAndUserIsAuthorized()
        throws NotFoundException, ForbiddenException, IOException {

        Publication publication = getPublicationWithDoiRequest(clock);
        insertPublication(publication);
        assertThat(publication.getDoiRequest().getStatus(), is(equalTo(INITIAL_DOI_REQUEST_STATUS)));

        service.updateDoiRequest(publication.getIdentifier(), APPROVED, publication.getOwner(), APPROVE_ACCESS_RIGHT);

        var publicationWithDoiRequest = service.fetchDoiRequestByPublicationIdentifier(publication.getIdentifier())
            .orElseThrow();
        DoiRequest actualDoiRequest = publicationWithDoiRequest.getDoiRequest();

        assertThat(actualDoiRequest.getStatus(), is(equalTo(NEW_DOI_REQUEST_STATUS)));

        assertThatModifiedDateIsUpdated(publication, publicationWithDoiRequest);
    }

    private Publication updatedPublication(Publication publication) {
        return publication.copy()
            .withModifiedDate(publication.getModifiedDate().plus(Period.ofDays(1)))
            .build();
    }

    private DynamoDBDoiRequestsService createServiceWithFailingJsonObjectMapper(ObjectMapper objectMapper)
        throws NoSuchFieldException, IllegalAccessException {
        DynamoDBDoiRequestsService serviceWithFailingJsonObjectMapper =
            DynamoDbDoiRequestsServiceFactory.serviceWithCustomClientWithoutCredentials(client, environment)
                .getService(EMPTY_CREDENTIALS);

        Field field = DynamoDBDoiRequestsService.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(serviceWithFailingJsonObjectMapper, objectMapper);
        return serviceWithFailingJsonObjectMapper;
    }

    private void assertThatModifiedDateIsUpdated(Publication originalPublication, Publication updatedPublication) {
        var oldModifiedDate = originalPublication.getModifiedDate();
        var newModifiedDate = updatedPublication.getModifiedDate();
        assertThat(newModifiedDate, is(greaterThan(oldModifiedDate)));
    }

    private Index indexThrowingException(String expectedMessage) {
        Index index = mock(Index.class);
        when(index.query(any(QuerySpec.class))).then(
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
        expectedMessage.setTimestamp(publicationModificationTime);
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

    private Publication expectedDoiRequestSummary(Publication publication) {
        DoiRequestMessage message = createDoiRequestMessage(publication);
        DoiRequest expectedDoiRequest = createDoiRequestObject(message);
        return createPublicationWithUpdatedDoiRequest(publication, expectedDoiRequest);
    }

    private DoiRequestMessage createDoiRequestMessage(Publication publication) {
        return new DoiRequestMessage.Builder()
            .withTimestamp(publicationModificationTime)
            .withText(DEFAULT_MESSAGE)
            .withAuthor(publication.getOwner())
            .build();
    }

    private Publication createPublicationWithUpdatedDoiRequest(Publication publication, DoiRequest expectedDoiRequest) {
        return publication.copy()
            .withCreatedDate(publicationCreationTime)
            .withModifiedDate(expectedDoiRequest.getModifiedDate())
            .withDoiRequest(expectedDoiRequest).build();
    }

    private DoiRequest createDoiRequestObject(DoiRequestMessage message) {
        return new DoiRequest.Builder()
            .withCreatedDate(publicationModificationTime)
            .withModifiedDate(publicationModificationTime)
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
        return super.getPublication(tableName, publicationId, publicationModificationTime);
    }
}
