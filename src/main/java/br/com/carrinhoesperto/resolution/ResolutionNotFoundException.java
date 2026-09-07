package br.com.carrinhoesperto.resolution;

import java.util.UUID;

/**
 * The caller's fiscal item exists but has not been resolved yet — used only by
 * the read endpoint ({@code GET .../resolution}). Triggering a resolution
 * ({@code POST}) never raises this. Maps to HTTP 404.
 *
 * <p>This is distinct from an {@code UNRESOLVED} <em>result</em>, which is a
 * successful {@code 200} outcome meaning "evaluated, no canonical product".
 */
public class ResolutionNotFoundException extends RuntimeException {

    public ResolutionNotFoundException(UUID fiscalItemId) {
        super("No resolution exists for fiscal item: " + fiscalItemId);
    }
}
