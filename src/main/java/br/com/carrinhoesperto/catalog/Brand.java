package br.com.carrinhoesperto.catalog;

import java.time.Instant;
import java.util.UUID;

/**
 * The consumer-facing brand. Platform-owned canonical data.
 *
 * @param manufacturerId owning manufacturer, or {@code null} when unknown /
 *                       not applicable (M004 §3.2) — never inferred from the name
 */
public record Brand(UUID id, String name, UUID manufacturerId, Instant createdAt, Instant updatedAt) {
}
