package br.com.carrinhoesperto.repository;

import com.opengamma.elsql.ElSqlBundle;
import com.opengamma.elsql.ElSqlConfig;
import br.com.carrinhoesperto.model.User;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for {@link User}, keyed by the Cognito subject.
 *
 * <p>This class is the implementation — there is deliberately no interface for
 * an ordinary repository. SQL lives in {@code sql/user.sql} and is loaded
 * through ELSQL.
 */
@Repository
public class UserRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ElSqlBundle sql;

    UserRepository(NamedParameterJdbcTemplate jdbc, ElSqlConfig elSqlConfig) {
        this.jdbc = jdbc;
        this.sql = ElSqlBundle.parse(elSqlConfig, new ClassPathResource("sql/user.sql"));
    }

    /**
     * Inserts a row for {@code cognitoSubject} unless one already exists.
     *
     * <p>Relies on {@code INSERT ... ON CONFLICT DO NOTHING}, so concurrent callers
     * converge on a single row without raising a constraint violation.
     *
     * @return {@code true} when a new row was created, {@code false} when one was already present
     */
    public boolean insertIfAbsent(String cognitoSubject) {
        MapSqlParameterSource params = new MapSqlParameterSource("cognitoSubject", cognitoSubject);
        return jdbc.update(sql.getSql("insertUserIfAbsent"), params) == 1;
    }

    /**
     * Stores the caller's Cognito {@code username} claim as a display handle.
     * Idempotent; a {@code null} username is accepted (leaves/sets the column null).
     */
    public void updateUsername(String cognitoSubject, String username) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("cognitoSubject", cognitoSubject)
            .addValue("username", username);
        jdbc.update(sql.getSql("updateUsername"), params);
    }

    public Optional<User> findByCognitoSubject(String cognitoSubject) {
        MapSqlParameterSource params = new MapSqlParameterSource("cognitoSubject", cognitoSubject);
        return jdbc.query(sql.getSql("findByCognitoSubject"), params, UserRepository::mapUser)
            .stream()
            .findFirst();
    }

    private static User mapUser(ResultSet rs, int rowNum) throws SQLException {
        return new User(
            rs.getObject("id", UUID.class),
            rs.getString("cognito_subject"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant()
        );
    }
}
