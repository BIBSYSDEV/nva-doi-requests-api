package no.unit.nva.doi.requests.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import no.unit.nva.doi.requests.api.model.CreateDoiRequest;
import no.unit.nva.doi.requests.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class CreateDoiRequestTest {

    @Test
    void validateThrowsExceptionWhenPublicationIdIsNotValidUuid() {
        String invalidUuid = "Invalid";
        CreateDoiRequest createDoiRequest = new CreateDoiRequest();
        createDoiRequest.setPublicationId(invalidUuid);
        Executable action = createDoiRequest::validate;
        BadRequestException exception = assertThrows(BadRequestException.class, action);
        assertThat(exception.getMessage(), containsString(CreateDoiRequest.INVALID_PUBLICATION_ID_ERROR));
    }

    @Test
    void validateDoesNotThrowExceptionWhenPublicationIdIsValidUuid() {
        UUID validUuid = UUID.randomUUID();
        CreateDoiRequest createDoiRequest = new CreateDoiRequest();
        createDoiRequest.setPublicationId(validUuid.toString());
        Executable action = createDoiRequest::validate;
        assertDoesNotThrow(action);

    }
}