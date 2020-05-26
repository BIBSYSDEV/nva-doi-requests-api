Feature: Find DOI requests

  Scenario Outline: A Creator finds DOI Requests
    Given A Creator has Publications with DOI Requests
    When they set the Accept header to "application/json"
    And they set the Authentication header to a Bearer token with their credentials
    And they request GET /doi/request?role=creator
    Then they receive a response with status code 200
    And they see that the response Content-Type header is "application/json"
    And they see that the response body is a DoiRequestsResponse
    And they see that the response body has a list of DOI Requests
    And they see that each DOI Request has a property <Property>

    Examples:
      | Property              |
      | doiRequestStatus      |
      | doiRequestDate        |
      | publicationIdentifier |
      | publicationTitle      |
      | publicationCreator    |

  Scenario: A Curator finds DOI Requests
    Given A Curator belongs to a Publisher that has Publications with DOI Requests
    When they set the Accept header to "application/json"
    And they set the Authentication header to a Bearer token with their credentials
    And they request GET /doi/requests?role=curator
    Then they receive a response with status code 200
    And they see that the response Content-Type header is "application/json"
    And they see that the response body is a DoiRequestsResponse
    And they see that the response body has a list of DOI Requests

  Scenario: An Anonymous User attempts to find DOI Requests as a Creator
    Given An Anonymous User wants to find DOI Requests
    When they set the Accept header to "application/json"
    And they request GET /doi/request?role=creator
    Then they receive a response with status code 401
    And they see that the response Content-Type header is "application/problem+json"
    And they see that the response body is a problem.json object
    And they see the response body has a field "title" with the value "Unauthorized"
    And they see the response body has a field "status" with the value "401"
    And they see the response body has a field "detail" with a description of the problem

  Scenario: A Creator attempts to find DOI Requests as Curator
    Given A Creator does not have the role of Curator
    When they set the Accept header to "application/json"
    And they set the Authentication header to a Bearer token with their credentials
    And they request GET /doi/request?role=curator
    Then they receive a response with status code 401
    And they see that the response Content-Type header is "application/problem+json"
    And they see that the response body is a problem.json object
    And they see the response body has a field "title" with the value "Unauthorized"
    And they see the response body has a field "status" with the value "401"
    And they see the response body has a field "detail" with a description of the problem