package br.com.carrinhoesperto.resolution.dto;

import java.util.UUID;

/** Admin request to re-point an existing store product code at another product (M007). */
public record StoreProductCodeUpdateRequest(UUID canonicalProductId) {
}
