package br.com.carrinhoesperto.exception;

import br.com.carrinhoesperto.catalog.CatalogEntityNotFoundException;
import br.com.carrinhoesperto.catalog.CatalogValidationException;
import br.com.carrinhoesperto.catalog.GtinAlreadyAssignedException;
import br.com.carrinhoesperto.fiscal.FiscalDocumentNotFoundException;
import br.com.carrinhoesperto.fiscal.FiscalValidationException;
import br.com.carrinhoesperto.fiscal.ingestion.InvalidQrCodeUrlException;
import br.com.carrinhoesperto.fiscal.ingestion.InvalidQrPayloadException;
import br.com.carrinhoesperto.fiscal.ingestion.NfceParseException;
import br.com.carrinhoesperto.fiscal.ingestion.SefazUnavailableException;
import br.com.carrinhoesperto.fiscal.ingestion.SefazUnexpectedResponseException;
import br.com.carrinhoesperto.fiscal.ingestion.UnsupportedNfceProviderException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps application exceptions to RFC 9457 problem responses. One
 * {@code @ExceptionHandler} per domain exception; add more as the app grows.
 *
 * <p>For the NFC-e ingestion failures that involve an upstream response
 * (SEFAZ unavailable / unexpected / unparseable) the client detail is a fixed,
 * generic string — raw HTML, upstream status codes, stack traces and any
 * consumer data stay server-side.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail onValidation(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(FiscalValidationException.class)
    public ProblemDetail onFiscalValidation(FiscalValidationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(FiscalDocumentNotFoundException.class)
    public ProblemDetail onFiscalDocumentNotFound(FiscalDocumentNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    // ── NFC-e ingestion (M003) ─────────────────────────────────────────────

    @ExceptionHandler({InvalidQrCodeUrlException.class, InvalidQrPayloadException.class})
    public ProblemDetail onInvalidQrCode(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(UnsupportedNfceProviderException.class)
    public ProblemDetail onUnsupportedProvider(UnsupportedNfceProviderException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
    }

    @ExceptionHandler(SefazUnavailableException.class)
    public ProblemDetail onSefazUnavailable(SefazUnavailableException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
            "The São Paulo NFC-e consultation service is currently unavailable. Try again later.");
    }

    @ExceptionHandler(SefazUnexpectedResponseException.class)
    public ProblemDetail onSefazUnexpectedResponse(SefazUnexpectedResponseException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
            "The São Paulo NFC-e consultation returned an unexpected response.");
    }

    @ExceptionHandler(NfceParseException.class)
    public ProblemDetail onNfceParseFailure(NfceParseException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
            "The São Paulo NFC-e consultation page could not be read.");
    }

    // ── Catalog (M004) ─────────────────────────────────────────────────────

    @ExceptionHandler(CatalogValidationException.class)
    public ProblemDetail onCatalogValidation(CatalogValidationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(CatalogEntityNotFoundException.class)
    public ProblemDetail onCatalogEntityNotFound(CatalogEntityNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(GtinAlreadyAssignedException.class)
    public ProblemDetail onGtinAlreadyAssigned(GtinAlreadyAssignedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }
}
