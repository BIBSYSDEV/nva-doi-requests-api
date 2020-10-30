package no.unit.nva.doi.requests.handlers;

import static nva.commons.utils.attempt.Try.attempt;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import java.util.Collections;
import java.util.UUID;
import no.unit.nva.doi.requests.contants.ServiceConstants;
import no.unit.nva.doi.requests.exception.BadRequestException;
import no.unit.nva.doi.requests.model.ApiUpdateDoiRequest;
import no.unit.nva.doi.requests.service.impl.DynamoDBDoiRequestsService;
import no.unit.nva.doi.requests.userdetails.UserDetails;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.exceptions.ForbiddenException;
import nva.commons.exceptions.commonexceptions.NotFoundException;
import nva.commons.handlers.ApiGatewayHandler;
import nva.commons.handlers.RequestInfo;
import nva.commons.utils.Environment;
import nva.commons.utils.JacocoGenerated;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateDoiRequestHandler extends ApiGatewayHandler<ApiUpdateDoiRequest, Void> {

    public static final String INVALID_PUBLICATION_ID_ERROR = "Invalid publication id: ";
    public static final String API_PUBLICATION_PATH_IDENTIFIER = "publicationIdentifier";
    private static final String LOCATION_TEMPLATE_PUBLICATION = "%s://%s/publication/%s";
    private final String apiScheme;
    private final String apiHost;
    private final DynamoDBDoiRequestsService doiRequestsService;

    @JacocoGenerated
    public UpdateDoiRequestHandler() {
        this(new Environment());
    }

    @JacocoGenerated
    public UpdateDoiRequestHandler(Environment environment) {
        this(environment, newDynamoDbClientService(environment));
    }

    public UpdateDoiRequestHandler(Environment environment,
                                   DynamoDBDoiRequestsService doiRequestsService) {
        super(ApiUpdateDoiRequest.class, environment, initializeLogger());
        this.apiScheme = environment.readEnv(ServiceConstants.API_SCHEME_ENV_VARIABLE);
        this.apiHost = environment.readEnv(ServiceConstants.API_HOST_ENV_VARIABLE);
        this.doiRequestsService = doiRequestsService;
    }

    @Override
    protected Void processInput(ApiUpdateDoiRequest input,
                                RequestInfo requestInfo,
                                Context context)
        throws ApiGatewayException {

        try {
            input.validate();
            UUID publicationIdentifier = getPublicationIdentifier(requestInfo);
            updateDoiRequestStatus(input, requestInfo, publicationIdentifier);
            updateContentLocationHeader(publicationIdentifier);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e.getMessage());
        }
        return null;
    }

    private void updateDoiRequestStatus(ApiUpdateDoiRequest input, RequestInfo requestInfo, UUID publicationIdentifier)
        throws ForbiddenException, NotFoundException {
        String username = getUserName(requestInfo);
        doiRequestsService.updateDoiRequest(publicationIdentifier, input.getDoiRequestStatus(), username);
    }

    @Override
    protected Integer getSuccessStatusCode(ApiUpdateDoiRequest input, Void output) {
        return HttpStatus.SC_ACCEPTED;
    }

    @JacocoGenerated
    private static DynamoDBDoiRequestsService newDynamoDbClientService(Environment environment) {
        return new DynamoDBDoiRequestsService(AmazonDynamoDBClientBuilder.defaultClient(), environment);
    }

    private static Logger initializeLogger() {
        return LoggerFactory.getLogger(UpdateDoiRequestHandler.class);
    }

    private void updateContentLocationHeader(UUID publicationIdentifier) {
        setAdditionalHeadersSupplier(() ->
            Collections.singletonMap(HttpHeaders.LOCATION, getContentLocation(publicationIdentifier)));
    }

    private String getContentLocation(UUID publicationID) {
        return String.format(LOCATION_TEMPLATE_PUBLICATION, apiScheme, apiHost, publicationID.toString());
    }

    private UUID getPublicationIdentifier(RequestInfo requestInfo) throws BadRequestException {
        String publicationIdentifierString = requestInfo.getPathParameter(API_PUBLICATION_PATH_IDENTIFIER);
        return attempt(() -> UUID.fromString(publicationIdentifierString))
            .orElseThrow(fail -> new BadRequestException(INVALID_PUBLICATION_ID_ERROR + publicationIdentifierString));
    }

    private String getUserName(RequestInfo requestInfo) throws ForbiddenException {
        try {
            return UserDetails.getUsername(requestInfo);
        } catch (IllegalArgumentException e) {
            // IllegalArgumentException: Missing from requestContext: /authorizer/claims/custom:feideId
            logger.warn(e.getMessage());
            throw new ForbiddenException();
        }
    }
}
