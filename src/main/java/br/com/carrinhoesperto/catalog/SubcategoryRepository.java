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
 * Persistence for {@link Subcategory}. Concrete class, no interface; SQL in
 * {@code sql/catalog_subcategory.sql} via ELSQL.
 */
@Repository
public class SubcategoryRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ElSqlBundle sql;

    SubcategoryRepository(NamedParameterJdbcTemplate jdbc, ElSqlConfig elSqlConfig) {
        this.jdbc = jdbc;
        this.sql = ElSqlBundle.parse(elSqlConfig, new ClassPathResource("sql/catalog_subcategory.sql"));
    }

    public void insert(UUID id, UUID categoryId, String name) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("categoryId", categoryId)
            .addValue("name", name);
        jdbc.update(sql.getSql("insert"), params);
    }

    public Optional<Subcategory> findById(UUID id) {
        return jdbc.query(sql.getSql("findById"),
                new MapSqlParameterSource("id", id), SubcategoryRepository::map)
            .stream().findFirst();
    }

    public boolean existsById(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql.getSql("existsById"),
            new MapSqlParameterSource("id", id), Boolean.class));
    }

    private static Subcategory map(ResultSet rs, int rowNum) throws SQLException {
        return new Subcategory(
            rs.getObject("id", UUID.class),
            rs.getObject("category_id", UUID.class),
            rs.getString("name"),
            instant(rs, "created_at"),
            instant(rs, "updated_at"));
    }
}
