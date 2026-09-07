package br.com.carrinhoesperto.catalog;

import java.time.Instant;
import java.util.UUID;

/** Top-level product taxonomy node (e.g. "Laticínios"). Platform-owned. */
public record Category(UUID id, String name, Instant createdAt, Instant updatedAt) {
}
