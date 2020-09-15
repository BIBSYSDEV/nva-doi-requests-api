package no.unit.nva.doi.requests.model;

import static nva.commons.utils.attempt.Try.attempt;

import java.util.UUID;
import no.unit.nva.doi.requests.exception.BadRequestException;
import nva.commons.utils.JacocoGenerated;

@SuppressWarnings("PMD.AbstractClassWithoutAbstractMethod")
public abstract class AbstractDoiRequest {

    public static final String INVALID_PUBLICATION_ID_ERROR = "Invalid publication id: ";
    public static final String PUBLICATION_ID_NOT_FOUND_ERROR_FORMAT = "Publication with identifier %s not found.";
    private String publicationId;

    protected AbstractDoiRequest() {

    }


    public void validate() throws BadRequestException {
        attempt(() -> UUID.fromString(publicationId))
                .orElseThrow(fail -> invalidUuidException());
    }

    protected BadRequestException invalidUuidException() {
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
}
