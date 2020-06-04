package no.unit.nva.doi.requests;

import static no.unit.nva.testutils.TestHeaders.getRequestHeaders;
import static nva.commons.utils.JsonUtils.objectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import no.unit.nva.doi.requests.exception.DynamoDBException;
import no.unit.nva.doi.requests.model.DoiRequestsResponse;
import no.unit.nva.doi.requests.service.DoiRequestsService;
import no.unit.nva.model.DoiRequestStatus;
import no.unit.nva.model.util.OrgNumberMapper;
import no.unit.nva.testutils.HandlerRequestBuilder;
import no.unit.nva.testutils.TestContext;
import no.unit.nva.testutils.TestHeaders;
import nva.commons.handlers.ApiGatewayHandler;
import nva.commons.handlers.GatewayResponse;
import nva.commons.utils.Environment;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FindDoiRequestsHandlerTest {

    public static final String CREATOR = "creator";
    public static final String ROLE = "role";
    public static final String AUTHORIZER = "authorizer";
    public static final String CLAIMS = "claims";
    public static final String CUSTOM_FEIDE_ID = "custom:feideId";
    public static final String CUSTOM_ORG_NUMBER = "custom:orgNumber";
    public static final String CUSTOM_APPLICATION_ROLES = "custom:applicationRoles";
    public static final String JUNIT = "junit";
    public static final String CURATOR = "curator";
    public static final String INVALID_ROLE = "invalid_role";
    public static final String EDITOR = "editor";

    private DoiRequestsService doiRequestsService;
    private Environment environment;
    private FindDoiRequestsHandler handler;
    private ByteArrayOutputStream outputStream;
    private Context context;

    @BeforeEach
    public void setUp() throws Exception {
        doiRequestsService = mock(DoiRequestsService.class);
        environment = mock(Environment.class);
        when(environment.readEnv(ApiGatewayHandler.ALLOWED_ORIGIN_ENV)).thenReturn("*");
        handler = new FindDoiRequestsHandler(doiRequestsService, environment);
        outputStream = new ByteArrayOutputStream();
        context = new TestContext();
    }

    @Test
    public void handleRequestReturnsStatusOKOnValidCreatorRoleInput() throws Exception {
        when(doiRequestsService.findDoiRequestsByStatusAndOwner(
            any(URI.class), any(DoiRequestStatus.class), anyString()
        )).thenReturn(new DoiRequestsResponse());

        InputStream inputStream = new HandlerRequestBuilder<Void>(objectMapper)
            .withHeaders(getRequestHeaders())
            .withQueryParameters(Map.of(ROLE, CREATOR))
            .withRequestContext(getRequestContext())
            .build();

        handler.handleRequest(inputStream, outputStream, context);

        GatewayResponse<DoiRequestsResponse> actual = objectMapper.readValue(
            outputStream.toByteArray(),
            GatewayResponse.class);

        GatewayResponse<DoiRequestsResponse> expected = new GatewayResponse<>(
            new DoiRequestsResponse(),
            TestHeaders.getResponseHeaders(),
            HttpStatus.SC_OK
        );

        assertEquals(expected, actual);
    }

    @Test
    public void handleRequestReturnsStatusOKOnValidCuratorRoleInput() throws Exception {
        when(doiRequestsService.findDoiRequestsByStatusAndOwner(
            any(URI.class), any(DoiRequestStatus.class), anyString()
        )).thenReturn(new DoiRequestsResponse());

        InputStream inputStream = new HandlerRequestBuilder<Void>(objectMapper)
            .withHeaders(getRequestHeaders())
            .withQueryParameters(Map.of(ROLE, CURATOR))
            .withRequestContext(getRequestContext())
            .build();

        handler.handleRequest(inputStream, outputStream, context);

        GatewayResponse<DoiRequestsResponse> actual = objectMapper.readValue(
            outputStream.toByteArray(),
            GatewayResponse.class);

        GatewayResponse<DoiRequestsResponse> expected = new GatewayResponse<>(
            new DoiRequestsResponse(),
            TestHeaders.getResponseHeaders(),
            HttpStatus.SC_OK
        );

        assertEquals(expected, actual);
    }

    @Test
    public void handleRequestReturnsStatusOKOnValidEditorRoleInput() throws Exception {
        when(doiRequestsService.findDoiRequestsByStatusAndOwner(
            any(URI.class), any(DoiRequestStatus.class), anyString()
        )).thenReturn(new DoiRequestsResponse());

        InputStream inputStream = new HandlerRequestBuilder<Void>(objectMapper)
            .withHeaders(getRequestHeaders())
            .withQueryParameters(Map.of(ROLE, EDITOR))
            .withRequestContext(getRequestContext())
            .build();

        handler.handleRequest(inputStream, outputStream, context);

        GatewayResponse<DoiRequestsResponse> actual = objectMapper.readValue(
            outputStream.toByteArray(),
            GatewayResponse.class);

        GatewayResponse<DoiRequestsResponse> expected = new GatewayResponse<>(
            new DoiRequestsResponse(),
            TestHeaders.getResponseHeaders(),
            HttpStatus.SC_OK
        );

        assertEquals(expected, actual);
    }

    @Test
    public void handleRequestReturnsStatusBadRequestOnInvalidRequestContext() throws Exception {
        when(doiRequestsService.findDoiRequestsByStatusAndOwner(
            any(URI.class), any(DoiRequestStatus.class), anyString()
        )).thenReturn(new DoiRequestsResponse());

        InputStream inputStream = new HandlerRequestBuilder<Void>(objectMapper)
            .withHeaders(getRequestHeaders())
            .withQueryParameters(Map.of(ROLE, CREATOR))
            .build();

        handler.handleRequest(inputStream, outputStream, context);

        GatewayResponse<DoiRequestsResponse> actual = objectMapper.readValue(
            outputStream.toByteArray(),
            GatewayResponse.class);

        assertEquals(HttpStatus.SC_BAD_REQUEST, actual.getStatusCode());
    }

    @Test
    public void handleRequestReturnsStatusUnauthorizedOnInvalidRoleRequested() throws Exception {
        when(doiRequestsService.findDoiRequestsByStatusAndOwner(
            any(URI.class), any(DoiRequestStatus.class), anyString()
        )).thenReturn(new DoiRequestsResponse());

        InputStream inputStream = new HandlerRequestBuilder<Void>(objectMapper)
            .withHeaders(getRequestHeaders())
            .withQueryParameters(Map.of(ROLE, INVALID_ROLE))
            .withRequestContext(getRequestContext())
            .build();

        handler.handleRequest(inputStream, outputStream, context);

        GatewayResponse<DoiRequestsResponse> actual = objectMapper.readValue(
            outputStream.toByteArray(),
            GatewayResponse.class);

        assertEquals(HttpStatus.SC_UNAUTHORIZED, actual.getStatusCode());
    }

    @Test
    public void handleRequestReturnsStatusBadGatewayOnServiceError() throws Exception {
        when(doiRequestsService.findDoiRequestsByStatusAndOwner(
            any(URI.class), any(DoiRequestStatus.class), anyString()
        )).thenThrow(DynamoDBException.class);

        InputStream inputStream = new HandlerRequestBuilder<Void>(objectMapper)
            .withHeaders(getRequestHeaders())
            .withQueryParameters(Map.of(ROLE, CREATOR))
            .withRequestContext(getRequestContext())
            .build();

        handler.handleRequest(inputStream, outputStream, context);

        GatewayResponse<DoiRequestsResponse> actual = objectMapper.readValue(
            outputStream.toByteArray(),
            GatewayResponse.class);

        assertEquals(HttpStatus.SC_BAD_GATEWAY, actual.getStatusCode());
    }

    private Map<String, Object> getRequestContext() {
        return Map.of(AUTHORIZER, Map.of(
            CLAIMS, Map.of(
                CUSTOM_FEIDE_ID, JUNIT,
                CUSTOM_ORG_NUMBER, OrgNumberMapper.UNIT_ORG_NUMBER,
                CUSTOM_APPLICATION_ROLES, String.join(",", CREATOR, CURATOR, EDITOR)
            ))
        );
    }
}
