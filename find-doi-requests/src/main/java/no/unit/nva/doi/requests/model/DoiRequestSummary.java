package no.unit.nva.doi.requests.model;

import static com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import no.unit.nva.model.DoiRequest;
import no.unit.nva.model.DoiRequestStatus;
import no.unit.nva.model.EntityDescription;

public class DoiRequestSummary {

    private final DoiRequestStatus doiRequestStatus;
    private final Instant doiRequestDate;
    private final UUID publicationIdentifier;
    private final String publicationTitle;
    private final String publicationOwner;

    /**
     * Constructor for DoiRequestSummary.
     *
     * @param identifier    publication identifier
     * @param owner publication owner
     * @param doiRequest    publication doiRequest
     * @param entityDescription publication entityDescription
     */
    @JsonCreator
    public DoiRequestSummary(@JsonProperty(value = "identifier", access = WRITE_ONLY) UUID identifier,
                             @JsonProperty(value = "owner", access = WRITE_ONLY) String owner,
                             @JsonProperty(value = "doiRequest", access = WRITE_ONLY) DoiRequest doiRequest,
                             @JsonProperty(value = "entityDescription", access = WRITE_ONLY)
                                     EntityDescription entityDescription) {
        this.doiRequestStatus = doiRequest.getStatus();
        this.doiRequestDate = doiRequest.getDate();
        this.publicationIdentifier = identifier;
        this.publicationTitle = entityDescription.getMainTitle();
        this.publicationOwner = owner;
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

}
