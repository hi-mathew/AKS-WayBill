package com.aks.waybill.service;

import com.aks.waybill.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Separate master-data services for Shipper and Consignee companies. */
public final class CompanyService {
    private static final DateTimeFormatter DB_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public enum CompanyType {
        SHIPPER("Shipper / Consignor"),
        CONSIGNEE("Consignee / Receiver");

        private final String displayName;
        CompanyType(String displayName) { this.displayName = displayName; }
        public String displayName() { return displayName; }
        public String tableName() { return this == SHIPPER ? "shipper_company" : "consignee_company"; }
    }

    private CompanyService() {}

    public record CompanyRecord(long id, String companyName, String contactPerson,
                                String address, String phoneNumber, String emailAddress,
                                boolean active) {
        @Override public String toString() { return companyName == null ? "" : companyName; }
    }

    public record CompanyPage(List<CompanyRecord> rows, int page, int pageSize, long totalRows) {
        public int totalPages() { return (int) Math.max(1, (totalRows + pageSize - 1) / pageSize); }
    }

    public static List<CompanyRecord> findActiveCompanies(CompanyType type) { return findAll(type, true); }

    public static CompanyRecord findByName(CompanyType type, String companyName, boolean activeOnly) {
        if (companyName == null || companyName.trim().isEmpty()) return null;
        try (Connection connection = Database.getConnection()) {
            return findByName(connection, type, companyName.trim(), activeOnly);
        } catch (SQLException e) { throw new IllegalStateException("Unable to load company", e); }
    }

