package no.unit.nva.doi.requests.userdetails;

import com.fasterxml.jackson.core.JsonPointer;
import nva.commons.handlers.RequestInfo;
import nva.commons.utils.JacocoGenerated;

public final class UserDetails {

    public static final String ROLE = "role";
    public static final JsonPointer FEIDE_ID = JsonPointer.compile("/authorizer/claims/custom:feideId");
    public static final JsonPointer CUSTOMER_ID = JsonPointer.compile("/authorizer/claims/custom:customerId");
    public static final JsonPointer APPLICATION_ROLES = JsonPointer.compile(
        "/authorizer/claims/custom:applicationRoles");

    @JacocoGenerated
    private UserDetails() {

    }

    public static String getUsername(RequestInfo requestInfo) {
        return requestInfo.getRequestContextParameter(FEIDE_ID);
    }

    public static String getCustomerId(RequestInfo requestInfo) {
        return requestInfo.getRequestContextParameter(CUSTOMER_ID);
    }

    public static String getAssignedRoles(RequestInfo requestInfo) {
        return requestInfo.getRequestContextParameter(APPLICATION_ROLES);
    }
}