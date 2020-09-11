package no.unit.nva.doi.requests.handlers;

import static no.unit.nva.doi.requests.model.AbstractDoiRequest.INVALID_PUBLICATION_ID_ERROR;
import static no.unit.nva.doi.requests.model.AbstractDoiRequest.PUBLICATION_ID_NOT_FOUND_ERROR_FORMAT;
import static no.unit.nva.doi.requests.service.impl.DynamoDBDoiRequestsService.WRONG_OWNER_ERROR;
import static nva.commons.utils.JsonUtils.objectMapper;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.Mockito.mock;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import no.unit.nva.doi.requests.contants.ServiceConstants;
import no.unit.nva.doi.requests.handlers.model.ApiTask;
import no.unit.nva.doi.requests.handlers.model.ApiUpdateDoiResponse;
import no.unit.nva.doi.requests.model.DoiRequestSummary;
import no.unit.nva.doi.requests.model.UpdateDoiRequest;
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
import org.hamcrest.core.Is;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

public class UpdateDoiRequestHandlerTest extends DoiRequestsDynamoDBLocal {
    public static final String INVALID_PUBLICATION_ID = "InvalidPublicationId";
    private static final String INVALID_USERNAME = "invalidUsername";
    public static final String NULL_STRING_REPRESENTATION = "null";

    private static final String USERNAME_NOT_IMPORTANT = INVALID_USERNAME;

    private final Environment environment;
    private final String publicationsTableName;
    private final Context context;
    private final Instant mockNow = Instant.now();
    private final Instant mockOneHourBefore = mockNow.minus(1, ChronoUnit.HOURS);
    private DynamoDBDoiRequestsService doiRequestsService;
    private UpdateDoiRequestHandler handler;


    public UpdateDoiRequestHandlerTest() {
        environment = MockEnvironment.mockEnvironment();
        publicationsTableName = environment.readEnv(ServiceConstants.PUBLICATIONS_TABLE_NAME_ENV_VARIABLE);
        context = mock(Context.class);
    }

    @BeforeEach
    public void init() {
        initializeDatabase();
        Clock mockClock = Clock.fixed(mockNow, ZoneId.systemDefault());
        doiRequestsService = new DynamoDBDoiRequestsService(client, objectMapper, environment, mockClock);
        handler = new UpdateDoiRequestHandler(environment, doiRequestsService);
    }

