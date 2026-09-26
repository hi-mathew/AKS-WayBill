package com.aks.waybill.service;

import com.aks.waybill.config.AppPaths;
import com.aks.waybill.db.Database;
import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class BackupService {
    private static final DateTimeFormatter STAMP=DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private BackupService(){}
    public static Path backupTo(Path target){
        if(target==null)throw new IllegalArgumentException("Backup file is required.");
        try{Path d=target.toAbsolutePath().normalize();Files.createDirectories(AppPaths.backupDirectory());if(d.getParent()!=null)Files.createDirectories(d.getParent());if(d.equals(AppPaths.databaseFile().toAbsolutePath().normalize()))throw new IllegalArgumentException("The backup file cannot be the active database file.");if(Files.exists(d))Files.delete(d);String escaped=d.toString().replace("'","''");try(Connection c=Database.getConnection();Statement s=c.createStatement()){s.execute("VACUUM INTO '"+escaped+"'");}AuditLogService.log("BACKUP","DATABASE",null,"Database backup created: "+d.getFileName());return d;}catch(Exception e){throw new IllegalStateException("Unable to create database backup: "+e.getMessage(),e);}
    }
    public static Path defaultBackupPath(){
        Path dir=AppPaths.backupDirectory();
        String base="backup-"+LocalDateTime.now().format(STAMP);
        Path candidate=dir.resolve(base+".db");
        int suffix=2;
        while(Files.exists(candidate)) candidate=dir.resolve(base+"-"+(suffix++)+".db");
        return candidate;
    }
    public static List<BackupInfo> listBackups(){
        Path dir=AppPaths.backupDirectory();
        try{
            if(!Files.isDirectory(dir)) return List.of();
            return Files.list(dir)
                    .filter(Files::isRegularFile)
                    .filter(p->{String n=p.getFileName().toString().toLowerCase(); return n.endsWith(".db") && (n.startsWith("backup-") || n.startsWith("before-restore-"));})
                    .map(BackupService::toInfo)
                    .sorted(Comparator.comparing(BackupInfo::modified).reversed())
                    .collect(Collectors.toList());
        }catch(IOException e){throw new IllegalStateException("Unable to read the backup folder: "+e.getMessage(),e);}
    }
    public static void deleteBackup(Path file){
        if(file==null) throw new IllegalArgumentException("Select a backup first.");
        Path target=file.toAbsolutePath().normalize();
        Path dir=AppPaths.backupDirectory().toAbsolutePath().normalize();
        if(!target.getParent().equals(dir)) throw new IllegalArgumentException("Only backups in the W.A.S.P backup folder can be deleted.");
        String name=target.getFileName().toString().toLowerCase();
        if(!(name.startsWith("backup-") || name.startsWith("before-restore-")) || !name.endsWith(".db")) throw new IllegalArgumentException("Invalid backup file.");
        try{Files.deleteIfExists(target);AuditLogService.log("DELETE_BACKUP","DATABASE",null,"Backup deleted: "+target.getFileName());}
        catch(IOException e){throw new IllegalStateException("Unable to delete the backup: "+e.getMessage(),e);}
    }
    private static BackupInfo toInfo(Path p){
        try{
            String n=p.getFileName().toString();
            String type=n.startsWith("before-restore-")?"Safety Copy":"Manual Backup";
            return new BackupInfo(p,type,Files.size(p),Files.getLastModifiedTime(p).toInstant(),findPerformedBy(n));
        }catch(IOException e){throw new IllegalStateException("Unable to read backup metadata: "+e.getMessage(),e);}
    }
    public record BackupInfo(Path path,String type,long size,java.time.Instant modified,String performedBy){}

    private static String findPerformedBy(String fileName) {
        String backupPattern = "%" + fileName.replace("%", "\\%").replace("_", "\\_") + "%";
        String sql = "SELECT COALESCE(u.username,'System') FROM audit_log a LEFT JOIN app_user u ON u.id=a.user_id "
                + "WHERE ((a.action='BACKUP' AND a.details LIKE ? ESCAPE '\\') "
                + "OR (a.action='RESTORE' AND a.details LIKE ? ESCAPE '\\')) "
                + "ORDER BY a.id DESC LIMIT 1";
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, "Database backup created: " + backupPattern);
            p.setString(2, "%Safety copy created: " + backupPattern);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? r.getString(1) : "Not recorded";
            }
        } catch (SQLException e) {
            return "Not recorded";
        }
    }
    public static void restoreFrom(Path source){
        if(source==null||!Files.isRegularFile(source))throw new IllegalArgumentException("A valid backup file is required."); Path temp=AppPaths.backupDirectory().resolve("waybill-restore-"+System.nanoTime()+".db");Path current=AppPaths.databaseFile();
        try{Files.createDirectories(AppPaths.backupDirectory());Files.copy(source,temp,StandardCopyOption.REPLACE_EXISTING);validateDatabase(temp);Path safety=AppPaths.backupDirectory().resolve("before-restore-"+LocalDateTime.now().format(STAMP)+".db");if(Files.exists(current))Files.copy(current,safety,StandardCopyOption.REPLACE_EXISTING);Files.move(temp,current,StandardCopyOption.REPLACE_EXISTING);Files.deleteIfExists(Path.of(current+"-wal"));Files.deleteIfExists(Path.of(current+"-shm"));AuditLogService.log("RESTORE","DATABASE",null,"Database restored from: "+source.getFileName()+"; Safety copy created: "+safety.getFileName());}catch(Exception e){try{Files.deleteIfExists(temp);}catch(IOException ignored){}throw new IllegalStateException("Unable to restore database: "+e.getMessage(),e);}
    }
    private static void validateDatabase(Path db)throws Exception{try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+db);Statement s=c.createStatement();ResultSet r=s.executeQuery("PRAGMA integrity_check")){if(!r.next()||!"ok".equalsIgnoreCase(r.getString(1)))throw new IllegalArgumentException("The selected file is not a valid SQLite database.");}}
}
