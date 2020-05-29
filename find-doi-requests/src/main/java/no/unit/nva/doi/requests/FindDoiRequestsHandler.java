package no.unit.nva.doi.requests;

import static no.unit.nva.model.DoiRequestStatus.REQUESTED;
import static no.unit.nva.model.util.OrgNumberMapper.toCristinId;
import static nva.commons.utils.JsonUtils.objectMapper;
import static nva.commons.utils.RequestUtils.getQueryParameter;
import static nva.commons.utils.RequestUtils.getRequestContextParameter;
import static org.apache.http.HttpStatus.SC_OK;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonPointer;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import no.unit.nva.doi.requests.exception.BadRequestException;
import no.unit.nva.doi.requests.exception.NotAuthorizedException;
import no.unit.nva.doi.requests.model.DoiRequestSummary;
import no.unit.nva.doi.requests.model.DoiRequestsResponse;
import no.unit.nva.doi.requests.service.DoiRequestsService;
import no.unit.nva.doi.requests.service.impl.DynamoDBDoiRequestsService;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.handlers.ApiGatewayHandler;
import nva.commons.handlers.RequestInfo;
import nva.commons.utils.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FindDoiRequestsHandler extends ApiGatewayHandler<Void, DoiRequestsResponse> {

    public static final String ROLE = "role";
    public static final JsonPointer FEIDE_ID = JsonPointer.compile("/authorizer/claims/custom:feideId");
    public static final JsonPointer ORG_NUMBER = JsonPointer.compile("/authorizer/claims/custom:orgNumber");
    public static final JsonPointer APPLICATION_ROLES = JsonPointer
        .compile("/authorizer/claims/custom:applicationRoles");
    public static final Logger logger = LoggerFactory.getLogger(FindDoiRequestsHandler.class);
    public static final String CREATOR = "creator";
    public static final String CURATOR = "curator";

    private DoiRequestsService doiRequestsService;

    /**
     * Default constructor for FindDoiRequestsHandler.
     */
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
     * @param doiRequestsService    doiRequestsService
     * @param environment   environment
     */
    public FindDoiRequestsHandler(DoiRequestsService doiRequestsService, Environment environment) {
        super(Void.class, environment, logger);
        this.doiRequestsService = doiRequestsService;
    }

    @Override
    protected DoiRequestsResponse processInput(Void input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {

        String user;
        String requestedRole;
        String assignedRoles;
        String orgNumber;
        try {
            user = getRequestContextParameter(requestInfo, FEIDE_ID);
            requestedRole = getQueryParameter(requestInfo, ROLE);
            assignedRoles = getRequestContextParameter(requestInfo, APPLICATION_ROLES);
            orgNumber = getRequestContextParameter(requestInfo, ORG_NUMBER);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e);
        }

        verifyRoles(requestedRole, assignedRoles);

        List<DoiRequestSummary> doiRequests = getDoiRequestsForRole(user, requestedRole, toCristinId(orgNumber));
        return DoiRequestsResponse.of(doiRequests);
    }

    private List<DoiRequestSummary> getDoiRequestsForRole(String user, String requestedRole, URI publisher)
        throws ApiGatewayException {
        List<DoiRequestSummary> doiRequests;
        if (requestedRole.equals(CREATOR)) {
            doiRequests = doiRequestsService.findDoiRequestsByStatusAndOwner(
                publisher, REQUESTED, user);
        } else if (requestedRole.equals(CURATOR)) {
            doiRequests = doiRequestsService.findDoiRequestsByStatus(
                publisher, REQUESTED);
        } else {
            doiRequests = new ArrayList<>();
        }
        return doiRequests;
    }

    private void verifyRoles(String requestedRole, String assignedRoles) throws NotAuthorizedException {
        if (!assignedRoles.contains(requestedRole)) {
            logger.info(String.format("Role '%s' not found among roles '%s'", requestedRole, assignedRoles));
            throw new NotAuthorizedException("User is missing requested role: " + requestedRole);
        }
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, DoiRequestsResponse output) {
        return SC_OK;
    }
}
