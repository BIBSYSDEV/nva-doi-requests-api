package no.unit.nva.doi.requests.util;

import static no.unit.nva.doi.requests.contants.ServiceConstants.API_HOST_ENV_VARIABLE;
import static no.unit.nva.doi.requests.contants.ServiceConstants.API_SCHEME_ENV_VARIABLE;
import static no.unit.nva.doi.requests.contants.ServiceConstants.DOI_REQUESTS_INDEX_ENV_VARIABLE;
import static no.unit.nva.doi.requests.contants.ServiceConstants.PUBLICATIONS_TABLE_NAME_ENV_VARIABLE;
import static no.unit.nva.doi.requests.util.DoiRequestsDynamoDBLocal.BY_DOI_REQUEST_INDEX_NAME;
import static no.unit.nva.doi.requests.util.DoiRequestsDynamoDBLocal.NVA_RESOURCES_TABLE_NAME;
import static nva.commons.handlers.ApiGatewayHandler.ALLOWED_ORIGIN_ENV;
import java.util.Map;
import java.util.Optional;
import nva.commons.handlers.AuthorizedApiGatewayHandler;
import nva.commons.utils.Environment;

public final class MockEnvironment {

    public static final String ENV_VARIABLE_NOT_FOUND_ERROR = "Did not find env variable:";
    public static final String ALLOW_CORS = "*";
    public static final String FAKE_API_HOST_ENV = "mocked-hostname.example.net";
    public static final String FAKE_API_SCHEME_ENV = "https";
    public static final String ASSUMED_ROLE_ARN = "AssumedRoleArn";

    /**
     * Mock environment with all env variables necessary (database and handlers).
     *
     * @return Environment.
     */
    public static Environment mockEnvironment() {
        final Map<String, String> envVariables = Map
            .of(ALLOWED_ORIGIN_ENV, ALLOW_CORS,
                PUBLICATIONS_TABLE_NAME_ENV_VARIABLE, NVA_RESOURCES_TABLE_NAME,
                DOI_REQUESTS_INDEX_ENV_VARIABLE, BY_DOI_REQUEST_INDEX_NAME,
                API_HOST_ENV_VARIABLE, FAKE_API_HOST_ENV,
                API_SCHEME_ENV_VARIABLE, FAKE_API_SCHEME_ENV,
                AuthorizedApiGatewayHandler.ASSUMED_ROLE_ARN_ENV_VAR, ASSUMED_ROLE_ARN
            );
        return new Environment() {
            @Override
            public Optional<String> readEnvOpt(String variableName) {
                return Optional.ofNullable(envVariables.getOrDefault(variableName, null));
            }

            @Override
            public String readEnv(String variableName) {
                return readEnvOpt(variableName).orElseThrow(() ->
                    new IllegalStateException(ENV_VARIABLE_NOT_FOUND_ERROR + variableName));
            }
        };
    }
}
