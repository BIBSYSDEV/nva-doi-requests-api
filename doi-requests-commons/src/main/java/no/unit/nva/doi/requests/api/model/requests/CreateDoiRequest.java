package no.unit.nva.doi.requests.api.model.requests;

import static nva.commons.utils.attempt.Try.attempt;

import java.util.Objects;
import java.util.UUID;
import no.unit.nva.doi.requests.exception.BadRequestException;
import nva.commons.utils.JacocoGenerated;

public class CreateDoiRequest {

    public static final String INVALID_PUBLICATION_ID_ERROR = "Invalid publication id: ";
    private String publicationId;
    private String message;

    public void validate() throws BadRequestException {
        attempt(() -> UUID.fromString(publicationId))
            .orElseThrow(fail -> invalidUuidException());
    }

    private BadRequestException invalidUuidException() {
        return new BadRequestException(INVALID_PUBLICATION_ID_ERROR + publicationId);
    }

    @JacocoGenerated
    public String getPublicationId() {
        return publicationId;
    }

    @JacocoGenerated
    public void setPublicationId(String publicationId) {
        this.publicationId = publicationId;
    }

    @JacocoGenerated
    public String getMessage() {
        return message;
    }

    @JacocoGenerated
    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    @JacocoGenerated
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CreateDoiRequest that = (CreateDoiRequest) o;
        return Objects.equals(getPublicationId(), that.getPublicationId())
            && Objects.equals(getMessage(), that.getMessage());
    }

    @Override
    @JacocoGenerated
    public int hashCode() {
        return Objects.hash(getPublicationId(), getMessage());
    }
}
