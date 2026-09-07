package br.com.carrinhoesperto.catalog;

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

import static br.com.carrinhoesperto.catalog.ManufacturerRepository.instant;

/**
 * Persistence for {@link Product}, the canonical product identity. Concrete
 * class, no interface; SQL in {@code sql/catalog_product.sql} via ELSQL.
 *
 * <p>Platform-owned — no owner scoping. {@link #findByGtin} returns at most one
 * row (unique index). {@link #searchByName} is a deterministic, case-insensitive
 * {@code LIKE} — there is no fuzzy matching in the catalog (M004 §17).
 */
@Repository
public class ProductRepository {

    /** Cap on deterministic name-search results. */
    static final int SEARCH_LIMIT = 50;

    private final NamedParameterJdbcTemplate jdbc;
    private final ElSqlBundle sql;

    ProductRepository(NamedParameterJdbcTemplate jdbc, ElSqlConfig elSqlConfig) {
        this.jdbc = jdbc;
        this.sql = ElSqlBundle.parse(elSqlConfig, new ClassPathResource("sql/catalog_product.sql"));
    }

    public void insert(Product product) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", product.id())
            .addValue("canonicalName", product.canonicalName())
            .addValue("brandId", product.brandId())
            .addValue("manufacturerId", product.manufacturerId())
            .addValue("categoryId", product.categoryId())
            .addValue("subcategoryId", product.subcategoryId())
            .addValue("productFamilyId", product.productFamilyId())
            .addValue("gtin", product.gtin())
            .addValue("packageQuantity", product.packageQuantity())
            .addValue("packageUnit", product.packageUnit() == null ? null : product.packageUnit().name())
            .addValue("packagingType", product.packagingType() == null ? null : product.packagingType().name());
        jdbc.update(sql.getSql("insert"), params);
    }

    public Optional<Product> findById(UUID id) {
        return jdbc.query(sql.getSql("findById"),
                new MapSqlParameterSource("id", id), ProductRepository::map)
            .stream().findFirst();
    }

    public Optional<Product> findByGtin(String gtin) {
        return jdbc.query(sql.getSql("findByGtin"),
                new MapSqlParameterSource("gtin", gtin), ProductRepository::map)
            .stream().findFirst();
    }

    /** Case-insensitive substring match on the canonical name, ordered by name. */
    public List<Product> searchByName(String query) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("pattern", "%" + query + "%")
            .addValue("limit", SEARCH_LIMIT);
        return jdbc.query(sql.getSql("searchByName"), params, ProductRepository::map);
    }

    /** Most recently created products first, capped at {@code limit}. Admin inspection (M006). */
    public List<Product> findRecent(int limit) {
        return jdbc.query(sql.getSql("findRecent"),
            new MapSqlParameterSource("limit", limit), ProductRepository::map);
    }

    private static Product map(ResultSet rs, int rowNum) throws SQLException {
        String unit = rs.getString("package_unit");
        String packaging = rs.getString("packaging_type");
        return new Product(
            rs.getObject("id", UUID.class),
            rs.getString("canonical_name"),
            rs.getObject("brand_id", UUID.class),
            rs.getObject("manufacturer_id", UUID.class),
            rs.getObject("category_id", UUID.class),
            rs.getObject("subcategory_id", UUID.class),
            rs.getObject("product_family_id", UUID.class),
            rs.getString("gtin"),
            rs.getBigDecimal("package_quantity"),
            unit == null ? null : PackageUnit.valueOf(unit),
            packaging == null ? null : PackagingType.valueOf(packaging),
            instant(rs, "created_at"),
            instant(rs, "updated_at"));
    }
}
