package no.unit.nva.doi.requests.exception;

import nva.commons.exceptions.ApiGatewayException;
import org.apache.http.HttpStatus;

public class BadRequestException extends ApiGatewayException {

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(Exception exception) {
        super(exception);
    }

    @Override
    protected Integer statusCode() {
        return HttpStatus.SC_BAD_REQUEST;
    }
}
