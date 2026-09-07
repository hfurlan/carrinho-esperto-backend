package br.com.carrinhoesperto.dashboard;

import br.com.carrinhoesperto.dashboard.dto.DashboardSummaryResponse;
import br.com.carrinhoesperto.dashboard.dto.IssuerSpendingResponse;
import br.com.carrinhoesperto.dashboard.dto.MonthlySpendingResponse;
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
 * Owner-scoped read aggregation for the consumer dashboard (M006). Concrete
 * class, no interface; SQL in {@code sql/dashboard.sql} via ELSQL.
 */
@Repository
public class DashboardRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ElSqlBundle sql;

    DashboardRepository(NamedParameterJdbcTemplate jdbc, ElSqlConfig elSqlConfig) {
        this.jdbc = jdbc;
        this.sql = ElSqlBundle.parse(elSqlConfig, new ClassPathResource("sql/dashboard.sql"));
    }

    public DashboardSummaryResponse summary(UUID ownerUserId) {
        return jdbc.queryForObject(sql.getSql("summary"),
            new MapSqlParameterSource("ownerUserId", ownerUserId),
            (rs, rowNum) -> new DashboardSummaryResponse(
                rs.getBigDecimal("total_spent"),
                rs.getLong("receipt_count"),
                rs.getLong("item_count"),
                rs.getLong("resolved_item_count"),
                rs.getLong("unresolved_item_count")));
    }

    public List<MonthlySpendingResponse> spendingByMonth(UUID ownerUserId) {
        return jdbc.query(sql.getSql("spendingByMonth"),
            new MapSqlParameterSource("ownerUserId", ownerUserId),
            (rs, rowNum) -> new MonthlySpendingResponse(
                rs.getString("month"), rs.getBigDecimal("total"), rs.getLong("receipt_count")));
    }

    public List<IssuerSpendingResponse> spendingByIssuer(UUID ownerUserId) {
        return jdbc.query(sql.getSql("spendingByIssuer"),
            new MapSqlParameterSource("ownerUserId", ownerUserId), DashboardRepository::mapIssuer);
    }

    private static IssuerSpendingResponse mapIssuer(ResultSet rs, int rowNum) throws SQLException {
        return new IssuerSpendingResponse(
            rs.getString("issuer_cnpj"),
            rs.getString("issuer_name"),
            rs.getBigDecimal("total"),
            rs.getLong("receipt_count"));
    }
}
