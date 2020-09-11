package no.unit.nva.doi.requests.handlers.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import nva.commons.utils.JacocoGenerated;

public class ApiTask {

    private final String href;
    private final String identifier;

    @JsonCreator
    public ApiTask(@JsonProperty("href") String href,
                   @JsonProperty("identifier") String identifier) {
        this.href = href;
        this.identifier = identifier;
    }

    public String getHref() {
        return href;
    }

    public String getIdentifier() {
        return identifier;
    }

    @Override
    @JacocoGenerated
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ApiTask)) {
            return false;
        }
        ApiTask apiTask = (ApiTask) o;
        return Objects.equals(getHref(), apiTask.getHref()) &&
            Objects.equals(getIdentifier(), apiTask.getIdentifier());
    }

    @Override
    @JacocoGenerated
    public int hashCode() {
        return Objects.hash(getHref(), getIdentifier());
    }

    private ApiTask(Builder builder) {
        href = builder.href;
        identifier = builder.identifier;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    @JacocoGenerated
    public static Builder newBuilder(ApiTask copy) {
        Builder builder = new Builder();
        builder.href = copy.getHref();
        builder.identifier = copy.getIdentifier();
        return builder;
    }

    public static final class Builder {

        private String href;
        private String identifier;

        private Builder() {
        }

        public Builder withHref(String val) {
            href = val;
            return this;
        }

        public Builder withIdentifier(String val) {
            identifier = val;
            return this;
        }

        public ApiTask build() {
            return new ApiTask(this);
        }
    }
}
