package no.unit.nva.doi.requests.util;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import no.unit.nva.model.DoiRequest;
import no.unit.nva.model.DoiRequestStatus;
import no.unit.nva.model.EntityDescription;
import no.unit.nva.model.Organization;
import no.unit.nva.model.Publication;
import no.unit.nva.model.PublicationStatus;

public final class PublicationGenerator {

    public static final URI PUBLISHER_ID = URI.create("http://example.org/publisher/1");
    public static final String OWNER = "owner";

    private PublicationGenerator() {

    }

    /**
     * Generates a Publication with sufficient data to map to DoiRequestSummary.
     *
     * @return publication
     */
    public static Publication getPublicationWithDoiRequest() {
        return getPublicationWithDoiRequest(Clock.systemDefaultZone());
    }

    /**
     * Generates a Publication with sufficient data to map to DoiRequestSummary, including setting DOIRequest creation
     * timestamp and last modified timestamp for the publication from the provided clock.
     *
     * @param clock Clock for easy testing.
     * @return publication
     */
    public static Publication getPublicationWithDoiRequest(Clock clock) {
        Publication publication = getPublicationWithoutDoiRequest(clock);
        Instant now = Instant.now(clock);
        return publication.copy()
            .withDoiRequest(new DoiRequest.Builder()
                .withCreatedDate(now)
                .withModifiedDate(now)
                .withStatus(DoiRequestStatus.REQUESTED)
                .build()
            )
            .build();
    }

    /**
     * Create publication without DoiRequest.
     *
     * @return publication
     */
    public static Publication getPublicationWithoutDoiRequest(Clock clock) {
        Instant now = clock.instant();
        return new Publication.Builder()
            .withIdentifier(UUID.randomUUID())
            .withCreatedDate(now)
            .withModifiedDate(now)
            .withStatus(PublicationStatus.PUBLISHED)
            .withOwner(OWNER)
            .withPublisher(new Organization.Builder()
                .withId(PUBLISHER_ID)
                .build()
            )
            .withEntityDescription(new EntityDescription.Builder()
                .withMainTitle("Main title")
                .build()
            )
            .build();
    }
}
