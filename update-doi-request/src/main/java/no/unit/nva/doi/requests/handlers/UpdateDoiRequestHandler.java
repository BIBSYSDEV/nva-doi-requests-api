package no.unit.nva.doi.requests.handlers;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.Tag;
import no.unit.nva.doi.requests.contants.ServiceConstants;
import no.unit.nva.doi.requests.exception.BadRequestException;
import no.unit.nva.doi.requests.model.ApiUpdateDoiRequest;
import no.unit.nva.doi.requests.service.impl.DynamoDbDoiRequestsServiceFactory;
import no.unit.nva.doi.requests.userdetails.UserDetails;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.exceptions.ForbiddenException;
import nva.commons.handlers.AuthorizedHandler;
import nva.commons.handlers.RequestInfo;
import nva.commons.utils.Environment;
import nva.commons.utils.JacocoGenerated;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

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

    private static AWSSecurityTokenService stsClient() {
        return AWSSecurityTokenServiceClientBuilder.defaultClient();
    }

    private static Logger initializeLogger() {
        return LoggerFactory.getLogger(UpdateDoiRequestHandler.class);
    }


    @Override
    protected Void processInput(ApiUpdateDoiRequest input,
                                RequestInfo requestInfo,
                                STSAssumeRoleSessionCredentialsProvider credentialsProvider,
                                Context context)
            throws ApiGatewayException {
        input.validate();
        var username = getUserName(requestInfo);

        var requestedStatusChange = input.getDoiRequestStatus()
                .orElseThrow(() -> new BadRequestException("You must request changes to do"));
        try {
            UUID publicationIdentifier = getPublicationIdentifier(requestInfo);
            doiRequestsServiceFactory.getService(credentialsProvider)
                    .updateDoiRequest(publicationIdentifier, requestedStatusChange, username);
            setAdditionalHeadersSupplier(() ->
                    Collections.singletonMap(HttpHeaders.LOCATION, getContentLocation(publicationIdentifier)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e.getMessage());
        }
        return null;
    }


    @Override
    protected List<Tag> sessionTags(RequestInfo requestInfo) {
        return null;
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

    @Override
    protected Integer getSuccessStatusCode(ApiUpdateDoiRequest input, Void output) {
        return HttpStatus.SC_ACCEPTED;
    }
}
