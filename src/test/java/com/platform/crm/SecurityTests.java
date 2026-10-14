package com.platform.crm;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class SecurityTests {

    @TestConfiguration
    static class MockSecurityConfig {
        @Bean
        @Primary
        public JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                        .header("alg", "none")
                        .claim("sub", "mock-user")
                        .build();
        }
    }

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void testApiIsSecured() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/test"))
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());

        // Blank X-Auth-User-Uuid still returns 401
        HttpRequest blankHeaderReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/clinic/directory"))
                .header("X-Auth-User-Uuid", "   ")
                .GET()
                .build();
        assertEquals(401, httpClient.send(blankHeaderReq, HttpResponse.BodyHandlers.ofString()).statusCode());

        // BFF session headers with full claims
        HttpRequest bffFullReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/clinic/directory"))
                .header("X-Auth-User-Uuid", "5e357ca1-4913-414f-91a6-81a7de9042ab")
                .header("X-Auth-Username", "anders_sand")
                .header("X-Auth-Full-Name", "Anders Sand")
                .header("X-Auth-Email", "client@mail.com")
                .header("X-Auth-Roles", "client, therapist, ")
                .GET()
                .build();
        assertEquals(200, httpClient.send(bffFullReq, HttpResponse.BodyHandlers.ofString()).statusCode());

        // BFF session headers with minimal header (defaults for username, fullName, email, roles)
        HttpRequest bffMinReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/clinic/directory"))
                .header("X-Auth-User-Uuid", "5e357ca1-4913-414f-91a6-81a7de9042ab")
                .GET()
                .build();
        assertEquals(200, httpClient.send(bffMinReq, HttpResponse.BodyHandlers.ofString()).statusCode());

        // BFF session headers with username but no fullName and blank roles
        HttpRequest bffPartialReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/clinic/directory"))
                .header("X-Auth-User-Uuid", "5e357ca1-4913-414f-91a6-81a7de9042ab")
                .header("X-Auth-Username", "anders")
                .header("X-Auth-Roles", "   ")
                .GET()
                .build();
        assertEquals(200, httpClient.send(bffPartialReq, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void testKeycloakSoleRoleAuthorityEndToEnd() throws Exception {
        String upgradingUserUuid = java.util.UUID.randomUUID().toString();

        // 1. Before CVR onboarding: user has only "client" role in Keycloak -> isTherapist is false
        HttpRequest meBeforeReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/clinic/me"))
                .header("X-Auth-User-Uuid", upgradingUserUuid)
                .header("X-Auth-Username", "ny_terapeut")
                .header("X-Auth-Full-Name", "Mette Lind")
                .header("X-Auth-Email", "mette@klinik.dk")
                .header("X-Auth-Roles", "client")
                .GET()
                .build();
        HttpResponse<String> meBeforeResp = httpClient.send(meBeforeReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, meBeforeResp.statusCode());
        org.junit.jupiter.api.Assertions.assertTrue(meBeforeResp.body().contains("\"isTherapist\":false"));

        // 2. Register clinic metadata with CVR via /api/v1/clinic/therapist-onboarding
        String onboardJson = """
                {
                  "userUuid": "%s",
                  "cvrNumber": "44556677",
                  "organisationName": "Lind Psykoterapi ApS",
                  "displayName": "Mette Lind (Cand.psych.)"
                }
                """.formatted(upgradingUserUuid);
        HttpRequest onboardReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/clinic/therapist-onboarding"))
                .header("Content-Type", "application/json")
                .header("X-Auth-User-Uuid", upgradingUserUuid)
                .header("X-Auth-Roles", "client")
                .POST(HttpRequest.BodyPublishers.ofString(onboardJson))
                .build();
        HttpResponse<String> onboardResp = httpClient.send(onboardReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, onboardResp.statusCode());

        // 3. Business DB (provider_profiles) does NOT grant roles: if Keycloak token only has "client",
        //    isTherapist remains false and therapist-only endpoints return 403 Forbidden
        HttpRequest meStillClientReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/clinic/me"))
                .header("X-Auth-User-Uuid", upgradingUserUuid)
                .header("X-Auth-Username", "ny_terapeut")
                .header("X-Auth-Roles", "client")
                .GET()
                .build();
        HttpResponse<String> meStillClientResp = httpClient.send(meStillClientReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, meStillClientResp.statusCode());
        org.junit.jupiter.api.Assertions.assertTrue(meStillClientResp.body().contains("\"isTherapist\":false"));
        org.junit.jupiter.api.Assertions.assertFalse(meStillClientResp.body().contains("ROLE_therapist"));

        String noteJson = """
                {
                  "clientUuid": "%s",
                  "therapistNote": "Kun Keycloak-rollen giver adgang.",
                  "therapistName": "Mette Lind",
                  "organisationName": "Lind Psykoterapi ApS"
                }
                """.formatted(java.util.UUID.randomUUID().toString());
        HttpRequest forbiddenNoteReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/journals/therapist-notes"))
                .header("Content-Type", "application/json")
                .header("X-Auth-User-Uuid", upgradingUserUuid)
                .header("X-Auth-Roles", "client")
                .POST(HttpRequest.BodyPublishers.ofString(noteJson))
                .build();
        HttpResponse<String> forbiddenNoteResp = httpClient.send(forbiddenNoteReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, forbiddenNoteResp.statusCode());

        // 4. Once Keycloak assigns the "therapist" role and the token is refreshed (X-Auth-Roles: client, therapist),
        //    Keycloak grants ROLE_therapist and therapist endpoints succeed
        HttpRequest meAfterKeycloakUpgradeReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/clinic/me"))
                .header("X-Auth-User-Uuid", upgradingUserUuid)
                .header("X-Auth-Username", "ny_terapeut")
                .header("X-Auth-Roles", "client, therapist")
                .GET()
                .build();
        HttpResponse<String> meAfterKeycloakUpgradeResp = httpClient.send(meAfterKeycloakUpgradeReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, meAfterKeycloakUpgradeResp.statusCode());
        org.junit.jupiter.api.Assertions.assertTrue(meAfterKeycloakUpgradeResp.body().contains("\"isTherapist\":true"));
        org.junit.jupiter.api.Assertions.assertTrue(meAfterKeycloakUpgradeResp.body().contains("\"therapist\""));

        HttpRequest allowedNoteReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/journals/therapist-notes"))
                .header("Content-Type", "application/json")
                .header("X-Auth-User-Uuid", upgradingUserUuid)
                .header("X-Auth-Roles", "client, therapist")
                .POST(HttpRequest.BodyPublishers.ofString(noteJson))
                .build();
        HttpResponse<String> allowedNoteResp = httpClient.send(allowedNoteReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, allowedNoteResp.statusCode());
    }
}
