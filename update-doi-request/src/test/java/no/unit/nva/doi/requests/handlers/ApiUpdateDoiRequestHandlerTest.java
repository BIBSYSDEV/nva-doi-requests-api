package no.unit.nva.doi.requests.handlers;

import static java.util.Objects.isNull;
import static no.unit.nva.doi.requests.api.model.requests.CreateDoiRequest.INVALID_PUBLICATION_ID_ERROR;
import static no.unit.nva.doi.requests.handlers.UpdateDoiRequestHandler.API_PUBLICATION_PATH_IDENTIFIER;
import static no.unit.nva.doi.requests.service.impl.DynamoDBDoiRequestsService.PUBLICATION_NOT_FOUND_ERROR_MESSAGE;
import static no.unit.nva.doi.requests.service.impl.DynamoDBDoiRequestsService.PUBLISHER_ID;
import static no.unit.nva.doi.requests.service.impl.DynamoDBDoiRequestsService.USER_NOT_ALLOWED_TO_APPROVE_DOI_REQUEST;
import static no.unit.nva.doi.requests.service.impl.DynamoDbDoiRequestsServiceFactory.EMPTY_CREDENTIALS;
import static no.unit.nva.doi.requests.util.MockEnvironment.FAKE_API_HOST_ENV;
import static no.unit.nva.doi.requests.util.MockEnvironment.FAKE_API_SCHEME_ENV;
import static no.unit.nva.doi.requests.util.MockEnvironment.mockEnvironment;
import static no.unit.nva.model.DoiRequestStatus.APPROVED;
import static no.unit.nva.model.DoiRequestStatus.REQUESTED;
import static no.unit.nva.useraccessmanagement.dao.AccessRight.APPROVE_DOI_REQUEST;
import static no.unit.nva.useraccessmanagement.dao.AccessRight.READ_DOI_REQUEST;
import static nva.commons.utils.JsonUtils.objectMapper;
import static nva.commons.utils.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringContains.containsString;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.unit.nva.doi.requests.contants.ServiceConstants;
import no.unit.nva.doi.requests.model.ApiUpdateDoiRequest;
import no.unit.nva.doi.requests.service.impl.DynamoDBDoiRequestsService;
import no.unit.nva.doi.requests.service.impl.DynamoDbDoiRequestsServiceFactory;
import no.unit.nva.doi.requests.util.DoiRequestsDynamoDBLocal;
import no.unit.nva.doi.requests.util.PublicationGenerator;
import no.unit.nva.model.DoiRequest;
import no.unit.nva.model.DoiRequestStatus;
import no.unit.nva.model.Publication;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.stubs.FakeStsClient;
import no.unit.nva.testutils.HandlerRequestBuilder;
import no.unit.nva.useraccessmanagement.dao.AccessRight;
import nva.commons.exceptions.ForbiddenException;
import nva.commons.exceptions.commonexceptions.NotFoundException;
import nva.commons.handlers.GatewayResponse;
import nva.commons.handlers.RequestInfo;
import nva.commons.utils.Environment;
import nva.commons.utils.log.LogUtils;
import nva.commons.utils.log.TestAppender;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

public class ApiUpdateDoiRequestHandlerTest extends DoiRequestsDynamoDBLocal {

    public static final String INVALID_PUBLICATION_IDENTIFIER = "InvalidPublicationId";
    public static final String FAKE_ENV_SCHEMA_AND_HOST = FAKE_API_SCHEME_ENV
        + "://"
        + FAKE_API_HOST_ENV
        + "/publication/";
    private static final String INVALID_USERNAME = "invalidUsername";
    private static final String USERNAME_NOT_IMPORTANT = INVALID_USERNAME;
    public static final String COMMA_SEPARATOR = ",";
    public static final String EMPTY_STRING = "";
    public static final String NOT_THE_PUBLICATION_OWNER = "notCurator@unit.no";
    private final Environment environment;
    private final String publicationsTableName;
    private final Context context;
    private final Instant mockNow = Instant.now();
    private final Instant mockOneHourBefore = mockNow.minus(1, ChronoUnit.HOURS);
    private final FakeStsClient stsClient;
    private DynamoDBDoiRequestsService doiRequestsService;
    private UpdateDoiRequestHandler handler;

    public ApiUpdateDoiRequestHandlerTest() {
        environment = mockEnvironment();
        publicationsTableName = environment.readEnv(ServiceConstants.PUBLICATIONS_TABLE_NAME_ENV_VARIABLE);
        stsClient = new FakeStsClient();
        context = new FakeContext();
    }

