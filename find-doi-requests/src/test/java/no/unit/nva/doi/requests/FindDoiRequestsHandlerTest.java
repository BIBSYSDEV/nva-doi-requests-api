package no.unit.nva.doi.requests;

import static no.unit.nva.testutils.TestHeaders.getRequestHeaders;
import static nva.commons.handlers.AuthorizedApiGatewayHandler.ASSUMED_ROLE_ARN_ENV_VAR;
import static nva.commons.utils.JsonUtils.objectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import no.unit.nva.doi.requests.exception.DynamoDBException;
import no.unit.nva.doi.requests.model.DoiRequestsResponse;
import no.unit.nva.doi.requests.service.impl.DynamoDBDoiRequestsService;
import no.unit.nva.doi.requests.service.impl.DynamoDbDoiRequestsServiceFactory;
import no.unit.nva.doi.requests.util.RequestContextUtils;
import no.unit.nva.model.DoiRequestStatus;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.stubs.FakeStsClient;
import no.unit.nva.testutils.HandlerRequestBuilder;
import no.unit.nva.testutils.TestHeaders;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.exceptions.GatewayResponseSerializingException;
import nva.commons.handlers.ApiGatewayHandler;
import nva.commons.handlers.GatewayResponse;
import nva.commons.utils.Environment;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

public class FindDoiRequestsHandlerTest {

    public static final String CREATOR = "creator";
    public static final String ROLE = "role";

    public static final String CURATOR = "curator";
    public static final String INVALID_ROLE = "invalid_role";
    public static final String EDITOR = "editor";
    public static final String SOME_ASSUMED_AWS_IAM_ROLE = "SomeAssumedAwsIamRole";

    private FindDoiRequestsHandler handler;
    private ByteArrayOutputStream outputStream;
    private Context context;
    private DynamoDbDoiRequestsServiceFactory factory;

    /**
     * Set up environment for test.
     */
    @BeforeEach
    public void setUp() {

        Environment environment = mockEnvironment();
        AWSSecurityTokenService fakeStsClient = new FakeStsClient();
        factory = createDefaultFactory();
        handler = new FindDoiRequestsHandler(environment, factory, fakeStsClient);
        outputStream = new ByteArrayOutputStream();
        context = new FakeContext();
    }

    @Test
    public void handleRequestReturnsStatusOKOnValidCreatorRoleInput() throws Exception {
        factory = prepareMocksWithOkResponse();

        InputStream inputStream = createRequestWithRequestedRoleAndAssignedRoles(CREATOR, CREATOR);
        FindDoiRequestsHandler handler = new FindDoiRequestsHandler(mockEnvironment(), factory, new FakeStsClient());
        handler.handleRequest(inputStream, outputStream, context);

        GatewayResponse<DoiRequestsResponse> actual = GatewayResponse.fromOutputStream(outputStream);
        GatewayResponse<DoiRequestsResponse> expected = createExpectedOkResponse();
        assertEquals(expected, actual);
    }

    @Test
    public void handleRequestReturnsStatusOKOnValidUpperCaseCreatorRoleInput() throws Exception {
        prepareMocksWithOkResponse();

        InputStream inputStream = createRequestWithRequestedRoleAndAssignedRoles(CREATOR.toUpperCase(), CREATOR);
        handler.handleRequest(inputStream, outputStream, context);

        GatewayResponse<DoiRequestsResponse> actual = GatewayResponse.fromOutputStream(outputStream);
        GatewayResponse<DoiRequestsResponse> expected = createExpectedOkResponse();
        assertEquals(expected, actual);
    }

    @Test
    public void handleRequestReturnsStatusOKOnValidCuratorRoleInput() throws Exception {
        prepareMocksWithOkResponse();

        InputStream inputStream = createRequestWithRequestedRoleAndAssignedRoles(CURATOR, CURATOR);
        handler.handleRequest(inputStream, outputStream, context);

        GatewayResponse<DoiRequestsResponse> actual = GatewayResponse.fromOutputStream(outputStream);
        GatewayResponse<DoiRequestsResponse> expected = createExpectedOkResponse();
        assertEquals(expected, actual);
    }

    @Test
    public void handleRequestReturnsStatusOKOnValidEditorRoleInput() throws Exception {
        prepareMocksWithOkResponse();

        InputStream inputStream = createRequestWithRequestedRoleAndAssignedRoles(EDITOR, EDITOR);
        handler.handleRequest(inputStream, outputStream, context);

        GatewayResponse<DoiRequestsResponse> actual = GatewayResponse.fromOutputStream(outputStream);
        GatewayResponse<DoiRequestsResponse> expected = createExpectedOkResponse();
        assertEquals(expected, actual);
    }

