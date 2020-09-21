package no.unit.nva.doi.requests.handlers;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import no.unit.nva.doi.requests.api.model.requests.CreateDoiRequest;
import no.unit.nva.doi.requests.service.DoiRequestsService;
import no.unit.nva.doi.requests.service.impl.DynamoDBDoiRequestsService;
import no.unit.nva.doi.requests.userdetails.UserDetails;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.handlers.ApiGatewayHandler;
import nva.commons.handlers.RequestInfo;
import nva.commons.utils.Environment;
import nva.commons.utils.JacocoGenerated;
import nva.commons.utils.JsonUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateDoiRequestHandler extends ApiGatewayHandler<CreateDoiRequest, Void> {

    private final DoiRequestsService doiRequestService;

    @JacocoGenerated
    public CreateDoiRequestHandler() {
        this(new Environment());
    }

    @JacocoGenerated
    private CreateDoiRequestHandler(Environment environment) {
        this(environment, defaultDoiRequestService(environment));
    }

    public CreateDoiRequestHandler(Environment environment, DoiRequestsService doiRequestsService) {
        super(CreateDoiRequest.class, environment, defaultLogger());
        this.doiRequestService = doiRequestsService;
    }

    @JacocoGenerated
    private static DynamoDBDoiRequestsService defaultDoiRequestService(Environment environment) {
        return new DynamoDBDoiRequestsService(
            AmazonDynamoDBClientBuilder.defaultClient(),
            JsonUtils.objectMapper,
            environment
        );
    }

    private static Logger defaultLogger() {
        return LoggerFactory.getLogger(CreateDoiRequestHandler.class);
    }

    @Override
    protected Void processInput(CreateDoiRequest input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {
        input.validate();
        String username = UserDetails.getUsername(requestInfo);
        doiRequestService.createDoiRequest(input, username);
        return null;
    }

    @Override
    protected Integer getSuccessStatusCode(CreateDoiRequest input, Void output) {
        return HttpStatus.SC_CREATED;
    }
}
