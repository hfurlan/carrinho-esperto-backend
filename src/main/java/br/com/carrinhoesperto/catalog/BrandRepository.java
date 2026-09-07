package br.com.carrinhoesperto.catalog;

import com.opengamma.elsql.ElSqlBundle;
import com.opengamma.elsql.ElSqlConfig;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import static br.com.carrinhoesperto.catalog.ManufacturerRepository.instant;

/**
 * Persistence for {@link Brand}. Concrete class, no interface; SQL in
 * {@code sql/catalog_brand.sql} via ELSQL.
 */
@Repository
public class BrandRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ElSqlBundle sql;

    BrandRepository(NamedParameterJdbcTemplate jdbc, ElSqlConfig elSqlConfig) {
        this.jdbc = jdbc;
        this.sql = ElSqlBundle.parse(elSqlConfig, new ClassPathResource("sql/catalog_brand.sql"));
    }

    public void insert(UUID id, String name, UUID manufacturerId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("name", name)
            .addValue("manufacturerId", manufacturerId);
        jdbc.update(sql.getSql("insert"), params);
    }

    public Optional<Brand> findById(UUID id) {
        return jdbc.query(sql.getSql("findById"),
                new MapSqlParameterSource("id", id), BrandRepository::map)
            .stream().findFirst();
    }

    public boolean existsById(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql.getSql("existsById"),
            new MapSqlParameterSource("id", id), Boolean.class));
    }

    private static Brand map(ResultSet rs, int rowNum) throws SQLException {
        return new Brand(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            rs.getObject("manufacturer_id", UUID.class),
            instant(rs, "created_at"),
            instant(rs, "updated_at"));
    }
}
