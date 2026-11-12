package it.prova.account.enablement;

import it.prova.account.enablement.EnablementService.AccessResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/organizations/{organizationId}")
public class EnablementController {

    private final EnablementService service;

    public EnablementController(EnablementService service) {
        this.service = service;
    }

    /**
     * PUT perché la richiesta descrive lo stato finale: ripeterla dà sempre lo stesso risultato.
     */
    @PutMapping("/enablements/{product}")
    public EnablementResponse provision(@PathVariable String organizationId,
                                        @PathVariable Product product,
                                        @Valid @RequestBody EnablementRequest request) {
        Enablement enablement = service.provision(organizationId, product,
                request.status(), request.startDate(), request.endDate());
        return EnablementResponse.from(enablement);
    }

    @GetMapping("/enablements")
    public List<EnablementResponse> list(@PathVariable String organizationId) {
        return service.findByOrganization(organizationId).stream().map(EnablementResponse::from).toList();
    }

    @GetMapping("/access/{product}")
    public AccessResponse checkAccess(@PathVariable String organizationId, @PathVariable Product product) {
        return service.checkAccess(organizationId, product);
    }

    public record EnablementRequest(@NotNull EnablementStatus status,
                                    @NotNull LocalDate startDate,
                                    LocalDate endDate) {
    }

    public record EnablementResponse(String organizationId, Product product, EnablementStatus status,
                                     LocalDate startDate, LocalDate endDate, Instant updatedAt) {
        static EnablementResponse from(Enablement e) {
            return new EnablementResponse(e.getOrganizationId(), e.getProduct(), e.getStatus(),
                    e.getStartDate(), e.getEndDate(), e.getUpdatedAt());
        }
    }
}
