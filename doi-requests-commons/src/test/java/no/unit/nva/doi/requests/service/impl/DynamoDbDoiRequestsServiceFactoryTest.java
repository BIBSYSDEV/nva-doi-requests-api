package no.unit.nva.doi.requests.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import nva.commons.utils.Environment;
import org.junit.jupiter.api.Test;

class DynamoDbDoiRequestsServiceFactoryTest {

    @Test
    public void factoryReturnsServiceWhenCalled() {
        Environment environment = mock(Environment.class);
        when(environment.readEnv(anyString())).thenReturn("*");
        assertDoesNotThrow(() -> new DynamoDbDoiRequestsServiceFactory(environment).getService(null));
    }
}