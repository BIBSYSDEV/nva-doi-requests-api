package no.unit.nva.doi.requests.api.model.responses;

import static nva.commons.utils.JsonUtils.objectMapper;
import static nva.commons.utils.attempt.Try.attempt;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import no.unit.nva.model.DoiRequest;
import no.unit.nva.model.EntityDescription;
import no.unit.nva.model.Publication;
import nva.commons.utils.JacocoGenerated;
import nva.commons.utils.attempt.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@SuppressWarnings("PMD.ExcessivePublicCount")
public class DoiRequestSummary {

    public static final String LOG_SERIALIZATION_ERROR_MESSAGE = "Could not serialize object:";
    private static final Logger logger = LoggerFactory.getLogger(DoiRequestSummary.class);
    private final String owner;
    private final String publicationTitle;
    private final Instant publicationModifiedDate;
    private final DoiRequest doiRequest;
    private final String publisherId;

    private final DoiRequestIdentity doiRequestIdentity;

    @JsonCreator
    public DoiRequestSummary(
        @JsonProperty("doiRequestIdentity") DoiRequestIdentity doiRequestIdentity,
        @JsonProperty("owner") String owner,
        @JsonProperty("doiRequest") DoiRequest doiRequest,
        @JsonProperty("publicationTitle") String publicationTitle,
        @JsonProperty("publicationModifiedDate") Instant publicationModifiedDate,
        @JsonProperty("publisherId") String publisherId) {
        this.doiRequestIdentity = doiRequestIdentity;
        this.owner = owner;
        this.doiRequest = doiRequest;
        this.publicationTitle = publicationTitle;
        this.publicationModifiedDate = publicationModifiedDate;
        this.publisherId = publisherId;
    }

    /**
     * Creates DoiRequest summary from a publication.
     *
     * @param publication the publication.
     * @return a DoiRequestSummary.
     */
    public static DoiRequestSummary fromPublication(Publication publication) {
        var doiRequestIdentity = new DoiRequestIdentity(publication.getIdentifier());
        String mainTitle = extractTitle(publication);
        return new DoiRequestSummary(doiRequestIdentity,
            publication.getOwner(),
            publication.getDoiRequest(),
            mainTitle,
            publication.getModifiedDate(),
            publication.getPublisherId()
        );
    }

    private static String extractTitle(Publication publication) {
        return Optional.of(publication.getEntityDescription())
            .map(EntityDescription::getMainTitle).orElse(null);
    }

    public String getOwner() {
        return owner;
    }

    public String getPublicationTitle() {
        return publicationTitle;
    }

    public Instant getPublicationModifiedDate() {
        return publicationModifiedDate;
    }

    public DoiRequest getDoiRequest() {
        return doiRequest;
    }

    public String getPublisherId() {
        return publisherId;
    }

    public DoiRequestIdentity getDoiRequestIdentity() {
        return doiRequestIdentity;
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
        return Objects.equals(getOwner(), that.getOwner())
            && Objects.equals(getPublicationTitle(), that.getPublicationTitle())
            && Objects.equals(getPublicationModifiedDate(), that.getPublicationModifiedDate())
            && Objects.equals(getDoiRequest(), that.getDoiRequest())
            && Objects.equals(getPublisherId(), that.getPublisherId())
            && Objects.equals(getDoiRequestIdentity(), that.getDoiRequestIdentity());
    }

    @Override
    @JacocoGenerated
    public int hashCode() {
        return Objects.hash(getOwner(), getPublicationTitle(), getPublicationModifiedDate(), getDoiRequest(),
            getPublisherId(), getDoiRequestIdentity());
    }

    @Override
    public String toString() {
        return attempt(this::toJsonString).orElse(this::failedSerializationWarning);
    }

    @JacocoGenerated
    private String failedSerializationWarning(Failure<String> fail) {
        String serializationAsWarning = LOG_SERIALIZATION_ERROR_MESSAGE + fail.getException().getMessage();
        logger.error(LOG_SERIALIZATION_ERROR_MESSAGE, fail.getException());
        return serializationAsWarning;
    }

    private String toJsonString() throws JsonProcessingException {
        return objectMapper.writeValueAsString(this);
    }
}
