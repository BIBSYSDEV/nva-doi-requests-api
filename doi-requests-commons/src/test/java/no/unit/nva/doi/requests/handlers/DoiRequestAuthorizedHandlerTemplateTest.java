package no.unit.nva.doi.requests.handlers;

import static nva.commons.handlers.RequestInfo.ACCESS_RIGHTS_CLAIM;
import static nva.commons.handlers.RequestInfo.APPLICATION_ROLES_CLAIM;
import static nva.commons.handlers.RequestInfo.CUSTOMER_ID_CLAIM;
import static nva.commons.handlers.RequestInfo.FEIDE_ID_CLAIM;
import static nva.commons.utils.JsonUtils.objectMapper;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.securitytoken.model.Tag;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.handlers.RequestInfo;
import org.junit.jupiter.api.Test;

public class DoiRequestAuthorizedHandlerTemplateTest {

    public static final String SAMPLE_FEIDE_ID = "SomeFeideId";
    public static final String SAMPLE_CUSTOMER_ID = "SAMPLE_CUSTOMER_ID";
    public static final String SOME_ROLE = "Role1";
    public static final String SOME_OTHER_ROLE = "role2";
    public static final String SOME_ACCESS_RIGHT = "someAccessRight";
    public static final String SOME_OTHER_ACCESS_RIGHT = "someOtherAccessRight";
    public static final String COMMA_DELIMITER = ",";
    private static final String CLAIMS = "claims";
    private static final String AUTHORIZER = "authorizer";
    private final DoiRequestAuthorizedHandlerTemplate<Void, Void> handler =
        new DoiRequestAuthorizedHandlerTemplateImpl();

    @Test
    public void sessionTagsReturnsListOfTagsContainingAccessRight() {

        RequestInfo requestInfo = new RequestInfo();
        requestInfo.setRequestContext(requestContext());
        Set<Tag> actualTags = new HashSet<>(handler.sessionTags(requestInfo));
        Set<Tag> expectedTags = expectedTags();
        assertThat(actualTags, is(equalTo(expectedTags)));
    }

    private Set<Tag> expectedTags() {
        Set<Tag> accessRightTags = Stream.of(SOME_ACCESS_RIGHT, SOME_OTHER_ACCESS_RIGHT)
            .map(accessRight -> new Tag().withKey(lowerCased(accessRight)).withValue(upperCased(accessRight)))
            .collect(Collectors.toSet());

        Tag publisherTag = new Tag()
            .withKey(DoiRequestAuthorizedHandlerTemplate.PUBLISHER_IDENTIFIER)
            .withValue(SAMPLE_CUSTOMER_ID);

        Set<Tag> allTags = new HashSet<>(accessRightTags);
        allTags.add(publisherTag);
        return allTags;
    }

    private String upperCased(String ar) {
        return ar.toUpperCase(Locale.getDefault());
    }

    private String lowerCased(String ar) {
        return ar.toLowerCase(Locale.getDefault());
    }

    private ObjectNode requestContext() {
        final ObjectNode requestContext = objectMapper.createObjectNode();
        final ObjectNode authorizerNode = objectMapper.createObjectNode();
        final ObjectNode claimsNode = objectMapper.createObjectNode();

        requestContext.set(AUTHORIZER, authorizerNode);
        authorizerNode.set(CLAIMS, claimsNode);

        claimsNode.put(FEIDE_ID_CLAIM, SAMPLE_FEIDE_ID);
        claimsNode.put(CUSTOMER_ID_CLAIM, SAMPLE_CUSTOMER_ID);
        claimsNode.put(APPLICATION_ROLES_CLAIM, String.join(COMMA_DELIMITER, SOME_ROLE, SOME_OTHER_ROLE));
        claimsNode.put(ACCESS_RIGHTS_CLAIM, String.join(COMMA_DELIMITER, SOME_ACCESS_RIGHT, SOME_OTHER_ACCESS_RIGHT));

        return requestContext;
    }

    private static class DoiRequestAuthorizedHandlerTemplateImpl
        extends DoiRequestAuthorizedHandlerTemplate<Void, Void> {

        public DoiRequestAuthorizedHandlerTemplateImpl() {
            super(Void.class, null, null, null);
        }

        @Override
        protected Void processInput(Void input, RequestInfo requestInfo,
                                    STSAssumeRoleSessionCredentialsProvider credentialsProvider, Context context)
            throws ApiGatewayException {
            return null;
        }

        @Override
        protected Integer getSuccessStatusCode(Void input, Void output) {
            return null;
        }
    }
}