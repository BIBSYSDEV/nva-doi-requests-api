package no.unit.nva.doi.requests.handlers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.handlers.GatewayResponse;
import nva.commons.utils.Environment;
import nva.commons.utils.JsonUtils;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

public class CreateDoiRequestHandlerTest {

    public static final String DEFAULT_ENV_VARIABLE_VALUE = "*";
    private static final String DEFAULT_PUBLICATION_ID = "SomePublicationId";
    private CreateDoiRequestHandler handler;
    private Context context;


    public CreateDoiRequestHandlerTest() {
        Environment environment = mockEnvironment();
        this.handler = new CreateDoiRequestHandler(environment);
        this.context = mock(Context.class);
    }

    @Test
    public void handleRequestReturnsBadRequestWhenPublicationIdIsEmpty() throws IOException {
        CreateDoiRequest doiRequest = requestWithoutPublicationId();
        InputStream input = createRequest(doiRequest);

        ByteArrayOutputStream output = outpuStream();
        handler.handleRequest(input, output, context);

        GatewayResponse<Problem> response = GatewayResponse.fromOutputStream(output);
        final Problem details = response.getBodyObject(Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.SC_BAD_REQUEST)));

        assertThat(details.getDetail(), containsString(CreateDoiRequest.EMPTY_PUBLICATION_ID));
    }

    @Test
    public void handleRequestReturnsOkIfPublicationIdIsNotEmpty() throws IOException {
        CreateDoiRequest doiRequest = requestWIthPublicationId();
        InputStream input = createRequest(doiRequest);

        ByteArrayOutputStream output = outpuStream();
        handler.handleRequest(input, output, context);

        GatewayResponse<Void> response = GatewayResponse.fromOutputStream(output);
        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.SC_CREATED)));
    }

    private Environment mockEnvironment() {
        Environment environment = mock(Environment.class);
        when(environment.readEnv(anyString())).thenReturn(DEFAULT_ENV_VARIABLE_VALUE);

        return environment;
    }

    private CreateDoiRequest requestWithoutPublicationId() {
        return new CreateDoiRequest();
    }

    private InputStream createRequest(CreateDoiRequest doiRequest)
        throws com.fasterxml.jackson.core.JsonProcessingException {
        return new HandlerRequestBuilder<CreateDoiRequest>(JsonUtils.objectMapper)
            .withBody(doiRequest)
            .build();
    }

    private CreateDoiRequest requestWIthPublicationId() {
        CreateDoiRequest doiRequest = new CreateDoiRequest();
        doiRequest.setPublicationId(DEFAULT_PUBLICATION_ID);
        return doiRequest;
    }

    private ByteArrayOutputStream outpuStream() {
        return new ByteArrayOutputStream();
    }
}
