package no.unit.nva.doi.requests.handlers;

import static no.unit.nva.doi.requests.util.MockEnvironment.mockEnvironment;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.time.Clock;
import java.time.Instant;
import no.unit.nva.doi.requests.contants.ServiceConstants;
import no.unit.nva.doi.requests.util.DoiRequestsDynamoDBLocal;
import no.unit.nva.doi.requests.util.PublicationGenerator;
import no.unit.nva.model.Publication;
import nva.commons.utils.Environment;
import org.junit.jupiter.api.BeforeEach;

public abstract class UpdateDoiTestUtils extends DoiRequestsDynamoDBLocal {

    public static final Instant PUBLICATION_CREATION_TIME = Instant.parse("1900-01-01T10:00:00.00Z");
    public static final Instant DOI_REQUEST_CREATION_TIME = Instant.parse("2000-12-03T10:15:30.00Z");
    public static final Instant DOI_REQUEST_MODIFICATION_TIME = Instant.parse("2000-12-03T10:15:30.00Z");

    private final Environment environment = mockEnvironment();
    private final String publicationsTableName = environment.readEnv(
        ServiceConstants.PUBLICATIONS_TABLE_NAME_ENV_VARIABLE);

    protected Clock mockClock;

    @BeforeEach
    public void setup() {
        mockClock = mock(Clock.class);
        when(mockClock.instant())
            .thenReturn(PUBLICATION_CREATION_TIME)
            .thenReturn(DOI_REQUEST_CREATION_TIME)
            .thenReturn(DOI_REQUEST_MODIFICATION_TIME);
    }

    protected Publication insertPublicationWithDoiRequest(Clock clock)
        throws com.fasterxml.jackson.core.JsonProcessingException {
        Publication publication = PublicationGenerator.getPublicationWithDoiRequest(clock)
            .copy()
            .withCreatedDate(PUBLICATION_CREATION_TIME)
            .build();
        insertPublication(publicationsTableName, publication);
        return publication;
    }

    protected Publication insertPublicationWithoutDoiRequest(Clock clock)
        throws com.fasterxml.jackson.core.JsonProcessingException {
        Publication publication = PublicationGenerator.getPublicationWithoutDoiRequest(clock)
            .copy()
            .withCreatedDate(PUBLICATION_CREATION_TIME)
            .build();
        insertPublication(publicationsTableName, publication);
        return publication;
    }
}
