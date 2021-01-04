package no.unit.nva.doi.requests.handlers;

import static no.unit.nva.doi.requests.handlers.UpdateDoiRequestHandler.API_PUBLICATION_PATH_IDENTIFIER;
import static no.unit.nva.doi.requests.util.MockEnvironment.mockEnvironment;
import static no.unit.nva.doi.requests.util.PublicationGenerator.OWNER;
import static nva.commons.utils.JsonUtils.objectMapper;
import static nva.commons.utils.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Mockito.mock;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import no.unit.nva.doi.requests.model.ApiUpdateDoiRequest;
import no.unit.nva.doi.requests.service.DoiRequestsService;
import no.unit.nva.doi.requests.service.impl.DynamoDbDoiRequestsServiceFactory;
import no.unit.nva.doi.requests.util.PublicationGenerator;
import no.unit.nva.model.DoiRequestMessage;
import no.unit.nva.model.Publication;
import no.unit.nva.stubs.FakeStsClient;
import no.unit.nva.testutils.HandlerRequestBuilder;
import no.unit.nva.useraccessmanagement.dao.AccessRight;
import nva.commons.exceptions.commonexceptions.NotFoundException;
import nva.commons.handlers.GatewayResponse;
import nva.commons.utils.Environment;
import nva.commons.utils.SingletonCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.zalando.problem.Problem;

public class DoiRequestMessageHandlerTest extends UpdateDoiTestUtils {

    public static final AWSCredentialsProvider IGNORED_CREDENTIALS = null;
    public static final String NOT_THE_OWNER = "not_the_owner";
    public static final String NOT_THE_PUBLISHER = "https://example.com/wrong_instutition";
    private final FakeStsClient stsClient = new FakeStsClient();
    private final Environment environment = mockEnvironment();
    private final Context context = mock(Context.class);
    private final Logger logger = mock(Logger.class);
    private DoiRequestMessageHandler handler;
    private ByteArrayOutputStream outputStream;
    private DoiRequestsService handlerService;

    @BeforeEach
    public void init() {
        initializeDatabase();
        DynamoDbDoiRequestsServiceFactory serviceFactory = DynamoDbDoiRequestsServiceFactory
            .serviceWithCustomClientWithoutCredentials(client, environment);
        handlerService = serviceFactory.getService(IGNORED_CREDENTIALS);
        outputStream = new ByteArrayOutputStream();

        handler = new DoiRequestMessageHandler(environment, stsClient, serviceFactory, logger);
    }

    @Test
    public void handlerSavesMessageWhenInputContainsMessageAndUserIsThePublicationOwner()
        throws IOException, NotFoundException {
        RequestInputStream requestInputStream = this::validCreatorRequest;

        assertMessageIsSavedWhenAuthorizedUserSendsMessage(requestInputStream);
    }

    @Test
    public void handlerSavesMessageWhenInputContainsMessageAndUserIsAnInstitutionCurator()
        throws IOException, NotFoundException {
        RequestInputStream requestSupplier = this::validCuratorRequest;

        assertMessageIsSavedWhenAuthorizedUserSendsMessage(requestSupplier);
    }

    @Test
    public void handlerReturnsAcceptedWhenUserIsThePublicationOwner() throws IOException {
        RequestInputStream requestInputStream = this::validCreatorRequest;
        assertResponseIsAcceptedWhenUserIsAuthorized(requestInputStream);
    }

    @Test
    public void handlerReturnsAcceptedWhenUserIsACuratorOfTheCorrectInstitution() throws IOException {
        RequestInputStream requestInputStream = this::validCuratorRequest;
        assertResponseIsAcceptedWhenUserIsAuthorized(requestInputStream);
    }

    @Test
    public void handlerReturnsForbiddenWhenUserIsNotTheOwnerOrAuthorizedToUpdateTheDoiRequest()
        throws IOException, NotFoundException {
        RequestInputStream request = this::invalidCreatorRequest;
        InputObjects inputs = userSendsMessageForPublication(request);
        assertThatHandlerReturnsForbiddenAndMessageIsNotSaved(inputs);
    }

    @Test
    public void handlerReturnsForbiddenWhenUserIsACuratorOfTheWrongInstitution() throws IOException,
                                                                                        NotFoundException {
        RequestInputStream requestInputStream = this::invalidCuratorRequest;
        InputObjects inputs = userSendsMessageForPublication(requestInputStream);
        assertThatHandlerReturnsForbiddenAndMessageIsNotSaved(inputs);
    }

