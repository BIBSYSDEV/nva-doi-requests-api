package no.unit.nva.doi.requests.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import java.util.UUID;
import no.unit.nva.doi.requests.contants.ServiceConstants;
import no.unit.nva.doi.requests.exception.BadRequestException;
import no.unit.nva.doi.requests.handlers.model.ApiTask;
import no.unit.nva.doi.requests.handlers.model.ApiUpdateDoiResponse;
import no.unit.nva.doi.requests.model.UpdateDoiRequest;
import no.unit.nva.doi.requests.service.DoiRequestsService;
import no.unit.nva.doi.requests.userdetails.UserDetails;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.exceptions.ForbiddenException;
import nva.commons.handlers.ApiGatewayHandler;
import nva.commons.handlers.RequestInfo;
import nva.commons.utils.Environment;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateDoiRequestHandler extends ApiGatewayHandler<UpdateDoiRequest, ApiUpdateDoiResponse> {
    private static final String LOCATION_TEMPLATE_PUBLICATION = "%s://%s/publication/%s";

    private final DoiRequestsService doiRequestService;
    private final String apiScheme;
    private final String apiHost;

    public UpdateDoiRequestHandler(Environment environment, DoiRequestsService doiRequestsService) {
        super(UpdateDoiRequest.class, environment, defaultLogger());
        this.apiScheme = environment.readEnv(ServiceConstants.API_SCHEME_ENV_VARIABLE);
        this.apiHost = environment.readEnv(ServiceConstants.API_HOST_ENV_VARIABLE);
        this.doiRequestService = doiRequestsService;
    }

    private static Logger defaultLogger() {
        return LoggerFactory.getLogger(UpdateDoiRequestHandler.class);
    }


    @Override
    protected ApiUpdateDoiResponse processInput(UpdateDoiRequest input, RequestInfo requestInfo, Context context) throws ApiGatewayException {
        input.validate();
        String username = getUserName(requestInfo);
        UUID publicationId = getPublicationIdentifier(requestInfo);

        var requestedStatusChange = input.getDOIRequestStatus()
                .orElseThrow(() -> new BadRequestException("You must request changes to do"));

        doiRequestService.updateDoiRequest(publicationId, requestedStatusChange, username);

        return ApiUpdateDoiResponse.newBuilder().withTask(ApiTask.newBuilder()
            .withHref(getContentLocation(publicationId))
            .withIdentifier(publicationId.toString())
            .build())
            .build();
    }

    private String getContentLocation(UUID publicationID) {
        return String.format(LOCATION_TEMPLATE_PUBLICATION, apiScheme, apiHost, publicationID.toString());
    }

    private UUID getPublicationIdentifier(RequestInfo requestInfo) {
        return UUID.fromString(requestInfo.getPathParameter("publicationIdentifier"));
    }

    private String getUserName(RequestInfo requestInfo) throws ForbiddenException {
        String username;
        try {
           username = UserDetails.getUsername(requestInfo);
        } catch (IllegalArgumentException e) {
            // IllegalArgumentExpcetion: Missing from requestContext: /authorizer/claims/custom:feideId
            defaultLogger().warn(e.getMessage());
            throw new ForbiddenException();
        }
        return username;
    }

    @Override
    protected Integer getSuccessStatusCode(UpdateDoiRequest input, ApiUpdateDoiResponse output) {
        return HttpStatus.SC_ACCEPTED;
    }
}
