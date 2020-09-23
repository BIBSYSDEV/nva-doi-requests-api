package no.unit.nva.doi.requests.api.model.responses;

import static no.unit.nva.hamcrest.DoesNotHaveNullOrEmptyFields.doesNotHaveNullOrEmptyFields;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.IsSame.sameInstance;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import no.unit.nva.model.DoiRequest;
import no.unit.nva.model.DoiRequestMessage;
import no.unit.nva.model.DoiRequestStatus;
import nva.commons.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class DoiRequestSummaryTest {

    public static final String SOME_MESSAGE = "SomeMessage";
    public static final String SOME_AUTHOR = "someAuthor";
    public static final String SOME_OWNER = "owner";
    public static final String PUBLICATION_TITLE = "publicationTitle";
    public static final String PUBLISHER_ID = "publisherId";
    public static final UUID ID = UUID.randomUUID();
    public static final UUID SAMPLE_ID = ID;
    private static final Instant now = Instant.now();

    private static DoiRequest sampleDoiRequest() {
        return new DoiRequest.Builder().withDate(now)
            .withStatus(DoiRequestStatus.REQUESTED)
            .withMessages(List.of(sampleDoiRequestMessage()))
            .build();
    }

    private static DoiRequestMessage sampleDoiRequestMessage() {
        return new DoiRequestMessage.Builder()
            .withAuthor(SOME_AUTHOR).withText(SOME_MESSAGE).build();
    }

    private DoiRequestSummary sampleDoiRequestSummary() {

        return new DoiRequestSummary(
            SAMPLE_ID,
            SOME_OWNER,
            sampleDoiRequest(),
            PUBLICATION_TITLE,
            now,
            PUBLISHER_ID);
    }

    @Test
    void equalsReturnsTrueForEquivalentObjects() {
        var left = sampleDoiRequestSummary();
        var right = sampleDoiRequestSummary();
        assertThat(left, doesNotHaveNullOrEmptyFields());
        assertThat(left, is(equalTo(right)));
        assertThat(left, is(not(sameInstance(right))));
    }

    @Test
    void hashCodeIsEqualForEquivalentObjects() {
        var left = sampleDoiRequestSummary();
        var right = sampleDoiRequestSummary();
        assertThat(left.hashCode(), is(equalTo(right.hashCode())));
    }

    @Test
    void toStringReturnsJsonRepresentation() {
        var doiRequestSummary = sampleDoiRequestSummary();
        String doiRequestSummaryStr = doiRequestSummary.toString();
        Executable jsonParsing = () -> JsonUtils.objectMapper.readValue(doiRequestSummaryStr, DoiRequestSummary.class);
        assertDoesNotThrow(jsonParsing);
    }
}