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

    public record Clause(long id, int clauseNumber, String title, String text, int displayOrder, boolean active) {}

    public static List<Clause> findAll() {
        return find(false);
    }

    public static List<Clause> findActive() {
        return find(true);
    }

    private static List<Clause> find(boolean activeOnly) {
        String sql = "SELECT id,clause_number,clause_title,clause_text,display_order,active FROM terms_condition "
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
        validate(title, text);
        int nextOrder = findAll().stream().mapToInt(Clause::displayOrder).max().orElse(0) + 1;
        int nextNumber = findAll().stream().mapToInt(Clause::clauseNumber).max().orElse(0) + 1;
        return save(0, nextNumber, title, text, nextOrder, active);
    }

    public static Clause update(long id, String title, String text, int displayOrder, boolean active) {
        if (id <= 0) throw new IllegalArgumentException("Valid clause is required.");
        Clause existing = findById(id);
        if (existing == null) throw new IllegalArgumentException("The selected clause no longer exists.");
        return save(id, existing.clauseNumber(), title, text, displayOrder, active);
    }

    private static Clause save(long id, int clauseNumber, String title, String text, int displayOrder, boolean active) {
        validate(title, text);
        String now = LocalDateTime.now().toString();
        try (Connection c = Database.getConnection()) {
            if (id == 0) {
                try (PreparedStatement p = c.prepareStatement("INSERT INTO terms_condition(clause_number,clause_title,clause_text,display_order,active,created_at,updated_at) VALUES(?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    p.setInt(1, clauseNumber); p.setString(2, title.trim()); p.setString(3, text.trim()); p.setInt(4, Math.max(1, displayOrder)); p.setInt(5, active ? 1 : 0); p.setString(6, now); p.setString(7, now); p.executeUpdate();
                    try (ResultSet r = p.getGeneratedKeys()) { if (!r.next()) throw new SQLException("Unable to determine clause ID."); id = r.getLong(1); }
                }
            } else {
                try (PreparedStatement p = c.prepareStatement("UPDATE terms_condition SET clause_number=?,clause_title=?,clause_text=?,display_order=?,active=?,updated_at=? WHERE id=?")) {
                    p.setInt(1, clauseNumber); p.setString(2, title.trim()); p.setString(3, text.trim()); p.setInt(4, Math.max(1, displayOrder)); p.setInt(5, active ? 1 : 0); p.setString(6, now); p.setLong(7, id);
                    if (p.executeUpdate() == 0) throw new IllegalArgumentException("The selected clause no longer exists.");
                }
            }
            normalizeOrdering(c);
            AuditLogService.log(id == 0 ? "CREATE" : "UPDATE", "TERMS_CONDITION", id, "Terms & Conditions clause saved");
            WaspLogger.info("Terms & Conditions clause saved. clauseId=" + id);
            return findById(id);
        } catch (SQLException e) { WaspLogger.error("Unable to save Terms & Conditions clause", e); throw new IllegalStateException("Unable to save Terms & Conditions clause", e); }
    }

    public static Clause findById(long id) {
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement("SELECT id,clause_number,clause_title,clause_text,display_order,active FROM terms_condition WHERE id=?")) {
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

    private static Clause read(ResultSet r) throws SQLException { return new Clause(r.getLong(1),r.getInt(2),r.getString(3),r.getString(4),r.getInt(5),r.getInt(6)==1); }
    private static void validate(String title,String text){if(title==null||title.isBlank())throw new IllegalArgumentException("Clause title is required.");if(text==null||text.isBlank())throw new IllegalArgumentException("Clause text is required.");if(title.length()>300)throw new IllegalArgumentException("Clause title cannot exceed 300 characters.");if(text.length()>3000)throw new IllegalArgumentException("Clause text cannot exceed 3000 characters.");}
}
