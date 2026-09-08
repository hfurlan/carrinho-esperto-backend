package br.com.carrinhoesperto.resolution;

import com.opengamma.elsql.ElSqlBundle;
import com.opengamma.elsql.ElSqlConfig;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static br.com.carrinhoesperto.resolution.ProductResolutionRepository.instant;

/**
 * Persistence for {@link StoreProductCode} (M007). Concrete class, no interface;
 * SQL in {@code sql/store_product_code.sql} via ELSQL. The {@code (store_id,
 * code)} unique constraint is the collision guard — the service checks first and
 * returns a 409 rather than relying on the DB error.
 */
@Repository
public class StoreProductCodeRepository {

    private static final int MAX_LIST = 500;

    private final NamedParameterJdbcTemplate jdbc;
    private final ElSqlBundle sql;

    StoreProductCodeRepository(NamedParameterJdbcTemplate jdbc, ElSqlConfig elSqlConfig) {
        this.jdbc = jdbc;
        this.sql = ElSqlBundle.parse(elSqlConfig, new ClassPathResource("sql/store_product_code.sql"));
    }

    public void insert(UUID id, String storeId, String code, UUID canonicalProductId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("storeId", storeId)
            .addValue("code", code)
            .addValue("canonicalProductId", canonicalProductId);
        jdbc.update(sql.getSql("insert"), params);
    }

    public void updateProduct(UUID id, UUID canonicalProductId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("canonicalProductId", canonicalProductId);
        jdbc.update(sql.getSql("updateProduct"), params);
    }

    public int delete(UUID id) {
        return jdbc.update(sql.getSql("delete"), new MapSqlParameterSource("id", id));
    }

    public Optional<StoreProductCode> findById(UUID id) {
        return jdbc.query(sql.getSql("findById"), new MapSqlParameterSource("id", id),
            StoreProductCodeRepository::map).stream().findFirst();
    }

    public Optional<StoreProductCode> findByStoreAndCode(String storeId, String code) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("storeId", storeId)
            .addValue("code", code);
        return jdbc.query(sql.getSql("findByStoreAndCode"), params, StoreProductCodeRepository::map)
            .stream().findFirst();
    }

    /** Admin listing. {@code storeId} / {@code code} are optional filters; {@code code} matches as a prefix. */
    public List<StoreProductCode> findFiltered(String storeId, String code) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("storeId", (storeId == null || storeId.isBlank()) ? null : storeId)
            .addValue("codeLike", (code == null || code.isBlank()) ? null : code.strip() + "%")
            .addValue("limit", MAX_LIST);
        return jdbc.query(sql.getSql("findFiltered"), params, StoreProductCodeRepository::map);
    }

    private static StoreProductCode map(ResultSet rs, int rowNum) throws SQLException {
        return new StoreProductCode(
            rs.getObject("id", UUID.class),
            rs.getString("store_id"),
            rs.getString("code"),
            rs.getObject("canonical_product_id", UUID.class),
            instant(rs, "created_at"),
            instant(rs, "updated_at"));
    }
}