    @Test
    public void handlerReturnsBadRequestWhenInputIsInvalid() throws IOException {
        RequestInputStream request = (message, publication) -> requestWithoutMessage(publication);
        userSendsMessageForPublication(request);
        GatewayResponse<?> response = GatewayResponse.fromOutputStream(outputStream);
        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_BAD_REQUEST)));
    }

    private void assertThatHandlerReturnsForbiddenAndMessageIsNotSaved(InputObjects inputs)
        throws JsonProcessingException, NotFoundException {
        GatewayResponse<Problem> response = GatewayResponse.fromOutputStream(outputStream);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_FORBIDDEN)));

        Publication actualPublication = fetchActualPublicationDirectly(inputs);

        List<DoiRequestMessage> messages = actualPublication.getDoiRequest().getMessages();
        assertThat(messages.size(), is(equalTo(0)));
    }

    private Publication fetchActualPublicationDirectly(InputObjects inputs)
        throws JsonProcessingException, NotFoundException {
        return handlerService.fetchDoiRequestByPublicationIdentifier(
            inputs.getPublication().getIdentifier()).orElseThrow();
    }

    private void assertMessageIsSavedWhenAuthorizedUserSendsMessage(RequestInputStream requestSupplier)
        throws IOException, NotFoundException {

        InputObjects inputObjects = userSendsMessageForPublication(requestSupplier);
        Publication actualPublication = fetchActualPublicationDirectly(inputObjects);
        assertThatActualPublicationHasExpectedMessage(inputObjects.getMessage(), actualPublication);
    }

    private InputObjects userSendsMessageForPublication(RequestInputStream requestSupplier) throws IOException {
        Publication publication = insertPublicationWithDoiRequest(mockClock);
        String userMessage = UUID.randomUUID().toString();
        InputStream request = requestSupplier.createRequest(userMessage, publication);
        handler.handleRequest(request, outputStream, context);
        return new InputObjects(publication, userMessage);
    }

    private void assertResponseIsAcceptedWhenUserIsAuthorized(RequestInputStream requestSupplier)
        throws IOException {
        userSendsMessageForPublication(requestSupplier);
        GatewayResponse<?> response = GatewayResponse.fromOutputStream(outputStream);
        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_ACCEPTED)));
    }

    private void assertThatActualPublicationHasExpectedMessage(String expectedMessage, Publication actualPublication) {
        String actualMessage = actualPublication.getDoiRequest().getMessages()
            .stream()
            .map(DoiRequestMessage::getText)
            .filter(messageText -> messageText.equals(expectedMessage))
            .collect(SingletonCollector.collect());
        assertThat(actualMessage, is(equalTo(expectedMessage)));
    }

    private InputStream validCreatorRequest(String message, Publication publication) {
        return creatorRequest(message, publication, OWNER);
    }

    private InputStream invalidCreatorRequest(String message, Publication publication) {
        return creatorRequest(message, publication, NOT_THE_OWNER);
    }

    private InputStream creatorRequest(String message, Publication publication, String userId) {
        ApiUpdateDoiRequest updateDoiRequest = new ApiUpdateDoiRequest();
        updateDoiRequest.setMessage(message);
        Map<String, String> pathParams =
            Map.of(API_PUBLICATION_PATH_IDENTIFIER, publication.getIdentifier().toString());

        return attempt(() -> new HandlerRequestBuilder<ApiUpdateDoiRequest>(objectMapper)
            .withBody(updateDoiRequest)
            .withFeideId(userId)
            .withAccessRight(AccessRight.READ_DOI_REQUEST.toString())
            .withPathParameters(pathParams)
            .build())
            .orElseThrow();
    }

    private InputStream validCuratorRequest(String message, Publication publication) {
        return curatorRequest(message, publication, PublicationGenerator.PUBLISHER_ID.toString());
    }

    private InputStream invalidCuratorRequest(String message, Publication publication) {
        return curatorRequest(message, publication, NOT_THE_PUBLISHER);
    }

    private InputStream curatorRequest(String message, Publication publication, String notThePublisher) {
        ApiUpdateDoiRequest updateDoiRequest = new ApiUpdateDoiRequest();
        updateDoiRequest.setMessage(message);
        Map<String, String> pathParams =
            Map.of(API_PUBLICATION_PATH_IDENTIFIER, publication.getIdentifier().toString());

        return attempt(() -> new HandlerRequestBuilder<ApiUpdateDoiRequest>(objectMapper)
            .withBody(updateDoiRequest)
            .withFeideId(NOT_THE_OWNER)
            .withCustomerId(notThePublisher)
            .withAccessRight(AccessRight.APPROVE_DOI_REQUEST.toString())
            .withAccessRight(AccessRight.REJECT_DOI_REQUEST.toString())
            .withPathParameters(pathParams)
            .build())
            .orElseThrow();
    }

    private InputStream requestWithoutMessage(Publication publication) {
        ApiUpdateDoiRequest updateDoiRequest = new ApiUpdateDoiRequest();
        Map<String, String> pathParams =
            Map.of(API_PUBLICATION_PATH_IDENTIFIER, publication.getIdentifier().toString());

        return attempt(() -> new HandlerRequestBuilder<ApiUpdateDoiRequest>(objectMapper)
            .withBody(updateDoiRequest)
            .withFeideId(OWNER)
            .withAccessRight(AccessRight.READ_DOI_REQUEST.toString())
            .withPathParameters(pathParams)
            .build())
            .orElseThrow();
    }

    private interface RequestInputStream {

        InputStream createRequest(String message, Publication publication);
    }

    private class InputObjects {

        private final Publication publication;
        private final String message;

        private InputObjects(Publication publication, String message) {
            this.publication = publication;
            this.message = message;
        }

        public Publication getPublication() {
            return publication;
        }

        public String getMessage() {
            return message;
        }
    }
}