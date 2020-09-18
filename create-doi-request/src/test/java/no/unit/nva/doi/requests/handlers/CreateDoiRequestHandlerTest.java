package no.unit.nva.doi.requests.handlers;

import static no.unit.nva.doi.requests.service.impl.DynamoDBDoiRequestsService.WRONG_OWNER_ERROR;
import static nva.commons.utils.JsonUtils.objectMapper;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.Mockito.mock;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import no.unit.nva.doi.requests.api.model.requests.CreateDoiRequest;
import no.unit.nva.doi.requests.api.model.responses.DoiRequestSummary;
import no.unit.nva.doi.requests.contants.ServiceConstants;
import no.unit.nva.doi.requests.service.DoiRequestsService;
import no.unit.nva.doi.requests.service.impl.DynamoDBDoiRequestsService;
import no.unit.nva.doi.requests.util.DoiRequestsDynamoDBLocal;
import no.unit.nva.doi.requests.util.MockEnvironment;
import no.unit.nva.doi.requests.util.PublicationGenerator;
import no.unit.nva.model.DoiRequest;
import no.unit.nva.model.DoiRequestStatus;
import no.unit.nva.model.Publication;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.exceptions.commonexceptions.NotFoundException;
import nva.commons.handlers.GatewayResponse;
import nva.commons.utils.Environment;
import nva.commons.utils.log.LogUtils;
import nva.commons.utils.log.TestAppender;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

public class CreateDoiRequestHandlerTest extends DoiRequestsDynamoDBLocal {

    public static final String INVALID_PUBLICATION_ID = "InvalidPublicationId";
    public static final String NULL_STRING_REPRESENTATION = "null";
    public static final String INVALID_USERNAME = "invalidUsername";
    public static final String USERNAME_NOT_IMPORTANT = INVALID_USERNAME;
    private final Environment environment;
    private final Instant mockNow = Instant.now();
    private final String publicationsTableName;
    private CreateDoiRequestHandler handler;
    private Context context;
    private DoiRequestsService doiRequestsService;

    public CreateDoiRequestHandlerTest() {

        environment = MockEnvironment.mockEnvironment();
        publicationsTableName = environment.readEnv(ServiceConstants.PUBLICATIONS_TABLE_NAME_ENV_VARIABLE);

        this.context = mock(Context.class);
    }

    @BeforeEach
    public void init() {
        initializeDatabase();
        Clock mockClock = Clock.fixed(mockNow, ZoneId.systemDefault());
        doiRequestsService = new DynamoDBDoiRequestsService(client, objectMapper, environment, mockClock);
        this.handler = new CreateDoiRequestHandler(environment, doiRequestsService);
    }

