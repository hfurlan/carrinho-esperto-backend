package br.com.carrinhoesperto.session;

import com.opengamma.elsql.ElSqlBundle;
import com.opengamma.elsql.ElSqlConfig;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for anonymous trial sessions (M006.1, docs/anonymous-trial.md).
 *
 * <p>An anonymous session is an {@code app_user} row with {@code kind =
 * 'ANONYMOUS'} — reusing the identity table keeps every owner-scoped Fiscal /
 * Dashboard / Resolution / Insight query working with no change. Concrete class,
 * no interface; SQL in {@code sql/anonymous_session.sql} via ELSQL.
 */
@Repository
public class AnonymousSessionRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ElSqlBundle sql;

    AnonymousSessionRepository(NamedParameterJdbcTemplate jdbc, ElSqlConfig elSqlConfig) {
        this.jdbc = jdbc;
        this.sql = ElSqlBundle.parse(elSqlConfig, new ClassPathResource("sql/anonymous_session.sql"));
    }

    /** Creates the {@code app_user} row for a new anonymous session. */
    public void insert(UUID id, String tokenHash) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("tokenHash", tokenHash);
        jdbc.update(sql.getSql("insert"), params);
    }

    /**
     * The anonymous {@code app_user} id for a presented session token digest, if
     * one exists. Empty for an unknown or malformed token — the filter treats
     * that the same as no session at all.
     */
    public Optional<UUID> findIdByTokenHash(String tokenHash) {
        return jdbc.query(sql.getSql("findIdByTokenHash"),
                new MapSqlParameterSource("tokenHash", tokenHash),
                (rs, rowNum) -> rs.getObject("id", UUID.class))
            .stream()
            .findFirst();
    }

    /** Records activity on the session; the basis for a future retention sweep. */
    public void touchLastSeen(UUID id) {
        jdbc.update(sql.getSql("touchLastSeen"), new MapSqlParameterSource("id", id));
    }
}
