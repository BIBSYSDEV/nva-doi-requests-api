package no.unit.nva.doi.requests.model;

import java.util.Objects;
import no.unit.nva.doi.requests.exception.BadRequestException;
import no.unit.nva.model.DoiRequestStatus;
import nva.commons.utils.JacocoGenerated;

public class ApiUpdateDoiRequest extends AbstractDoiRequest {

    public static final String NO_CHANGE_REQUESTED_ERROR = "You must request changes to do";
    private DoiRequestStatus doiRequestStatus;

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
        if (!(o instanceof ApiUpdateDoiRequest)) {
            return false;
        }
        ApiUpdateDoiRequest that = (ApiUpdateDoiRequest) o;
        return doiRequestStatus == that.doiRequestStatus && Objects.equals(getPublicationId(), that.getPublicationId());
    }

    @JacocoGenerated
    public DoiRequestStatus getDoiRequestStatus() {
        return this.doiRequestStatus;
    }

    @JacocoGenerated
    public void setDoiRequestStatus(DoiRequestStatus status) {
        this.doiRequestStatus = status;
    }

    @Override
    public void validate() throws BadRequestException {
        if (doiRequestStatus == null) {
            throw noChangeRequested();
        }
    }

    private BadRequestException noChangeRequested() {
        return new BadRequestException(NO_CHANGE_REQUESTED_ERROR);
    }
}
