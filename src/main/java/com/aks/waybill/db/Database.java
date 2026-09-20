package com.aks.waybill.db;

import com.aks.waybill.config.AppPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public final class Database {
    private Database() {
    }

    public static void initialize() {
        try {
            Files.createDirectories(AppPaths.dataDirectory());
            try (Connection c = getConnection(); Statement s = c.createStatement()) {
                s.execute("PRAGMA foreign_keys = ON");
                s.execute("PRAGMA journal_mode = WAL");
                s.execute("CREATE TABLE IF NOT EXISTS app_user (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE COLLATE NOCASE, password_hash TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL CHECK(role IN ('ADMIN','USER')), enabled INTEGER NOT NULL DEFAULT 1, failed_login_attempts INTEGER NOT NULL DEFAULT 0, locked_until TEXT, last_login_at TEXT, must_change_password INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL, updated_at TEXT NOT NULL)");
                ensureColumn(c, "app_user", "user_code", "TEXT");
                ensureDefaultUserCodes(c);
                s.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_app_user_user_code ON app_user(user_code)");
                s.execute("CREATE TABLE IF NOT EXISTS application_settings (setting_key TEXT PRIMARY KEY, setting_value TEXT)");
                s.execute("CREATE TABLE IF NOT EXISTS company (id INTEGER PRIMARY KEY AUTOINCREMENT, company_name TEXT NOT NULL, contact_person TEXT, address TEXT, phone_number TEXT, email_address TEXT, active INTEGER NOT NULL DEFAULT 1, created_at TEXT NOT NULL, updated_at TEXT NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS saved_carrier (id INTEGER PRIMARY KEY AUTOINCREMENT, carrier_name TEXT NOT NULL UNIQUE COLLATE NOCASE, driver_name TEXT, vehicle_trailer_no TEXT, active INTEGER NOT NULL DEFAULT 1, created_at TEXT NOT NULL, updated_at TEXT NOT NULL)");
                ensureColumn(c, "saved_carrier", "driver_name", "TEXT");
                ensureColumn(c, "saved_carrier", "vehicle_trailer_no", "TEXT");
                s.execute("CREATE TABLE IF NOT EXISTS saved_location (id INTEGER PRIMARY KEY AUTOINCREMENT, location_name TEXT NOT NULL UNIQUE COLLATE NOCASE, active INTEGER NOT NULL DEFAULT 1, created_at TEXT NOT NULL, updated_at TEXT NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS waybill (id INTEGER PRIMARY KEY AUTOINCREMENT, waybill_number TEXT NOT NULL UNIQUE, waybill_date TEXT NOT NULL, shipper_company_id INTEGER, consignee_company_id INTEGER, carrier_name TEXT, driver_name TEXT, vehicle_trailer_no TEXT, origin_loading_point TEXT, destination_unloading_point TEXT, estimated_delivery_date TEXT, special_instructions TEXT, hazardous_materials INTEGER NOT NULL DEFAULT 0, remarks TEXT, shipper_declaration_name TEXT, shipper_declaration_date TEXT, carrier_receipt_driver_name TEXT, carrier_receipt_date TEXT, consignee_pod_receiver_name TEXT, consignee_pod_date TEXT, created_by INTEGER, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, FOREIGN KEY(shipper_company_id) REFERENCES company(id), FOREIGN KEY(consignee_company_id) REFERENCES company(id), FOREIGN KEY(created_by) REFERENCES app_user(id))");
                ensureColumn(c, "waybill", "shipper_declaration_name", "TEXT");
                ensureColumn(c, "waybill", "shipper_declaration_date", "TEXT");
                ensureColumn(c, "waybill", "carrier_receipt_driver_name", "TEXT");
                ensureColumn(c, "waybill", "carrier_receipt_date", "TEXT");
                ensureColumn(c, "waybill", "consignee_pod_receiver_name", "TEXT");
                ensureColumn(c, "waybill", "consignee_pod_date", "TEXT");
                s.execute("CREATE TABLE IF NOT EXISTS waybill_item (id INTEGER PRIMARY KEY AUTOINCREMENT, waybill_id INTEGER NOT NULL, item_number INTEGER NOT NULL, description TEXT, package_type TEXT, quantity REAL, weight_kg REAL, volume_m3 REAL, FOREIGN KEY(waybill_id) REFERENCES waybill(id) ON DELETE CASCADE)");
                s.execute("CREATE TABLE IF NOT EXISTS audit_log (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER, action TEXT NOT NULL, entity_type TEXT, entity_id INTEGER, details TEXT, created_at TEXT NOT NULL, FOREIGN KEY(user_id) REFERENCES app_user(id))");
            }
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Unable to initialize AKS Waybill database", e);
        }
    }

    private static void ensureColumn(Connection c, String table, String column, String definition) throws SQLException {
        boolean exists = false;
        try (PreparedStatement p = c.prepareStatement("PRAGMA table_info(" + table + ")"); ResultSet r = p.executeQuery()) {
            while (r.next()) if (column.equalsIgnoreCase(r.getString("name"))) { exists = true; break; }
        }
        if (!exists) {
            try (Statement s = c.createStatement()) { s.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition); }
        }
    }

    private static void ensureDefaultUserCodes(Connection c) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT id, username FROM app_user WHERE user_code IS NULL OR TRIM(user_code) = '' ORDER BY id"); ResultSet r = p.executeQuery()) {
            while (r.next()) {
                String username = r.getString("username");
                String base = username == null ? "USR" : username.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
                if (base.length() < 2) base = "USR";
                if (base.length() > 10) base = base.substring(0, 10);
                String code = base;
                int n = 1;
                while (userCodeExists(c, code, r.getLong("id"))) code = (base.length() >= 8 ? base.substring(0, 8) : base) + n++;
                try (PreparedStatement u = c.prepareStatement("UPDATE app_user SET user_code=?, updated_at=? WHERE id=?")) {
                    u.setString(1, code); u.setString(2, java.time.Instant.now().toString()); u.setLong(3, r.getLong("id")); u.executeUpdate();
                }
            }
        }
    }

    private static boolean userCodeExists(Connection c, String code, long excludeId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM app_user WHERE user_code=? AND id<>? LIMIT 1")) {
            p.setString(1, code); p.setLong(2, excludeId);
            try (ResultSet r = p.executeQuery()) { return r.next(); }
        }
    }

    public static Connection getConnection() throws SQLException {
        Connection c = DriverManager.getConnection(AppPaths.jdbcUrl());
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys = ON");
        }
        return c;
    }
}
