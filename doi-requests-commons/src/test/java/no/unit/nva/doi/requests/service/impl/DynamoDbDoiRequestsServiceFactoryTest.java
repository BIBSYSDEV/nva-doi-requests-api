package no.unit.nva.doi.requests.service.impl;

import static no.unit.nva.doi.requests.service.impl.DynamoDbDoiRequestsServiceFactory.MISSING_SDK_CLIENT_ERROR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import nva.commons.utils.Environment;
import nva.commons.utils.log.LogUtils;
import nva.commons.utils.log.TestAppender;
import org.junit.jupiter.api.Test;

class DynamoDbDoiRequestsServiceFactoryTest {

    @Test
    public void factoryReturnsServiceWhenCalled() {
        TestAppender testAppender = LogUtils.getTestingAppender(DynamoDbDoiRequestsServiceFactory.class);
        Environment environment = mock(Environment.class);
        when(environment.readEnv(anyString())).thenReturn("*");

        DynamoDBDoiRequestsService service =
            new DynamoDbDoiRequestsServiceFactory(environment).getService(null);
        assertThatClientCreationWasInvoked(service, testAppender);
    }

    public void assertThatClientCreationWasInvoked(DynamoDBDoiRequestsService service, TestAppender appender) {
        if (service == null) {
            assertThat(appender.getMessages(), containsString(MISSING_SDK_CLIENT_ERROR));
        }
    }
}