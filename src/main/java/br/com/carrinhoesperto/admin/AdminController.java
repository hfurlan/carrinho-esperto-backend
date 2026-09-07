package br.com.carrinhoesperto.admin;

import br.com.carrinhoesperto.admin.dto.AdminDashboardResponse;
import br.com.carrinhoesperto.admin.dto.AdminFiscalDocumentRow;
import br.com.carrinhoesperto.admin.dto.AdminFiscalItemRow;
import br.com.carrinhoesperto.admin.dto.AdminResolutionRow;
import br.com.carrinhoesperto.catalog.dto.ProductResponse;
import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Administrative inspection surface (M006 §10). Every route here is gated to
 * {@code ROLE_ADMIN} by {@code SecurityConfig} ({@code /api/admin/**} →
 * {@code hasRole("ADMIN")}); a non-admin caller gets 403. Reads are
 * platform-wide by design (this is for debugging and product validation), and
 * expose no consumer identity data (none is ever persisted).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    public AdminDashboardResponse dashboard() {
        return adminService.dashboard();
    }

    @GetMapping("/fiscal-documents")
    public List<AdminFiscalDocumentRow> fiscalDocuments() {
        return adminService.fiscalDocuments();
    }

    @GetMapping("/fiscal-documents/{id}")
    public FiscalDocumentResponse fiscalDocument(@PathVariable UUID id) {
        return adminService.fiscalDocument(id);
    }

    @GetMapping("/fiscal-items")
    public List<AdminFiscalItemRow> fiscalItems() {
        return adminService.fiscalItems();
    }

    @GetMapping("/resolutions")
    public List<AdminResolutionRow> resolutions() {
        return adminService.resolutions();
    }

    @GetMapping("/catalog/products")
    public List<ProductResponse> catalogProducts(@RequestParam(required = false) String name) {
        return adminService.catalogProducts(name);
    }
}
