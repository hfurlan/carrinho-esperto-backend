package br.com.carrinhoesperto.admin;

import br.com.carrinhoesperto.admin.dto.AdminDashboardResponse;
import br.com.carrinhoesperto.admin.dto.AdminFiscalDocumentRow;
import br.com.carrinhoesperto.admin.dto.AdminFiscalItemRow;
import br.com.carrinhoesperto.admin.dto.AdminResolutionRow;
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
import java.util.List;
import java.util.UUID;

/**
 * Platform-wide inspection reads for the admin surface (M006 §10). Concrete
 * class, no interface; SQL in {@code sql/admin.sql} via ELSQL. Not owner-scoped —
 * only reached through the ROLE_ADMIN-gated {@code /api/admin/**} paths.
 */
@Repository
public class AdminRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ElSqlBundle sql;

    AdminRepository(NamedParameterJdbcTemplate jdbc, ElSqlConfig elSqlConfig) {
        this.jdbc = jdbc;
        this.sql = ElSqlBundle.parse(elSqlConfig, new ClassPathResource("sql/admin.sql"));
    }

    public AdminDashboardResponse counts() {
        return jdbc.queryForObject(sql.getSql("counts"), new MapSqlParameterSource(),
            (rs, rowNum) -> new AdminDashboardResponse(
                rs.getLong("fiscal_documents"),
                rs.getLong("fiscal_items"),
                rs.getLong("resolved_items"),
                rs.getLong("unresolved_items"),
                rs.getLong("catalog_products"),
                rs.getLong("users")));
    }

    public List<AdminFiscalDocumentRow> fiscalDocuments(int limit) {
        return jdbc.query(sql.getSql("fiscalDocuments"),
            new MapSqlParameterSource("limit", limit), AdminRepository::mapDocument);
    }

    public List<AdminFiscalItemRow> fiscalItems(int limit) {
        return jdbc.query(sql.getSql("fiscalItems"),
            new MapSqlParameterSource("limit", limit), AdminRepository::mapItem);
    }

    public List<AdminResolutionRow> resolutions(int limit) {
        return jdbc.query(sql.getSql("resolutions"),
            new MapSqlParameterSource("limit", limit), AdminRepository::mapResolution);
    }

    private static AdminFiscalDocumentRow mapDocument(ResultSet rs, int rowNum) throws SQLException {
        return new AdminFiscalDocumentRow(
            rs.getObject("id", UUID.class),
            rs.getObject("owner_user_id", UUID.class),
            rs.getString("access_key"),
            instant(rs, "issued_at"),
            rs.getString("issuer_cnpj"),
            rs.getString("issuer_name"),
            rs.getBigDecimal("total_amount"),
            rs.getLong("item_count"),
            instant(rs, "created_at"));
    }

    private static AdminFiscalItemRow mapItem(ResultSet rs, int rowNum) throws SQLException {
        return new AdminFiscalItemRow(
            rs.getObject("id", UUID.class),
            rs.getObject("fiscal_document_id", UUID.class),
            rs.getString("access_key"),
            rs.getString("raw_description"),
            rs.getString("supplier_item_code"),
            rs.getString("gtin"),
            rs.getString("gtin_taxable"),
            rs.getBigDecimal("quantity"),
            rs.getString("unit"),
            rs.getBigDecimal("unit_price"),
            rs.getBigDecimal("total_price"),
            rs.getString("resolution_status"),
            rs.getObject("canonical_product_id", UUID.class));
    }

    private static AdminResolutionRow mapResolution(ResultSet rs, int rowNum) throws SQLException {
        return new AdminResolutionRow(
            rs.getObject("id", UUID.class),
            rs.getObject("fiscal_item_id", UUID.class),
            rs.getString("fiscal_raw_description"),
            rs.getString("status"),
            rs.getString("method"),
            rs.getObject("canonical_product_id", UUID.class),
            rs.getString("canonical_product_name"),
            rs.getLong("evidence_count"),
            instant(rs, "created_at"),
            instant(rs, "updated_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value != null ? value.toInstant() : null;
    }
}
