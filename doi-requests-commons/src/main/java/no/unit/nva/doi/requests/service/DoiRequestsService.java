package no.unit.nva.doi.requests.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import no.unit.nva.doi.requests.api.model.requests.CreateDoiRequest;
import no.unit.nva.doi.requests.model.ApiUpdateDoiRequest;
import no.unit.nva.model.DoiRequestStatus;
import no.unit.nva.model.Publication;
import no.unit.nva.useraccessmanagement.dao.AccessRight;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.exceptions.commonexceptions.NotFoundException;

public interface DoiRequestsService {

    List<Publication> findDoiRequestsByStatus(URI publisher, DoiRequestStatus status) throws ApiGatewayException;

    List<Publication> findDoiRequestsByStatusAndOwner(URI publisher, DoiRequestStatus status, String owner)
        throws ApiGatewayException;

    Optional<Publication> fetchDoiRequestByPublicationIdentifier(UUID publicationIdentifier)
        throws JsonProcessingException, NotFoundException;

    void createDoiRequest(CreateDoiRequest createDoiRequest, String username)
        throws ApiGatewayException;

    void updateDoiRequest(UUID publicationIdentifier, ApiUpdateDoiRequest requestedStatusChange,
                          String requestedByUsername, List<AccessRight> userAccessRights)
        throws ApiGatewayException;

    void addMessage(UUID publicationIdentifier, String message, String userId) throws ApiGatewayException;
}