    public static CompanyRecord findById(CompanyType type, long id) {
        String sql = "SELECT id, company_name, contact_person, address, phone_number, email_address, active FROM " + table(type) + " WHERE id=?";
        try (Connection connection = Database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) { return rs.next() ? read(rs) : null; }
        } catch (SQLException e) { throw new IllegalStateException("Unable to load company", e); }
    }

    public static CompanyPage findPage(CompanyType type, String search, int page, int pageSize) {
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        int safePage = Math.max(0, page);
        String term = search == null ? "" : search.trim();
        String where = "";
        if (!term.isBlank()) {
            where = " WHERE company_name LIKE ? COLLATE NOCASE OR COALESCE(contact_person,'') LIKE ? COLLATE NOCASE "
                    + "OR COALESCE(phone_number,'') LIKE ? COLLATE NOCASE OR COALESCE(email_address,'') LIKE ? COLLATE NOCASE ";
        }
        String like = "%" + term + "%";
        String table = table(type);
        try (Connection connection = Database.getConnection()) {
            long total;
            try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table + where)) {
                if (!term.isBlank()) bindSearch(statement, like);
                try (ResultSet rs = statement.executeQuery()) { rs.next(); total = rs.getLong(1); }
            }
            int totalPages = (int) Math.max(1, (total + safePageSize - 1) / safePageSize);
            int normalizedPage = Math.min(safePage, totalPages - 1);
            String sql = "SELECT id, company_name, contact_person, address, phone_number, email_address, active FROM " + table + where
                    + " ORDER BY company_name COLLATE NOCASE, id LIMIT ? OFFSET ?";
            List<CompanyRecord> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = 1;
                if (!term.isBlank()) {
                    statement.setString(index++, like); statement.setString(index++, like);
                    statement.setString(index++, like); statement.setString(index++, like);
                }
                statement.setInt(index++, safePageSize); statement.setInt(index, normalizedPage * safePageSize);
                try (ResultSet rs = statement.executeQuery()) { while (rs.next()) rows.add(read(rs)); }
            }
            return new CompanyPage(rows, normalizedPage, safePageSize, total);
        } catch (SQLException e) { throw new IllegalStateException("Unable to load companies", e); }
    }

    public static long countAll() {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT (SELECT COUNT(*) FROM shipper_company) + (SELECT COUNT(*) FROM consignee_company)" );
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) { throw new IllegalStateException("Unable to count companies", e); }
    }

    public static CompanyRecord create(CompanyType type, String companyName, String contactPerson, String address,
                                       String phoneNumber, String emailAddress) {
        validate(companyName);
        String now = DB_DATE_TIME.format(LocalDateTime.now());
        String table = table(type);
        try (Connection connection = Database.getConnection()) {
            if (findByName(connection, type, companyName.trim(), false) != null)
                throw new IllegalArgumentException("A company with this name already exists in the " + type.displayName() + " master.");
            String sql = "INSERT INTO " + table + " (company_name, contact_person, address, phone_number, email_address, active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 1, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                bindCompany(statement, companyName, contactPerson, address, phoneNumber, emailAddress, now);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Unable to determine company ID.");
                    return findById(type, keys.getLong(1));
                }
            }
        } catch (SQLException e) { throw new IllegalStateException("Unable to create company", e); }
    }

    public static CompanyRecord update(CompanyType type, long id, String companyName, String contactPerson, String address,
                                       String phoneNumber, String emailAddress, boolean active) {
        validate(companyName);
        String now = DB_DATE_TIME.format(LocalDateTime.now());
        String table = table(type);
        try (Connection connection = Database.getConnection()) {
            CompanyRecord duplicate = findByName(connection, type, companyName.trim(), false);
            if (duplicate != null && duplicate.id() != id) throw new IllegalArgumentException("Another company already uses this name in the " + type.displayName() + " master.");
            String sql = "UPDATE " + table + " SET company_name=?, contact_person=?, address=?, phone_number=?, email_address=?, active=?, updated_at=? WHERE id=?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bindCompany(statement, companyName, contactPerson, address, phoneNumber, emailAddress, now);
                statement.setInt(7, active ? 1 : 0); statement.setLong(8, id);
                if (statement.executeUpdate() == 0) throw new IllegalArgumentException("The selected company no longer exists.");
            }
            return findById(type, id);
        } catch (SQLException e) { throw new IllegalStateException("Unable to update company", e); }
    }

    public static void setActive(CompanyType type, long id, boolean active) {
        String sql = "UPDATE " + table(type) + " SET active=?, updated_at=? WHERE id=?";
        try (Connection connection = Database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, active ? 1 : 0); statement.setString(2, DB_DATE_TIME.format(LocalDateTime.now())); statement.setLong(3, id); statement.executeUpdate();
        } catch (SQLException e) { throw new IllegalStateException("Unable to update company status", e); }
    }

    private static String table(CompanyType type) {
        if (type == null) throw new IllegalArgumentException("Company type is required.");
        return type.tableName();
    }

    private static List<CompanyRecord> findAll(CompanyType type, boolean activeOnly) {
        String sql = "SELECT id, company_name, contact_person, address, phone_number, email_address, active FROM " + table(type)
                + (activeOnly ? " WHERE active=1" : "") + " ORDER BY company_name COLLATE NOCASE, id";
        try (Connection connection = Database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql); ResultSet rs = statement.executeQuery()) {
            List<CompanyRecord> result = new ArrayList<>(); while (rs.next()) result.add(read(rs)); return result;
        } catch (SQLException e) { throw new IllegalStateException("Unable to load companies", e); }
    }

    private static CompanyRecord findByName(Connection connection, CompanyType type, String name, boolean activeOnly) throws SQLException {
        String sql = "SELECT id, company_name, contact_person, address, phone_number, email_address, active FROM " + table(type)
                + " WHERE company_name=? COLLATE NOCASE" + (activeOnly ? " AND active=1" : "") + " ORDER BY id LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name); try (ResultSet rs = statement.executeQuery()) { return rs.next() ? read(rs) : null; }
        }
    }

    private static CompanyRecord read(ResultSet rs) throws SQLException {
        return new CompanyRecord(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getInt(7) == 1);
    }

    private static void bindSearch(PreparedStatement statement, String like) throws SQLException {
        statement.setString(1, like); statement.setString(2, like); statement.setString(3, like); statement.setString(4, like);
    }

    private static void bindCompany(PreparedStatement statement, String companyName, String contactPerson, String address,
                                    String phoneNumber, String emailAddress, String now) throws SQLException {
        statement.setString(1, companyName.trim()); statement.setString(2, blankToNull(contactPerson)); statement.setString(3, blankToNull(address));
        statement.setString(4, blankToNull(phoneNumber)); statement.setString(5, blankToNull(emailAddress)); statement.setString(6, now); statement.setString(7, now);
    }

    private static void validate(String companyName) {
        if (companyName == null || companyName.trim().isEmpty()) throw new IllegalArgumentException("Company name is required.");
        if (companyName.length() > 300) throw new IllegalArgumentException("Company name cannot exceed 300 characters.");
    }

    private static String blankToNull(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
}
