package no.unit.nva.doi.requests.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import no.unit.nva.doi.requests.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class UpdateDoiRequestStatusRequestTest {

    @Test
    void validateThrowsExceptionWhenDoiRequestStatusIsMissing() {
        var updateDoiRequest = new UpdateDoiRequestStatusRequest();

        Executable action = updateDoiRequest::validate;
        BadRequestException exception = assertThrows(BadRequestException.class, action);
        assertThat(exception.getMessage(), containsString(UpdateDoiRequestStatusRequest.NO_CHANGE_REQUESTED_ERROR));
    }
}