package br.com.carrinhoesperto.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Local mirror of an authenticated Cognito user.
 *
 * <p>{@code cognitoSubject} is the Cognito {@code sub} claim — the stable external
 * identity key. No profile attributes (email, name), roles, memberships or
 * organizations are modelled yet — add them deliberately when a real need shows up.
 */
public record User(UUID id, String cognitoSubject, Instant createdAt) {
}
