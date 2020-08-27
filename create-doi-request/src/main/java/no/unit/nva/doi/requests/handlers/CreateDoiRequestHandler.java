package no.unit.nva.doi.requests.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.handlers.ApiGatewayHandler;
import nva.commons.handlers.RequestInfo;
import nva.commons.utils.Environment;
import nva.commons.utils.JacocoGenerated;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateDoiRequestHandler extends ApiGatewayHandler<CreateDoiRequest, Void> {

    @JacocoGenerated
    public CreateDoiRequestHandler() {
        this(new Environment());
    }

    public CreateDoiRequestHandler(Environment environment) {
        super(CreateDoiRequest.class, environment, defaultLogger());
    }

    private static Logger defaultLogger() {
        return LoggerFactory.getLogger(CreateDoiRequest.class);
    }

    @Override
    protected Void processInput(CreateDoiRequest input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {
        input.validate();
        return null;
    }

    @Override
    protected Integer getSuccessStatusCode(CreateDoiRequest input, Void output) {
        return HttpStatus.SC_CREATED;
    }
}