    @Test
    public void handleRequestReturnsStatusBadRequestOnInvalidRequestContext() throws Exception {
        prepareMocksWithOkResponse();

        InputStream inputStream = createRequestWithMissingRequestContext();
        handler.handleRequest(inputStream, outputStream, context);

        GatewayResponse<Problem> actual = GatewayResponse.fromOutputStream(outputStream);
        assertEquals(HttpStatus.SC_BAD_REQUEST, actual.getStatusCode());
    }

    @Test
    public void handleRequestReturnsStatusUnauthorizedOnInvalidRoleRequested() throws Exception {
        prepareMocksWithOkResponse();

        InputStream inputStream = createRequestWithRequestedRoleAndAssignedRoles(INVALID_ROLE, CREATOR);
        handler.handleRequest(inputStream, outputStream, context);

        GatewayResponse<Problem> actual = GatewayResponse.fromOutputStream(outputStream);
        assertEquals(HttpStatus.SC_UNAUTHORIZED, actual.getStatusCode());
    }

    @Test
    public void handleRequestReturnsStatusBadGatewayOnServiceError() throws Exception {
        DynamoDbDoiRequestsServiceFactory factory = prepareMocksWithDatabaseError();
        FindDoiRequestsHandler handler = new FindDoiRequestsHandler(mockEnvironment(), factory, new FakeStsClient());

        InputStream inputStream = createRequestWithRequestedRoleAndAssignedRoles(CREATOR, CREATOR);
        handler.handleRequest(inputStream, outputStream, context);

        GatewayResponse<Problem> actual = GatewayResponse.fromOutputStream(outputStream);
        assertEquals(HttpStatus.SC_BAD_GATEWAY, actual.getStatusCode());
    }

    private Environment mockEnvironment() {
        Environment environment = mock(Environment.class);
        when(environment.readEnv(ApiGatewayHandler.ALLOWED_ORIGIN_ENV)).thenReturn("*");
        when(environment.readEnv(ASSUMED_ROLE_ARN_ENV_VAR)).thenReturn(SOME_ASSUMED_AWS_IAM_ROLE);
        return environment;
    }

    private DynamoDbDoiRequestsServiceFactory createDefaultFactory() {
        DynamoDBDoiRequestsService doiRequestsService = mock(DynamoDBDoiRequestsService.class);
        return new DynamoDbDoiRequestsServiceFactory(cred -> doiRequestsService);
    }

    private InputStream createRequestWithRequestedRoleAndAssignedRoles(String requestedRole, String... assignedRoles)
        throws JsonProcessingException {
        return new HandlerRequestBuilder<Void>(objectMapper)
            .withHeaders(getRequestHeaders())
            .withQueryParameters(Map.of(ROLE, requestedRole))
            .withRequestContext(RequestContextUtils.requestContext(assignedRoles))
            .build();
    }

    private InputStream createRequestWithMissingRequestContext() throws JsonProcessingException {
        return new HandlerRequestBuilder<Void>(objectMapper)
            .withHeaders(getRequestHeaders())
            .withQueryParameters(Map.of(ROLE, CREATOR))
            .build();
    }

    private GatewayResponse<DoiRequestsResponse> createExpectedOkResponse()
        throws GatewayResponseSerializingException {
        return new GatewayResponse<>(
            new DoiRequestsResponse(),
            TestHeaders.getResponseHeaders(),
            HttpStatus.SC_OK
        );
    }

    private DynamoDbDoiRequestsServiceFactory prepareMocksWithOkResponse() throws ApiGatewayException {
        DynamoDBDoiRequestsService doiRequestsService = mock(DynamoDBDoiRequestsService.class);
        when(doiRequestsService.findDoiRequestsByStatusAndOwner(
            any(URI.class), any(DoiRequestStatus.class), anyString()
        )).thenReturn(new DoiRequestsResponse());
        factory = new DynamoDbDoiRequestsServiceFactory(ignored -> doiRequestsService);
        return factory;
    }

    private DynamoDbDoiRequestsServiceFactory prepareMocksWithDatabaseError() throws ApiGatewayException {
        DynamoDBDoiRequestsService doiRequestsService = mock(DynamoDBDoiRequestsService.class);
        when(doiRequestsService.findDoiRequestsByStatusAndOwner(
            any(URI.class), any(DoiRequestStatus.class), anyString()
        )).thenThrow(DynamoDBException.class);
        factory = new DynamoDbDoiRequestsServiceFactory(ignored -> doiRequestsService);
        return factory;
    }
}
