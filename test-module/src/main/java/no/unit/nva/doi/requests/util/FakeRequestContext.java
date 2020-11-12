package no.unit.nva.doi.requests.util;

import static nva.commons.handlers.RequestInfo.ACCESS_RIGHTS_CLAIM;
import static nva.commons.handlers.RequestInfo.APPLICATION_ROLES_CLAIM;
import static nva.commons.handlers.RequestInfo.CUSTOMER_ID_CLAIM;
import static nva.commons.handlers.RequestInfo.FEIDE_ID_CLAIM;
import static nva.commons.utils.JsonUtils.objectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import nva.commons.utils.JacocoGenerated;

public final class FakeRequestContext {

    public static final String SAMPLE_FEIDE_ID = "SomeFeideId";
    public static final String SAMPLE_CUSTOMER_ID = "SAMPLE_CUSTOMER_ID";
    public static final String SOME_ROLE = "Role1";
    public static final String SOME_OTHER_ROLE = "Role2";
    public static final String SOME_ACCESS_RIGHT = "someAccessRight";
    public static final String SOME_OTHER_ACCESS_RIGHT = "someOtherAccessRight";
    public static final String COMMA_DELIMITER = ",";
    private static final String CLAIMS = "claims";
    private static final String AUTHORIZER = "authorizer";

    private FakeRequestContext() {
    }

    @JacocoGenerated
    public static ObjectNode requestContext() {
        return requestContext(SOME_ROLE, SOME_OTHER_ROLE);
    }

    public static ObjectNode requestContext(String... roles) {
        final ObjectNode requestContext = objectMapper.createObjectNode();
        final ObjectNode authorizerNode = objectMapper.createObjectNode();

        final ObjectNode claimsNode = objectMapper.createObjectNode();
        claimsNode.put(FEIDE_ID_CLAIM, SAMPLE_FEIDE_ID);
        claimsNode.put(CUSTOMER_ID_CLAIM, SAMPLE_CUSTOMER_ID);
        claimsNode.put(APPLICATION_ROLES_CLAIM, String.join(COMMA_DELIMITER, roles));
        claimsNode.put(ACCESS_RIGHTS_CLAIM, String.join(COMMA_DELIMITER, SOME_ACCESS_RIGHT, SOME_OTHER_ACCESS_RIGHT));

        authorizerNode.set(CLAIMS, claimsNode);
        requestContext.set(AUTHORIZER, authorizerNode);

        return requestContext;
    }
}