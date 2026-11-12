package it.prova.receipts.closure;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/closures")
public class ClosureController {

    private final ClosureService service;

    public ClosureController(ClosureService service) {
        this.service = service;
    }

    /**
     * Crea (o restituisce se esiste già) la chiusura di un giorno passato.
     * Normalmente la crea il job notturno, questo endpoint serve per farla a mano.
     */
    @PostMapping
    public ClosureResponse create(@Valid @RequestBody CreateClosureRequest request) {
        return ClosureResponse.from(service.create(request.organizationId(), request.cashRegisterId(), request.businessDate()));
    }

    /** esito della trasmissione */
    @GetMapping("/{id}")
    public ClosureResponse get(@PathVariable Long id) {
        return ClosureResponse.from(service.get(id));
    }

    @GetMapping
    public List<ClosureResponse> findByOrganization(@RequestParam String organizationId) {
        return service.findByOrganization(organizationId).stream().map(ClosureResponse::from).toList();
    }

    public record CreateClosureRequest(@NotBlank String organizationId,
                                       @NotBlank String cashRegisterId,
                                       @NotNull LocalDate businessDate) {
    }

    public record ClosureResponse(Long id, String organizationId, String cashRegisterId, LocalDate businessDate,
                                  int receiptCount, BigDecimal totalAmount, ClosureStatus status, int attempts,
                                  Instant nextAttemptAt, String lastError, String protocolNumber, Instant updatedAt) {
        static ClosureResponse from(DailyClosure c) {
            return new ClosureResponse(c.getId(), c.getOrganizationId(), c.getCashRegisterId(), c.getBusinessDate(),
                    c.getReceiptCount(), c.getTotalAmount(), c.getStatus(), c.getAttempts(), c.getNextAttemptAt(),
                    c.getLastError(), c.getProtocolNumber(), c.getUpdatedAt());
        }
    }
}
