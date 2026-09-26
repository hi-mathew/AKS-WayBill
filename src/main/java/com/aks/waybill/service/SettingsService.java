package com.aks.waybill.service;

import com.aks.waybill.logging.WaspLogger;

import com.aks.waybill.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Handles application-level settings for the fixed part and running sequence of waybill numbers. */
public final class SettingsService {
    public static final String WAYBILL_PART_1 = "waybill.part1";
    public static final String WAYBILL_NEXT_SEQUENCE = "waybill.next_sequence";
    public static final String LIST_PAGE_SIZE = "application.list_page_size";
    public static final String BACKUP_RETENTION_ENABLED = "backup.retention.enabled";
    public static final String BACKUP_RETENTION_COUNT = "backup.retention.count";

    private static final String DEFAULT_PART_1 = "AKS";
    private static final long DEFAULT_NEXT_SEQUENCE = 1001L;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final boolean DEFAULT_BACKUP_RETENTION_ENABLED = false;
    private static final int DEFAULT_BACKUP_RETENTION_COUNT = 10;

    private SettingsService() {}

    public static WaybillNumberSettings getWaybillNumberSettings() {
        try (Connection c = Database.getConnection()) {
            ensureDefault(c, WAYBILL_PART_1, DEFAULT_PART_1);
            ensureDefault(c, WAYBILL_NEXT_SEQUENCE, String.valueOf(DEFAULT_NEXT_SEQUENCE));
            return new WaybillNumberSettings(getValue(c, WAYBILL_PART_1), Long.parseLong(getValue(c, WAYBILL_NEXT_SEQUENCE)));
        } catch (Exception e) { WaspLogger.error("Unable to load waybill number settings", e); throw new IllegalStateException("Unable to load waybill number settings", e); }
    }

    public static void saveWaybillNumberSettings(String part1, long nextSequence) {
        String p1 = normalizePart(part1);
        if (p1.isBlank()) throw new IllegalArgumentException("Waybill Part 1 is required.");
        if (!p1.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Waybill Part 1 may contain only letters, numbers, hyphens and underscores.");
        if (p1.length() > 20) throw new IllegalArgumentException("Waybill Part 1 cannot be longer than 20 characters.");
        if (nextSequence < 1) throw new IllegalArgumentException("Next sequence number must be greater than zero.");
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try { upsert(c, WAYBILL_PART_1, p1); upsert(c, WAYBILL_NEXT_SEQUENCE, String.valueOf(nextSequence)); c.commit(); }
            catch (Exception e) { WaspLogger.error("Operation failed in SettingsService", e); c.rollback(); throw e; }
            finally { c.setAutoCommit(true); }
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { WaspLogger.error("Operation failed in SettingsService", e); throw new IllegalStateException("Unable to save waybill number settings", e); }
    }


    public static int getPageSize() {
        try (Connection c = Database.getConnection()) {
            ensureDefault(c, LIST_PAGE_SIZE, String.valueOf(DEFAULT_PAGE_SIZE));
            return Integer.parseInt(getValue(c, LIST_PAGE_SIZE));
        } catch (Exception e) { WaspLogger.error("Unable to load pagination setting", e); throw new IllegalStateException("Unable to load pagination setting", e); }
    }

    public static boolean isBackupRetentionEnabled() {
        try (Connection c = Database.getConnection()) {
            ensureDefault(c, BACKUP_RETENTION_ENABLED, String.valueOf(DEFAULT_BACKUP_RETENTION_ENABLED));
            return Boolean.parseBoolean(getValue(c, BACKUP_RETENTION_ENABLED));
        } catch (Exception e) { WaspLogger.error("Unable to load backup retention setting", e); throw new IllegalStateException("Unable to load backup retention setting", e); }
    }

    public static int getBackupRetentionCount() {
        try (Connection c = Database.getConnection()) {
            ensureDefault(c, BACKUP_RETENTION_COUNT, String.valueOf(DEFAULT_BACKUP_RETENTION_COUNT));
            return Integer.parseInt(getValue(c, BACKUP_RETENTION_COUNT));
        } catch (Exception e) { WaspLogger.error("Unable to load backup retention count", e); throw new IllegalStateException("Unable to load backup retention count", e); }
    }

    public static void saveBackupRetention(boolean enabled, int keepCount) {
        if (keepCount != 5 && keepCount != 10 && keepCount != 20 && keepCount != 50 && keepCount != 100) {
            throw new IllegalArgumentException("Backup retention count must be 5, 10, 20, 50 or 100.");
        }
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                upsert(c, BACKUP_RETENTION_ENABLED, String.valueOf(enabled));
                upsert(c, BACKUP_RETENTION_COUNT, String.valueOf(keepCount));
                c.commit();
            } catch (Exception e) { WaspLogger.error("Operation failed in SettingsService", e);
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (Exception e) { WaspLogger.error("Unable to save backup retention settings", e); throw new IllegalStateException("Unable to save backup retention settings", e); }
    }

    public static void savePageSize(int pageSize) {
        if (pageSize != 10 && pageSize != 20 && pageSize != 50 && pageSize != 100) {
            throw new IllegalArgumentException("Page size must be 10, 20, 50 or 100.");
        }
        try (Connection c = Database.getConnection()) { upsert(c, LIST_PAGE_SIZE, String.valueOf(pageSize)); }
        catch (Exception e) { WaspLogger.error("Operation failed in SettingsService", e); throw new IllegalStateException("Unable to save pagination setting", e); }
    }

    private static String normalizePart(String v) { return v == null ? "" : v.trim().toUpperCase(); }
    private static void ensureDefault(Connection c,String k,String v)throws Exception{try(PreparedStatement p=c.prepareStatement("INSERT OR IGNORE INTO application_settings(setting_key,setting_value) VALUES(?,?)")){p.setString(1,k);p.setString(2,v);p.executeUpdate();}}
    private static String getValue(Connection c,String k)throws Exception{try(PreparedStatement p=c.prepareStatement("SELECT setting_value FROM application_settings WHERE setting_key=?")){p.setString(1,k);try(ResultSet r=p.executeQuery()){if(!r.next()||r.getString(1)==null)throw new Exception("Missing setting: "+k);return r.getString(1);}}}
    private static void upsert(Connection c,String k,String v)throws Exception{try(PreparedStatement p=c.prepareStatement("INSERT INTO application_settings(setting_key,setting_value) VALUES(?,?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value")){p.setString(1,k);p.setString(2,v);p.executeUpdate();}}

    public record WaybillNumberSettings(String part1, long nextSequence) {}
}
