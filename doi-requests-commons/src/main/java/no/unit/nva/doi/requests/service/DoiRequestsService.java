package no.unit.nva.doi.requests.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import no.unit.nva.doi.requests.api.model.requests.CreateDoiRequest;
import no.unit.nva.model.DoiRequestStatus;
import no.unit.nva.model.Publication;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.exceptions.ForbiddenException;
import nva.commons.exceptions.commonexceptions.ConflictException;
import nva.commons.exceptions.commonexceptions.NotFoundException;

public interface DoiRequestsService {

    String DOI_ALREADY_EXISTS_ERROR = "DoiRequest already exists for publication: ";

    List<Publication> findDoiRequestsByStatus(URI publisher, DoiRequestStatus status) throws ApiGatewayException;

    List<Publication> findDoiRequestsByStatusAndOwner(URI publisher, DoiRequestStatus status, String owner)
        throws ApiGatewayException;

    Optional<Publication> fetchDoiRequestByPublicationIdentifier(UUID publicationIdentifier)
        throws JsonProcessingException, NotFoundException;

    void createDoiRequest(CreateDoiRequest createDoiRequest, String username)
        throws ConflictException, NotFoundException, ForbiddenException;

    void updateDoiRequest(UUID publicationIdentifier, DoiRequestStatus requestedStatusChange,
                          String requestedByUsername)
        throws NotFoundException, ForbiddenException;
}
