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
 * Persistence for {@link FiscalPayment}. Concrete class, no interface; SQL in
 * {@code sql/fiscal_payment.sql} via ELSQL.
 *
 * <p>Always scoped through {@code fiscal_document_id}; the caller
 * ({@link FiscalDocumentService}) has already verified ownership of the parent
 * document.
 */
@Repository
public class FiscalPaymentRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ElSqlBundle sql;

    FiscalPaymentRepository(NamedParameterJdbcTemplate jdbc, ElSqlConfig elSqlConfig) {
        this.jdbc = jdbc;
        this.sql = ElSqlBundle.parse(elSqlConfig, new ClassPathResource("sql/fiscal_payment.sql"));
    }

    public void insert(FiscalPayment payment) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", payment.id())
            .addValue("fiscalDocumentId", payment.fiscalDocumentId())
            .addValue("lineNumber", payment.lineNumber())
            .addValue("method", payment.method())
            .addValue("methodDescription", payment.methodDescription())
            .addValue("amount", payment.amount());
        jdbc.update(sql.getSql("insert"), params);
    }

    public List<FiscalPayment> findAllByDocument(UUID fiscalDocumentId) {
        return jdbc.query(sql.getSql("findAllByDocument"),
            new MapSqlParameterSource("fiscalDocumentId", fiscalDocumentId), FiscalPaymentRepository::mapPayment);
    }

    private static FiscalPayment mapPayment(ResultSet rs, int rowNum) throws SQLException {
        return new FiscalPayment(
            rs.getObject("id", UUID.class),
            rs.getObject("fiscal_document_id", UUID.class),
            rs.getInt("line_number"),
            rs.getString("method"),
            rs.getString("method_description"),
            rs.getBigDecimal("amount"));
    }
}
