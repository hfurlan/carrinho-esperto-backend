package br.com.carrinhoesperto.resolution;

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
 * Persistence for {@link ProductResolution}. Concrete class, no interface; SQL
 * in {@code sql/product_resolution.sql} via ELSQL.
 *
 * <p>References Fiscal ({@code fiscal_item_id}) and Catalog
 * ({@code canonical_product_id}) by id only. At most one row per fiscal item;
 * {@link ProductResolutionService} upserts.
 */
@Repository
public class ProductResolutionRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ElSqlBundle sql;

    ProductResolutionRepository(NamedParameterJdbcTemplate jdbc, ElSqlConfig elSqlConfig) {
        this.jdbc = jdbc;
        this.sql = ElSqlBundle.parse(elSqlConfig, new ClassPathResource("sql/product_resolution.sql"));
    }

    public void insert(UUID id, UUID fiscalItemId, UUID canonicalProductId,
                       ResolutionStatus status, ResolutionMethod method) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("fiscalItemId", fiscalItemId)
            .addValue("canonicalProductId", canonicalProductId)
            .addValue("status", status.name())
            .addValue("method", method == null ? null : method.name());
        jdbc.update(sql.getSql("insert"), params);
    }

    public void updateOutcome(UUID id, UUID canonicalProductId,
                              ResolutionStatus status, ResolutionMethod method) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("canonicalProductId", canonicalProductId)
            .addValue("status", status.name())
            .addValue("method", method == null ? null : method.name());
        jdbc.update(sql.getSql("updateOutcome"), params);
    }

    public Optional<ProductResolution> findByFiscalItemId(UUID fiscalItemId) {
        return jdbc.query(sql.getSql("findByFiscalItemId"),
                new MapSqlParameterSource("fiscalItemId", fiscalItemId), ProductResolutionRepository::map)
            .stream().findFirst();
    }

    public Optional<ProductResolution> findById(UUID id) {
        return jdbc.query(sql.getSql("findById"),
                new MapSqlParameterSource("id", id), ProductResolutionRepository::map)
            .stream().findFirst();
    }

    private static ProductResolution map(ResultSet rs, int rowNum) throws SQLException {
        String method = rs.getString("method");
        return new ProductResolution(
            rs.getObject("id", UUID.class),
            rs.getObject("fiscal_item_id", UUID.class),
            rs.getObject("canonical_product_id", UUID.class),
            ResolutionStatus.valueOf(rs.getString("status")),
            method == null ? null : ResolutionMethod.valueOf(method),
            instant(rs, "created_at"),
            instant(rs, "updated_at"));
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value != null ? value.toInstant() : null;
    }
}
