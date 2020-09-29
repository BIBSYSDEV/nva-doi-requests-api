package no.unit.nva.doi.requests.model;

import java.util.ArrayList;
import java.util.List;
import no.unit.nva.model.Publication;

public class DoiRequestsResponse extends ArrayList<Publication> {

    /**
     * Creates DoiRequestResponse from list of DoiRequestSummary.
     *
     * @param publication list of Publication
     * @return doiRequestResponse
     */
    public static DoiRequestsResponse of(List<Publication> publication) {
        DoiRequestsResponse response = new DoiRequestsResponse();
        response.addAll(publication);
        return response;
    }


}
