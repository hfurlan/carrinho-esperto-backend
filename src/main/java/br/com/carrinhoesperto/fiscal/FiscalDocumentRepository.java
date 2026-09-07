package br.com.carrinhoesperto.fiscal;

import com.opengamma.elsql.ElSqlBundle;
import com.opengamma.elsql.ElSqlConfig;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for {@link FiscalDocument}, the aggregate root of the Fiscal
 * context. Concrete class, no interface; SQL lives in {@code sql/fiscal_document.sql}
 * and is loaded through ELSQL.
 *
 * <p>Every owner-scoped query filters on {@code owner_user_id} — a fiscal
 * document is never returned by id alone (docs/fiscal-core.md §12).
 */
@Repository
public class FiscalDocumentRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ElSqlBundle sql;

    FiscalDocumentRepository(NamedParameterJdbcTemplate jdbc, ElSqlConfig elSqlConfig) {
        this.jdbc = jdbc;
        this.sql = ElSqlBundle.parse(elSqlConfig, new ClassPathResource("sql/fiscal_document.sql"));
    }

    /**
     * Inserts the document row. The caller assigns {@code document.id()};
     * {@code created_at} is filled by the database default. Uses
     * {@code INSERT ... ON CONFLICT DO NOTHING} on {@code (owner_user_id,
     * access_key)}.
     *
     * @return {@code true} when a new row was created, {@code false} when this
     *         owner already had the same access key (idempotent re-import)
     */
    public boolean insert(FiscalDocument document) {
        FiscalIssuer issuer = document.issuer();
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", document.id())
            .addValue("ownerUserId", document.ownerUserId())
            .addValue("accessKey", document.accessKey())
            .addValue("documentNumber", document.documentNumber())
            .addValue("series", document.series())
            .addValue("issuedAt", OffsetDateTime.ofInstant(document.issuedAt(), ZoneOffset.UTC))
            .addValue("issuerCnpj", issuer.cnpj())
            .addValue("issuerLegalName", issuer.legalName())
            .addValue("issuerTradeName", issuer.tradeName())
            .addValue("issuerAddress", issuer.address())
            .addValue("totalAmount", document.totalAmount())
            .addValue("totalItemsAmount", document.totalItemsAmount())
            .addValue("totalDiscount", document.totalDiscount());
        return jdbc.update(sql.getSql("insert"), params) == 1;
    }

    public Optional<FiscalDocument> findByIdAndOwner(UUID id, UUID ownerUserId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("ownerUserId", ownerUserId);
        return jdbc.query(sql.getSql("findByIdAndOwner"), params, FiscalDocumentRepository::mapDocument)
            .stream()
            .findFirst();
    }

    public Optional<FiscalDocument> findByAccessKeyAndOwner(String accessKey, UUID ownerUserId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("accessKey", accessKey)
            .addValue("ownerUserId", ownerUserId);
        return jdbc.query(sql.getSql("findByAccessKeyAndOwner"), params, FiscalDocumentRepository::mapDocument)
            .stream()
            .findFirst();
    }

    public List<Summary> findAllByOwner(UUID ownerUserId) {
        return jdbc.query(sql.getSql("findAllByOwner"),
            new MapSqlParameterSource("ownerUserId", ownerUserId), FiscalDocumentRepository::mapSummary);
    }

    private static FiscalDocument mapDocument(ResultSet rs, int rowNum) throws SQLException {
        return new FiscalDocument(
            rs.getObject("id", UUID.class),
            rs.getObject("owner_user_id", UUID.class),
            rs.getString("access_key"),
            (Long) rs.getObject("document_number"),
            (Integer) rs.getObject("series"),
            instant(rs, "issued_at"),
            new FiscalIssuer(
                rs.getString("issuer_cnpj"),
                rs.getString("issuer_legal_name"),
                rs.getString("issuer_trade_name"),
                rs.getString("issuer_address")),
            rs.getBigDecimal("total_amount"),
            rs.getBigDecimal("total_items_amount"),
            rs.getBigDecimal("total_discount"),
            instant(rs, "created_at"));
    }

    private static Summary mapSummary(ResultSet rs, int rowNum) throws SQLException {
        return new Summary(mapDocument(rs, rowNum), rs.getLong("item_count"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value != null ? value.toInstant() : null;
    }

    /** A fiscal document plus its line count, for the owner listing. */
    public record Summary(FiscalDocument document, long itemCount) {
    }
}
