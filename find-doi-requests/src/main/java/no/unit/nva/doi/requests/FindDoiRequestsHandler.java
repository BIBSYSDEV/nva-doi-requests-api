package no.unit.nva.doi.requests;

import static no.unit.nva.doi.requests.userdetails.UserDetails.ROLE;
import static no.unit.nva.model.DoiRequestStatus.REQUESTED;
import static nva.commons.utils.attempt.Try.attempt;
import static org.apache.http.HttpStatus.SC_OK;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import no.unit.nva.doi.requests.exception.BadRequestException;
import no.unit.nva.doi.requests.exception.NotAuthorizedException;
import no.unit.nva.doi.requests.handlers.DoiRequestAuthorizedHandlerTemplate;
import no.unit.nva.doi.requests.model.DoiRequestsResponse;
import no.unit.nva.doi.requests.service.DoiRequestsService;
import no.unit.nva.doi.requests.service.impl.DynamoDbDoiRequestsServiceFactory;
import no.unit.nva.doi.requests.userdetails.UserDetails;
import no.unit.nva.model.Publication;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.handlers.RequestInfo;
import nva.commons.utils.Environment;
import nva.commons.utils.JacocoGenerated;
import nva.commons.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FindDoiRequestsHandler extends DoiRequestAuthorizedHandlerTemplate<Void, DoiRequestsResponse> {

    public static final Logger logger = LoggerFactory.getLogger(FindDoiRequestsHandler.class);
    public static final String CREATOR = "creator";
    public static final String CURATOR = "curator";
    public static final String ROLES_SEPARATOR = ",";
    private final DynamoDbDoiRequestsServiceFactory serviceFactory;

    @JacocoGenerated
    public FindDoiRequestsHandler() {
        this(new Environment());
    }

    @JacocoGenerated
    protected FindDoiRequestsHandler(Environment environment) {
        this(environment, DEFAULT_SERVICE_FACTORY, defaultStsClient());
    }

    protected FindDoiRequestsHandler(Environment environment,
                                     DynamoDbDoiRequestsServiceFactory serviceFactory,
                                     AWSSecurityTokenService stsClient) {
        super(Void.class, environment, stsClient, logger);
        this.serviceFactory = serviceFactory;
    }

    @Override
    protected DoiRequestsResponse processInput(Void input, RequestInfo requestInfo,
                                               STSAssumeRoleSessionCredentialsProvider credentialsProvider,
                                               Context context) throws ApiGatewayException {

        String requestInfoJson = attempt(() -> JsonUtils.objectMapper.writeValueAsString(requestInfo)).orElseThrow();
        logger.info("RequestInfo:\n" + requestInfoJson);

        String user;
        String requestedRole;
        String assignedRoles;
        String customerId;
        try {
            user = UserDetails.getUsername(requestInfo);
            assignedRoles = UserDetails.getAssignedRoles(requestInfo);
            customerId = UserDetails.getCustomerId(requestInfo);
            requestedRole = requestInfo.getQueryParameter(ROLE);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e);
        }
        DoiRequestsService doiRequestsService = this.serviceFactory.getService(credentialsProvider);
        verifyRoles(requestedRole, assignedRoles);

        List<Publication> doiRequests = getDoiRequestsForRole(doiRequestsService,
            user,
            requestedRole,
            URI.create(customerId));
        return DoiRequestsResponse.of(doiRequests);
    }


    @Override
    protected Integer getSuccessStatusCode(Void input, DoiRequestsResponse output) {
        return SC_OK;
    }



    private List<Publication> getDoiRequestsForRole(DoiRequestsService doiRequestsService,
                                                    String user,
                                                    String requestedRole,
                                                    URI publisher)
        throws ApiGatewayException {
        List<Publication> doiRequests;
        if (requestedRole.equalsIgnoreCase(CREATOR)) {
            doiRequests = doiRequestsService.findDoiRequestsByStatusAndOwner(publisher, REQUESTED, user);
        } else if (requestedRole.equalsIgnoreCase(CURATOR)) {
            doiRequests = doiRequestsService.findDoiRequestsByStatus(publisher, REQUESTED);
        } else {
            doiRequests = Collections.emptyList();
        }
        return doiRequests;
    }

    private void verifyRoles(String requestedRole, String assignedRoles) throws NotAuthorizedException {
        Optional<String> foundRole = Arrays.stream(assignedRoles.split(ROLES_SEPARATOR))
            .filter(role -> role.equalsIgnoreCase(requestedRole))
            .findAny();

        if (foundRole.isEmpty()) {
            logger.info(String.format("Role '%s' not found among roles '%s'", requestedRole, assignedRoles));
            throw new NotAuthorizedException("User is missing requested role: " + requestedRole);
        }
    }
}
