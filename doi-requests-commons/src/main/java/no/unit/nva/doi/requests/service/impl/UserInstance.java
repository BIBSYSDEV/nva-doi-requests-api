package no.unit.nva.doi.requests.service.impl;

import java.net.URI;
import java.util.Optional;
import java.util.Set;
import no.unit.nva.useraccessmanagement.dao.AccessRight;

public class UserInstance {

    private final String userId;
    private final URI publisherId;
    private final Set<AccessRight> accessRights;

    public UserInstance(String userId, URI publisherId, Set<AccessRight> accessRights) {

        this.userId = userId;
        this.publisherId = publisherId;
        this.accessRights = accessRights;
    }

    public String getUserId() {
        return userId;
    }

    public Optional<URI> getPublisherId() {
        return Optional.ofNullable(publisherId);
    }

    public Set<AccessRight> getAccessRights() {
        return accessRights;
    }
}
