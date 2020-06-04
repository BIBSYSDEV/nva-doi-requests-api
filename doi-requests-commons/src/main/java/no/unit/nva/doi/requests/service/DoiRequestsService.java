package no.unit.nva.doi.requests.service;

import java.net.URI;
import java.util.List;
import no.unit.nva.doi.requests.model.DoiRequestSummary;
import no.unit.nva.model.DoiRequestStatus;
import nva.commons.exceptions.ApiGatewayException;

public interface DoiRequestsService {

    List<DoiRequestSummary> findDoiRequestsByStatus(URI publisher, DoiRequestStatus status) throws ApiGatewayException;

    List<DoiRequestSummary> findDoiRequestsByStatusAndOwner(URI publisher, DoiRequestStatus status, String owner)
        throws ApiGatewayException;
}
