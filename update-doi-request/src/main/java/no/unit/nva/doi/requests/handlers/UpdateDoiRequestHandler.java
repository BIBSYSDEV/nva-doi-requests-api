package no.unit.nva.doi.requests.handlers;

import static nva.commons.utils.attempt.Try.attempt;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import java.util.UUID;
import no.unit.nva.doi.requests.exception.BadRequestException;
import no.unit.nva.doi.requests.model.ApiUpdateDoiRequest;
import nva.commons.exceptions.ForbiddenException;
import nva.commons.handlers.RequestInfo;
import nva.commons.utils.Environment;
import org.slf4j.Logger;

public abstract class UpdateDoiRequestHandler extends DoiRequestAuthorizedHandlerTemplate<ApiUpdateDoiRequest, Void> {

    public static final String API_PUBLICATION_PATH_IDENTIFIER = "publicationIdentifier";
    public static final String INVALID_PUBLICATION_ID_ERROR = "Invalid publication id: ";
    public static final String NO_USERNAME_FOUND = "No username found";

    protected UpdateDoiRequestHandler(
        Class<ApiUpdateDoiRequest> iclass, Environment environment,
        AWSSecurityTokenService stsClient, Logger logger) {
        super(iclass, environment, stsClient, logger);
    }

    protected UUID getPublicationIdentifier(RequestInfo requestInfo) throws BadRequestException {
        String publicationIdentifierString = requestInfo.getPathParameter(API_PUBLICATION_PATH_IDENTIFIER);
        return attempt(() -> UUID.fromString(publicationIdentifierString))
            .orElseThrow(fail -> new BadRequestException(INVALID_PUBLICATION_ID_ERROR + publicationIdentifierString));
    }

    protected String getUserName(RequestInfo requestInfo) throws ForbiddenException {
        try {
            return requestInfo.getFeideId().orElseThrow();
        } catch (Exception e) {
            logger.warn(NO_USERNAME_FOUND);
            throw new ForbiddenException();
        }
    }
}
