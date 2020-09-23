package no.unit.nva.doi.requests.api.model.responses;

import static nva.commons.utils.JsonUtils.objectMapper;
import static nva.commons.utils.attempt.Try.attempt;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import no.unit.nva.model.DoiRequest;
import no.unit.nva.model.EntityDescription;
import no.unit.nva.model.Publication;
import nva.commons.utils.JacocoGenerated;
import nva.commons.utils.attempt.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JsonTypeInfo(use = Id.NAME, property = "type")
@SuppressWarnings("PMD.ExcessivePublicCount")
public class DoiRequestSummary {

    public static final String LOG_SERIALIZATION_ERROR_MESSAGE = "Could not serialize object:";
    private static final Logger logger = LoggerFactory.getLogger(DoiRequestSummary.class);
    public final UUID id;
    private final String owner;
    private final String mainTitle;
    private final Instant modifiedDate;
    private final DoiRequest doiRequest;
    private final String publisherId;

    @JsonCreator
    public DoiRequestSummary(
        @JsonProperty("id") UUID id,
        @JsonProperty("owner") String owner,
        @JsonProperty("doiRequest") DoiRequest doiRequest,
        @JsonProperty("mailTitle") String mainTitle,
        @JsonProperty("modifiedDate") Instant modifiedDate,
        @JsonProperty("publisherId") String publisherId) {
        this.id = id;
        this.owner = owner;
        this.doiRequest = doiRequest;
        this.mainTitle = mainTitle;
        this.modifiedDate = modifiedDate;
        this.publisherId = publisherId;
    }

    /**
     * Creates DoiRequest summary from a publication.
     *
     * @param publication the publication.
     * @return a DoiRequestSummary.
     */
    public static DoiRequestSummary fromPublication(Publication publication) {

        return new DoiRequestSummary(publication.getIdentifier(),
            publication.getOwner(),
            publication.getDoiRequest(),
            extractTitle(publication),
            publication.getModifiedDate(),
            publication.getPublisherId()
        );
    }

    private static String extractTitle(Publication publication) {
        return Optional.of(publication.getEntityDescription())
            .map(EntityDescription::getMainTitle).orElse(null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DoiRequestSummary that = (DoiRequestSummary) o;
        return Objects.equals(getId(), that.getId())
            && Objects.equals(getOwner(), that.getOwner())
            && Objects.equals(getMainTitle(), that.getMainTitle())
            && Objects.equals(getModifiedDate(), that.getModifiedDate())
            && Objects.equals(getDoiRequest(), that.getDoiRequest())
            && Objects.equals(getPublisherId(), that.getPublisherId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getOwner(), getMainTitle(), getModifiedDate(), getDoiRequest(), getPublisherId());
    }

    public UUID getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }

    public String getMainTitle() {
        return mainTitle;
    }

    public Instant getModifiedDate() {
        return modifiedDate;
    }

    public DoiRequest getDoiRequest() {
        return doiRequest;
    }

    public String getPublisherId() {
        return publisherId;
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
