package no.unit.nva.doi.requests.api.model.responses;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.time.Instant;
import java.util.UUID;
import no.unit.nva.model.DoiRequest;
import nva.commons.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class DoiRequestSummaryTest {

    @Test
    void toStringReturnsJsonRepresentation() {
        DoiRequest doiRequest = new DoiRequest();
        DoiRequestSummary doiRequestSummary =
            new DoiRequestSummary(
                UUID.randomUUID(),
                "owner", doiRequest,
                "publicationTitle",
                Instant.now(),
                "publisherId");
        String doiRequestSummaryStr = doiRequestSummary.toString();
        Executable jsonParsing = () -> JsonUtils.objectMapper.readValue(doiRequestSummaryStr, DoiRequestSummary.class);
        assertDoesNotThrow(jsonParsing);
    }
}