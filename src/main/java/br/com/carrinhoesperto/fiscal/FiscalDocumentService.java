package br.com.carrinhoesperto.fiscal;

import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentRequest;
import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentResponse;
import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentSummaryResponse;
import br.com.carrinhoesperto.fiscal.dto.FiscalIssuerRequest;
import br.com.carrinhoesperto.fiscal.dto.FiscalItemRequest;
import br.com.carrinhoesperto.fiscal.dto.FiscalPaymentRequest;
import br.com.carrinhoesperto.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates the {@link FiscalDocument} aggregate (M002, docs/fiscal-core.md):
 * the document, its {@link FiscalItem} lines and its {@link FiscalPayment}
 * lines. A create runs in one transaction that either fully succeeds or fully
 * rolls back.
 *
 * <p><strong>Fiscal truth is preserved.</strong> {@code rawDescription} and the
 * fiscal identifiers are stored exactly as received; nothing here canonicalizes
 * a product, infers a brand, or calls an external source. That is the future
 * Resolution/Catalog work and is out of scope for M002 (docs/architecture.md
 * §4.1).
 *
 * <p>Ownership is always the authenticated caller, resolved through
 * {@link UserService}; no identifier is ever trusted from the request.
 * Re-submitting a document with an {@code accessKey} the caller already imported
 * is idempotent — the stored document is returned, not a duplicate.
 */
@Service
public class FiscalDocumentService {

    private static final Logger log = LoggerFactory.getLogger(FiscalDocumentService.class);

    private static final int ACCESS_KEY_LENGTH = 44;
    private static final int CNPJ_LENGTH = 14;
    private static final int MAX_NAME_LENGTH = 300;
    private static final int MAX_ADDRESS_LENGTH = 400;
    private static final int MAX_DESCRIPTION_LENGTH = 300;
    private static final int MAX_CODE_LENGTH = 60;
    private static final int MAX_GTIN_LENGTH = 20;
    private static final int MAX_UNIT_LENGTH = 20;
    private static final int MAX_CLASSIFICATION_LENGTH = 20;
    private static final int MAX_PAYMENT_METHOD_LENGTH = 40;
    private static final int MAX_PAYMENT_METHOD_DESCRIPTION_LENGTH = 120;

    private final FiscalDocumentRepository fiscalDocumentRepository;
    private final FiscalItemRepository fiscalItemRepository;
    private final FiscalPaymentRepository fiscalPaymentRepository;
    private final UserService userService;

    FiscalDocumentService(FiscalDocumentRepository fiscalDocumentRepository,
                          FiscalItemRepository fiscalItemRepository,
                          FiscalPaymentRepository fiscalPaymentRepository,
                          UserService userService) {
        this.fiscalDocumentRepository = fiscalDocumentRepository;
        this.fiscalItemRepository = fiscalItemRepository;
        this.fiscalPaymentRepository = fiscalPaymentRepository;
        this.userService = userService;
    }

    /**
     * Persists a fiscal-document payload as fiscal truth, or returns the
     * already-stored document when this caller has imported the same access key
     * before.
     *
     * @return the stored document and whether this call created it
     */
    @Transactional
    public CreateResult create(FiscalDocumentRequest request) {
        if (request == null) {
            throw new FiscalValidationException("A fiscal document payload is required");
        }
        UUID ownerId = userService.currentUserId();

        String accessKey = normalizeAccessKey(request.accessKey());

        var existing = fiscalDocumentRepository.findByAccessKeyAndOwner(accessKey, ownerId);
        if (existing.isPresent()) {
            return new CreateResult(loadDetail(existing.get().id(), ownerId), false);
        }

        Instant issuedAt = parseIssuedAt(request.issuedAt());
        FiscalIssuer issuer = validateIssuer(request.issuer());
        BigDecimal totalAmount = requireAmount(request.totalAmount(), "totalAmount");
        BigDecimal totalItemsAmount = optionalAmount(request.totalItemsAmount(), "totalItemsAmount");
        BigDecimal totalDiscount = optionalAmount(request.totalDiscount(), "totalDiscount");

        List<FiscalItemRequest> itemRequests = request.items();
        if (itemRequests == null || itemRequests.isEmpty()) {
            throw new FiscalValidationException("A fiscal document must have at least one item");
        }
        List<FiscalPaymentRequest> paymentRequests =
            request.payments() == null ? List.of() : request.payments();

        UUID documentId = UUID.randomUUID();
        FiscalDocument document = new FiscalDocument(
            documentId, ownerId, accessKey, request.documentNumber(), request.series(),
            issuedAt, issuer, totalAmount, totalItemsAmount, totalDiscount, null);

        List<FiscalItem> items = buildItems(documentId, itemRequests);
        List<FiscalPayment> payments = buildPayments(documentId, paymentRequests);

        if (!fiscalDocumentRepository.insert(document)) {
            // A concurrent import of the same (owner, accessKey) committed first.
            FiscalDocument stored = fiscalDocumentRepository.findByAccessKeyAndOwner(accessKey, ownerId)
                .orElseThrow(() -> new IllegalStateException(
                    "fiscal_document row missing immediately after ON CONFLICT DO NOTHING"));
            return new CreateResult(loadDetail(stored.id(), ownerId), false);
        }
        items.forEach(fiscalItemRepository::insert);
        payments.forEach(fiscalPaymentRepository::insert);

        log.info("fiscal document imported id={} ownerId={} accessKey={} items={} payments={}",
            documentId, ownerId, accessKey, items.size(), payments.size());
        return new CreateResult(loadDetail(documentId, ownerId), true);
    }

