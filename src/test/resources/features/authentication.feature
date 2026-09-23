Feature: User Authentication, Token Validation, and Role-Based Access Control via Keycloak IAM
  As a system security architect
  I want all incoming HTTP requests to be authenticated against Keycloak OIDC JWT tokens
  So that user identities, roles (THERAPIST, CLIENT, ADMIN), and tenant boundaries are rigorously enforced across the API

  Background:
    Given Keycloak IAM is running and healthy at "http://localhost:8080"
    And the backend Spring Security filter chain is configured as an OAuth2 Resource Server
    And Keycloak public JWKS key set is available at "http://localhost:8080/oauth/v2/keys"

  # =========================================================================
  # 1. UNAUTHENTICATED & MALFORMED TOKEN SCENARIOS
  # =========================================================================

  Scenario: Request to protected endpoint without Authorization header is rejected
    Given no "Authorization" header is present in the request
    When a client sends a GET request to "/api/v1/appointments/client/550e8400-e29b-41d4-a716-446655440000"
    Then the system should reject the request
    And return an HTTP response with status code 401 Unauthorized
    And the response header "WWW-Authenticate" should contain 'Bearer error="unauthorized"'

  Scenario: Request with malformed Bearer token syntax is rejected
    Given an "Authorization" header with value "Bearer invalid-jwt-token-format"
    When a client sends a GET request to "/api/v1/user/profile"
    Then the system should reject the request
    And return an HTTP response with status code 401 Unauthorized

  Scenario: Request with expired Keycloak JWT token is rejected
    Given a Keycloak JWT token with expiration timestamp in the past "2026-01-01T00:00:00Z"
    When a client sends a GET request to "/api/v1/user/profile" with the expired token
    Then the system should reject the request
    And return an HTTP response with status code 401 Unauthorized
    And the error details should state "Jwt expired"

  # =========================================================================
  # 2. VALID TOKEN & USER PROFILE SCENARIOS
  # =========================================================================

  Scenario: Authenticated client retrieves their own user profile
    Given a valid Keycloak JWT token signed by "http://localhost:8080"
    And the JWT token contains subject UUID "550e8400-e29b-41d4-a716-446655440000"
    And the JWT token contains email "jesper@example.com" and name "Jesper Kock"
    And the JWT token contains assigned role "ROLE_CLIENT"
    When the client sends a GET request to "/api/v1/user/profile" with the valid token
    Then the response status should be 200 OK
    And the response body should be a JSON object containing:
      | userUuid  | 550e8400-e29b-41d4-a716-446655440000 |
      | email     | jesper@example.com                   |
      | name      | Jesper Kock                          |
      | primaryRole | CLIENT                             |

  # =========================================================================
  # 3. ROLE-BASED ACCESS CONTROL (RBAC) & PERMISSION DENIED SCENARIOS
  # =========================================================================

  Scenario: Client user attempts to access Therapist-only clinical journal endpoint
    Given a valid Keycloak JWT token for subject UUID "550e8400-e29b-41d4-a716-446655440000"
    And the user has role "ROLE_CLIENT" only
    When the user sends a POST request to "/api/v1/journals/therapist-notes" with body:
      """
      {
        "clientUuid": "550e8400-e29b-41d4-a716-446655440000",
        "therapistNote": "Unauthorized attempt"
      }
      """
    Then the system should reject the access
    And return an HTTP response with status code 403 Forbidden
    And the response body should contain error message "Access Denied: Required role ROLE_THERAPIST"

  Scenario: Therapist user accesses Therapist-only clinical journal endpoint
    Given a valid Keycloak JWT token for subject UUID "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
    And the user has role "ROLE_THERAPIST"
    When the user sends a POST request to "/api/v1/journals/therapist-notes" with body:
      """
      {
        "clientUuid": "550e8400-e29b-41d4-a716-446655440000",
        "therapistNote": "Therapy progress evaluation note"
      }
      """
    Then the response status should be 200 OK
    And the note should be saved and linked to therapist UUID "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
