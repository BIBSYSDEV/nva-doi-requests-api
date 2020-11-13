package no.unit.nva.doi.requests.handlers;

import static nva.commons.utils.attempt.Try.attempt;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.Tag;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import no.unit.nva.doi.requests.service.impl.DynamoDbDoiRequestsServiceFactory;
import nva.commons.handlers.AuthorizedApiGatewayHandler;
import nva.commons.handlers.RequestInfo;
import nva.commons.utils.Environment;
import nva.commons.utils.JacocoGenerated;
import nva.commons.utils.StringUtils;
import org.slf4j.Logger;

public abstract class DoiRequestAuthorizedHandlerTemplate<I, O> extends AuthorizedApiGatewayHandler<I, O> {

    public static final String PUBLISHER_IDENTIFIER = "publisherIdentifier";
    protected static final DynamoDbDoiRequestsServiceFactory DEFAULT_SERVICE_FACTORY = defaultServiceFactory();

    @JacocoGenerated
    protected DoiRequestAuthorizedHandlerTemplate(Class<I> iclass, Environment environment,
                                                  AWSSecurityTokenService stsClient,
                                                  Logger logger) {
        super(iclass, environment, stsClient, logger);
    }

    @JacocoGenerated
    protected static DynamoDbDoiRequestsServiceFactory defaultServiceFactory() {
        return new DynamoDbDoiRequestsServiceFactory();
    }

    @JacocoGenerated
    protected static AWSSecurityTokenService defaultStsClient() {
        return AWSSecurityTokenServiceClientBuilder.defaultClient();
    }

    @Override
    protected final List<Tag> sessionTags(RequestInfo requestInfo) {

        List<Tag> assumedRoleTags = accessRightsToTags(requestInfo);

        Optional<Tag> publisherIdentifierTag = requestInfo
            .getCustomerId()
            .flatMap(this::createPublisherTag);

        publisherIdentifierTag.ifPresent(assumedRoleTags::add);

        return assumedRoleTags;
    }

    private Optional<Tag> createPublisherTag(String publisherTagValue) {
        if (StringUtils.isBlank(publisherTagValue)) {
            return Optional.empty();
        }
        return Optional.of(createTag(PUBLISHER_IDENTIFIER, publisherTagValue));
    }

    private Tag createTag(String tagKey, String tagValue) {
        return new Tag().withKey(tagKey).withValue(tagValue);
    }

    private List<Tag> accessRightsToTags(RequestInfo requestInfo) {
        return attempt(requestInfo::getAccessRights)
            .toOptional()
            .stream()
            .flatMap(Collection::stream)
            .map(this::tagAccessRight)
            .collect(Collectors.toList());
    }

    private Tag tagAccessRight(String ar) {
        return createTag(tagKey(ar), tagValue(ar));
    }

    private String tagValue(String ar) {
        return ar.toUpperCase(Locale.getDefault());
    }

    private String tagKey(String ar) {
        return ar.toLowerCase(Locale.getDefault());
    }
}
