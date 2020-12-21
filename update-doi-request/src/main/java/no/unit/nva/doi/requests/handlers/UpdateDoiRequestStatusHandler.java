package no.unit.nva.doi.requests.handlers;

import static nva.commons.utils.attempt.Try.attempt;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import no.unit.nva.doi.requests.contants.ServiceConstants;
import no.unit.nva.doi.requests.exception.BadRequestException;
import no.unit.nva.doi.requests.model.ApiUpdateDoiRequest;
import no.unit.nva.doi.requests.service.impl.DynamoDBDoiRequestsService;
import no.unit.nva.doi.requests.service.impl.DynamoDbDoiRequestsServiceFactory;
import no.unit.nva.useraccessmanagement.dao.AccessRight;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.handlers.RequestInfo;
import nva.commons.utils.Environment;
import nva.commons.utils.JacocoGenerated;
import nva.commons.utils.JsonUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateDoiRequestStatusHandler extends UpdateDoiRequestHandler {

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
        super(ApiUpdateDoiRequest.class, environment, stsClient, logger);
        this.apiScheme = environment.readEnv(ServiceConstants.API_SCHEME_ENV_VARIABLE);
        this.apiHost = environment.readEnv(ServiceConstants.API_HOST_ENV_VARIABLE);
        this.doiRequestsServiceFactory = doiRequestsServiceFactory;
    }

    @Override
    protected Void processInput(ApiUpdateDoiRequest input,
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
    protected Integer getSuccessStatusCode(ApiUpdateDoiRequest input, Void output) {
        return HttpStatus.SC_ACCEPTED;
    }

    @JacocoGenerated
    private static Environment defaultEnvironment() {
        return new Environment();
    }

    private void updateDoiRequestStatus(ApiUpdateDoiRequest input, RequestInfo requestInfo,
                                        STSAssumeRoleSessionCredentialsProvider credentials, UUID publicationIdentifier)
        throws ApiGatewayException {

        String username = getUserName(requestInfo);
        List<AccessRight> accessRights = extractAccessRights(requestInfo);
        DynamoDBDoiRequestsService doiRequestService = doiRequestsServiceFactory.getService(credentials);
        doiRequestService.updateDoiRequest(publicationIdentifier, input, username, accessRights);
    }

    private List<AccessRight> extractAccessRights(RequestInfo requestInfo) {
        return requestInfo.getAccessRights()
            .stream()
            .map(AccessRight::fromString)
            .collect(Collectors.toList());
    }

    private void updateContentLocationHeader(UUID publicationIdentifier) {
        setAdditionalHeadersSupplier(() ->
            Collections.singletonMap(HttpHeaders.LOCATION, getContentLocation(publicationIdentifier)));
    }

    private String getContentLocation(UUID publicationID) {
        return String.format(LOCATION_TEMPLATE_PUBLICATION, apiScheme, apiHost, publicationID.toString());
    }



}
