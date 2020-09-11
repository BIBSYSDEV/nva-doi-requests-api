package no.unit.nva.doi.requests.handlers.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import nva.commons.utils.JacocoGenerated;

public class ApiUpdateDoiResponse {

    private ApiTask task;

    @JsonCreator
    public ApiUpdateDoiResponse(@JsonProperty("task") ApiTask task) {
        this.task = task;
    }

    private ApiUpdateDoiResponse(Builder builder) {
        setTask(builder.task);
    }

    @Override
    @JacocoGenerated
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ApiUpdateDoiResponse)) {
            return false;
        }
        ApiUpdateDoiResponse that = (ApiUpdateDoiResponse) o;
        return Objects.equals(getTask(), that.getTask());
    }

    @Override
    @JacocoGenerated
    public int hashCode() {
        return Objects.hash(getTask());
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    @JacocoGenerated
    public static Builder newBuilder(ApiUpdateDoiResponse copy) {
        Builder builder = new Builder();
        builder.task = copy.getTask();
        return builder;
    }

    public ApiTask getTask() {
        return task;
    }

    public void setTask(ApiTask task) {
        this.task = task;
    }

    public static final class Builder {

        private ApiTask task;

        private Builder() {
        }

        public Builder withTask(ApiTask val) {
            task = val;
            return this;
        }

        public ApiUpdateDoiResponse build() {
            return new ApiUpdateDoiResponse(this);
        }
    }
}
