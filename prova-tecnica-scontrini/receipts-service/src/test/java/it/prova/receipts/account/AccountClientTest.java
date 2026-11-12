package it.prova.receipts.account;

import it.prova.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AccountClientTest {

    private static final String URL = "http://account/organizations/org-1/access/SCONTRINA";

    private MockRestServiceServer server;
    private AccountClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://account");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AccountClient(builder.build());
    }

    @Test
    void allowedOrganizationCanIssue() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"organizationId\":\"org-1\",\"product\":\"SCONTRINA\",\"allowed\":true}", MediaType.APPLICATION_JSON));

        assertThatCode(() -> client.checkCanIssue("org-1")).doesNotThrowAnyException();
    }

    @Test
    void notAllowedOrganizationGetsForbiddenWithReason() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"organizationId\":\"org-1\",\"product\":\"SCONTRINA\",\"allowed\":false,\"reason\":\"ORGANIZATION_SUSPENDED\"}",
                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.checkCanIssue("org-1"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN)
                .hasFieldOrPropertyWithValue("code", "ORGANIZATION_SUSPENDED");
    }

    @Test
    void unknownOrganizationGetsNotFound() {
        server.expect(requestTo(URL)).andRespond(withResourceNotFound());

        assertThatThrownBy(() -> client.checkCanIssue("org-1"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "ORGANIZATION_NOT_FOUND");
    }

    @Test
    void accountDownMeansServiceUnavailable() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.checkCanIssue("org-1"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.SERVICE_UNAVAILABLE)
                .hasFieldOrPropertyWithValue("code", "ACCOUNT_UNAVAILABLE");
    }
}
