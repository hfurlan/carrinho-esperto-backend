package br.com.carrinhoesperto.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates and resolves anonymous trial sessions (M006.1, docs/anonymous-trial.md).
 *
 * <p>A session is identified by a 256-bit opaque token handed to the browser
 * once, at creation. Only its SHA-256 digest is persisted (as an {@code app_user}
 * row of {@code kind = 'ANONYMOUS'}); the raw token is a bearer credential for
 * that session's Fiscal data and is never stored or logged.
 *
 * <p>This is deliberately <em>not</em> a general identity/session platform: no
 * expiry, no rotation, no account conversion. A future milestone owns retention
 * and "create an account to keep your history".
 */
@Service
public class AnonymousSessionService {

    private static final Logger log = LoggerFactory.getLogger(AnonymousSessionService.class);

    private static final int TOKEN_BYTES = 32;

    private final AnonymousSessionRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    AnonymousSessionService(AnonymousSessionRepository repository) {
        this.repository = repository;
    }

    /**
     * Provisions a new anonymous session.
     *
     * @return the raw token (return it to the caller once; it cannot be recovered later)
     */
    @Transactional
    public String create() {
        byte[] raw = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        UUID userId = UUID.randomUUID();
        repository.insert(userId, sha256Hex(token));
        log.info("anonymous session created appUserId={}", userId);
        return token;
    }

    /**
     * Resolves a presented session token to its owning {@code app_user} id.
     * Read-only — safe to call from a request path running in a read-only
     * transaction. Empty for an absent, malformed or unknown token.
     */
    @Transactional(readOnly = true)
    public Optional<UUID> resolve(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return repository.findIdByTokenHash(sha256Hex(rawToken.strip()));
    }

    /** Records activity on the session (write-path only — e.g. after an import). */
    @Transactional
    public void touch(UUID appUserId) {
        repository.touchLastSeen(appUserId);
    }

    /**
     * Hands the receipts of the anonymous session identified by {@code rawToken}
     * to {@code targetUserId} (a real account, M006.2), then removes the
     * anonymous {@code app_user} row.
     *
     * <p>No-op when the token is absent/unknown or already resolves to
     * {@code targetUserId}. The caller can only ever affect the session whose
     * token it presents.
     *
     * @return the number of receipts moved
     */
    @Transactional
    public int claimInto(String rawToken, UUID targetUserId) {
        Optional<UUID> anon = resolve(rawToken);
        if (anon.isEmpty() || anon.get().equals(targetUserId)) {
            return 0;
        }
        UUID anonUserId = anon.get();
        int moved = repository.reassignFiscalDocuments(anonUserId, targetUserId);
        repository.deleteAnonymousUser(anonUserId);
        log.info("anonymous session claimed anonAppUserId={} targetAppUserId={} movedReceipts={}",
            anonUserId, targetUserId, moved);
        return moved;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
