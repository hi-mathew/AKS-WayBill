package com.aks.waybill.service;

import com.aks.waybill.db.Database;
import com.aks.waybill.security.SessionContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Generates user-specific waybill numbers: Part1/UserCode/Date/Sequence. */
public final class WaybillNumberService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private WaybillNumberService() {}

    public static String preview() { return preview(LocalDate.now(), SessionContext.requireUserCode()); }
    public static String preview(LocalDate date) { return preview(date, SessionContext.requireUserCode()); }
    public static String preview(LocalDate date, String userCode) {
        SettingsService.WaybillNumberSettings s = SettingsService.getWaybillNumberSettings();
        return format(s.part1(), normalizeCode(userCode), date == null ? LocalDate.now() : date, s.nextSequence());
    }

    public static String allocateNext(Connection connection, LocalDate date, Long userId) throws SQLException {
        if (connection == null) throw new IllegalArgumentException("Database connection is required.");
        if (date == null) throw new IllegalArgumentException("Waybill date is required.");
        if (userId == null || userId <= 0) throw new IllegalArgumentException("Authenticated user is required to generate a waybill number.");
        ensureDefaults(connection);
        String userCode = loadUserCode(connection, userId);
        long sequence = allocateSequence(connection);
        SettingsService.WaybillNumberSettings settings = readSettings(connection);
        return format(settings.part1(), userCode, date, sequence);
    }

    private static String loadUserCode(Connection c, long userId) throws SQLException {
        try (PreparedStatement p=c.prepareStatement("SELECT user_code FROM app_user WHERE id=? AND enabled=1")) {
            p.setLong(1,userId); try(ResultSet r=p.executeQuery()) {
                if(!r.next()) throw new SQLException("The logged-in user is not available.");
                String code=normalizeCode(r.getString(1));
                if(code.isBlank()) throw new SQLException("The logged-in user does not have a waybill user code configured.");
                return code;
            }
        }
    }
    private static String normalizeCode(String code){return code==null?"":code.trim().toUpperCase();}
    private static long allocateSequence(Connection c)throws SQLException{
        String sql="UPDATE application_settings SET setting_value=CAST(setting_value AS INTEGER)+1 WHERE setting_key=? AND CAST(setting_value AS INTEGER)>=1 RETURNING CAST(setting_value AS INTEGER)-1";
        try(PreparedStatement p=c.prepareStatement(sql)){p.setString(1,SettingsService.WAYBILL_NEXT_SEQUENCE);try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("Unable to allocate the next waybill sequence number.");return r.getLong(1);}}
    }
    private static SettingsService.WaybillNumberSettings readSettings(Connection c)throws SQLException{return new SettingsService.WaybillNumberSettings(getValue(c,SettingsService.WAYBILL_PART_1),0);}
    private static void ensureDefaults(Connection c)throws SQLException{ensureDefault(c,SettingsService.WAYBILL_PART_1,"AKS");ensureDefault(c,SettingsService.WAYBILL_NEXT_SEQUENCE,"1001");}
    private static void ensureDefault(Connection c,String k,String v)throws SQLException{try(PreparedStatement p=c.prepareStatement("INSERT OR IGNORE INTO application_settings(setting_key,setting_value) VALUES(?,?)")){p.setString(1,k);p.setString(2,v);p.executeUpdate();}}
    private static String getValue(Connection c,String k)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT setting_value FROM application_settings WHERE setting_key=?")){p.setString(1,k);try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("Missing setting: "+k);return r.getString(1);}}}
    private static String format(String p1,String code,LocalDate date,long seq){return p1+"/"+code+"/"+DATE_FORMAT.format(date)+"/"+seq;}
}
