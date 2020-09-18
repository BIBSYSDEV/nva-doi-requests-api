package no.unit.nva.doi.requests.model;

import java.util.ArrayList;
import java.util.List;
import no.unit.nva.doi.requests.api.model.DoiRequestSummary;

public class DoiRequestsResponse extends ArrayList<DoiRequestSummary> {

    /**
     * Creates DoiRequestResponse from list of DoiRequestSummary.
     *
     * @param doiRequests   list of DoiRequestSummary
     * @return  doiRequestResponse
     */
    public static DoiRequestsResponse of(List<DoiRequestSummary> doiRequests) {
        DoiRequestsResponse response = new DoiRequestsResponse();
        response.addAll(doiRequests);
        return response;
    }


}