    @BeforeEach
    public void init() {
        initializeDatabase();
        Clock mockClock = getFixedClockWithDefaultTimeZone(mockNow);

        DynamoDbDoiRequestsServiceFactory doiRequestsServiceFactory = DynamoDbDoiRequestsServiceFactory
            .serviceWithCustomClientWithoutCredentials(client, environment, mockClock);
        doiRequestsService = doiRequestsServiceFactory.getService(EMPTY_CREDENTIALS);
        handler = new UpdateDoiRequestHandler(environment, stsClient, doiRequestsServiceFactory);
    }

    @Test
    public void handleRequestReturnsBadRequestWhenPublicationIdIsInvalid() throws IOException {
        var updateDoiRequest = createApproveDoiRequest();

        GatewayResponse<Problem> response = sendRequest(updateDoiRequest,
            INVALID_PUBLICATION_IDENTIFIER, USERNAME_NOT_IMPORTANT);

        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.SC_BAD_REQUEST)));
        final Problem details = response.getBodyObject(Problem.class);

        assertThat(details.getDetail(), containsString(INVALID_PUBLICATION_ID_ERROR));
        assertThat(details.getDetail(), containsString(INVALID_PUBLICATION_IDENTIFIER));
    }

    @Test
    public void handleRequestReturnsNotFoundWhenPublicationDoesNotExist() throws IOException {
        var notExistingPublicationIdentifier = UUID.randomUUID().toString();
        ApiUpdateDoiRequest updateDoiRequest = createApproveDoiRequest();

        GatewayResponse<Problem> response = sendRequest(updateDoiRequest, notExistingPublicationIdentifier,
            USERNAME_NOT_IMPORTANT);

        final Problem details = response.getBodyObject(Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.SC_NOT_FOUND)));

        assertThat(details.getDetail(),
            containsString(PUBLICATION_NOT_FOUND_ERROR_MESSAGE + notExistingPublicationIdentifier));
    }

    @Test
    public void handleRequestReturnsBadRequestWhenPublicationDoesNotHaveDoiRequest() throws IOException {
        var publication = insertPublicationWithoutDoiRequest(
            getFixedClockWithDefaultTimeZone(mockOneHourBefore));

        ApiUpdateDoiRequest updateDoiRequest = createApproveDoiRequest();

        GatewayResponse<Problem> response = sendRequest(updateDoiRequest, publication.getIdentifier().toString(),
            publication.getOwner(), APPROVE_DOI_REQUEST);

        final Problem details = response.getBodyObject(Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.SC_BAD_REQUEST)));

        assertThat(details.getDetail(),
            containsString("You must initiate creation of a DoiRequest before you can update it."));
    }

    @Test
    public void handleRequestReturnsBadRequestMissingDoiRequest() throws IOException {
        var publication =
            insertPublicationWithoutDoiRequest(getFixedClockWithDefaultTimeZone(mockOneHourBefore));

        var updateDoiRequest = createInvalidUpdateDoiRequest();

        GatewayResponse<Problem> response = sendRequest(updateDoiRequest, publication, publication.getOwner());

        final Problem details = response.getBodyObject(Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.SC_BAD_REQUEST)));

        assertThat(details.getDetail(),
            containsString("You must request changes to do"));
    }

    @Test
    public void handleRequestReturnsForbiddenWhenUserRequestsApprovalForDoiRequestButHasNoRight()
        throws IOException {
        TestAppender appender = LogUtils.getTestingAppender(DynamoDBDoiRequestsService.class);
        Publication publication = insertPublicationWithDoiRequest(
            getFixedClockWithDefaultTimeZone(mockOneHourBefore));
        ApiUpdateDoiRequest updateDoiRequest = createApproveDoiRequest();
        GatewayResponse<Problem> response = sendRequest(updateDoiRequest, publication, NOT_THE_PUBLICATION_OWNER,
            READ_DOI_REQUEST);

        Problem problem = response.getBodyObject(Problem.class);
        assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_FORBIDDEN));
        assertThat(problem.getDetail(), is(equalTo(ForbiddenException.DEFAULT_MESSAGE)));
        assertThatLogsContainReasonForForbiddenMessage(appender, NOT_THE_PUBLICATION_OWNER);
        assertThatProblemDetailsDoesNotRevealSensitiveInformation(problem, NOT_THE_PUBLICATION_OWNER, PUBLISHER_ID);
    }

    @Test
    public void handleRequestDoesNotChangeDoiRequestStatusWhenUserRequestsApprovalForDoiRequestButHasNoRight()
        throws IOException, NotFoundException {
        Publication publication = insertPublicationWithDoiRequest(
            getFixedClockWithDefaultTimeZone(mockOneHourBefore));
        ApiUpdateDoiRequest updateDoiRequest = createApproveDoiRequest();
        sendRequest(updateDoiRequest, publication, NOT_THE_PUBLICATION_OWNER, READ_DOI_REQUEST);

        DoiRequest doiRequest = doiRequestsService.fetchDoiRequestByPublicationIdentifier(publication.getIdentifier())
            .map(Publication::getDoiRequest)
            .orElseThrow();

        assertThat(doiRequest.getStatus(), is(equalTo(REQUESTED)));
    }

    @Test
    public void handleRequestReturnsAcceptedWhenUserRequestsApprovalForDoiRequestAndHasApprovalRight()
        throws IOException {
        Publication publication = insertPublicationWithDoiRequest(getFixedClockWithDefaultTimeZone(mockOneHourBefore));
        ApiUpdateDoiRequest updateDoiRequest = createApproveDoiRequest();
        GatewayResponse<Void> response = sendRequest(updateDoiRequest, publication, NOT_THE_PUBLICATION_OWNER,
            APPROVE_DOI_REQUEST);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_ACCEPTED)));
    }

    @Test
    public void handleRequestUpdatesDoiRequestStatusWhenUserRequestsApprovalForDoiRequestAndHasApprovalRight()
        throws IOException, NotFoundException {
        Publication publication = insertPublicationWithDoiRequest(getFixedClockWithDefaultTimeZone(mockOneHourBefore));

        ApiUpdateDoiRequest updateDoiRequest = createApproveDoiRequest();
        sendRequest(updateDoiRequest, publication, NOT_THE_PUBLICATION_OWNER, APPROVE_DOI_REQUEST);

        DoiRequest actualDoiRequest = doiRequestsService.fetchDoiRequestByPublicationIdentifier(
            publication.getIdentifier())
            .map(Publication::getDoiRequest)
            .orElseThrow();

        assertThat(actualDoiRequest.getStatus(), is(equalTo(APPROVED)));
    }

    @Test
    public void handleRequestReturnsForbiddenExceptionWhenUsernameIsNotInRequestContext()
        throws IOException {
        final TestAppender appender = LogUtils.getTestingAppender(UpdateDoiRequestHandler.class);
        Publication publication = insertPublicationWithoutDoiRequest(
            getFixedClockWithDefaultTimeZone(mockOneHourBefore));

        ApiUpdateDoiRequest updateDoiRequest = createApproveDoiRequest();

        var requestContextMissingUsername = objectMapper.createObjectNode();
        GatewayResponse<Problem> response = sendRequest(updateDoiRequest, publication, requestContextMissingUsername);

        final Problem details = response.getBodyObject(Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.SC_FORBIDDEN)));

        assertThat(details.getDetail(), is(equalTo("Forbidden")));
        assertThat(appender.getMessages(),
            containsString("Missing from requestContext: /authorizer/claims/custom:feideId"));
    }

    @Test
    public void handleRequestSucessfullyUpdatesStatusWhenPublicationIdIsValid() throws IOException, NotFoundException {
        var publication = insertPublicationWithDoiRequest(getFixedClockWithDefaultTimeZone(mockNow));

        ApiUpdateDoiRequest updateRequest = createApproveDoiRequest();

        GatewayResponse<Void> response = sendRequest(updateRequest, publication, validUsername(publication),
            APPROVE_DOI_REQUEST);

        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.SC_ACCEPTED)));

        var actualUpdatedPublication = readPublicationDirectlyFromDynamo(publication.getIdentifier());
        var expectedUpdatedPublication = expectedUpdatedPublication(publication, actualUpdatedPublication);

        assertThat(actualUpdatedPublication, is(equalTo(expectedUpdatedPublication)));

        assertThat(response.getHeaders(), hasEntry(HttpHeaders.LOCATION,
            FAKE_ENV_SCHEMA_AND_HOST + publication.getIdentifier().toString()));
    }

    private ApiUpdateDoiRequest createApproveDoiRequest() {
        var updateDoiRequest = new ApiUpdateDoiRequest();
        updateDoiRequest.setDoiRequestStatus(APPROVED);
        return updateDoiRequest;
    }

    private ApiUpdateDoiRequest createInvalidUpdateDoiRequest() {
        return new ApiUpdateDoiRequest();
    }

    private Clock getFixedClockWithDefaultTimeZone(Instant instant) {
        return Clock.fixed(instant, ZoneId.systemDefault());
    }

    private void assertThatProblemDetailsDoesNotRevealSensitiveInformation(Problem details,
                                                                           String username,
                                                                           String publicationOwner) {
        String errorMessage = details.getDetail();
        assertThat(errorMessage, not(containsString(username)));
        assertThat(errorMessage, not(containsString(publicationOwner)));
    }

    private void assertThatLogsContainReasonForForbiddenMessage(TestAppender appender, String username) {

        assertThat(appender.getMessages(), containsString(USER_NOT_ALLOWED_TO_APPROVE_DOI_REQUEST));
        assertThat(appender.getMessages(), containsString(username));
    }

    private String validUsername(Publication publication) {
        return publication.getOwner();
    }

    private Publication readPublicationDirectlyFromDynamo(UUID identifier) throws NotFoundException {
        return doiRequestsService.fetchDoiRequestByPublicationIdentifier(identifier).orElseThrow();
    }

    private Publication expectedUpdatedPublication(Publication originalPublication,
                                                   Publication updatedPublication) {
        //modified dates need to be copied from the actual updated publication because the modifiedDate
        //cannot be controlled

        DoiRequest expectedDoiRequestWithUnsyncedModifiedDate = new DoiRequest.Builder()
            .withCreatedDate(mockNow)
            .withStatus(APPROVED)
            .build();

        Publication expectedPublicationWithWrongDates = originalPublication
            .copy()
            .withDoiRequest(expectedDoiRequestWithUnsyncedModifiedDate)
            .build();

        return syncUnControllableDates(expectedPublicationWithWrongDates, updatedPublication);
    }

    // copy actual modified dates because they are set in Publication class by uncontrollable clock.
    private Publication syncUnControllableDates(Publication expectedPublicationWithWrongDates,
                                                Publication updatedPublication) {

        DoiRequest correctedDoiRequest = expectedPublicationWithWrongDates
            .getDoiRequest().copy().withModifiedDate(updatedPublication.getDoiRequest().getModifiedDate())
            .build();

        return expectedPublicationWithWrongDates.copy()
            .withModifiedDate(updatedPublication.getModifiedDate())
            .withDoiRequest(correctedDoiRequest)
            .build();
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

    private <T> GatewayResponse<T> sendRequest(ApiUpdateDoiRequest doiRequest,
                                               Publication publication,
                                               String username,
                                               AccessRight... accessRights)
        throws IOException {
        return sendRequest(doiRequest, publication.getIdentifier().toString(), username, accessRights);
    }

    private <T> GatewayResponse<T> sendRequest(ApiUpdateDoiRequest doiRequest,
                                               String publicationIdentifier,
                                               String username,
                                               AccessRight... accessRights)
        throws IOException {
        var pathParams = Map.of(API_PUBLICATION_PATH_IDENTIFIER, publicationIdentifier);
        InputStream input = createRequest(doiRequest, pathParams, username, accessRights);
        ByteArrayOutputStream output = outputStream();
        handler.handleRequest(input, output, context);

        return GatewayResponse.fromOutputStream(output);
    }

    private <T> GatewayResponse<T> sendRequest(ApiUpdateDoiRequest doiRequest,
                                               Publication publication,
                                               ObjectNode requestContext)
        throws IOException {
        return sendRequest(doiRequest, publication.getIdentifier().toString(), requestContext);
    }

    private <T> GatewayResponse<T> sendRequest(ApiUpdateDoiRequest doiRequest,
                                               String publicationIdentifier,
                                               ObjectNode requestContext)
        throws IOException {
        var pathParams = Map.of(API_PUBLICATION_PATH_IDENTIFIER, publicationIdentifier);
        InputStream input = createRequest(doiRequest, pathParams, requestContext);
        ByteArrayOutputStream output = outputStream();
        handler.handleRequest(input, output, context);

        return GatewayResponse.fromOutputStream(output);
    }

    private InputStream createRequest(ApiUpdateDoiRequest doiRequest,
                                      Map<String, String> pathParameters,
                                      String username,
                                      AccessRight... accessRights)
        throws JsonProcessingException {
        var requestContext = objectMapper.createObjectNode();
        ObjectNode claims = requestContext
            .putObject("authorizer")
            .putObject("claims");

        claims.put(RequestInfo.FEIDE_ID_CLAIM, username);
        claims.put(RequestInfo.CUSTOMER_ID_CLAIM, "http://some.customer.id");
        claims.put(RequestInfo.ACCESS_RIGHTS_CLAIM, accessRightsToCsv(accessRights));

        return createRequest(doiRequest, pathParameters, requestContext);
    }

    private String accessRightsToCsv(AccessRight[] accessRights) {
        if (isNull(accessRights)) {
            return EMPTY_STRING;
        }
        return Stream.of(accessRights)
            .map(AccessRight::toString)
            .collect(Collectors.joining(COMMA_SEPARATOR));
    }

    private InputStream createRequest(ApiUpdateDoiRequest doiRequest,
                                      Map<String, String> pathParameters,
                                      ObjectNode requestContext) throws JsonProcessingException {

        return new HandlerRequestBuilder<ApiUpdateDoiRequest>(objectMapper)
            .withBody(doiRequest)
            .withPathParameters(pathParameters)
            .withRequestContext(requestContext)
            .build();
    }

    private ByteArrayOutputStream outputStream() {
        return new ByteArrayOutputStream();
    }
}
