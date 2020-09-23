package no.unit.nva.doi.requests.api.model.responses;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import java.util.UUID;
import nva.commons.utils.JacocoGenerated;

public class DoiRequestIdentity {

    private final UUID publicationId;

    @JacocoGenerated
    @JsonCreator
    public DoiRequestIdentity(@JsonProperty("publicationId") UUID publicationId) {
        this.publicationId = publicationId;
    }

    @JacocoGenerated
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DoiRequestIdentity that = (DoiRequestIdentity) o;
        return Objects.equals(getPublicationId(), that.getPublicationId());
    }

    @JacocoGenerated
    @Override
    public int hashCode() {
        return Objects.hash(getPublicationId());
    }

    @JacocoGenerated
    public UUID getPublicationId() {
        return publicationId;
    }
}
