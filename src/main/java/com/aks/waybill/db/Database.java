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
            Files.createDirectories(AppPaths.databaseFile().getParent());
            migrateLegacyPackagedDatabaseIfNeeded();
            try (Connection c = getConnection(); Statement s = c.createStatement()) {
                s.execute("PRAGMA foreign_keys = ON");
                s.execute("PRAGMA journal_mode = WAL");
                s.execute("CREATE TABLE IF NOT EXISTS app_user (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE COLLATE NOCASE, password_hash TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL CHECK(role IN ('ADMIN','USER')), enabled INTEGER NOT NULL DEFAULT 1, failed_login_attempts INTEGER NOT NULL DEFAULT 0, locked_until TEXT, last_login_at TEXT, must_change_password INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL, updated_at TEXT NOT NULL)");
                ensureColumn(c, "app_user", "user_code", "TEXT");
                ensureDefaultUserCodes(c);
                s.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_app_user_user_code ON app_user(user_code)");
                s.execute("CREATE TABLE IF NOT EXISTS application_settings (setting_key TEXT PRIMARY KEY, setting_value TEXT)");
                ensureSeparateCompanyMasters(c);
                s.execute("CREATE TABLE IF NOT EXISTS saved_carrier (id INTEGER PRIMARY KEY AUTOINCREMENT, carrier_name TEXT NOT NULL UNIQUE COLLATE NOCASE, driver_name TEXT, vehicle_trailer_no TEXT, active INTEGER NOT NULL DEFAULT 1, created_at TEXT NOT NULL, updated_at TEXT NOT NULL)");
                ensureColumn(c, "saved_carrier", "driver_name", "TEXT");
                ensureColumn(c, "saved_carrier", "vehicle_trailer_no", "TEXT");
                s.execute("CREATE TABLE IF NOT EXISTS saved_location (id INTEGER PRIMARY KEY AUTOINCREMENT, location_name TEXT NOT NULL UNIQUE COLLATE NOCASE, active INTEGER NOT NULL DEFAULT 1, created_at TEXT NOT NULL, updated_at TEXT NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS waybill (id INTEGER PRIMARY KEY AUTOINCREMENT, waybill_number TEXT NOT NULL UNIQUE, waybill_date TEXT NOT NULL, shipper_company_id INTEGER, consignee_company_id INTEGER, carrier_name TEXT, driver_name TEXT, vehicle_trailer_no TEXT, origin_loading_point TEXT, destination_unloading_point TEXT, estimated_delivery_date TEXT, special_instructions TEXT, hazardous_materials INTEGER NOT NULL DEFAULT 0, remarks TEXT, shipper_declaration_name TEXT, shipper_declaration_date TEXT, carrier_receipt_driver_name TEXT, carrier_receipt_date TEXT, consignee_pod_receiver_name TEXT, consignee_pod_date TEXT, status TEXT NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','FINAL')), finalized_at TEXT, finalized_by INTEGER, final_to_draft_reason TEXT, created_by INTEGER, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, FOREIGN KEY(shipper_company_id) REFERENCES shipper_company(id), FOREIGN KEY(consignee_company_id) REFERENCES consignee_company(id), FOREIGN KEY(created_by) REFERENCES app_user(id), FOREIGN KEY(finalized_by) REFERENCES app_user(id))");
                ensureColumn(c, "waybill", "shipper_declaration_name", "TEXT");
                ensureColumn(c, "waybill", "shipper_declaration_date", "TEXT");
                ensureColumn(c, "waybill", "carrier_receipt_driver_name", "TEXT");
                ensureColumn(c, "waybill", "carrier_receipt_date", "TEXT");
                ensureColumn(c, "waybill", "consignee_pod_receiver_name", "TEXT");
                ensureColumn(c, "waybill", "consignee_pod_date", "TEXT");
                ensureColumn(c, "waybill", "status", "TEXT NOT NULL DEFAULT 'DRAFT'");
                ensureColumn(c, "waybill", "finalized_at", "TEXT");
                ensureColumn(c, "waybill", "finalized_by", "INTEGER");
                ensureColumn(c, "waybill", "final_to_draft_reason", "TEXT");
                s.execute("UPDATE waybill SET status='DRAFT' WHERE status IS NULL OR status NOT IN ('DRAFT','FINAL')");
                s.execute("CREATE TABLE IF NOT EXISTS waybill_item (id INTEGER PRIMARY KEY AUTOINCREMENT, waybill_id INTEGER NOT NULL, item_number INTEGER NOT NULL, description TEXT, package_type TEXT, quantity REAL, weight_kg REAL, volume_m3 REAL, FOREIGN KEY(waybill_id) REFERENCES waybill(id) ON DELETE CASCADE)");
                s.execute("CREATE TABLE IF NOT EXISTS audit_log (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER, action TEXT NOT NULL, entity_type TEXT, entity_id INTEGER, details TEXT, created_at TEXT NOT NULL, FOREIGN KEY(user_id) REFERENCES app_user(id))");
                ensureColumn(c, "audit_log", "entity_label", "TEXT");
                s.execute("CREATE TABLE IF NOT EXISTS terms_condition (id INTEGER PRIMARY KEY AUTOINCREMENT, clause_number INTEGER NOT NULL, clause_title TEXT NOT NULL, clause_text TEXT NOT NULL, display_order INTEGER NOT NULL, active INTEGER NOT NULL DEFAULT 1, created_at TEXT NOT NULL, updated_at TEXT NOT NULL)");
                seedTermsConditions(c);
            }
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Unable to initialize W.A.S.P database", e);
        }
    }


    /**
     * Older packaged development builds stored the database beside the application
     * under <app>\data\waybill.db. If that file exists and the new persistent
     * AppData database does not, migrate it once. This keeps upgrades safe while
     * the production database location is moved outside Program Files.
     */
    private static void migrateLegacyPackagedDatabaseIfNeeded() throws IOException {
        if (!AppPaths.isPackaged() || Files.exists(AppPaths.databaseFile())) return;
        java.nio.file.Path legacy = AppPaths.applicationDirectory().resolve("data").resolve("waybill.db");
        if (!Files.isRegularFile(legacy)) return;
        Files.copy(legacy, AppPaths.databaseFile());
    }

    private static void ensureSeparateCompanyMasters(Connection c) throws SQLException {
        boolean legacyCompany = tableExists(c, "company");
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS shipper_company (id INTEGER PRIMARY KEY AUTOINCREMENT, company_name TEXT NOT NULL, contact_person TEXT, address TEXT, phone_number TEXT, email_address TEXT, active INTEGER NOT NULL DEFAULT 1, created_at TEXT NOT NULL, updated_at TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS consignee_company (id INTEGER PRIMARY KEY AUTOINCREMENT, company_name TEXT NOT NULL, contact_person TEXT, address TEXT, phone_number TEXT, email_address TEXT, active INTEGER NOT NULL DEFAULT 1, created_at TEXT NOT NULL, updated_at TEXT NOT NULL)");
        }
        if (!legacyCompany) return;

        c.createStatement().execute("PRAGMA foreign_keys = OFF");
        try (Statement s = c.createStatement()) {
            // Existing data is preserved, but copied into two independent masters.
            s.execute("INSERT OR IGNORE INTO shipper_company(id, company_name, contact_person, address, phone_number, email_address, active, created_at, updated_at) SELECT id, company_name, contact_person, address, phone_number, email_address, active, created_at, updated_at FROM company");
            s.execute("INSERT OR IGNORE INTO consignee_company(id, company_name, contact_person, address, phone_number, email_address, active, created_at, updated_at) SELECT id, company_name, contact_person, address, phone_number, email_address, active, created_at, updated_at FROM company");
            if (tableExists(c, "waybill")) {
                s.execute("DROP TABLE IF EXISTS waybill_new");
                s.execute("CREATE TABLE waybill_new (id INTEGER PRIMARY KEY AUTOINCREMENT, waybill_number TEXT NOT NULL UNIQUE, waybill_date TEXT NOT NULL, shipper_company_id INTEGER, consignee_company_id INTEGER, carrier_name TEXT, driver_name TEXT, vehicle_trailer_no TEXT, origin_loading_point TEXT, destination_unloading_point TEXT, estimated_delivery_date TEXT, special_instructions TEXT, hazardous_materials INTEGER NOT NULL DEFAULT 0, remarks TEXT, shipper_declaration_name TEXT, shipper_declaration_date TEXT, carrier_receipt_driver_name TEXT, carrier_receipt_date TEXT, consignee_pod_receiver_name TEXT, consignee_pod_date TEXT, created_by INTEGER, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, FOREIGN KEY(shipper_company_id) REFERENCES shipper_company(id), FOREIGN KEY(consignee_company_id) REFERENCES consignee_company(id), FOREIGN KEY(created_by) REFERENCES app_user(id))");
                s.execute("INSERT INTO waybill_new SELECT * FROM waybill");
                s.execute("DROP TABLE waybill");
                s.execute("ALTER TABLE waybill_new RENAME TO waybill");
            }
            s.execute("DROP TABLE company");
        } finally {
            c.createStatement().execute("PRAGMA foreign_keys = ON");
        }
    }

    private static void seedTermsConditions(Connection c) throws SQLException {
        try (PreparedStatement count = c.prepareStatement("SELECT COUNT(*) FROM terms_condition"); ResultSet r = count.executeQuery()) {
            if (r.next() && r.getInt(1) > 0) return;
        }
        String[][] clauses = new String[][] {
                {"1","Contract of Carriage","This Waybill is a non-negotiable document. The Carrier agrees to transport the shipment described on Page 1 to the designated delivery address with reasonable dispatch. The Shipper acknowledges that this Waybill constitutes the entire agreement between the parties regarding carriage."},
                {"2","Warranties & Cargo Accuracy","The Shipper warrants that all information provided regarding cargo description, weight, volume, markings, and packaging is complete, accurate, and truthful. The Shipper is solely liable for any fines, penalties, losses, or expenses resulting from inaccurate or misleading declarations."},
                {"3","Packaging & Marking Requirements","All goods must be prepared, packed, labeled, and marked in a manner suitable for safe transportation during normal handling. The Carrier reserves the right to reject any consignment that is inadequately packed, leaking, damaged, or deemed unsafe for transport."},
                {"4","Hazardous & Restricted Goods","Dangerous goods, explosives, flammable liquids, radioactive materials, or contraband will not be accepted unless declared in writing prior to shipment. Hazardous consignments must comply fully with national and international transport regulations and be accompanied by proper Safety Data Sheets (SDS)."},
                {"5","Carrier Rights & Inspection","The Carrier reserves the right, but is not obligated, to inspect any package or consignment at any time without prior notice to verify contents, condition, weight, or regulatory compliance."},
                {"6","Carrier Limitation of Liability","Unless a higher valuation is declared by the Shipper prior to transit and additional charges paid, the Carrier’s total cumulative liability for loss, damage, or delay shall be strictly limited to standard statutory limits or $2.00 per kilogram ($0.90 per pound) of affected cargo, whichever is lower."},
                {"7","Exclusions from Liability","The Carrier shall not be liable for loss, damage, or delay caused by: (a) Acts of God, war, civil commotion, or extreme weather; (b) Inherent vice or natural defect of the cargo; (c) Insufficient packaging or incorrect addressing; (d) Actions or omissions of the Shipper, Consignee, or Customs Authorities."},
                {"8","Freight Charges & Payment","Freight charges are due upon delivery unless credit terms have been pre-approved. The Carrier maintains a general lien on all cargo in its possession for unpaid transport fees, storage fees, or administrative expenses incurred on behalf of the shipment."},
                {"9","Claims & Notice Requirements","Notice of visible loss or damage must be recorded on the Proof of Delivery upon receipt. Written formal claims for non-visible damage must be submitted to the Carrier within 7 business days of delivery. Failure to give notice releases Carrier from all liability."},
                {"10","Governing Law & Jurisdiction","This contract shall be governed by and construed in accordance with the laws of the jurisdiction where the shipment originates. Any legal disputes arising hereunder shall be subject to the exclusive jurisdiction of the competent courts in that territory."}
        };
        String now = java.time.LocalDateTime.now().toString();
        try (PreparedStatement p = c.prepareStatement("INSERT INTO terms_condition(clause_number,clause_title,clause_text,display_order,active,created_at,updated_at) VALUES(?,?,?,?,1,?,?)")) {
            for (int i=0;i<clauses.length;i++) { p.setInt(1,Integer.parseInt(clauses[i][0])); p.setString(2,clauses[i][1]); p.setString(3,clauses[i][2]); p.setInt(4,i+1); p.setString(5,now); p.setString(6,now); p.addBatch(); }
            p.executeBatch();
        }
    }

    private static boolean tableExists(Connection c, String table) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            p.setString(1, table);
            try (ResultSet r = p.executeQuery()) { return r.next(); }
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
