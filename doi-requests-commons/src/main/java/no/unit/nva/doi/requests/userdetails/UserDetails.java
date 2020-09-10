package no.unit.nva.doi.requests.userdetails;

import static nva.commons.utils.RequestUtils.getRequestContextParameter;

import com.fasterxml.jackson.core.JsonPointer;
import nva.commons.handlers.RequestInfo;

public final class UserDetails {

    public static final String ROLE = "role";
    public static final JsonPointer FEIDE_ID = JsonPointer.compile("/authorizer/claims/custom:feideId");
    public static final JsonPointer CUSTOMER_ID = JsonPointer.compile("/authorizer/claims/custom:customerId");
    public static final JsonPointer APPLICATION_ROLES = JsonPointer.compile(
        "/authorizer/claims/custom:applicationRoles");

    public static String getUsername(RequestInfo requestInfo) {
        return getRequestContextParameter(requestInfo, FEIDE_ID);
    }

    public static String getCustomerId(RequestInfo requestInfo) {
        return getRequestContextParameter(requestInfo, CUSTOMER_ID);
    }

    public static String getAssignedRoles(RequestInfo requestInfo) {
        return getRequestContextParameter(requestInfo, APPLICATION_ROLES);
    }
}
