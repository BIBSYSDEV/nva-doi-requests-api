package no.unit.nva.doi.requests.model;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import no.unit.nva.doi.requests.util.PublicationGenerator;
import no.unit.nva.model.Publication;
import nva.commons.utils.JsonUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DoiRequestSummaryTest {

    @Test
    public void test() throws Exception {
        Publication publication = PublicationGenerator.getPublication();

        DoiRequestSummary doiRequestSummary = JsonUtils.objectMapper.readValue(
            JsonUtils.objectMapper.writeValueAsString(publication),
            DoiRequestSummary.class);

        assertNotNull(doiRequestSummary);
    }

}
