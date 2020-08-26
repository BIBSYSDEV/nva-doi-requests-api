package no.unit.nva.doi.requests.handlers;

import static nva.commons.utils.attempt.Try.attempt;

import java.util.UUID;
import no.unit.nva.doi.requests.exception.BadRequestException;

public class CreateDoiRequest {

    public static final String INVALID_PUBLICATION_ID_ERROR = "Invalid publication id: ";

    private String publicationId;

    public void validate() throws BadRequestException {
        attempt(() -> UUID.fromString(publicationId))
            .orElseThrow(fail -> new BadRequestException(INVALID_PUBLICATION_ID_ERROR + publicationId));
    }

    public String getPublicationId() {
        return publicationId;
    }

    public void setPublicationId(String publicationId) {
        this.publicationId = publicationId;
    }
}
