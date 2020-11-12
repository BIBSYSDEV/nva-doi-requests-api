package no.unit.nva.doi.requests.handlers;

import static nva.commons.utils.attempt.Try.attempt;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.Tag;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import no.unit.nva.doi.requests.service.impl.DynamoDbDoiRequestsServiceFactory;
import nva.commons.handlers.AuthorizedApiGatewayHandler;
import nva.commons.handlers.RequestInfo;
import nva.commons.utils.Environment;
import nva.commons.utils.JacocoGenerated;
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

    @Override
    protected final List<Tag> sessionTags(RequestInfo requestInfo) {

        List<Tag> accessRightsTags = accessRightsToTags(requestInfo);

        Tag publisherIdentifierTag = createTag(PUBLISHER_IDENTIFIER, requestInfo.getCustomerId().orElse(null));

        ArrayList<Tag> tags = new ArrayList<>(accessRightsTags);
        tags.add(publisherIdentifierTag);
        return tags;
    }

    private Tag createTag(String publisherIdentifier, String s) {
        return new Tag().withKey(publisherIdentifier)
            .withValue(s);
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

    @JacocoGenerated
    protected static DynamoDbDoiRequestsServiceFactory defaultServiceFactory() {
        return new DynamoDbDoiRequestsServiceFactory();
    }

    @JacocoGenerated
    protected static AWSSecurityTokenService defaultStsClient() {
        return AWSSecurityTokenServiceClientBuilder.defaultClient();
    }
}