    @Transactional(readOnly = true)
    public List<FiscalDocumentSummaryResponse> listOwned() {
        UUID ownerId = userService.currentUserId();
        return fiscalDocumentRepository.findAllByOwner(ownerId).stream()
            .map(FiscalDocumentSummaryResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public FiscalDocumentResponse getOwnedDetail(UUID documentId) {
        UUID ownerId = userService.currentUserId();
        return loadDetail(documentId, ownerId);
    }

    /**
     * The caller's fiscal document for a given (already-normalized, 44-digit)
     * access key, if it exists. Lets an alternative ingestion path (e.g. NFC-e
     * QR import) reuse the M002 {@code (owner_user_id, access_key)} identity
     * without re-implementing dedupe.
     */
    @Transactional(readOnly = true)
    public Optional<FiscalDocumentResponse> findOwnedByAccessKey(String accessKey) {
        UUID ownerId = userService.currentUserId();
        return fiscalDocumentRepository.findByAccessKeyAndOwner(accessKey, ownerId)
            .map(document -> loadDetail(document.id(), ownerId));
    }

    private FiscalDocumentResponse loadDetail(UUID documentId, UUID ownerId) {
        FiscalDocument document = fiscalDocumentRepository.findByIdAndOwner(documentId, ownerId)
            .orElseThrow(() -> new FiscalDocumentNotFoundException(documentId));
        List<FiscalItem> items = fiscalItemRepository.findAllByDocument(documentId);
        List<FiscalPayment> payments = fiscalPaymentRepository.findAllByDocument(documentId);
        return FiscalDocumentResponse.from(document, items, payments);
    }

    // ── Items / payments ────────────────────────────────────────────────────

    private List<FiscalItem> buildItems(UUID documentId, List<FiscalItemRequest> requests) {
        boolean anyLineNumber = requests.stream().anyMatch(r -> r != null && r.lineNumber() != null);
        boolean allLineNumbers = requests.stream().allMatch(r -> r != null && r.lineNumber() != null);
        if (anyLineNumber && !allLineNumbers) {
            throw new FiscalValidationException(
                "lineNumber must be provided for every item or for none");
        }

        Set<Integer> seenLines = new HashSet<>();
        List<FiscalItem> items = new ArrayList<>(requests.size());
        for (int i = 0; i < requests.size(); i++) {
            FiscalItemRequest r = requests.get(i);
            if (r == null) {
                throw new FiscalValidationException("Item " + (i + 1) + " is missing");
            }
            int lineNumber = allLineNumbers ? r.lineNumber() : i + 1;
            if (lineNumber < 1) {
                throw new FiscalValidationException("Item lineNumber must be >= 1");
            }
            if (!seenLines.add(lineNumber)) {
                throw new FiscalValidationException("Duplicate item lineNumber: " + lineNumber);
            }

            // rawDescription is fiscal truth: validated for presence, stored verbatim.
            String rawDescription = r.rawDescription();
            if (rawDescription == null || rawDescription.strip().isEmpty()) {
                throw new FiscalValidationException(
                    "Item " + lineNumber + ": rawDescription must not be blank");
            }
            if (rawDescription.length() > MAX_DESCRIPTION_LENGTH) {
                throw new FiscalValidationException(
                    "Item " + lineNumber + ": rawDescription must be at most "
                        + MAX_DESCRIPTION_LENGTH + " characters");
            }

            items.add(new FiscalItem(
                UUID.randomUUID(),
                documentId,
                lineNumber,
                rawDescription,
                preserveGtin(r.gtin(), lineNumber, "gtin"),
                preserveGtin(r.gtinTaxable(), lineNumber, "gtinTaxable"),
                optionalText(r.supplierItemCode(), "supplierItemCode", MAX_CODE_LENGTH),
                requireNonNegative(r.quantity(), "Item " + lineNumber + ": quantity"),
                requireText(r.unit(), "Item " + lineNumber + ": unit", MAX_UNIT_LENGTH),
                requireNonNegative(r.unitPrice(), "Item " + lineNumber + ": unitPrice"),
                requireNonNegative(r.totalPrice(), "Item " + lineNumber + ": totalPrice"),
                optionalNonNegative(r.discount(), "Item " + lineNumber + ": discount"),
                optionalText(r.ncm(), "ncm", MAX_CLASSIFICATION_LENGTH),
                optionalText(r.cest(), "cest", MAX_CLASSIFICATION_LENGTH)));
        }
        return items;
    }

    private List<FiscalPayment> buildPayments(UUID documentId, List<FiscalPaymentRequest> requests) {
        List<FiscalPayment> payments = new ArrayList<>(requests.size());
        for (int i = 0; i < requests.size(); i++) {
            FiscalPaymentRequest r = requests.get(i);
            if (r == null) {
                throw new FiscalValidationException("Payment " + (i + 1) + " is missing");
            }
            payments.add(new FiscalPayment(
                UUID.randomUUID(),
                documentId,
                i + 1,
                requireText(r.method(), "Payment " + (i + 1) + ": method", MAX_PAYMENT_METHOD_LENGTH),
                optionalText(r.methodDescription(), "Payment " + (i + 1) + ": methodDescription",
                    MAX_PAYMENT_METHOD_DESCRIPTION_LENGTH),
                requireNonNegative(r.amount(), "Payment " + (i + 1) + ": amount")));
        }
        return payments;
    }

    // ── Field validation ────────────────────────────────────────────────────

    private FiscalIssuer validateIssuer(FiscalIssuerRequest issuer) {
        if (issuer == null) {
            throw new FiscalValidationException("issuer is required");
        }
        String cnpj = digitsOnly(issuer.cnpj());
        if (cnpj == null) {
            throw new FiscalValidationException("issuer.cnpj is required");
        }
        if (cnpj.length() != CNPJ_LENGTH) {
            throw new FiscalValidationException(
                "issuer.cnpj must have " + CNPJ_LENGTH + " digits");
        }
        return new FiscalIssuer(
            cnpj,
            optionalText(issuer.legalName(), "issuer.legalName", MAX_NAME_LENGTH),
            optionalText(issuer.tradeName(), "issuer.tradeName", MAX_NAME_LENGTH),
            optionalText(issuer.address(), "issuer.address", MAX_ADDRESS_LENGTH));
    }

    private String normalizeAccessKey(String raw) {
        String digits = digitsOnly(raw);
        if (digits == null) {
            throw new FiscalValidationException("accessKey is required");
        }
        if (digits.length() != ACCESS_KEY_LENGTH) {
            throw new FiscalValidationException(
                "accessKey must have " + ACCESS_KEY_LENGTH + " digits");
        }
        return digits;
    }

    private Instant parseIssuedAt(String raw) {
        if (raw == null || raw.strip().isEmpty()) {
            throw new FiscalValidationException("issuedAt is required");
        }
        String value = raw.strip();
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException withoutOffset) {
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException notInstant) {
                throw new FiscalValidationException(
                    "issuedAt must be an ISO-8601 date-time with an offset (e.g. 2024-05-01T18:30:00-03:00)");
            }
        }
    }

