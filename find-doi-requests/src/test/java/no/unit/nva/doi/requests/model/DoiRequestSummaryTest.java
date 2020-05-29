package no.unit.nva.doi.requests.model;

import static nva.commons.utils.JsonUtils.objectMapper;
import static util.PublicationGenerator.getPublication;

import no.unit.nva.model.Publication;
import org.junit.jupiter.api.Test;

public class DoiRequestSummaryTest {

    @Test
    public void test() throws Exception {
        Publication publication = getPublication();

        DoiRequestSummary doiRequestSummary = objectMapper.readValue(
            objectMapper.writeValueAsString(publication),
            DoiRequestSummary.class);

        objectMapper.writeValue(System.out, doiRequestSummary);
    }

}
