package br.com.carrinhoesperto.admin;

import br.com.carrinhoesperto.admin.dto.AdminDashboardResponse;
import br.com.carrinhoesperto.admin.dto.AdminFiscalDocumentRow;
import br.com.carrinhoesperto.admin.dto.AdminFiscalItemRow;
import br.com.carrinhoesperto.admin.dto.AdminResolutionRow;
import br.com.carrinhoesperto.catalog.CatalogService;
import br.com.carrinhoesperto.catalog.dto.ProductResponse;
import br.com.carrinhoesperto.fiscal.FiscalDocumentService;
import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Read-only inspection layer for the admin surface (M006 §10). This is not a new
 * bounded context — it reuses the Fiscal / Resolution / Catalog data through
 * {@link AdminRepository} (capped list reads) and the existing application
 * services. Every entry point is gated to {@code ROLE_ADMIN} in
 * {@code SecurityConfig}; this service performs no per-user scoping.
 */
@Service
public class AdminService {

    /** Row cap for the admin list endpoints (no cursor pagination in M006). */
    static final int LIST_LIMIT = 200;

    private final AdminRepository adminRepository;
    private final FiscalDocumentService fiscalDocumentService;
    private final CatalogService catalogService;

    AdminService(AdminRepository adminRepository,
                 FiscalDocumentService fiscalDocumentService,
                 CatalogService catalogService) {
        this.adminRepository = adminRepository;
        this.fiscalDocumentService = fiscalDocumentService;
        this.catalogService = catalogService;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse dashboard() {
        return adminRepository.counts();
    }

    @Transactional(readOnly = true)
    public List<AdminFiscalDocumentRow> fiscalDocuments() {
        return adminRepository.fiscalDocuments(LIST_LIMIT);
    }

    @Transactional(readOnly = true)
    public FiscalDocumentResponse fiscalDocument(UUID id) {
        return fiscalDocumentService.findByIdForAdmin(id);
    }

    @Transactional(readOnly = true)
    public List<AdminFiscalItemRow> fiscalItems() {
        return adminRepository.fiscalItems(LIST_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<AdminResolutionRow> resolutions() {
        return adminRepository.resolutions(LIST_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> catalogProducts(String nameQuery) {
        if (nameQuery != null && !nameQuery.isBlank()) {
            return catalogService.searchProducts(nameQuery);
        }
        return catalogService.listRecentProducts(LIST_LIMIT);
    }
}
