package no.unit.nva.doi.requests.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import no.unit.nva.doi.requests.model.DoiRequestSummary;
import no.unit.nva.model.DoiRequestStatus;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.exceptions.commonexceptions.ConflictException;

public interface DoiRequestsService {

    List<DoiRequestSummary> findDoiRequestsByStatus(URI publisher, DoiRequestStatus status) throws ApiGatewayException;

    List<DoiRequestSummary> findDoiRequestsByStatusAndOwner(URI publisher, DoiRequestStatus status, String owner)
        throws ApiGatewayException;

    DoiRequestSummary  fetchDoiRequest(UUID publicationId) throws JsonProcessingException;

    void createDoiRequest(UUID publicationId) throws ConflictException, JsonProcessingException;

}
