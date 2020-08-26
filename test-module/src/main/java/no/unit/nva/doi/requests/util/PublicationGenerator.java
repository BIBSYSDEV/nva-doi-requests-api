package no.unit.nva.doi.requests.util;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import no.unit.nva.model.DoiRequest;
import no.unit.nva.model.DoiRequestStatus;
import no.unit.nva.model.EntityDescription;
import no.unit.nva.model.Organization;
import no.unit.nva.model.Publication;

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
        return getPublicationWithoutDoiRequest().copy()
            .withDoiRequest(new DoiRequest.Builder()
                .withDate(Instant.now())
                .withStatus(DoiRequestStatus.REQUESTED)
                .build()
            )
            .build();
    }

    public static Publication getPublicationWithoutDoiRequest() {
        return new Publication.Builder()
            .withIdentifier(UUID.randomUUID())
            .withModifiedDate(Instant.now())
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