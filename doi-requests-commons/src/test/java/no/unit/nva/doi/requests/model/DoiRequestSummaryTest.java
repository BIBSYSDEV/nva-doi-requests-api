package no.unit.nva.doi.requests.model;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.dynamodbv2.document.Item;
import java.util.Optional;
import no.unit.nva.doi.requests.api.model.responses.DoiRequestSummary;
import org.junit.jupiter.api.Test;

public class DoiRequestSummaryTest {

    public static final String GARBAGE_JSON = "{InvalidJson}";

    @Test
    public void toDoiDoiRequestSummaryThrowsExceptionOnInvalidJsonItem() {
        Item item = mock(Item.class);
        when(item.toJSON()).thenReturn(GARBAGE_JSON);
        Optional<DoiRequestSummary> doiRequestSummary = DoiRequestSummary.fromItem(item);
        assertTrue(doiRequestSummary.isEmpty());
    }
}
