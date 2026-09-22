Feature: User Management API

  Scenario: Creating a new client user
    Given the backend server is running
    When I send a POST request to "/api/users" with the following JSON:
      """
      {
        "username": "newclient",
        "password": "securepassword",
        "role": "CLIENT"
      }
      """
    Then the response status should be 201
    And the response body should contain "newclient"

  Scenario: Creating a new admin user
    Given the backend server is running
    When I send a POST request to "/api/users" with the following JSON:
      """
      {
        "username": "newadmin",
        "password": "securepassword",
        "role": "ADMIN"
      }
      """
    Then the response status should be 201
    And the response body should contain "newadmin"

  Scenario: Creating a new user with an existing username
    Given the backend server is running
    When I send a POST request to "/api/users" with the following JSON:
      """
      {
        "username": "newclient",
        "password": "securepassword",
        "role": "CLIENT"
      }
      """
    Then the response status should be 400
    And the response body should contain "username already exists"

  Scenario: Creating a new user with an invalid role
    Given the backend server is running
    When I send a POST request to "/api/users" with the following JSON:
      """
      {
        "username": "invalidroleuser",
        "password": "securepassword",
        "role": "INVALID_ROLE"
      }
      """
    Then the response status should be 400
    And the response body should contain "invalid role"

    