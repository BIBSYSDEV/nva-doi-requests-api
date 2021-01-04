package no.unit.nva.doi.requests.handlers;

import static no.unit.nva.doi.requests.handlers.UpdateDoiRequestHandler.API_PUBLICATION_PATH_IDENTIFIER;
import static no.unit.nva.doi.requests.util.MockEnvironment.mockEnvironment;
import static nva.commons.utils.JsonUtils.objectMapper;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import no.unit.nva.doi.requests.model.ApiUpdateDoiRequest;
import no.unit.nva.doi.requests.service.impl.DynamoDBDoiRequestsService;
import no.unit.nva.doi.requests.service.impl.DynamoDbDoiRequestsServiceFactory;
import no.unit.nva.doi.requests.util.DoiRequestsDynamoDBLocal;
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

public class DoiRequestMessageHandlerTest extends UpdateDoiTestUtils {

    public static final String PUBLICATION_OWNER = "publication@owner.com";
    public static final AWSCredentialsProvider IGNORED_CREDENTIALS = null;
    private DoiRequestMessageHandler handler;
    private ByteArrayOutputStream outputStream;

    private final FakeStsClient stsClient = new FakeStsClient();
    private final Environment environment = mockEnvironment();
    private final Context context = mock(Context.class);
    private final Logger logger = mock(Logger.class);
    private DynamoDBDoiRequestsService handlerService;

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
    public void handlerSavesMessageWhenInputContainsMessage() throws IOException, NotFoundException {
        Publication publication = insertPublicationWithDoiRequest(mockClock);

        String expectedMessage = UUID.randomUUID().toString();
        InputStream request = createRequest(expectedMessage, publication);
        handler.handleRequest(request, outputStream, context);

        Publication actualPublication = handlerService.fetchDoiRequestByPublicationIdentifier(
            publication.getIdentifier()).orElseThrow();

        assertThatActualPublicationHasExpectedMessage(expectedMessage, actualPublication);
    }


    /*
           TODO:
           handler saves message when message exists and user is the publication owner
           handler saves message when message exists and user is a curator
           handler returns accepted when message exists and user is the publication owner
           handler returns accepted when message exists and user is a curator
           handler returns Forbidden when user is not authorized (not the owner or a curator)
           handler returns BadRequest when user

     */

    private void assertThatActualPublicationHasExpectedMessage(String expectedMessage, Publication actualPublication) {
        String actualMessage = actualPublication.getDoiRequest().getMessages()
            .stream()
            .map(DoiRequestMessage::getText)
            .filter(messageText -> messageText.equals(expectedMessage))
            .collect(SingletonCollector.collect());
        assertThat(actualMessage, is(equalTo(expectedMessage)));
    }

    private InputStream createRequest(String message, Publication publication) throws JsonProcessingException {
        ApiUpdateDoiRequest updateDoiRequest = new ApiUpdateDoiRequest();
        updateDoiRequest.setMessage(message);
        Map<String, String> pathParams =
            Map.of(API_PUBLICATION_PATH_IDENTIFIER, publication.getIdentifier().toString());

        return new HandlerRequestBuilder<ApiUpdateDoiRequest>(objectMapper)
            .withBody(updateDoiRequest)
            .withFeideId(PUBLICATION_OWNER)
            .withAccessRight(AccessRight.READ_DOI_REQUEST.toString())
            .withPathParameters(pathParams)
            .build();
    }
}