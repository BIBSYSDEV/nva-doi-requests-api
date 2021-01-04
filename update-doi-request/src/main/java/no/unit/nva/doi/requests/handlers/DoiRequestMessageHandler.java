package no.unit.nva.doi.requests.handlers;

import static java.util.Objects.isNull;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import no.unit.nva.doi.requests.exception.BadRequestException;
import no.unit.nva.doi.requests.service.impl.DynamoDBDoiRequestsService;
import no.unit.nva.doi.requests.service.impl.DynamoDbDoiRequestsServiceFactory;
import no.unit.nva.doi.requests.service.impl.UserInstance;
import no.unit.nva.useraccessmanagement.dao.AccessRight;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.handlers.RequestInfo;
import nva.commons.utils.Environment;
import nva.commons.utils.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DoiRequestMessageHandler extends UpdateDoiRequestHandler<DoiRequestMessageDto> {

    public static final String NO_MESSAGE_ERROR = "Message missing";
    private final DynamoDbDoiRequestsServiceFactory serviceFactory;
    private static final Logger LOGGER = LoggerFactory.getLogger(DoiRequestMessageHandler.class);

    @JacocoGenerated
    public DoiRequestMessageHandler() {
        this(new Environment());
    }

    @JacocoGenerated
    public DoiRequestMessageHandler(Environment environment) {
        this(environment, defaultStsClient(), defaultServiceFactory(), LOGGER);
    }

    protected DoiRequestMessageHandler(Environment environment,
                                       AWSSecurityTokenService stsClient,
                                       DynamoDbDoiRequestsServiceFactory serviceFactory,
                                       Logger logger) {
        super(DoiRequestMessageDto.class, environment, stsClient, logger);
        this.serviceFactory = serviceFactory;
    }

    @Override
    protected Integer getSuccessStatusCode(DoiRequestMessageDto input, Void output) {
        return HttpURLConnection.HTTP_ACCEPTED;
    }

    @Override
    protected Void processInput(DoiRequestMessageDto input, RequestInfo requestInfo,
                                STSAssumeRoleSessionCredentialsProvider credentialsProvider, Context context)
        throws ApiGatewayException {
        DynamoDBDoiRequestsService service = serviceFactory.getService(credentialsProvider);

        String userId = getUserName(requestInfo);
        URI publisherId = requestInfo.getCustomerId().map(URI::create).orElse(null);
        Set<AccessRight> accessRights = extractAccessRights(requestInfo);
        String message = extractMessage(input);
        UUID publicationId = getPublicationIdentifier(requestInfo);
        UserInstance userInstance = new UserInstance(userId, publisherId, accessRights);
        service.addMessage(publicationId, message, userInstance);
        return null;
    }

    private String extractMessage(DoiRequestMessageDto input) throws BadRequestException {
        if (isNull(input.getMessage())) {
            throw new BadRequestException(NO_MESSAGE_ERROR);
        }
        return input.getMessage();
    }

    private Set<AccessRight> extractAccessRights(RequestInfo requestInfo) {
        return requestInfo.getAccessRights()
            .stream()
            .map(AccessRight::fromString)
            .collect(Collectors.toSet());
    }
}
