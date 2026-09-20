package com.aks.waybill.service;

import com.aks.waybill.config.AppPaths;
import com.aks.waybill.db.Database;
import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class BackupService {
    private static final DateTimeFormatter STAMP=DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private BackupService(){}
    public static Path backupTo(Path target){
        if(target==null)throw new IllegalArgumentException("Backup file is required.");
        try{Path d=target.toAbsolutePath().normalize();Files.createDirectories(AppPaths.backupDirectory());if(d.getParent()!=null)Files.createDirectories(d.getParent());if(d.equals(AppPaths.databaseFile().toAbsolutePath().normalize()))throw new IllegalArgumentException("The backup file cannot be the active database file.");if(Files.exists(d))Files.delete(d);String escaped=d.toString().replace("'","''");try(Connection c=Database.getConnection();Statement s=c.createStatement()){s.execute("VACUUM INTO '"+escaped+"'");}AuditLogService.log("BACKUP","DATABASE",null,"Database backup created: "+d.getFileName());return d;}catch(Exception e){throw new IllegalStateException("Unable to create database backup: "+e.getMessage(),e);}
    }
    public static Path defaultBackupPath(){return AppPaths.backupDirectory().resolve("backup-"+LocalDateTime.now().format(STAMP)+".db");}
    public static void restoreFrom(Path source){
        if(source==null||!Files.isRegularFile(source))throw new IllegalArgumentException("A valid backup file is required."); Path temp=AppPaths.backupDirectory().resolve("waybill-restore-"+System.nanoTime()+".db");Path current=AppPaths.databaseFile();
        try{Files.createDirectories(AppPaths.backupDirectory());Files.copy(source,temp,StandardCopyOption.REPLACE_EXISTING);validateDatabase(temp);Path safety=AppPaths.backupDirectory().resolve("before-restore-"+LocalDateTime.now().format(STAMP)+".db");if(Files.exists(current))Files.copy(current,safety,StandardCopyOption.REPLACE_EXISTING);Files.move(temp,current,StandardCopyOption.REPLACE_EXISTING);Files.deleteIfExists(Path.of(current+"-wal"));Files.deleteIfExists(Path.of(current+"-shm"));AuditLogService.log("RESTORE","DATABASE",null,"Database restored from: "+source.getFileName());}catch(Exception e){try{Files.deleteIfExists(temp);}catch(IOException ignored){}throw new IllegalStateException("Unable to restore database: "+e.getMessage(),e);}
    }
    private static void validateDatabase(Path db)throws Exception{try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+db);Statement s=c.createStatement();ResultSet r=s.executeQuery("PRAGMA integrity_check")){if(!r.next()||!"ok".equalsIgnoreCase(r.getString(1)))throw new IllegalArgumentException("The selected file is not a valid SQLite database.");}}
}
