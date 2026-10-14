package com.aks.waybill.service;

import com.aks.waybill.logging.WaspLogger;

import com.aks.waybill.db.Database;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Administrator-managed Terms & Conditions used by generated waybill reports. */
public final class TermsConditionService {
    private TermsConditionService() {}

    public record Clause(long id, int clauseNumber, String printedNumber, String itemType, String title, String text, int displayOrder, boolean active, boolean hazardousMaterialsReference) {}

    public static List<Clause> findAll() {
        return find(false);
    }

    public static List<Clause> findActive() {
        return find(true);
    }

    private static List<Clause> find(boolean activeOnly) {
        String sql = "SELECT id,clause_number,printed_number,item_type,clause_title,clause_text,display_order,active,hazardous_materials_reference FROM terms_condition "
                + (activeOnly ? "WHERE active=1 " : "") + "ORDER BY display_order,id";
        List<Clause> result = new ArrayList<>();
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql); ResultSet r = p.executeQuery()) {
            while (r.next()) result.add(read(r));
            return result;
        } catch (SQLException e) { WaspLogger.error("Operation failed in TermsConditionService", e);
            throw new IllegalStateException("Unable to load Terms & Conditions", e);
        }
    }

    public static Clause create(String title, String text, boolean active) {
        int nextOrder = findAll().stream().mapToInt(Clause::displayOrder).max().orElse(0) + 1;
        int nextNumber = nextOrder;
        return save(0, nextNumber, String.valueOf(nextNumber) + ".", "CLAUSE", title, text, nextOrder, active, false);
    }

    public static Clause create(String printedNumber, String itemType, String title, String text,
                                int displayOrder, boolean active, boolean hazardousMaterialsReference) {
        int nextOrder = findAll().stream().mapToInt(Clause::displayOrder).max().orElse(0) + 1;
        int rank = displayOrder > 0 ? displayOrder : nextOrder;
        return save(0, rank, printedNumber, itemType, title, text, rank, active, hazardousMaterialsReference);
    }

    public static Clause update(long id, String title, String text, int displayOrder, boolean active, boolean hazardousMaterialsReference) {
        if (id <= 0) throw new IllegalArgumentException("Valid clause is required.");
        Clause existing = findById(id);
        if (existing == null) throw new IllegalArgumentException("The selected clause no longer exists.");
        return save(id, existing.clauseNumber(), existing.printedNumber(), existing.itemType(), title, text,
                displayOrder, active, hazardousMaterialsReference);
    }

    public static Clause update(long id, String printedNumber, String itemType, String title, String text,
                                int displayOrder, boolean active, boolean hazardousMaterialsReference) {
        if (id <= 0) throw new IllegalArgumentException("Valid item is required.");
        Clause existing = findById(id);
        if (existing == null) throw new IllegalArgumentException("The selected item no longer exists.");
        return save(id, existing.clauseNumber(), printedNumber, itemType, title, text,
                displayOrder, active, hazardousMaterialsReference);
    }

    private static Clause save(long id, int clauseNumber, String printedNumber, String itemType,
                               String title, String text, int displayOrder, boolean active,
                               boolean hazardousMaterialsReference) {
        validate(itemType, title, text, printedNumber, hazardousMaterialsReference);
        String now = LocalDateTime.now().toString();
        String type = itemType.trim().toUpperCase(java.util.Locale.ROOT);
        String printed = printedNumber == null || printedNumber.isBlank() ? null : printedNumber.trim();
        String body = text == null ? "" : text.trim();
        try (Connection c = Database.getConnection()) {
            if (id == 0) {
                try (PreparedStatement p = c.prepareStatement("INSERT INTO terms_condition(clause_number,printed_number,item_type,clause_title,clause_text,display_order,active,hazardous_materials_reference,created_at,updated_at,global_id,source_installation_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    p.setInt(1, clauseNumber); p.setString(2, printed); p.setString(3, type);
                    p.setString(4, title.trim()); p.setString(5, body); p.setInt(6, Math.max(1, displayOrder));
                    p.setInt(7, active ? 1 : 0); p.setInt(8, hazardousMaterialsReference ? 1 : 0);
                    p.setString(9, now); p.setString(10, now); p.setString(11, java.util.UUID.randomUUID().toString());
                    p.setString(12, loadInstallationId(c)); p.executeUpdate();
                    try (ResultSet r = p.getGeneratedKeys()) { if (!r.next()) throw new SQLException("Unable to determine item ID."); id = r.getLong(1); }
                }
            } else {
                try (PreparedStatement p = c.prepareStatement("UPDATE terms_condition SET clause_number=?,printed_number=?,item_type=?,clause_title=?,clause_text=?,display_order=?,active=?,hazardous_materials_reference=?,updated_at=? WHERE id=?")) {
                    p.setInt(1, clauseNumber); p.setString(2, printed); p.setString(3, type); p.setString(4, title.trim());
                    p.setString(5, body); p.setInt(6, Math.max(1, displayOrder)); p.setInt(7, active ? 1 : 0);
                    p.setInt(8, hazardousMaterialsReference ? 1 : 0); p.setString(9, now); p.setLong(10, id);
                    if (p.executeUpdate() == 0) throw new IllegalArgumentException("The selected item no longer exists.");
                }
            }
            normalizeOrdering(c);
            AuditLogService.log(id == 0 ? "CREATE" : "UPDATE", "TERMS_CONDITION", id, "Terms & Conditions item saved");
            WaspLogger.info("Terms & Conditions item saved. itemId=" + id);
            return findById(id);
        } catch (SQLException e) { WaspLogger.error("Unable to save Terms & Conditions item", e); throw new IllegalStateException("Unable to save Terms & Conditions item", e); }
    }

    public static Clause findById(long id) {
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement("SELECT id,clause_number,printed_number,item_type,clause_title,clause_text,display_order,active,hazardous_materials_reference FROM terms_condition WHERE id=?")) {
            p.setLong(1, id);
            try (ResultSet r = p.executeQuery()) { return r.next() ? read(r) : null; }
        } catch (SQLException e) { WaspLogger.error("Unable to load Terms & Conditions clause", e); throw new IllegalStateException("Unable to load Terms & Conditions clause", e); }
    }

    public static void delete(long id) {
        if (id <= 0) throw new IllegalArgumentException("Valid clause is required.");
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement("DELETE FROM terms_condition WHERE id=?")) {
            p.setLong(1, id);
            if (p.executeUpdate() == 0) throw new IllegalArgumentException("The selected clause no longer exists.");
            normalizeOrdering(c);
            AuditLogService.log("DELETE", "TERMS_CONDITION", id, "Terms & Conditions clause deleted");
            WaspLogger.info("Terms & Conditions clause deleted. clauseId=" + id);
        } catch (SQLException e) { WaspLogger.error("Unable to delete Terms & Conditions clause", e); throw new IllegalStateException("Unable to delete Terms & Conditions clause", e); }
    }

    public static void move(long id, boolean up) {
        List<Clause> clauses = findAll();
        int index = -1;
        for (int i=0;i<clauses.size();i++) if (clauses.get(i).id() == id) { index=i; break; }
        int target = up ? index-1 : index+1;
        if (index < 0 || target < 0 || target >= clauses.size()) return;
        Clause a=clauses.get(index), b=clauses.get(target);
        try (Connection c=Database.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement p=c.prepareStatement("UPDATE terms_condition SET display_order=?,updated_at=? WHERE id=?")) {
                String now=LocalDateTime.now().toString();
                p.setInt(1,b.displayOrder()); p.setString(2,now); p.setLong(3,a.id()); p.addBatch();
                p.setInt(1,a.displayOrder()); p.setString(2,now); p.setLong(3,b.id()); p.addBatch(); p.executeBatch();
                normalizeOrdering(c);
                c.commit();
            } catch (RuntimeException|SQLException e) { WaspLogger.error("Operation failed in TermsConditionService", e); c.rollback(); throw e; } finally { c.setAutoCommit(true); }
            AuditLogService.log("REORDER", "TERMS_CONDITION", id, "Terms & Conditions clause reordered");
        } catch (SQLException e) { WaspLogger.error("Operation failed in TermsConditionService", e); throw new IllegalStateException("Unable to reorder Terms & Conditions",e); }
    }

    private static void normalizeOrdering(Connection c) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement("SELECT id FROM terms_condition ORDER BY display_order,id"); ResultSet r = p.executeQuery()) {
            while (r.next()) ids.add(r.getLong(1));
        }
        try (PreparedStatement p = c.prepareStatement("UPDATE terms_condition SET display_order=?, clause_number=?, updated_at=? WHERE id=?")) {
            String now = LocalDateTime.now().toString();
            for (int i = 0; i < ids.size(); i++) {
                int number = i + 1;
                p.setInt(1, number);
                p.setInt(2, number);
                p.setString(3, now);
                p.setLong(4, ids.get(i));
                p.addBatch();
            }
            p.executeBatch();
        }
    }

    public static List<Clause> findHazardousMaterialReferences() { return find(true, true); }

    private static List<Clause> find(boolean activeOnly, boolean hazardousOnly) {
        String sql = "SELECT id,clause_number,printed_number,item_type,clause_title,clause_text,display_order,active,hazardous_materials_reference FROM terms_condition "
                + (activeOnly ? "WHERE active=1 " : "")
                + (hazardousOnly ? (activeOnly ? "AND " : "WHERE ") + "hazardous_materials_reference=1 " : "")
                + "ORDER BY display_order,id";
        List<Clause> result = new ArrayList<>();
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql); ResultSet r = p.executeQuery()) {
            while (r.next()) result.add(read(r));
            return result;
        } catch (SQLException e) { WaspLogger.error("Unable to load hazardous-material Terms & Conditions references", e); throw new IllegalStateException("Unable to load hazardous-material Terms & Conditions references", e); }
    }

    private static Clause read(ResultSet r) throws SQLException { return new Clause(r.getLong(1), r.getInt(2), r.getString(3), r.getString(4), r.getString(5), r.getString(6), r.getInt(7), r.getInt(8)==1, r.getInt(9)==1); }
    private static void validate(String itemType, String title, String text, String printedNumber, boolean hazardousReference) {
        if (itemType == null || !java.util.Set.of("CLAUSE","HEADING","GROUP_HEADING").contains(itemType.trim().toUpperCase(java.util.Locale.ROOT)))
            throw new IllegalArgumentException("Choose a valid Terms & Conditions item type.");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Title or heading text is required.");
        if (title.length() > 300) throw new IllegalArgumentException("Title cannot exceed 300 characters.");
        if (text != null && text.length() > 3000) throw new IllegalArgumentException("Clause text cannot exceed 3000 characters.");
        if (printedNumber != null && printedNumber.length() > 50) throw new IllegalArgumentException("Printed number cannot exceed 50 characters.");
        String type = itemType.trim().toUpperCase(java.util.Locale.ROOT);
        if ("CLAUSE".equals(type) && (text == null || text.isBlank())) throw new IllegalArgumentException("Clause text is required for a numbered clause.");
        if (!"CLAUSE".equals(type) && hazardousReference) throw new IllegalArgumentException("Only clauses can be referenced by the Hazardous Materials declaration.");
        if ("GROUP_HEADING".equals(type) && text != null && !text.isBlank()) throw new IllegalArgumentException("Group headings do not have body text.");
    }
    private static String loadInstallationId(Connection c) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT setting_value FROM application_settings WHERE setting_key='data_exchange.installation_id'")) {
            try (ResultSet r = p.executeQuery()) {
                if (!r.next() || r.getString(1) == null || r.getString(1).isBlank()) {
                    throw new SQLException("W.A.S.P. Data Exchange installation ID is not initialized.");
                }
                return r.getString(1);
            }
        }
    }

}
