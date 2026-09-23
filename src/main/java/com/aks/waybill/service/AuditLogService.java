package com.aks.waybill.service;

import com.aks.waybill.db.Database;
import com.aks.waybill.security.SessionContext;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class AuditLogService {
    private AuditLogService() {}
    public record AuditRecord(long id, String userName, String action, String entityType, Long entityId, String entityLabel, String details, LocalDateTime createdAt) {}
    public static void log(String action, String entityType, Long entityId, String details) {
        String entityLabel = resolveEntityLabel(entityType, entityId);
        logWithEntityLabel(action, entityType, entityId, entityLabel, details);
    }

    /** Writes an audit entry with a supplied entity label. Useful when the entity is about to be deleted. */
    public static void logWithEntityLabel(String action, String entityType, Long entityId, String entityLabel, String details) {
        Long userId = null;
        try { if (SessionContext.getCurrentUser() != null) userId = SessionContext.requireUserId(); } catch (RuntimeException ignored) {}
        try (Connection c=Database.getConnection(); PreparedStatement p=c.prepareStatement("INSERT INTO audit_log(user_id,action,entity_type,entity_id,entity_label,details,created_at) VALUES(?,?,?,?,?,?,?)")) {
            if(userId==null)p.setNull(1,Types.INTEGER);else p.setLong(1,userId);
            p.setString(2,action); p.setString(3,entityType);
            if(entityId==null)p.setNull(4,Types.INTEGER);else p.setLong(4,entityId);
            if(entityLabel==null||entityLabel.isBlank())p.setNull(5,Types.VARCHAR);else p.setString(5,entityLabel);
            p.setString(6,details); p.setString(7,LocalDateTime.now().toString()); p.executeUpdate();
        } catch(SQLException e){throw new IllegalStateException("Unable to write audit log",e);}
    }

    private static String resolveEntityLabel(String entityType, Long entityId) {
        if (entityId == null || entityType == null || !"WAYBILL".equalsIgnoreCase(entityType)) return null;
        try (Connection c=Database.getConnection(); PreparedStatement p=c.prepareStatement("SELECT waybill_number FROM waybill WHERE id=?")) {
            p.setLong(1, entityId);
            try(ResultSet r=p.executeQuery()){ return r.next() ? r.getString(1) : null; }
        } catch(SQLException ignored) {
            return null;
        }
    }
    public static Page findPage(String search,int page,int pageSize){
        int size=Math.max(1,Math.min(100,pageSize)), requested=Math.max(0,page); String term=search==null?"":search.trim();
        String where=term.isBlank()?"":" WHERE COALESCE(u.username,'') LIKE ? COLLATE NOCASE OR a.action LIKE ? COLLATE NOCASE OR COALESCE(a.entity_type,'') LIKE ? COLLATE NOCASE OR COALESCE(a.entity_label,'') LIKE ? COLLATE NOCASE OR COALESCE(w.waybill_number,'') LIKE ? COLLATE NOCASE OR COALESCE(a.details,'') LIKE ? COLLATE NOCASE "; String like="%"+term+"%";
        try(Connection c=Database.getConnection()){
            long total; try(PreparedStatement p=c.prepareStatement("SELECT COUNT(*) FROM audit_log a LEFT JOIN app_user u ON u.id=a.user_id LEFT JOIN waybill w ON a.entity_type='WAYBILL' AND w.id=a.entity_id"+where)){if(!term.isBlank()){for(int i=1;i<=6;i++)p.setString(i,like);}try(ResultSet r=p.executeQuery()){r.next();total=r.getLong(1);}}
            int pages=(int)Math.max(1,(total+size-1)/size), normalized=Math.min(requested,pages-1); List<AuditRecord> rows=new ArrayList<>();
            String sql="SELECT a.id,COALESCE(u.username,'System'),a.action,COALESCE(a.entity_type,''),a.entity_id,COALESCE(a.entity_label,w.waybill_number,''),COALESCE(a.details,''),a.created_at FROM audit_log a LEFT JOIN app_user u ON u.id=a.user_id LEFT JOIN waybill w ON a.entity_type='WAYBILL' AND w.id=a.entity_id"+where+" ORDER BY a.id DESC LIMIT ? OFFSET ?";
            try(PreparedStatement p=c.prepareStatement(sql)){int i=1;if(!term.isBlank()){for(int n=0;n<6;n++)p.setString(i++,like);}p.setInt(i++,size);p.setInt(i,normalized*size);try(ResultSet r=p.executeQuery()){while(r.next())rows.add(new AuditRecord(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),readLong(r, 5), r.getString(6), r.getString(7), parseDateTime(r.getString(8))));}}
            return new Page(rows,normalized,size,total);
        }catch(SQLException e){throw new IllegalStateException("Unable to load audit log",e);}
    }
    private static Long readLong(ResultSet r, int column) throws SQLException {
        long value = r.getLong(column);
        return r.wasNull() ? null : value;
    }

    private static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return LocalDateTime.MIN;
        String text = value.trim();
        try { return LocalDateTime.parse(text); } catch (Exception ignored) {}
        try { return java.time.OffsetDateTime.parse(text).toLocalDateTime(); } catch (Exception ignored) {}
        try { return java.time.Instant.parse(text).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime(); } catch (Exception ignored) {}
        return LocalDateTime.MIN;
    }

    public record Page(List<AuditRecord> rows,int page,int pageSize,long totalRows){public int totalPages(){return(int)Math.max(1,(totalRows+pageSize-1)/pageSize);}}
}
