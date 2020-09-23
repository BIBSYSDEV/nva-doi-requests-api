package no.unit.nva.doi.requests;

import static no.unit.nva.testutils.TestHeaders.getRequestHeaders;
import static nva.commons.utils.JsonUtils.objectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import no.unit.nva.doi.requests.exception.DynamoDBException;
import no.unit.nva.doi.requests.model.DoiRequestsResponse;
import no.unit.nva.doi.requests.service.DoiRequestsService;
import no.unit.nva.model.DoiRequestStatus;
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
    public static final String AUTHORIZER = "authorizer";
    public static final String CLAIMS = "claims";
    public static final String CUSTOM_FEIDE_ID = "custom:feideId";
    public static final String CUSTOM_CUSTOMER_ID = "custom:customerId";
    public static final String CUSTOM_APPLICATION_ROLES = "custom:applicationRoles";
    public static final String JUNIT = "junit";
    public static final String CURATOR = "curator";
    public static final String INVALID_ROLE = "invalid_role";
    public static final String EDITOR = "editor";
    public static final String SAMPLE_CUSTOMER_ID = "http://example.org/publisher/123";
    private DoiRequestsService doiRequestsService;
    private FindDoiRequestsHandler handler;
    private ByteArrayOutputStream outputStream;
    private Context context;

    /**
     * Set up environment for test.
     */
    @BeforeEach
    public void setUp() {
        doiRequestsService = mock(DoiRequestsService.class);
        Environment environment = mock(Environment.class);
        when(environment.readEnv(ApiGatewayHandler.ALLOWED_ORIGIN_ENV)).thenReturn("*");
        handler = new FindDoiRequestsHandler(doiRequestsService, environment);
        outputStream = new ByteArrayOutputStream();
        context = mock(Context.class);
    }

    @Test
    public void handleRequestReturnsStatusOKOnValidCreatorRoleInput() throws Exception {
        prepareMocksWithOkResponse();

        InputStream inputStream = createRequestWithRole(CREATOR);
        handler.handleRequest(inputStream, outputStream, context);

        GatewayResponse<DoiRequestsResponse> actual = GatewayResponse.fromOutputStream(outputStream);
        GatewayResponse<DoiRequestsResponse> expected = createExpectedOkResponse();
        assertEquals(expected, actual);
    }

    @Test
    public void handleRequestReturnsStatusOKOnValidUpperCaseCreatorRoleInput() throws Exception {
        prepareMocksWithOkResponse();

        InputStream inputStream = createRequestWithRole(CREATOR.toUpperCase());
        handler.handleRequest(inputStream, outputStream, context);

        GatewayResponse<DoiRequestsResponse> actual = GatewayResponse.fromOutputStream(outputStream);
        GatewayResponse<DoiRequestsResponse> expected = createExpectedOkResponse();
        assertEquals(expected, actual);
    }

    @Test
    public void handleRequestReturnsStatusOKOnValidCuratorRoleInput() throws Exception {
        prepareMocksWithOkResponse();

        InputStream inputStream = createRequestWithRole(CURATOR);
        handler.handleRequest(inputStream, outputStream, context);

        GatewayResponse<DoiRequestsResponse> actual = GatewayResponse.fromOutputStream(outputStream);
        GatewayResponse<DoiRequestsResponse> expected = createExpectedOkResponse();
        assertEquals(expected, actual);
    }

    @Test
    public void handleRequestReturnsStatusOKOnValidEditorRoleInput() throws Exception {
        prepareMocksWithOkResponse();

        InputStream inputStream = createRequestWithRole(EDITOR);
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

        InputStream inputStream = createRequestWithRole(INVALID_ROLE);
        handler.handleRequest(inputStream, outputStream, context);

        GatewayResponse<Problem> actual = GatewayResponse.fromOutputStream(outputStream);
        assertEquals(HttpStatus.SC_UNAUTHORIZED, actual.getStatusCode());
    }

    @Test
    public void handleRequestReturnsStatusBadGatewayOnServiceError() throws Exception {
        prepareMocksWithDatabaseError();

        InputStream inputStream = createRequestWithRole(CREATOR);
        handler.handleRequest(inputStream, outputStream, context);

        GatewayResponse<Problem> actual = GatewayResponse.fromOutputStream(outputStream);
        assertEquals(HttpStatus.SC_BAD_GATEWAY, actual.getStatusCode());
    }

    private InputStream createRequestWithRole(String creator) throws JsonProcessingException {
        return new HandlerRequestBuilder<Void>(objectMapper)
            .withHeaders(getRequestHeaders())
            .withQueryParameters(Map.of(ROLE, creator))
            .withRequestContext(getRequestContext())
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

    private void prepareMocksWithOkResponse() throws ApiGatewayException {
        when(doiRequestsService.findDoiRequestsByStatusAndOwner(
            any(URI.class), any(DoiRequestStatus.class), anyString()
        )).thenReturn(new DoiRequestsResponse());
    }

    private void prepareMocksWithDatabaseError() throws ApiGatewayException {
        when(doiRequestsService.findDoiRequestsByStatusAndOwner(
            any(URI.class), any(DoiRequestStatus.class), anyString()
        )).thenThrow(DynamoDBException.class);
    }

    private Map<String, Object> getRequestContext() {
        return Map.of(AUTHORIZER, Map.of(
            CLAIMS, Map.of(
                CUSTOM_FEIDE_ID, JUNIT,
                CUSTOM_CUSTOMER_ID, SAMPLE_CUSTOMER_ID,
                CUSTOM_APPLICATION_ROLES, String.join(",", CREATOR, CURATOR, EDITOR)
            ))
        );
    }
}
