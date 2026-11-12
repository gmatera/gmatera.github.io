package it.prova.account.organization;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/organizations")
public class OrganizationController {

    private final OrganizationService service;

    public OrganizationController(OrganizationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationResponse create(@Valid @RequestBody CreateOrganizationRequest request) {
        return OrganizationResponse.from(service.create(request.name()));
    }

    @GetMapping("/{id}")
    public OrganizationResponse get(@PathVariable String id) {
        return OrganizationResponse.from(service.get(id));
    }

    @GetMapping
    public List<OrganizationResponse> list() {
        return service.findAll().stream().map(OrganizationResponse::from).toList();
    }

    @PutMapping("/{id}/status")
    public OrganizationResponse changeStatus(@PathVariable String id, @Valid @RequestBody ChangeStatusRequest request) {
        return OrganizationResponse.from(service.changeStatus(id, request.status()));
    }

    public record CreateOrganizationRequest(@NotBlank @Size(max = 200) String name) {
    }

    public record ChangeStatusRequest(@NotNull OrganizationStatus status) {
    }

    public record OrganizationResponse(String id, String name, OrganizationStatus status, Instant createdAt) {
        static OrganizationResponse from(Organization o) {
            return new OrganizationResponse(o.getId(), o.getName(), o.getStatus(), o.getCreatedAt());
        }
    }
}
