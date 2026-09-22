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
