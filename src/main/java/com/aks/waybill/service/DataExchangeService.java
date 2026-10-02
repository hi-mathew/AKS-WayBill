package com.aks.waybill.service;

import com.aks.waybill.db.Database;
import com.aks.waybill.logging.WaspLogger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Creates controlled W.A.S.P. Data Exchange packages. A package is not a copy of
 * waybill.db: it contains a manifest and CSV snapshots that can later be validated
 * and imported by the central consolidator.
 */
public final class DataExchangeService {
    public enum ExportType { FULL, INCREMENTAL }

    public record ExportHistory(String exportId, ExportType type, String startedAt, String completedAt,
                                long recordCount, String status, String packagePath, String packageHash) {}

    public record ExportResult(String exportId, ExportType type, Path packagePath, long recordCount,
                               String packageHash, String fromTimestamp, String toTimestamp) {}

    private record TableSpec(String table, String fileName, String timestampColumn, String extraWhere) {}

    private static final String APP_VERSION = "2.0.0";
    private static final String SCHEMA_VERSION = "2.0.1";
    private static final String NULL_MARKER = "\\N";
    private static final List<TableSpec> TABLES = List.of(
            new TableSpec("app_user", "app_user.csv", "updated_at", ""),
            new TableSpec("shipper_company", "shipper_company.csv", "updated_at", ""),
            new TableSpec("consignee_company", "consignee_company.csv", "updated_at", ""),
            new TableSpec("saved_carrier", "saved_carrier.csv", "updated_at", ""),
            new TableSpec("saved_location", "saved_location.csv", "updated_at", ""),
            new TableSpec("waybill", "waybill.csv", "updated_at", ""),
            new TableSpec("waybill_item", "waybill_item.csv", null, "JOIN waybill w ON w.id = waybill_item.waybill_id"),
            new TableSpec("audit_log", "audit_log.csv", "created_at", ""),
            new TableSpec("terms_condition", "terms_condition.csv", "updated_at", "")
    );

    private DataExchangeService() {}

    public static boolean hasSuccessfulFullExport() {
        try (Connection c = Database.getConnection();
             PreparedStatement p = c.prepareStatement("SELECT 1 FROM data_exchange_export WHERE export_type='FULL' AND status='COMPLETED' LIMIT 1")) {
            try (ResultSet r = p.executeQuery()) { return r.next(); }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to check Data Exchange export history", e);
        }
    }

