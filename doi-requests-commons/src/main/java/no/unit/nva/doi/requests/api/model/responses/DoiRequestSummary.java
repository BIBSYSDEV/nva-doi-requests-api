package no.unit.nva.doi.requests.api.model.responses;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import no.unit.nva.model.DoiRequest;
import no.unit.nva.model.DoiRequestStatus;
import no.unit.nva.model.EntityDescription;
import no.unit.nva.model.Publication;
import nva.commons.utils.JacocoGenerated;

public class DoiRequestSummary {

    private final DoiRequestStatus doiRequestStatus;
    private final Instant doiRequestDate;
    private final UUID publicationIdentifier;
    private final String publicationTitle;
    private final String publicationOwner;

    /**
     * Constructor for DoiRequestSummary.
     *
     * @param identifier        publication identifier
     * @param owner             publication owner
     * @param doiRequest        publication doiRequest
     * @param entityDescription publication entityDescription
     */
    @JsonCreator
    public DoiRequestSummary(@JsonProperty(value = "identifier", access = Access.WRITE_ONLY) UUID identifier,
                             @JsonProperty(value = "owner", access = Access.WRITE_ONLY) String owner,
                             @JsonProperty(value = "doiRequest", access = Access.WRITE_ONLY) DoiRequest doiRequest,
                             @JsonProperty(value = "entityDescription", access = Access.WRITE_ONLY)
                                 EntityDescription entityDescription) {
        this.doiRequestStatus = doiRequest.getStatus();
        this.doiRequestDate = doiRequest.getDate();
        this.publicationIdentifier = identifier;
        this.publicationTitle = entityDescription.getMainTitle();
        this.publicationOwner = owner;
    }
    
    /**
     * Creates DoiRequest summary from a publication.
     *
     * @param publication the publication.
     * @return a DoiRequestSummary.
     */
    public static DoiRequestSummary fromPublication(Publication publication) {
        return new DoiRequestSummary(
            publication.getIdentifier(),
            publication.getOwner(),
            publication.getDoiRequest(),
            publication.getEntityDescription()
        );
    }

    public DoiRequestStatus getDoiRequestStatus() {
        return doiRequestStatus;
    }

    public Instant getDoiRequestDate() {
        return doiRequestDate;
    }

    public UUID getPublicationIdentifier() {
        return publicationIdentifier;
    }

    public String getPublicationTitle() {
        return publicationTitle;
    }

    public String getPublicationOwner() {
        return publicationOwner;
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
        DoiRequestSummary that = (DoiRequestSummary) o;
        return doiRequestStatus == that.doiRequestStatus
            && Objects.equals(doiRequestDate, that.doiRequestDate)
            && Objects.equals(publicationIdentifier, that.publicationIdentifier)
            && Objects.equals(publicationTitle, that.publicationTitle)
            && Objects.equals(publicationOwner, that.publicationOwner);
    }

    @Override
    @JacocoGenerated
    public int hashCode() {
        return Objects.hash(doiRequestStatus, doiRequestDate, publicationIdentifier, publicationTitle,
            publicationOwner);
    }
}