    @Test
    public void handleRequestReturnsBadRequestWhenPublicationIdIsEmpty() throws IOException {
        CreateDoiRequest doiRequest = requestWithoutPublicationId();
        GatewayResponse<Problem> response = sendRequest(doiRequest, USERNAME_NOT_IMPORTANT);

        final Problem details = response.getBodyObject(Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.SC_BAD_REQUEST)));
        assertThat(details.getDetail(), containsString(CreateDoiRequest.INVALID_PUBLICATION_ID_ERROR));
        assertThat(details.getDetail(), containsString(NULL_STRING_REPRESENTATION));
    }

    @Test
    public void handleRequestReturnsBadRequestWhenPublicationIdIsInvalid() throws IOException {
        CreateDoiRequest doiRequest = requestWithoutPublicationId();
        doiRequest.setPublicationId(INVALID_PUBLICATION_ID);
        GatewayResponse<Problem> response = sendRequest(doiRequest, USERNAME_NOT_IMPORTANT);

        final Problem details = response.getBodyObject(Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.SC_BAD_REQUEST)));
        assertThat(details.getDetail(), containsString(CreateDoiRequest.INVALID_PUBLICATION_ID_ERROR));
        assertThat(details.getDetail(), containsString(doiRequest.getPublicationId()));
    }

    @Test
    public void handleRequestReturnsCreatedIfPublicationIdIsNotEmpty() throws IOException {
        Publication publication = PublicationGenerator.getPublicationWithoutDoiRequest();
        insertPublication(publicationsTableName, publication);
        CreateDoiRequest doiRequest = createDoiRequest(publication);

        GatewayResponse<Void> response = sendRequest(doiRequest, validUsername(publication));

        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.SC_CREATED)));
    }

    @Test
    public void handleRequestSavesDoiRequestToPublicationWhenPublicationIdIsNotEmpty()
        throws IOException, NotFoundException {
        Publication publication = insertPublicationWithoutDoiRequest();

        CreateDoiRequest doiRequest = createDoiRequest(publication);

        InputStream input = createRequest(doiRequest, validUsername(publication));
        ByteArrayOutputStream output = outputStream();
        handler.handleRequest(input, output, context);

        DoiRequestSummary actualDoiRequestSummary = readPublicationDirectlyFromDynamo(doiRequest);
        DoiRequestSummary expectedDoiRequestSummary = expectedDoiRequestSummary(publication);

        assertThat(actualDoiRequestSummary, is(equalTo(expectedDoiRequestSummary)));
    }

    @Test
    public void handleRequestReturnsForbiddenExceptionWhenInputUsernameIsNotThePublicationOwner()
        throws IOException {
        TestAppender appender = LogUtils.getTestingAppender(DynamoDBDoiRequestsService.class);
        Publication publication = insertPublicationWithoutDoiRequest();
        CreateDoiRequest doiRequest = createDoiRequest(publication);

        GatewayResponse<Problem> response = sendRequest(doiRequest, INVALID_USERNAME);

        final Problem details = response.getBodyObject(Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.SC_FORBIDDEN)));

        assertThatProblemDetailsDoesNotRevealSensitiveInformation(details, INVALID_USERNAME,
            validUsername(publication));

        assertThatLogsContainReasonForForbiddenMessage(appender, publication);
    }

    @Test
    public void handleRequestReturnsConflictErrorWhenDoiRequestAlreadyExists()
        throws IOException {
        Publication publication = insertPublicationWithDoiRequest();

        CreateDoiRequest doiRequest = createDoiRequest(publication);

        GatewayResponse<Problem> response = sendRequest(doiRequest, validUsername(publication));
        Problem problem = response.getBodyObject(Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.SC_CONFLICT)));
        assertThat(problem.getDetail(), containsString(DynamoDBDoiRequestsService.DOI_ALREADY_EXISTS_ERROR));
    }

    private String validUsername(Publication publication) {
        return publication.getOwner();
    }

    private void assertThatLogsContainReasonForForbiddenMessage(TestAppender appender, Publication publication) {
        String expectedErrorMessage = String.format(WRONG_OWNER_ERROR, INVALID_USERNAME, publication.getOwner());
        assertThat(appender.getMessages(), containsString(expectedErrorMessage));
    }

    private void assertThatProblemDetailsDoesNotRevealSensitiveInformation(Problem details,
                                                                           String username,
                                                                           String publicationOwner) {
        String errorMessage = details.getDetail();
        assertThat(errorMessage, not(containsString(username)));
        assertThat(errorMessage, not(containsString(publicationOwner)));
    }

    private <T> GatewayResponse<T> sendRequest(CreateDoiRequest doiRequest, String username) throws IOException {
        InputStream input = createRequest(doiRequest, username);
        ByteArrayOutputStream output = outputStream();
        handler.handleRequest(input, output, context);

        return GatewayResponse.fromOutputStream(output);
    }

    private DoiRequestSummary expectedDoiRequestSummary(Publication publication) {
        DoiRequest includedDoiRequest = new DoiRequest.Builder()
            .withDate(mockNow)
            .withStatus(DoiRequestStatus.REQUESTED)
            .build();
        publication.setDoiRequest(includedDoiRequest);
        return DoiRequestSummary.fromPublication(publication);
    }

    private DoiRequestSummary readPublicationDirectlyFromDynamo(CreateDoiRequest doiRequest)
        throws JsonProcessingException, NotFoundException {
        return doiRequestsService.fetchDoiRequest(
            UUID.fromString(doiRequest.getPublicationId()))
            .orElseThrow();
    }

    private Publication insertPublicationWithoutDoiRequest() throws com.fasterxml.jackson.core.JsonProcessingException {
        Publication publication = PublicationGenerator.getPublicationWithoutDoiRequest();
        insertPublication(publicationsTableName, publication);
        return publication;
    }

    private Publication insertPublicationWithDoiRequest() throws JsonProcessingException {
        Publication publication = PublicationGenerator.getPublicationWithDoiRequest();
        insertPublication(publicationsTableName, publication);
        return publication;
    }

    private CreateDoiRequest requestWithoutPublicationId() {
        return new CreateDoiRequest();
    }

    private InputStream createRequest(CreateDoiRequest doiRequest, String username)
        throws com.fasterxml.jackson.core.JsonProcessingException {
        ObjectNode requestContext = objectMapper.createObjectNode();
        requestContext
            .putObject("authorizer")
            .putObject("claims")
            .put("custom:feideId", username);
        JavaType mapType = objectMapper.getTypeFactory()
            .constructParametricType(Map.class, String.class, Object.class);
        Map<String, Object> requestContextMap = objectMapper.convertValue(requestContext, mapType);
        return new HandlerRequestBuilder<CreateDoiRequest>(objectMapper)
            .withBody(doiRequest)
            .withRequestContext(requestContextMap)
            .build();
    }

    private CreateDoiRequest createDoiRequest(Publication publication) {
        CreateDoiRequest doiRequest = new CreateDoiRequest();
        doiRequest.setPublicationId(publication.getIdentifier().toString());
        return doiRequest;
    }

    private ByteArrayOutputStream outputStream() {
        return new ByteArrayOutputStream();
    }
}