    private static String digitsOnly(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    /**
     * Keeps a GTIN value as fiscal truth: a real GTIN and the literal
     * {@code "SEM GTIN"} are both preserved as-is; only an entirely absent value
     * becomes {@code null}.
     */
    private static String preserveGtin(String raw, int lineNumber, String field) {
        if (raw == null) {
            return null;
        }
        String value = raw.strip();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > MAX_GTIN_LENGTH) {
            throw new FiscalValidationException(
                "Item " + lineNumber + ": " + field + " must be at most " + MAX_GTIN_LENGTH + " characters");
        }
        return value;
    }

    private static String requireText(String raw, String field, int maxLength) {
        String value = raw == null ? null : raw.strip();
        if (value == null || value.isEmpty()) {
            throw new FiscalValidationException(field + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new FiscalValidationException(field + " must be at most " + maxLength + " characters");
        }
        return value;
    }

    private static String optionalText(String raw, String field, int maxLength) {
        String value = raw == null ? null : raw.strip();
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (value.length() > maxLength) {
            throw new FiscalValidationException(field + " must be at most " + maxLength + " characters");
        }
        return value;
    }

    private static BigDecimal requireAmount(BigDecimal value, String field) {
        if (value == null) {
            throw new FiscalValidationException(field + " is required");
        }
        if (value.signum() < 0) {
            throw new FiscalValidationException(field + " must not be negative");
        }
        return value;
    }

    private static BigDecimal optionalAmount(BigDecimal value, String field) {
        if (value == null) {
            return null;
        }
        if (value.signum() < 0) {
            throw new FiscalValidationException(field + " must not be negative");
        }
        return value;
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String field) {
        return requireAmount(value, field);
    }

    private static BigDecimal optionalNonNegative(BigDecimal value, String field) {
        return optionalAmount(value, field);
    }

    /** Outcome of {@link #create}: the stored document and whether this call created it. */
    public record CreateResult(FiscalDocumentResponse document, boolean created) {
    }
}
