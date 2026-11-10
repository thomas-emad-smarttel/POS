package com.restaurantpos.desktop.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.restaurantpos.shared.dto.pos.CategoryDto;
import com.restaurantpos.shared.dto.pos.ProductDto;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Terminal-local SQLite cache using WAL mode for crash resilience and offline read-only menu browsing.
 */
public class LocalCacheDatabase {

    private final String jdbcUrl;
    private final ObjectMapper objectMapper;

    public LocalCacheDatabase(Path dbFilePath) {
        Objects.requireNonNull(dbFilePath, "dbFilePath must not be null");
        this.jdbcUrl = "jdbc:sqlite:" + dbFilePath.toAbsolutePath();
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public void initialize() throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL;");
            statement.execute("PRAGMA synchronous=NORMAL;");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS terminal_metadata (
                        meta_key TEXT PRIMARY KEY,
                        meta_value TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    );
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS cached_categories (
                        branch_id TEXT NOT NULL,
                        category_id TEXT NOT NULL,
                        display_order INTEGER NOT NULL,
                        payload_json TEXT NOT NULL,
                        cached_at TEXT NOT NULL,
                        PRIMARY KEY (branch_id, category_id)
                    );
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS cached_products (
                        branch_id TEXT NOT NULL,
                        product_id TEXT NOT NULL,
                        category_id TEXT NOT NULL,
                        display_order INTEGER NOT NULL,
                        payload_json TEXT NOT NULL,
                        cached_at TEXT NOT NULL,
                        PRIMARY KEY (branch_id, product_id)
                    );
                    """);
        }
    }

    public void putMetadata(String key, String value) throws SQLException {
        String sql = """
                INSERT INTO terminal_metadata (meta_key, meta_value, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT(meta_key) DO UPDATE SET
                    meta_value = excluded.meta_value,
                    updated_at = excluded.updated_at;
                """;
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.setString(3, Instant.now().toString());
            ps.executeUpdate();
        }
    }

    public Optional<String> getMetadata(String key) throws SQLException {
        String sql = "SELECT meta_value FROM terminal_metadata WHERE meta_key = ?";
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        }
    }

    public void replaceBranchCategories(UUID branchId, List<CategoryDto> categories) throws Exception {
        String now = Instant.now().toString();
        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            conn.setAutoCommit(false);
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM cached_categories WHERE branch_id = ?")) {
                del.setString(1, branchId.toString());
                del.executeUpdate();
            }
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO cached_categories (branch_id, category_id, display_order, payload_json, cached_at) VALUES (?, ?, ?, ?, ?)")) {
                for (CategoryDto c : categories) {
                    ins.setString(1, branchId.toString());
                    ins.setString(2, c.id().toString());
                    ins.setInt(3, c.displayOrder());
                    ins.setString(4, objectMapper.writeValueAsString(c));
                    ins.setString(5, now);
                    ins.addBatch();
                }
                ins.executeBatch();
            }
            conn.commit();
        }
    }

    public List<CategoryDto> readBranchCategories(UUID branchId) throws Exception {
        List<CategoryDto> result = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT payload_json FROM cached_categories WHERE branch_id = ? ORDER BY display_order ASC")) {
            ps.setString(1, branchId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(objectMapper.readValue(rs.getString(1), CategoryDto.class));
                }
            }
        }
        return result;
    }

    public void replaceBranchProducts(UUID branchId, List<ProductDto> products) throws Exception {
        String now = Instant.now().toString();
        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            conn.setAutoCommit(false);
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM cached_products WHERE branch_id = ?")) {
                del.setString(1, branchId.toString());
                del.executeUpdate();
            }
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO cached_products (branch_id, product_id, category_id, display_order, payload_json, cached_at) VALUES (?, ?, ?, ?, ?, ?)")) {
                for (ProductDto p : products) {
                    ins.setString(1, branchId.toString());
                    ins.setString(2, p.id().toString());
                    ins.setString(3, p.categoryId().toString());
                    ins.setInt(4, p.displayOrder());
                    ins.setString(5, objectMapper.writeValueAsString(p));
                    ins.setString(6, now);
                    ins.addBatch();
                }
                ins.executeBatch();
            }
            conn.commit();
        }
    }

    public List<ProductDto> readBranchProducts(UUID branchId) throws Exception {
        List<ProductDto> result = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT payload_json FROM cached_products WHERE branch_id = ? ORDER BY display_order ASC")) {
            ps.setString(1, branchId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(objectMapper.readValue(rs.getString(1), ProductDto.class));
                }
            }
        }
        return result;
    }
}
