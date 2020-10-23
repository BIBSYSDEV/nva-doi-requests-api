package no.unit.nva.doi.requests.handlers;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.Tag;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import no.unit.nva.doi.requests.contants.ServiceConstants;
import no.unit.nva.doi.requests.exception.BadRequestException;
import no.unit.nva.doi.requests.model.ApiUpdateDoiRequest;
import no.unit.nva.doi.requests.service.DoiRequestsService;
import no.unit.nva.doi.requests.service.impl.DynamoDBDoiRequestsService;
import no.unit.nva.doi.requests.service.impl.DynamoDbDoiRequestsServiceFactory;
import no.unit.nva.doi.requests.userdetails.UserDetails;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.exceptions.ForbiddenException;
import nva.commons.exceptions.commonexceptions.NotFoundException;
import nva.commons.handlers.AuthorizedHandler;
import nva.commons.handlers.RequestInfo;
import nva.commons.utils.Environment;
import nva.commons.utils.JacocoGenerated;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateDoiRequestHandler extends AuthorizedHandler<ApiUpdateDoiRequest, Void> {

    public static final String API_PUBLICATION_PATH_IDENTIFIER = "publicationIdentifier";
    public static final AWSCredentialsProvider EMPTY_CREDENTIALS = null;
    private static final String LOCATION_TEMPLATE_PUBLICATION = "%s://%s/publication/%s";
    private final DynamoDbDoiRequestsServiceFactory doiRequestsServiceFactory;
    private final String apiScheme;
    private final String apiHost;

    @JacocoGenerated
    public UpdateDoiRequestHandler() {
        this(new Environment(), stsClient(), new DynamoDbDoiRequestsServiceFactory());
    }

    public UpdateDoiRequestHandler(Environment environment,
                                   AWSSecurityTokenService stsClient,
                                   DynamoDbDoiRequestsServiceFactory doiRequestsService) {
        super(ApiUpdateDoiRequest.class, environment, stsClient, initializeLogger());
        this.apiScheme = environment.readEnv(ServiceConstants.API_SCHEME_ENV_VARIABLE);
        this.apiHost = environment.readEnv(ServiceConstants.API_HOST_ENV_VARIABLE);
        this.doiRequestsServiceFactory = doiRequestsService;
    }

    @Override
    protected Void processInput(ApiUpdateDoiRequest input,
                                RequestInfo requestInfo,
                                STSAssumeRoleSessionCredentialsProvider credentials,
                                Context context)
        throws ApiGatewayException {

        input.validate();

        try {
            DynamoDBDoiRequestsService doiRequestService = doiRequestsServiceFactory.getService(credentials);
            UUID publicationIdentifier = updateDoiRequestStatus(input, requestInfo, doiRequestService);
            updateContentLocationHeader(publicationIdentifier);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e.getMessage());
        }
        return null;
    }

    private void updateContentLocationHeader(UUID publicationIdentifier) {
        setAdditionalHeadersSupplier(() ->
            Collections.singletonMap(HttpHeaders.LOCATION, getContentLocation(publicationIdentifier)));
    }

    private UUID updateDoiRequestStatus(ApiUpdateDoiRequest input,
                                        RequestInfo requestInfo,
                                        DoiRequestsService doiRequestsService)
        throws ForbiddenException, NotFoundException {

        var username = getUserName(requestInfo);
        UUID publicationIdentifier = getPublicationIdentifier(requestInfo);
        doiRequestsService.updateDoiRequest(publicationIdentifier, input.getDoiRequestStatus(), username);

        return publicationIdentifier;
    }

    @Override
    protected List<Tag> sessionTags(RequestInfo requestInfo) {
        return null;
    }

    @Override
    protected Integer getSuccessStatusCode(ApiUpdateDoiRequest input, Void output) {
        return HttpStatus.SC_ACCEPTED;
    }

    @JacocoGenerated
    private static AWSSecurityTokenService stsClient() {
        return AWSSecurityTokenServiceClientBuilder.defaultClient();
    }

    private static Logger initializeLogger() {
        return LoggerFactory.getLogger(UpdateDoiRequestHandler.class);
    }

    private String getContentLocation(UUID publicationID) {
        return String.format(LOCATION_TEMPLATE_PUBLICATION, apiScheme, apiHost, publicationID.toString());
    }

    private UUID getPublicationIdentifier(RequestInfo requestInfo) {
        return UUID.fromString(requestInfo.getPathParameter(API_PUBLICATION_PATH_IDENTIFIER));
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
