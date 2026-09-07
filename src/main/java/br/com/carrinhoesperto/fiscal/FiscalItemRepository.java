package br.com.carrinhoesperto.fiscal;

import com.opengamma.elsql.ElSqlBundle;
import com.opengamma.elsql.ElSqlConfig;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Persistence for {@link FiscalItem}. Concrete class, no interface; SQL in
 * {@code sql/fiscal_item.sql} via ELSQL.
 *
 * <p>Always scoped through {@code fiscal_document_id}; the caller
 * ({@link FiscalDocumentService}) has already verified ownership of the parent
 * document. Values are written and read verbatim — the Fiscal context never
 * canonicalizes a description or a fiscal identifier.
 */
@Repository
public class FiscalItemRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ElSqlBundle sql;

    FiscalItemRepository(NamedParameterJdbcTemplate jdbc, ElSqlConfig elSqlConfig) {
        this.jdbc = jdbc;
        this.sql = ElSqlBundle.parse(elSqlConfig, new ClassPathResource("sql/fiscal_item.sql"));
    }

    public void insert(FiscalItem item) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", item.id())
            .addValue("fiscalDocumentId", item.fiscalDocumentId())
            .addValue("lineNumber", item.lineNumber())
            .addValue("rawDescription", item.rawDescription())
            .addValue("gtin", item.gtin())
            .addValue("gtinTaxable", item.gtinTaxable())
            .addValue("supplierItemCode", item.supplierItemCode())
            .addValue("quantity", item.quantity())
            .addValue("unit", item.unit())
            .addValue("unitPrice", item.unitPrice())
            .addValue("totalPrice", item.totalPrice())
            .addValue("discount", item.discount())
            .addValue("ncm", item.ncm())
            .addValue("cest", item.cest());
        jdbc.update(sql.getSql("insert"), params);
    }

    public List<FiscalItem> findAllByDocument(UUID fiscalDocumentId) {
        return jdbc.query(sql.getSql("findAllByDocument"),
            new MapSqlParameterSource("fiscalDocumentId", fiscalDocumentId), FiscalItemRepository::mapItem);
    }

    private static FiscalItem mapItem(ResultSet rs, int rowNum) throws SQLException {
        return new FiscalItem(
            rs.getObject("id", UUID.class),
            rs.getObject("fiscal_document_id", UUID.class),
            rs.getInt("line_number"),
            rs.getString("raw_description"),
            rs.getString("gtin"),
            rs.getString("gtin_taxable"),
            rs.getString("supplier_item_code"),
            rs.getBigDecimal("quantity"),
            rs.getString("unit"),
            rs.getBigDecimal("unit_price"),
            rs.getBigDecimal("total_price"),
            rs.getBigDecimal("discount"),
            rs.getString("ncm"),
            rs.getString("cest"));
    }
}
