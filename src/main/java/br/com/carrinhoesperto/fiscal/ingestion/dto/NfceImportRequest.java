package br.com.carrinhoesperto.fiscal.ingestion.dto;

/**
 * Body of {@code POST /api/fiscal-documents/import} (M003).
 *
 * @param qrCodeUrl the full URL encoded in the NFC-e QR code, as scanned by the
 *                  user. Untrusted input — validated against the São Paulo SEFAZ
 *                  consultation allow-list before any outbound request.
 */
public record NfceImportRequest(String qrCodeUrl) {
}
