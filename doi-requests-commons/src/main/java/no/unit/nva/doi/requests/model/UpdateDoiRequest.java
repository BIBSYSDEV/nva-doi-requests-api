package no.unit.nva.doi.requests.model;

import no.unit.nva.model.DoiRequestStatus;
import nva.commons.utils.JacocoGenerated;

import java.util.Objects;
import java.util.Optional;

public class UpdateDoiRequest extends AbstractDoiRequest {

    private DoiRequestStatus doiRequestStatus = null;

    @Override
    @JacocoGenerated
    public int hashCode() {
        return Objects.hash(doiRequestStatus, super.hashCode());
    }

    @Override
    @JacocoGenerated
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UpdateDoiRequest)) {
            return false;
        }
        UpdateDoiRequest that = (UpdateDoiRequest) o;
        return doiRequestStatus == that.doiRequestStatus && Objects.equals(getPublicationId(), that.getPublicationId());
    }

    @JacocoGenerated
    public Optional<DoiRequestStatus> getDOIRequestStatus() {
        return Optional.ofNullable(this.doiRequestStatus);
    }

    @JacocoGenerated
    public void setDOIRequestStatus(DoiRequestStatus status) {
        this.doiRequestStatus = status;
    }
}
