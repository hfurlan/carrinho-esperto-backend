package br.com.carrinhoesperto.resolution;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Deterministic check for whether a fiscal value is usable as a canonical GTIN
 * (M005 §19).
 *
 * <p>A value is usable iff, after trimming, it is 8–14 digits — matching the
 * {@code CHECK (gtin ~ '^[0-9]{8,14}$')} on {@code catalog.product.gtin}. This
 * rejects blank values, non-numeric text, wrong-length numbers, and the fiscal
 * {@code "SEM GTIN"} placeholder. No GS1 check-digit validation is performed
 * (kept deliberately minimal); the rule is only "do not treat arbitrary fiscal
 * text as a canonical GTIN".
 */
public final class Gtin {

    private static final Pattern CANONICAL = Pattern.compile("\\d{8,14}");

    private Gtin() {
    }

    /** The trimmed digit string if {@code raw} is a usable canonical GTIN, else empty. */
    public static Optional<String> canonical(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.strip();
        return CANONICAL.matcher(value).matches() ? Optional.of(value) : Optional.empty();
    }
}
