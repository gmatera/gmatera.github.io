package it.prova.receipts.receipt;

import it.prova.receipts.receipt.ReceiptService.IssueResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/receipts")
public class ReceiptController {

    private final ReceiptService service;

    public ReceiptController(ReceiptService service) {
        this.service = service;
    }

    /**
     * 201 se il documento è stato emesso ora, 200 se la chiave di idempotenza era già stata usata.
     */
    @PostMapping
    public ResponseEntity<ReceiptResponse> issue(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                 @Valid @RequestBody CreateReceiptRequest request) {
        IssueResult result = service.issue(idempotencyKey, request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ReceiptResponse.from(result.receipt()));
    }

    @PostMapping("/{id}/cancellation")
    @ResponseStatus(HttpStatus.CREATED)
    public ReceiptResponse cancel(@PathVariable Long id) {
        return ReceiptResponse.from(service.cancel(id));
    }

    @GetMapping("/{id}")
    public ReceiptResponse get(@PathVariable Long id) {
        return ReceiptResponse.from(service.get(id));
    }

    @GetMapping
    public List<ReceiptResponse> findByDay(@RequestParam String organizationId,
                                           @RequestParam String cashRegisterId,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.findByDay(organizationId, cashRegisterId, date).stream().map(ReceiptResponse::from).toList();
    }

    public record CreateReceiptRequest(
            @NotBlank String organizationId,
            @NotBlank @Size(max = 50) String cashRegisterId,
            @NotNull PaymentType paymentType,
            @Size(max = 50) String customerId,
            @NotEmpty List<@Valid LineRequest> lines) {
    }

    public record LineRequest(
            @NotBlank @Size(max = 200) String description,
            @Min(1) int quantity,
            @NotNull @DecimalMin("0.01") @Digits(integer = 9, fraction = 2) BigDecimal unitPrice,
            @NotNull Integer vatRate) {
    }

    public record ReceiptResponse(Long id, String organizationId, String cashRegisterId, int number,
                                  ReceiptType type, PaymentType paymentType, String customerId,
                                  BigDecimal totalAmount, BigDecimal vatAmount, Instant issuedAt,
                                  LocalDate businessDate, Long originalReceiptId, List<LineResponse> lines) {
        static ReceiptResponse from(Receipt r) {
            List<LineResponse> lines = r.getLines().stream()
                    .map(l -> new LineResponse(l.getDescription(), l.getQuantity(), l.getUnitPrice(), l.getVatRate(), l.getLineTotal()))
                    .toList();
            return new ReceiptResponse(r.getId(), r.getOrganizationId(), r.getCashRegisterId(), r.getNumber(),
                    r.getType(), r.getPaymentType(), r.getCustomerId(), r.getTotalAmount(), r.getVatAmount(),
                    r.getIssuedAt(), r.getBusinessDate(), r.getOriginalReceiptId(), lines);
        }
    }

    public record LineResponse(String description, int quantity, BigDecimal unitPrice, int vatRate, BigDecimal lineTotal) {
    }
}
