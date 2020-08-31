package no.unit.nva.doi.requests.userdetails;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.file.Path;
import nva.commons.handlers.RequestInfo;
import nva.commons.utils.IoUtils;
import nva.commons.utils.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UserDetailsTest {

    private static final String FEIDE_ID_IN_FILE = "feide@id";
    private static final String CUSTOMER_ID_IN_FILE = "123456";
    private static final String ROLES_IN_FILE = "Role1,Role2,Role3";
    private RequestInfo requestInfo;

    @BeforeEach
    public void init() throws JsonProcessingException {

        String requestInfoResource = IoUtils.stringFromResources(Path.of("requestInfoWithContext.json"));
        requestInfo = JsonUtils.objectMapper.readValue(requestInfoResource, RequestInfo.class);
    }

    @Test
    public void getUsernameReturnsTheUserId() {
        String actualUsername = UserDetails.getUsername(requestInfo);
        assertThat(actualUsername, is(equalTo(FEIDE_ID_IN_FILE)));
    }

    @Test
    public void getCustomerIdReturnsTheCustomerId() {
        String actualCustomerId = UserDetails.getCustomerId(requestInfo);
        assertThat(actualCustomerId, is(equalTo(CUSTOMER_ID_IN_FILE)));
    }

    @Test
    public void getAssignedRolesReturnsAssignedRoles() {
        String actualRoles = UserDetails.getAssignedRoles(requestInfo);
        assertThat(actualRoles, is(equalTo(ROLES_IN_FILE)));
    }
}