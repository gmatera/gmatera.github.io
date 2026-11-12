package it.prova.account;

import it.prova.account.organization.OrganizationController;
import it.prova.account.organization.OrganizationService;
import it.prova.common.ApiException;
import it.prova.common.CorrelationId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica il formato di errore uniforme e la gestione del correlation id.
 */
@WebMvcTest(OrganizationController.class)
class OrganizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrganizationService organizationService;

    @Test
    void notFoundUsesStandardErrorFormatAndEchoesCorrelationId() throws Exception {
        when(organizationService.get("xyz"))
                .thenThrow(ApiException.notFound("ORGANIZATION_NOT_FOUND", "Organizzazione xyz non trovata"));

        mockMvc.perform(get("/organizations/xyz").header(CorrelationId.HEADER, "abc-123"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(CorrelationId.HEADER, "abc-123"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/organizations/xyz"))
                .andExpect(jsonPath("$.correlationId").value("abc-123"));
    }

    @Test
    void invalidBodyReturnsValidationErrorWithDetails() throws Exception {
        mockMvc.perform(post("/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0]").value(startsWith("name:")))
                // se il client non manda il correlation id lo genera il servizio
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }
}
