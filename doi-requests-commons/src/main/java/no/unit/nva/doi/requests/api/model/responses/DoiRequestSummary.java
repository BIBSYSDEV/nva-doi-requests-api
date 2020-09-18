package no.unit.nva.doi.requests.api.model.responses;

import static nva.commons.utils.JsonUtils.objectMapper;
import static nva.commons.utils.attempt.Try.attempt;

import com.amazonaws.services.dynamodbv2.document.Item;
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

/**
 * { "doiRequest": { "date": "2020-09-08T10:11:28.964774Z", "messages": [ { "author": "kjmo@unit.no", "timestamp":
 * "2020-09-08T10:11:28.964779Z", "type": "DoiRequestMessage" } ], "status": "REQUESTED", "type": "DoiRequest" },
 * "doiRequestStatusDate": "REQUESTED#2020-09-08T10:11:28.964774Z", "identifier":
 * "aea76470-39af-4943-b876-061f7576baa5",
 * "modifiedDate": "2020-09-08T09:57:36.872369Z", "publisherId": "https://api.dev.nva.aws.unit
 * .no/customer/f54c8aa9-073a-46a1-8f7c-dde66c853934" }
 */
public class DoiRequestSummary {

    public static final String DOI_REQUEST_PARSING_ERROR_LOG_MESSAGE = "DoiRequestSummary parsing failed for item: ";
    public static final String LOG_SERIALIZATION_ERROR_MESSAGE = "Could not serialize object:";
    private static final Logger logger = LoggerFactory.getLogger(DoiRequestSummary.class);
    private final String owner;
    private final String publicationTitle;
    private final Instant publicationModifiedDate;
    private final DoiRequest doiRequest;
    private final String publisherId;

    private final DoiRequestIdentity doiRequestIdentity;

    public DoiRequestSummary(DoiRequestIdentity doiRequestIdentity, String owner, DoiRequest doiRequest,
                             String publicationTitle, Instant publicationModifiedDate, String publisherId) {
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

    public static Optional<DoiRequestSummary> fromItem(Item item) {
        return attempt(() -> parseItem(item)).toOptional(fail -> logFailure(fail, item));
    }

    private static String extractTitle(Publication publication) {
        return Optional.of(publication.getEntityDescription())
            .map(EntityDescription::getMainTitle).orElse(null);
    }

    private static DoiRequestSummary parseItem(Item item) throws JsonProcessingException {
        return objectMapper.readValue(item.toJSON(), DoiRequestSummary.class);
    }

    private static void logFailure(Failure<DoiRequestSummary> fail, Item item) {
        logger.error(DOI_REQUEST_PARSING_ERROR_LOG_MESSAGE + item.toJSONPretty());
        logger.error("Exception", fail.getException());
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
        logger.warn(LOG_SERIALIZATION_ERROR_MESSAGE, fail.getException());
        return serializationAsWarning;
    }

    private String toJsonString() throws JsonProcessingException {
        return objectMapper.writeValueAsString(this);
    }
}
