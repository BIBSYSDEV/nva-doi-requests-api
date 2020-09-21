package no.unit.nva.doi.requests.model;

import static no.unit.nva.doi.requests.model.AbstractDoiRequest.INVALID_PUBLICATION_ID_ERROR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import no.unit.nva.doi.requests.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class ApiUpdateDoiRequestTest {

    @Test
    void validateThrowsExceptionWhenPublicationIdIsNotValidUuid() {
        String invalidUuid = "Invalid";
        var updateDoiRequest = new ApiUpdateDoiRequest();
        updateDoiRequest.setPublicationId(invalidUuid);
        Executable action = updateDoiRequest::validate;
        var exception = assertThrows(BadRequestException.class, action);
        assertThat(exception.getMessage(), containsString(INVALID_PUBLICATION_ID_ERROR));
    }

    @Test
    void validateDoesNotThrowExceptionWhenPublicationIdIsValidUuid() {
        var validUuid = UUID.randomUUID();
        var updateDoiRequest = new ApiUpdateDoiRequest();
        updateDoiRequest.setPublicationId(validUuid.toString());
        Executable action = updateDoiRequest::validate;
        assertDoesNotThrow(action);
    }
}