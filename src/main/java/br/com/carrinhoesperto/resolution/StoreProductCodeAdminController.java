package br.com.carrinhoesperto.resolution;

import br.com.carrinhoesperto.resolution.dto.StoreProductCodeRequest;
import br.com.carrinhoesperto.resolution.dto.StoreProductCodeResponse;
import br.com.carrinhoesperto.resolution.dto.StoreProductCodeUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin management of the store vocabulary (M007 §10, §17). Mounted under
 * {@code /api/admin/**}, so {@code SecurityConfig} gates every verb to
 * {@code ROLE_ADMIN} — an ordinary consumer gets {@code 403}. The domain lives
 * in Resolution; only the HTTP surface sits on the admin path.
 */
@RestController
@RequestMapping("/api/admin/store-product-codes")
public class StoreProductCodeAdminController {

    private final StoreProductCodeService service;

    StoreProductCodeAdminController(StoreProductCodeService service) {
        this.service = service;
    }

    /** List mappings, optionally filtered by store CNPJ and/or a {@code cProd} prefix. */
    @GetMapping
    public List<StoreProductCodeResponse> list(
            @RequestParam(required = false) String storeId,
            @RequestParam(required = false) String code) {
        return service.list(storeId, code);
    }

    @PostMapping
    public ResponseEntity<StoreProductCodeResponse> create(
            @RequestBody(required = false) StoreProductCodeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public StoreProductCodeResponse update(
            @PathVariable UUID id, @RequestBody(required = false) StoreProductCodeUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
