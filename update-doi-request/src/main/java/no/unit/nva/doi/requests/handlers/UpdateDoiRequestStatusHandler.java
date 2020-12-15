package no.unit.nva.doi.requests.handlers;

import static nva.commons.utils.attempt.Try.attempt;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import java.util.Collections;
import java.util.UUID;
import no.unit.nva.doi.requests.contants.ServiceConstants;
import no.unit.nva.doi.requests.exception.BadRequestException;
import no.unit.nva.doi.requests.model.UpdateDoiRequestStatusRequest;
import no.unit.nva.doi.requests.service.impl.DynamoDBDoiRequestsService;
import no.unit.nva.doi.requests.service.impl.DynamoDbDoiRequestsServiceFactory;
import no.unit.nva.doi.requests.userdetails.UserDetails;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.exceptions.ForbiddenException;
import nva.commons.exceptions.commonexceptions.NotFoundException;
import nva.commons.handlers.RequestInfo;
import nva.commons.utils.Environment;
import nva.commons.utils.JacocoGenerated;
import nva.commons.utils.JsonUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateDoiRequestStatusHandler extends DoiRequestAuthorizedHandlerTemplate<UpdateDoiRequestStatusRequest, Void> {

    public static final String INVALID_PUBLICATION_ID_ERROR = "Invalid publication id: ";
    public static final String API_PUBLICATION_PATH_IDENTIFIER = "publicationIdentifier";
    private static final String LOCATION_TEMPLATE_PUBLICATION = "%s://%s/publication/%s";

    private static final Logger logger = LoggerFactory.getLogger(UpdateDoiRequestStatusHandler.class);
    private final DynamoDbDoiRequestsServiceFactory doiRequestsServiceFactory;

    private final String apiScheme;
    private final String apiHost;

    @JacocoGenerated
    public UpdateDoiRequestStatusHandler() {
        this(defaultEnvironment(), defaultStsClient(), DEFAULT_SERVICE_FACTORY);
    }

    public UpdateDoiRequestStatusHandler(Environment environment,
                                         AWSSecurityTokenService stsClient,
                                         DynamoDbDoiRequestsServiceFactory doiRequestsServiceFactory) {
        super(UpdateDoiRequestStatusRequest.class, environment, stsClient, logger);
        this.apiScheme = environment.readEnv(ServiceConstants.API_SCHEME_ENV_VARIABLE);
        this.apiHost = environment.readEnv(ServiceConstants.API_HOST_ENV_VARIABLE);
        this.doiRequestsServiceFactory = doiRequestsServiceFactory;
    }

    @Override
    protected Void processInput(UpdateDoiRequestStatusRequest input,
                                RequestInfo requestInfo,
                                STSAssumeRoleSessionCredentialsProvider credentials,
                                Context context)
        throws ApiGatewayException {

        String requestInfoJson = attempt(() -> JsonUtils.objectMapper.writeValueAsString(requestInfo)).orElseThrow();
        logger.info("RequestInfo:\n" + requestInfoJson);

        try {
            input.validate();
            UUID publicationIdentifier = getPublicationIdentifier(requestInfo);
            updateDoiRequestStatus(input, requestInfo, credentials, publicationIdentifier);
            updateContentLocationHeader(publicationIdentifier);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e.getMessage());
        }
        return null;
    }

    @Override
    protected Integer getSuccessStatusCode(UpdateDoiRequestStatusRequest input, Void output) {
        return HttpStatus.SC_ACCEPTED;
    }

    @JacocoGenerated
    private static Environment defaultEnvironment() {
        return new Environment();
    }

    private void updateDoiRequestStatus(UpdateDoiRequestStatusRequest input, RequestInfo requestInfo,
                                        STSAssumeRoleSessionCredentialsProvider credentials, UUID publicationIdentifier)
        throws ForbiddenException, NotFoundException {
        var doiRequestStatus = input.getDoiRequestStatus();
        String username = getUserName(requestInfo);
        DynamoDBDoiRequestsService doiRequestService = doiRequestsServiceFactory.getService(credentials);
        doiRequestService.updateDoiRequest(publicationIdentifier, doiRequestStatus, username);
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
