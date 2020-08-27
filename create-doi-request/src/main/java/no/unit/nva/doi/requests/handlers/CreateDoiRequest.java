package no.unit.nva.doi.requests.handlers;

import static java.util.Objects.isNull;

import no.unit.nva.doi.requests.exception.BadRequestException;

public class CreateDoiRequest {

    public static final String EMPTY_PUBLICATION_ID = "Publication id cannot be empty";
    private String publicationId;

    public void validate() throws BadRequestException {
        if (isNull(publicationId) || publicationId.isBlank()) {
            throw new BadRequestException(EMPTY_PUBLICATION_ID);
        }
    }

    public String getPublicationId() {
        return publicationId;
    }

    public void setPublicationId(String publicationId) {
        this.publicationId = publicationId;
    }
}
