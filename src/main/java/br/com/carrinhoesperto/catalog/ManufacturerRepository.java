package br.com.carrinhoesperto.catalog;

import com.opengamma.elsql.ElSqlBundle;
import com.opengamma.elsql.ElSqlConfig;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for {@link Manufacturer}. Concrete class, no interface; SQL in
 * {@code sql/catalog_manufacturer.sql} via ELSQL. Platform-owned data — no
 * owner scoping.
 */
@Repository
public class ManufacturerRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ElSqlBundle sql;

    ManufacturerRepository(NamedParameterJdbcTemplate jdbc, ElSqlConfig elSqlConfig) {
        this.jdbc = jdbc;
        this.sql = ElSqlBundle.parse(elSqlConfig, new ClassPathResource("sql/catalog_manufacturer.sql"));
    }

    public void insert(UUID id, String name) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("name", name);
        jdbc.update(sql.getSql("insert"), params);
    }

    public Optional<Manufacturer> findById(UUID id) {
        return jdbc.query(sql.getSql("findById"),
                new MapSqlParameterSource("id", id), ManufacturerRepository::map)
            .stream().findFirst();
    }

    public boolean existsById(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql.getSql("existsById"),
            new MapSqlParameterSource("id", id), Boolean.class));
    }

    private static Manufacturer map(ResultSet rs, int rowNum) throws SQLException {
        return new Manufacturer(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            instant(rs, "created_at"),
            instant(rs, "updated_at"));
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value != null ? value.toInstant() : null;
    }
}
