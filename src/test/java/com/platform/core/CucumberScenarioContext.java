package com.platform.core;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static io.restassured.RestAssured.given;

public class CucumberScenarioContext {

    private RequestSpecification requestSpec = given();
    private Response lastResponse;
    private String generatedToken = "dummy-token";
    private final Map<String, Object> pendingJwtClaims = new HashMap<>();

    private String currentClientUuid;
    private String currentTherapistUuid;
    private String lastQueriedRangeFrom;
    private String lastQueriedRangeTo;
    private String lastCancelledAppointmentId;

    private Response clientBundleResponse;
    private Response clientHistoryResponse;

    public void resetScenarioState() {
        this.requestSpec = given();
        this.lastResponse = null;
        this.generatedToken = "dummy-token";
        this.pendingJwtClaims.clear();
        this.currentClientUuid = null;
        this.currentTherapistUuid = null;
        this.lastQueriedRangeFrom = null;
        this.lastQueriedRangeTo = null;
        this.lastCancelledAppointmentId = null;
        this.clientBundleResponse = null;
        this.clientHistoryResponse = null;
    }

    public RequestSpecification getRequestSpec() {
        return requestSpec;
    }

    public void setRequestSpec(RequestSpecification requestSpec) {
        this.requestSpec = requestSpec;
    }

    public Response getLastResponse() {
        return lastResponse;
    }

    public void setLastResponse(Response lastResponse) {
        this.lastResponse = lastResponse;
    }

    public String getGeneratedToken() {
        return generatedToken;
    }

    public void setGeneratedToken(String generatedToken) {
        this.generatedToken = generatedToken;
    }

    public Map<String, Object> getPendingJwtClaims() {
        return pendingJwtClaims;
    }

    public String getCurrentClientUuid() {
        return currentClientUuid;
    }

    public void setCurrentClientUuid(String currentClientUuid) {
        this.currentClientUuid = currentClientUuid;
    }

    public String getCurrentTherapistUuid() {
        return currentTherapistUuid;
    }

    public Optional<String> findCurrentTherapistUuid() {
        return Optional.ofNullable(currentTherapistUuid);
    }

    public void setCurrentTherapistUuid(String currentTherapistUuid) {
        this.currentTherapistUuid = currentTherapistUuid;
    }

    public String getLastQueriedRangeFrom() {
        return lastQueriedRangeFrom;
    }

    public void setLastQueriedRangeFrom(String lastQueriedRangeFrom) {
        this.lastQueriedRangeFrom = lastQueriedRangeFrom;
    }

    public String getLastQueriedRangeTo() {
        return lastQueriedRangeTo;
    }

    public void setLastQueriedRangeTo(String lastQueriedRangeTo) {
        this.lastQueriedRangeTo = lastQueriedRangeTo;
    }

    public String getLastCancelledAppointmentId() {
        return lastCancelledAppointmentId;
    }

    public void setLastCancelledAppointmentId(String lastCancelledAppointmentId) {
        this.lastCancelledAppointmentId = lastCancelledAppointmentId;
    }

    public Response getClientBundleResponse() {
        return clientBundleResponse;
    }

    public void setClientBundleResponse(Response clientBundleResponse) {
        this.clientBundleResponse = clientBundleResponse;
    }

    public Response getClientHistoryResponse() {
        return clientHistoryResponse;
    }

    public void setClientHistoryResponse(Response clientHistoryResponse) {
        this.clientHistoryResponse = clientHistoryResponse;
    }
}
