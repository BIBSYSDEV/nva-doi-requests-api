package no.unit.nva.doi.requests.handlers;

import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import java.net.HttpURLConnection;
import java.util.UUID;
import no.unit.nva.doi.requests.model.ApiUpdateDoiRequest;
import no.unit.nva.doi.requests.service.impl.DynamoDBDoiRequestsService;
import no.unit.nva.doi.requests.service.impl.DynamoDbDoiRequestsServiceFactory;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.handlers.RequestInfo;
import nva.commons.utils.Environment;
import org.slf4j.Logger;

public class DoiRequestMessageHandler extends UpdateDoiRequestHandler {

    private final DynamoDbDoiRequestsServiceFactory serviceFactory;

    protected DoiRequestMessageHandler(Environment environment,
                                       AWSSecurityTokenService stsClient,
                                       DynamoDbDoiRequestsServiceFactory serviceFactory,
                                       Logger logger) {
        super(ApiUpdateDoiRequest.class, environment, stsClient, logger);
        this.serviceFactory = serviceFactory;
    }

    @Override
    protected Integer getSuccessStatusCode(ApiUpdateDoiRequest input, Void output) {
        return HttpURLConnection.HTTP_ACCEPTED;
    }

    @Override
    protected Void processInput(ApiUpdateDoiRequest input, RequestInfo requestInfo,
                                STSAssumeRoleSessionCredentialsProvider credentialsProvider, Context context)
        throws ApiGatewayException {
        DynamoDBDoiRequestsService service = serviceFactory.getService(credentialsProvider);
        String userId = getUserName(requestInfo);
        String message = input.getMessage().orElseThrow();
        UUID publicationId = getPublicationIdentifier(requestInfo);
        service.addMessage(publicationId, message, userId);
        return null;
    }
}
