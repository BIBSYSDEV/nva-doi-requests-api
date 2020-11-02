package no.unit.nva.doi.requests.service.impl;

import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import java.time.Clock;
import java.util.function.Function;
import nva.commons.utils.Environment;
import nva.commons.utils.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynamoDbDoiRequestsServiceFactory {

    public static final String MISSING_SDK_CLIENT_ERROR = "Sdk client has not been installed in the system";
    private static final Logger logger = LoggerFactory.getLogger(DynamoDbDoiRequestsServiceFactory.class);
    public static final AWSCredentialsProvider EMPTY_CREDENTIALS = null;

    private final Function<AWSCredentialsProvider, DynamoDBDoiRequestsService> serviceProvider;

    @JacocoGenerated
    public DynamoDbDoiRequestsServiceFactory() {
        this(new Environment());
    }

    @JacocoGenerated
    public DynamoDbDoiRequestsServiceFactory(Environment environment) {
        this(credentialsProvider -> serviceWitDefaultClientWithCredentials(credentialsProvider, environment));
    }

    /**
     * Create a factory that provides a new instance of the service every time it is called. The provider accepts {@link
     * AWSCredentialsProvider} as credentials parameter.
     *
     * @param serviceProvider a lambda function calling one of the available static functions.
     */
    @JacocoGenerated
    public DynamoDbDoiRequestsServiceFactory(
        Function<AWSCredentialsProvider, DynamoDBDoiRequestsService> serviceProvider) {
        this.serviceProvider = serviceProvider;
    }

    @JacocoGenerated
    public static DynamoDBDoiRequestsService serviceWithDefaultClientWithoutCredentials(Environment environment) {
        return new DynamoDbDoiRequestsServiceFactory(environment).getService(null);
    }

    @JacocoGenerated
    public static DynamoDbDoiRequestsServiceFactory serviceWithCustomClientWithoutCredentials(AmazonDynamoDB client,
                                                                                              Environment environment) {
        return serviceWithCustomClientWithoutCredentials(client, environment, Clock.systemDefaultZone());
    }

    @JacocoGenerated
    public static DynamoDbDoiRequestsServiceFactory serviceWithCustomClientWithoutCredentials(AmazonDynamoDB client,
                                                                                              Environment environment,
                                                                                              Clock clock) {
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
    private static DynamoDBDoiRequestsService serviceWitDefaultClientWithCredentials(AWSCredentialsProvider credentials,
                                                                                     Environment environment) {

        try {
            var client = AmazonDynamoDBClientBuilder.standard().withCredentials(credentials).build();
            return new DynamoDBDoiRequestsService(client, environment, Clock.systemDefaultZone());
        } catch (SdkClientException e) {
            return handleMissingSdkClientError();
        }
    }

    @JacocoGenerated
    private static DynamoDBDoiRequestsService handleMissingSdkClientError() {
        logger.error(MISSING_SDK_CLIENT_ERROR);
        return null;
    }
}
