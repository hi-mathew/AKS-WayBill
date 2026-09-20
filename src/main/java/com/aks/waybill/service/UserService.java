package com.aks.waybill.service;

import com.aks.waybill.db.Database;
import com.aks.waybill.security.PasswordService;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** User administration service. Only administrators should call mutating methods. */
public final class UserService {
    private UserService() {}

    public record UserRecord(long id, String username, String displayName, String userCode,
                             String role, boolean enabled, boolean mustChangePassword,
                             String lastLoginAt, String createdAt) {}

    public static List<UserRecord> findAll() {
        String sql = "SELECT id, username, display_name, user_code, role, enabled, must_change_password, last_login_at, created_at "
                + "FROM app_user ORDER BY display_name COLLATE NOCASE, username COLLATE NOCASE";
        List<UserRecord> result = new ArrayList<>();
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql); ResultSet r = p.executeQuery()) {
            while (r.next()) result.add(read(r));
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load users", e);
        }
    }

    public static UserRecord findById(long id) {
        try (Connection c = Database.getConnection()) {
            return findById(c, id);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load user", e);
        }
    }

    public static UserRecord create(String username, String displayName, String userCode, String role, String password) {
        validate(username, displayName, userCode, role);
        if (password == null || password.length() < 8) throw new IllegalArgumentException("Password must contain at least 8 characters.");
        String now = Instant.now().toString();
        String sql = "INSERT INTO app_user(username,password_hash,display_name,user_code,role,enabled,must_change_password,created_at,updated_at) VALUES(?,?,?,?,?,1,0,?,?)";
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, username.trim());
            p.setString(2, PasswordService.hash(password));
            p.setString(3, displayName.trim());
            p.setString(4, normalizeCode(userCode));
            p.setString(5, role.trim().toUpperCase());
            p.setString(6, now);
            p.setString(7, now);
            p.executeUpdate();
            try (ResultSet keys = p.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Unable to determine new user ID.");
                return findById(c, keys.getLong(1));
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("unique")) {
                throw new IllegalArgumentException("Username or user code already exists.");
            }
            throw new IllegalStateException("Unable to create user", e);
        }
    }

    public static UserRecord update(long id, String username, String displayName, String userCode, String role) {
        validate(username, displayName, userCode, role);
        String now = Instant.now().toString();
        String sql = "UPDATE app_user SET username=?, display_name=?, user_code=?, role=?, updated_at=? WHERE id=?";
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, username.trim());
            p.setString(2, displayName.trim());
            p.setString(3, normalizeCode(userCode));
            p.setString(4, role.trim().toUpperCase());
            p.setString(5, now);
            p.setLong(6, id);
            if (p.executeUpdate() == 0) throw new IllegalArgumentException("The selected user no longer exists.");
            return findById(c, id);
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("unique")) {
                throw new IllegalArgumentException("Username or user code already exists.");
            }
            throw new IllegalStateException("Unable to update user", e);
        }
    }

    public static void setEnabled(long id, boolean enabled) {
        String now = Instant.now().toString();
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement("UPDATE app_user SET enabled=?, updated_at=? WHERE id=?")) {
            p.setInt(1, enabled ? 1 : 0); p.setString(2, now); p.setLong(3, id);
            if (p.executeUpdate() == 0) throw new IllegalArgumentException("The selected user no longer exists.");
        } catch (SQLException e) { throw new IllegalStateException("Unable to update user status", e); }
    }

    public static void resetPassword(long id, String password) {
        if (password == null || password.length() < 8) throw new IllegalArgumentException("Password must contain at least 8 characters.");
        String now = Instant.now().toString();
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement("UPDATE app_user SET password_hash=?, must_change_password=0, failed_login_attempts=0, locked_until=NULL, updated_at=? WHERE id=?")) {
            p.setString(1, PasswordService.hash(password)); p.setString(2, now); p.setLong(3, id);
            if (p.executeUpdate() == 0) throw new IllegalArgumentException("The selected user no longer exists.");
        } catch (SQLException e) { throw new IllegalStateException("Unable to reset password", e); }
    }

    private static void validate(String username, String displayName, String userCode, String role) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Username is required.");
        if (username.trim().length() > 100) throw new IllegalArgumentException("Username cannot exceed 100 characters.");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("Display name is required.");
        if (displayName.trim().length() > 200) throw new IllegalArgumentException("Display name cannot exceed 200 characters.");
        String code = normalizeCode(userCode);
        if (!code.matches("[A-Z0-9_-]{2,20}")) throw new IllegalArgumentException("User Code must be 2-20 letters, numbers, hyphens or underscores.");
        if (!"ADMIN".equalsIgnoreCase(role) && !"USER".equalsIgnoreCase(role)) throw new IllegalArgumentException("Role must be ADMIN or USER.");
    }

    private static String normalizeCode(String value) { return value == null ? "" : value.trim().toUpperCase(); }

    private static UserRecord findById(Connection c, long id) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT id, username, display_name, user_code, role, enabled, must_change_password, last_login_at, created_at FROM app_user WHERE id=?")) {
            p.setLong(1, id);
            try (ResultSet r = p.executeQuery()) { return r.next() ? read(r) : null; }
        }
    }

    private static UserRecord read(ResultSet r) throws SQLException {
        return new UserRecord(r.getLong("id"), r.getString("username"), r.getString("display_name"),
                r.getString("user_code"), r.getString("role"), r.getInt("enabled") == 1,
                r.getInt("must_change_password") == 1, r.getString("last_login_at"), r.getString("created_at"));
    }
}
