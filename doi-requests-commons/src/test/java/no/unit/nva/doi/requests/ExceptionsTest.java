package no.unit.nva.doi.requests;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import no.unit.nva.doi.requests.exception.BadRequestException;
import no.unit.nva.doi.requests.exception.DynamoDBException;
import no.unit.nva.doi.requests.exception.NotAuthorizedException;
import nva.commons.exceptions.ApiGatewayException;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ExceptionsTest {

    public static final String MESSAGE = "Message";

    @Test
    public void badRequestExceptionHasStatusCode400() {
        ApiGatewayException exception = new BadRequestException(MESSAGE);
        Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    public void badRequestExceptionAcceptsExceptionAsInputParameter() {
        assertDoesNotThrow(() -> new BadRequestException(new RuntimeException()));
    }

    @Test
    public void dynamoDBExceptionHasStatusCode502() {
        ApiGatewayException exception = new DynamoDBException(MESSAGE, null);
        Assertions.assertEquals(HttpStatus.SC_BAD_GATEWAY, exception.getStatusCode());
    }

    @Test
    public void notAuthorizedExceptionHasStatusCode401() {
        ApiGatewayException exception = new NotAuthorizedException(null);
        Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, exception.getStatusCode());
    }
}