    @Test
    public void handleRequestReturnsBadRequestWhenPublicationIdIsInvalid() throws IOException {
        var updateDoiRequest = requestWithoutPublicationId();
        updateDoiRequest.setPublicationId(INVALID_PUBLICATION_ID);

        GatewayResponse<Problem> response =  sendRequest(updateDoiRequest, USERNAME_NOT_IMPORTANT);


        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.SC_BAD_REQUEST)));
        final Problem details = response.getBodyObject(Problem.class);

        assertThat(details.getDetail(), containsString(INVALID_PUBLICATION_ID_ERROR));
        assertThat(details.getDetail(), containsString(updateDoiRequest.getPublicationId()));
    }

    @Test
    public void handleRequestReturnsBadRequestWhenNoPublicationId() throws IOException {
        var updateDoiRequest = requestWithoutPublicationId();

        GatewayResponse<Problem> response = sendRequest(updateDoiRequest, USERNAME_NOT_IMPORTANT,
            Collections.emptyMap());


        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.SC_BAD_REQUEST)));
        final Problem details = response.getBodyObject(Problem.class);

        assertThat(details.getDetail(), containsString(INVALID_PUBLICATION_ID_ERROR));
        assertThat(details.getDetail(), containsString(NULL_STRING_REPRESENTATION));
    }

    @Test
    public void handleRequestReturnsNotFoundWhenPublicationDoesNotExist() throws IOException {
        var updateDoiRequest = new UpdateDoiRequest();
        var notExistingPublicationIdentifier = UUID.randomUUID().toString();
        updateDoiRequest.setPublicationId(notExistingPublicationIdentifier);
        updateDoiRequest.setDOIRequestStatus(DoiRequestStatus.APPROVED);

        GatewayResponse<Problem> response =  sendRequest(updateDoiRequest, USERNAME_NOT_IMPORTANT);

        final Problem details = response.getBodyObject(Problem.class);

        assertThat(response.getStatusCode(), Is.is(IsEqual.equalTo(HttpStatus.SC_NOT_FOUND)));

        assertThat(details.getDetail(),
            containsString(String.format(PUBLICATION_ID_NOT_FOUND_ERROR_FORMAT, notExistingPublicationIdentifier)));
    }

    @Test
    public void handleRequestReturnsBadRequestWhenPublicationDoesNotHaveDoiRequest() throws IOException {
        var publication = insertPublicationWithoutDoiRequest(
            Clock.fixed(mockOneHourBefore, ZoneId.systemDefault()));

        var updateDoiRequest = new UpdateDoiRequest();
        updateDoiRequest.setPublicationId(publication.getIdentifier().toString());
        updateDoiRequest.setDOIRequestStatus(DoiRequestStatus.APPROVED);

        GatewayResponse<Problem> response =  sendRequest(updateDoiRequest, publication.getOwner());

        final Problem details = response.getBodyObject(Problem.class);

        assertThat(response.getStatusCode(), Is.is(IsEqual.equalTo(HttpStatus.SC_BAD_REQUEST)));

        assertThat(details.getDetail(),
            containsString("You must initiate creation of a DoiRequest before you can update it."));
    }

    @Test
    public void handleRequestReturnsForbiddenExceptionWhenInputUsernameIsNotThePublicationOwner()
            throws IOException {
        final TestAppender appender = LogUtils.getTestingAppender(DynamoDBDoiRequestsService.class);
        Publication publication = insertPublicationWithoutDoiRequest(
            Clock.fixed(mockOneHourBefore, ZoneId.systemDefault()));

        var updateDoiRequest = new UpdateDoiRequest();
        updateDoiRequest.setPublicationId(publication.getIdentifier().toString());
        updateDoiRequest.setDOIRequestStatus(DoiRequestStatus.APPROVED);

        GatewayResponse<Problem> response = sendRequest(updateDoiRequest, INVALID_USERNAME);

        final Problem details = response.getBodyObject(Problem.class);

        assertThat(response.getStatusCode(), Is.is(IsEqual.equalTo(HttpStatus.SC_FORBIDDEN)));

        assertThatProblemDetailsDoesNotRevealSensitiveInformation(details, INVALID_USERNAME,
                validUsername(publication));

        assertThatLogsContainReasonForForbiddenMessage(appender, publication);
    }

    @Test
    public void handleRequestReturnsForbiddenExceptionWhenUsernameIsNotInRequestContext()
        throws IOException {
        final TestAppender appender = LogUtils.getTestingAppender(UpdateDoiRequestHandler.class);
        Publication publication = insertPublicationWithoutDoiRequest(
            Clock.fixed(mockOneHourBefore, ZoneId.systemDefault()));

        var updateDoiRequest = new UpdateDoiRequest();
        updateDoiRequest.setPublicationId(publication.getIdentifier().toString());
        updateDoiRequest.setDOIRequestStatus(DoiRequestStatus.APPROVED);

        var requestContext = objectMapper.createObjectNode();
        GatewayResponse<Problem> response = sendRequest(updateDoiRequest, requestContext);

        final Problem details = response.getBodyObject(Problem.class);

        assertThat(response.getStatusCode(), Is.is(IsEqual.equalTo(HttpStatus.SC_FORBIDDEN)));

        assertThat(details.getDetail(), is(equalTo("Forbidden")));
        assertThat(appender.getMessages(),
            containsString("Missing from requestContext: /authorizer/claims/custom:feideId"));
    }

    private void assertThatProblemDetailsDoesNotRevealSensitiveInformation(Problem details,
                                                                           String username,
                                                                           String publicationOwner) {
        String errorMessage = details.getDetail();
        assertThat(errorMessage, not(containsString(username)));
        assertThat(errorMessage, not(containsString(publicationOwner)));
    }

    private void assertThatLogsContainReasonForForbiddenMessage(TestAppender appender, Publication publication) {
        String expectedErrorMessage = String.format(WRONG_OWNER_ERROR, INVALID_USERNAME, publication.getOwner());
        assertThat(appender.getMessages(), containsString(expectedErrorMessage));
    }

    @Test
    public void handleRequestSucessfullyUpdatesStatusWhenPublicationIdIsValid() throws IOException, NotFoundException {
        var publication = insertPublicationWithDoiRequest(Clock.fixed(mockNow,
                ZoneId.systemDefault()));


        var updateRequest = new UpdateDoiRequest();
        updateRequest.setPublicationId(publication.getIdentifier().toString());
        updateRequest.setDOIRequestStatus(DoiRequestStatus.APPROVED);


        var output = outpuStream();

        GatewayResponse<ApiUpdateDoiResponse> response = sendRequest(updateRequest, validUsername(publication));

        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.SC_ACCEPTED)));

        DoiRequestSummary actualDoiRequestSummary = readPublicationDirectlyFromDynamo(publication.getIdentifier());
        DoiRequestSummary expectedDoiRequestSummary = expectedDoiRequestSummary(publication);

        assertThat(actualDoiRequestSummary, is(equalTo(expectedDoiRequestSummary)));

        var responseEntity = response.getBodyObject(ApiUpdateDoiResponse.class);
        assertThat(responseEntity, is(equalTo(ApiUpdateDoiResponse.newBuilder()
            .withTask(ApiTask.newBuilder()
                .withIdentifier(publication.getIdentifier().toString())
                .withHref("https://mocked-hostname.example.net/publication/" + publication.getIdentifier().toString())
                .build())
            .build())));
    }

    private String validUsername(Publication publication) {
        return publication.getOwner();
    }

    private DoiRequestSummary readPublicationDirectlyFromDynamo(UUID id) throws NotFoundException {
        return doiRequestsService.fetchDoiRequest(id).orElseThrow();
    }

    private DoiRequestSummary expectedDoiRequestSummary(Publication publication) {
        var includedDoiRequest = new DoiRequest.Builder()
                .withDate(mockNow)
                .withStatus(DoiRequestStatus.APPROVED)
                .build();
        return new DoiRequestSummary(
                publication.getIdentifier(),
                publication.getOwner(),
                includedDoiRequest,
                publication.getEntityDescription());
    }

    private Publication insertPublicationWithDoiRequest(Clock clock)
        throws com.fasterxml.jackson.core.JsonProcessingException {
        Publication publication = PublicationGenerator.getPublicationWithDoiRequest(clock)
            .copy()
            .withCreatedDate(mockOneHourBefore)
            .build();
        insertPublication(publicationsTableName, publication);
        return publication;
    }

    private Publication insertPublicationWithoutDoiRequest(Clock clock)
        throws com.fasterxml.jackson.core.JsonProcessingException {
        Publication publication = PublicationGenerator.getPublicationWithoutDoiRequest(clock)
                .copy()
                .withCreatedDate(mockOneHourBefore)
                .build();
        insertPublication(publicationsTableName, publication);
        return publication;
    }

    private UpdateDoiRequest requestWithoutPublicationId() {
        return new UpdateDoiRequest();
    }

    private <T> GatewayResponse<T> sendRequest(UpdateDoiRequest doiRequest, String username) throws IOException {
        var pathParams = Map.of("publicationIdentifier", doiRequest.getPublicationId());
        InputStream input = createRequest(doiRequest, username, pathParams);
        ByteArrayOutputStream output = outpuStream();
        handler.handleRequest(input, output, context);

        return GatewayResponse.fromOutputStream(output);
    }

    private <T> GatewayResponse<T> sendRequest(UpdateDoiRequest doiRequest, JsonNode requestContext)
        throws IOException {
        var pathParams = Map.of("publicationIdentifier", doiRequest.getPublicationId());
        InputStream input = createRequest(doiRequest, pathParams, requestContext);
        ByteArrayOutputStream output = outpuStream();
        handler.handleRequest(input, output, context);

        return GatewayResponse.fromOutputStream(output);
    }

    private <T> GatewayResponse<T> sendRequest(UpdateDoiRequest doiRequest, String username,
                                               Map<String, String> pathParameters) throws IOException {
        InputStream input = createRequest(doiRequest, username, pathParameters);
        ByteArrayOutputStream output = outpuStream();
        handler.handleRequest(input, output, context);

        return GatewayResponse.fromOutputStream(output);
    }

    private InputStream createRequest(UpdateDoiRequest doiRequest, String username, Map<String, String> pathParameters)
        throws JsonProcessingException {
        var requestContext = objectMapper.createObjectNode();
        requestContext
                .putObject("authorizer")
                .putObject("claims")
                .put("custom:feideId", username);

        return createRequest(doiRequest, pathParameters, requestContext);
    }

    private InputStream createRequest(UpdateDoiRequest doiRequest,
                                      Map<String, String> pathParameters,
                                      JsonNode requestContext) throws JsonProcessingException {
        var mapType = objectMapper.getTypeFactory()
            .constructParametricType(Map.class, String.class, Object.class);
        Map<String, Object> requestContextMap = objectMapper.convertValue(requestContext, mapType);
        return new HandlerRequestBuilder<UpdateDoiRequest>(objectMapper)
            .withBody(doiRequest)
            .withPathParameters(pathParameters)
            .withRequestContext(requestContextMap)
            .build();
    }

    private ByteArrayOutputStream outpuStream() {
        return new ByteArrayOutputStream();
    }

}
