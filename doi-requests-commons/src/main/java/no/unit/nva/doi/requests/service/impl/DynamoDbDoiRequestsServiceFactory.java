package no.unit.nva.doi.requests.service.impl;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;

import java.time.Clock;
import java.util.function.Function;

import nva.commons.utils.Environment;
import nva.commons.utils.JacocoGenerated;

public class DynamoDbDoiRequestsServiceFactory {

    private final Function<AWSCredentialsProvider, DynamoDBDoiRequestsService> serviceProvider;

    @JacocoGenerated
    public DynamoDbDoiRequestsServiceFactory() {
        this(new Environment());
    }

    @JacocoGenerated
    public DynamoDbDoiRequestsServiceFactory(Environment environment) {
        this(credentials -> defaultServiceWithCredentials(credentials, environment));
    }

    /**
     * Create a factory that provides a new instance of the service every time it is called.
     * The provider accepts {@link AWSCredentialsProvider} as credentials parameter.
     *
     * @param serviceProvider a lambda function calling one of the available static functions.
     */
    @JacocoGenerated
    public DynamoDbDoiRequestsServiceFactory(
            Function<AWSCredentialsProvider, DynamoDBDoiRequestsService> serviceProvider) {
        this.serviceProvider = serviceProvider;
    }

    @JacocoGenerated
    public static DynamoDBDoiRequestsService defaultServiceWithoutCredentials(Environment environment) {
        return new DynamoDbDoiRequestsServiceFactory(environment).getService(null);
    }

    @JacocoGenerated
    public static DynamoDbDoiRequestsServiceFactory fromClientWithoutCredentials(AmazonDynamoDB client,
                                                                                 Environment environment) {
        return fromClientWithoutCredentials(client, environment, Clock.systemDefaultZone());
    }

    @JacocoGenerated
    public static DynamoDbDoiRequestsServiceFactory fromClientWithoutCredentials(AmazonDynamoDB client,
                                                                                 Environment environment, Clock
                                                                                         clock) {
        return new DynamoDbDoiRequestsServiceFactory(ignoreCredentials(client, environment, clock));
    }

    @JacocoGenerated
    public DynamoDBDoiRequestsService getService(AWSCredentialsProvider credentials) {
        return serviceProvider.apply(credentials);
    }

    @JacocoGenerated
    private static Function<AWSCredentialsProvider, DynamoDBDoiRequestsService> ignoreCredentials(
            AmazonDynamoDB client,
            Environment environment,
            Clock clock) {

        return cred -> new DynamoDBDoiRequestsService(client, environment, clock);
    }

    @JacocoGenerated
    private static DynamoDBDoiRequestsService defaultServiceWithCredentials(AWSCredentialsProvider credentials,
                                                                            Environment environment) {
        AmazonDynamoDB client = dbClientWithCredentials(credentials);
        return new DynamoDBDoiRequestsService(client, environment, Clock.systemDefaultZone());
    }

    @JacocoGenerated
    private static AmazonDynamoDB dbClientWithCredentials(AWSCredentialsProvider credentials) {
        return AmazonDynamoDBClientBuilder.standard().withCredentials(credentials).build();
    }
}