    public static List<ExportHistory> history() {
        List<ExportHistory> rows = new ArrayList<>();
        String sql = "SELECT export_id, export_type, started_at, completed_at, record_count, status, package_path, package_hash " +
                "FROM data_exchange_export ORDER BY id DESC";
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql); ResultSet r = p.executeQuery()) {
            while (r.next()) {
                ExportType type;
                try { type = ExportType.valueOf(r.getString(2)); } catch (Exception e) { type = ExportType.FULL; }
                rows.add(new ExportHistory(r.getString(1), type, r.getString(3), r.getString(4), r.getLong(5),
                        r.getString(6), r.getString(7), r.getString(8)));
            }
            return rows;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load Data Exchange export history", e);
        }
    }

    public static ExportResult export(ExportType type, Path destination) {
        if (type == null) throw new IllegalArgumentException("Export type is required.");
        if (destination == null) throw new IllegalArgumentException("Export destination is required.");
        if (type == ExportType.INCREMENTAL && !hasSuccessfulFullExport()) {
            throw new IllegalStateException("An incremental export is available only after the first successful Full Export.");
        }

        String exportId = UUID.randomUUID().toString();
        String startedAt = Instant.now().toString();
        String fromTimestamp = type == ExportType.INCREMENTAL ? lastSuccessfulExportTimestamp() : null;
        // Capture the cutoff before reading any business rows. Changes made after this
        // instant are deliberately left for the next export, preventing an in-progress
        // export from silently missing changes that happen during package creation.
        String toTimestamp = Instant.now().toString();
        long recordCount = 0;
        Path temp = null;

        try {
            Files.createDirectories(destination.toAbsolutePath().getParent());
            String baseName = "WASP-DataExchange-" + type.name() + "-" +
                    DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(java.time.ZoneId.systemDefault()).format(Instant.now()) +
                    "-" + exportId.substring(0, 8) + ".waspexport.zip";
            Path target = destination.resolve(baseName);
            temp = destination.resolve("." + baseName + ".tmp");

            insertExportStart(exportId, type, startedAt, fromTimestamp, toTimestamp);

            Map<String, Long> counts = new LinkedHashMap<>();
            try (OutputStream fileOut = Files.newOutputStream(temp);
                 ZipOutputStream zip = new ZipOutputStream(fileOut, StandardCharsets.UTF_8)) {
                for (TableSpec spec : TABLES) {
                    long count = writeTable(zip, spec, fromTimestamp, toTimestamp, type);
                    counts.put(spec.table(), count);
                    recordCount += count;
                }
                writeManifest(zip, exportId, type, startedAt, fromTimestamp, toTimestamp, counts, recordCount);
            }

            String hash = sha256(temp);
            Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            String completedAt = Instant.now().toString();
            markExportCompleted(exportId, completedAt, recordCount, target.toAbsolutePath().toString(), hash);
            updateInstallationLastExport(completedAt);
            WaspLogger.info("Data Exchange export completed. exportId=" + exportId + ", type=" + type + ", records=" + recordCount);
            return new ExportResult(exportId, type, target, recordCount, hash, fromTimestamp, toTimestamp);
        } catch (Exception e) {
            if (temp != null) {
                try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            }
            markExportFailedQuietly(exportId, e.getMessage());
            WaspLogger.error("Data Exchange export failed. exportId=" + exportId, e);
            throw new IllegalStateException("Data Exchange export failed: " + safeMessage(e), e);
        }
    }

    private static long writeTable(ZipOutputStream zip, TableSpec spec, String from, String to, ExportType type) throws SQLException, IOException {
        zip.putNextEntry(new ZipEntry("data/" + spec.fileName()));
        long count = 0;
        String sql = selectSql(spec, from, to, type);
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            int idx = 1;
            if (type == ExportType.INCREMENTAL) {
                // Database timestamps are stored as local ISO_LOCAL_DATE_TIME values,
                // while Data Exchange checkpoints are stored as UTC Instants. Convert
                // the checkpoint window to the same local representation before
                // comparing it with the SQLite timestamp columns.
                p.setString(idx++, checkpointToLocalTimestamp(from));
                p.setString(idx, checkpointToLocalTimestamp(to));
            }
            try (ResultSet r = p.executeQuery()) {
                ResultSetMetaData md = r.getMetaData();
                List<String> columns = exportedColumns(spec.table(), md);
                writeCsvRow(zip, columns);
                while (r.next()) {
                    List<String> values = new ArrayList<>(columns.size());
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        if ("password_hash".equalsIgnoreCase(md.getColumnName(i))) continue;
                        values.add(r.getString(i));
                    }
                    writeCsvRow(zip, values);
                    count++;
                }
            }
        } finally {
            zip.closeEntry();
        }
        return count;
    }

    private static String selectSql(TableSpec spec, String from, String to, ExportType type) {
        String select = "SELECT " + ("app_user".equals(spec.table())
                ? "id, global_id, source_installation_id, username, display_name, user_code, role, enabled, created_at, updated_at"
                : spec.table() + ".*") + " FROM " + spec.table() + " " + spec.extraWhere();
        if (type == ExportType.FULL) return select + " ORDER BY " + spec.table() + ".id";
        String condition;
        if ("waybill_item".equals(spec.table())) {
            condition = " WHERE w.updated_at > ? AND w.updated_at <= ?";
        } else {
            condition = " WHERE " + spec.table() + "." + spec.timestampColumn() + " > ? AND " + spec.table() + "." + spec.timestampColumn() + " <= ?";
        }
        // waybill_item has the JOIN in extraWhere, so append WHERE before it is not possible.
        if ("waybill_item".equals(spec.table())) {
            return "SELECT waybill_item.* FROM waybill_item JOIN waybill w ON w.id = waybill_item.waybill_id" + condition + " ORDER BY waybill_item.id";
        }
        return "SELECT " + ("app_user".equals(spec.table())
                ? "id, global_id, source_installation_id, username, display_name, user_code, role, enabled, created_at, updated_at"
                : spec.table() + ".*") + " FROM " + spec.table() + condition + " ORDER BY " + spec.table() + ".id";
    }

    private static String checkpointToLocalTimestamp(String utcTimestamp) {
        try {
            LocalDateTime local = Instant.parse(utcTimestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
            return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(local);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid Data Exchange timestamp: " + utcTimestamp, e);
        }
    }

    private static List<String> exportedColumns(String table, ResultSetMetaData md) throws SQLException {
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= md.getColumnCount(); i++) columns.add(md.getColumnName(i));
        return columns;
    }

    private static void writeCsvRow(OutputStream out, List<String> values) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) line.append(',');
            String value = values.get(i);
            if (value == null) value = NULL_MARKER;
            String escaped = value.replace("\"", "\"\"");
            line.append('"').append(escaped).append('"');
        }
        line.append('\n');
        out.write(line.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void writeManifest(ZipOutputStream zip, String exportId, ExportType type, String startedAt,
                                      String from, String to, Map<String, Long> counts, long total) throws IOException {
        zip.putNextEntry(new ZipEntry("manifest.json"));
        StringBuilder json = new StringBuilder();
        json.append("{\n")
                .append("  \"format\": \"W.A.S.P. Data Exchange\",\n")
                .append("  \"format_version\": \"1.0\",\n")
                .append("  \"application_version\": \"").append(jsonEscape(APP_VERSION)).append("\",\n")
                .append("  \"schema_version\": \"").append(jsonEscape(SCHEMA_VERSION)).append("\",\n")
                .append("  \"installation_id\": \"").append(jsonEscape(Database.getDataExchangeInstallationId())).append("\",\n")
                .append("  \"export_id\": \"").append(jsonEscape(exportId)).append("\",\n")
                .append("  \"export_type\": \"").append(type.name()).append("\",\n")
                .append("  \"started_at\": \"").append(jsonEscape(startedAt)).append("\",\n")
                .append("  \"from_timestamp\": ").append(from == null ? "null" : "\"" + jsonEscape(from) + "\"").append(",\n")
                .append("  \"to_timestamp\": \"").append(jsonEscape(to)).append("\",\n")
                .append("  \"null_marker\": \"").append(jsonEscape(NULL_MARKER)).append("\",\n")
                .append("  \"total_records\": ").append(total).append(",\n")
                .append("  \"tables\": {\n");
        int n = 0;
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            if (n++ > 0) json.append(",\n");
            json.append("    \"").append(jsonEscape(e.getKey())).append("\": ").append(e.getValue());
        }
        json.append("\n  }\n}\n");
        zip.write(json.toString().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String jsonEscape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String lastSuccessfulExportTimestamp() {
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(
                "SELECT to_timestamp FROM data_exchange_export WHERE status='COMPLETED' ORDER BY id DESC LIMIT 1")) {
            try (ResultSet r = p.executeQuery()) {
                if (!r.next() || r.getString(1) == null) throw new IllegalStateException("No successful export is available as the incremental starting point.");
                return r.getString(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to determine the last successful Data Exchange export", e);
        }
    }

    private static void insertExportStart(String exportId, ExportType type, String started, String from, String to) throws SQLException {
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(
                "INSERT INTO data_exchange_export(export_id,export_type,started_at,from_timestamp,to_timestamp,status) VALUES(?,?,?,?,?,'RUNNING')")) {
            p.setString(1, exportId); p.setString(2, type.name()); p.setString(3, started); p.setString(4, from); p.setString(5, to); p.executeUpdate();
        }
    }

    private static void markExportCompleted(String exportId, String completed, long count, String path, String hash) throws SQLException {
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(
                "UPDATE data_exchange_export SET completed_at=?,record_count=?,status='COMPLETED',package_path=?,package_hash=? WHERE export_id=?")) {
            p.setString(1, completed); p.setLong(2, count); p.setString(3, path); p.setString(4, hash); p.setString(5, exportId); p.executeUpdate();
        }
    }

    private static void updateInstallationLastExport(String completedAt) throws SQLException {
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(
                "UPDATE data_exchange_installation SET last_export_at=? WHERE installation_id=?")) {
            p.setString(1, completedAt);
            p.setString(2, Database.getDataExchangeInstallationId());
            p.executeUpdate();
        }
    }

    private static void markExportFailedQuietly(String exportId, String message) {
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(
                "UPDATE data_exchange_export SET completed_at=?,status='FAILED',details=? WHERE export_id=?")) {
            p.setString(1, Instant.now().toString()); p.setString(2, message == null ? "Export failed." : message); p.setString(3, exportId); p.executeUpdate();
        } catch (SQLException ignored) {
            WaspLogger.error("Unable to record Data Exchange export failure. exportId=" + exportId, ignored);
        }
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private static String safeMessage(Throwable e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
}
