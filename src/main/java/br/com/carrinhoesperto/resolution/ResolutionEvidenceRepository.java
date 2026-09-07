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
import java.util.UUID;

import static br.com.carrinhoesperto.resolution.ProductResolutionRepository.instant;

/**
 * Persistence for {@link ResolutionEvidence}. Concrete class, no interface; SQL
 * in {@code sql/resolution_evidence.sql} via ELSQL.
 */
@Repository
public class ResolutionEvidenceRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ElSqlBundle sql;

    ResolutionEvidenceRepository(NamedParameterJdbcTemplate jdbc, ElSqlConfig elSqlConfig) {
        this.jdbc = jdbc;
        this.sql = ElSqlBundle.parse(elSqlConfig, new ClassPathResource("sql/resolution_evidence.sql"));
    }

    public void insert(UUID id, UUID resolutionId, ResolutionEvidenceType type,
                       ResolutionEvidenceSource source, String value) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("resolutionId", resolutionId)
            .addValue("type", type.name())
            .addValue("source", source.name())
            .addValue("value", value);
        jdbc.update(sql.getSql("insert"), params);
    }

    public void deleteByResolutionId(UUID resolutionId) {
        jdbc.update(sql.getSql("deleteByResolutionId"),
            new MapSqlParameterSource("resolutionId", resolutionId));
    }

    public List<ResolutionEvidence> findByResolutionId(UUID resolutionId) {
        return jdbc.query(sql.getSql("findByResolutionId"),
            new MapSqlParameterSource("resolutionId", resolutionId), ResolutionEvidenceRepository::map);
    }

    private static ResolutionEvidence map(ResultSet rs, int rowNum) throws SQLException {
        return new ResolutionEvidence(
            rs.getObject("id", UUID.class),
            rs.getObject("resolution_id", UUID.class),
            ResolutionEvidenceType.valueOf(rs.getString("type")),
            ResolutionEvidenceSource.valueOf(rs.getString("source")),
            rs.getString("value"),
            instant(rs, "created_at"));
    }
}
