package no.unit.nva.doi.requests;

import static no.unit.nva.doi.requests.userdetails.UserDetails.ROLE;
import static no.unit.nva.model.DoiRequestStatus.REQUESTED;
import static nva.commons.utils.JsonUtils.objectMapper;
import static org.apache.http.HttpStatus.SC_OK;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import no.unit.nva.doi.requests.exception.BadRequestException;
import no.unit.nva.doi.requests.exception.NotAuthorizedException;
import no.unit.nva.doi.requests.api.model.responses.DoiRequestSummary;
import no.unit.nva.doi.requests.model.DoiRequestsResponse;
import no.unit.nva.doi.requests.service.DoiRequestsService;
import no.unit.nva.doi.requests.service.impl.DynamoDBDoiRequestsService;
import no.unit.nva.doi.requests.userdetails.UserDetails;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.handlers.ApiGatewayHandler;
import nva.commons.handlers.RequestInfo;
import nva.commons.utils.Environment;
import nva.commons.utils.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FindDoiRequestsHandler extends ApiGatewayHandler<Void, DoiRequestsResponse> {

    public static final Logger logger = LoggerFactory.getLogger(FindDoiRequestsHandler.class);
    public static final String CREATOR = "creator";
    public static final String CURATOR = "curator";
    public static final String ROLES_SEPARATOR = ",";

    private DoiRequestsService doiRequestsService;

    /**
     * Default constructor for FindDoiRequestsHandler.
     */
    @JacocoGenerated
    public FindDoiRequestsHandler() {
        this(new DynamoDBDoiRequestsService(
                AmazonDynamoDBClientBuilder.defaultClient(),
                objectMapper,
                new Environment()),
            new Environment());
    }

    /**
     * Constructor for FindDoiRequestsHandler.
     *
     * @param doiRequestsService doiRequestsService
     * @param environment        environment
     */
    public FindDoiRequestsHandler(DoiRequestsService doiRequestsService, Environment environment) {
        super(Void.class, environment, logger);
        this.doiRequestsService = doiRequestsService;
    }

    @Override
    protected DoiRequestsResponse processInput(Void input, String apiGatewayInputString, Context context)
        throws ApiGatewayException {
        return super.processInput(input, apiGatewayInputString, context);
    }

    @Override
    protected DoiRequestsResponse processInput(Void input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {

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

        verifyRoles(requestedRole, assignedRoles);

        List<DoiRequestSummary> doiRequests = getDoiRequestsForRole(user, requestedRole, URI.create(customerId));
        return DoiRequestsResponse.of(doiRequests);
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, DoiRequestsResponse output) {
        return SC_OK;
    }

    private List<DoiRequestSummary> getDoiRequestsForRole(String user, String requestedRole, URI publisher)
        throws ApiGatewayException {
        List<DoiRequestSummary> doiRequests;
        if (requestedRole.equalsIgnoreCase(CREATOR)) {
            doiRequests = doiRequestsService.findDoiRequestsByStatusAndOwner(
                publisher, REQUESTED, user);
        } else if (requestedRole.equalsIgnoreCase(CURATOR)) {
            doiRequests = doiRequestsService.findDoiRequestsByStatus(
                publisher, REQUESTED);
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
