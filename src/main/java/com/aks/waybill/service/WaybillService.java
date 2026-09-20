package com.aks.waybill.service;

import com.aks.waybill.db.Database;
import com.aks.waybill.security.SessionContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Persists waybills and their line items. */
public final class WaybillService {

    private static final DateTimeFormatter DB_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DB_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private WaybillService() {
    }

    public record WaybillItemData(String description, String packageType,
                                  Double quantity, Double weightKg, Double volumeM3) {
    }

    public record CompanyData(String companyName, String contactPerson, String address,
                              String phoneNumber, String emailAddress) {
    }

    public record WaybillData(LocalDate waybillDate, CompanyData shipper, CompanyData consignee,
                              String carrierName, String driverName,
                              String vehicleTrailerNo, String originLoadingPoint,
                              String destinationUnloadingPoint, LocalDate estimatedDeliveryDate,
                              String specialInstructions, boolean hazardousMaterials,
                              String remarks, Long createdBy, List<WaybillItemData> items) {
    }

    public record SavedWaybill(long id, String waybillNumber) {
    }

    public record WaybillListRow(long id, String waybillNumber, LocalDate waybillDate,
                                 String shipperName, String consigneeName, String carrierName) {
    }

    public record WaybillDetails(long id, String waybillNumber, LocalDate waybillDate,
                                 CompanyData shipper, CompanyData consignee,
                                 String carrierName, String driverName, String vehicleTrailerNo,
                                 String originLoadingPoint, String destinationUnloadingPoint,
                                 LocalDate estimatedDeliveryDate, String specialInstructions,
                                 boolean hazardousMaterials, String remarks,
                                 List<WaybillItemData> items) {
    }

    public record WaybillPage(List<WaybillListRow> rows, int page, int pageSize, long totalRows) {
        public int totalPages() {
            return (int) Math.max(1, (totalRows + pageSize - 1) / pageSize);
        }
    }

    public static WaybillPage findPage(String search, LocalDate fromDate, LocalDate toDate, int page, int pageSize) {
        return findPage(search, fromDate, toDate, page, pageSize, null);
    }

    /**
     * Loads waybills according to the logged-in user's role. Administrators see
     * all waybills; normal users see only waybills they created.
     */
    public static WaybillPage findPageForCurrentUser(String search, LocalDate fromDate, LocalDate toDate, int page, int pageSize) {
        Long ownerId = SessionContext.isAdmin() ? null : SessionContext.requireUserId();
        return findPage(search, fromDate, toDate, page, pageSize, ownerId);
    }

    private static WaybillPage findPage(String search, LocalDate fromDate, LocalDate toDate, int page, int pageSize, Long ownerId) {
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        int safePage = Math.max(0, page);
        String term = search == null ? "" : search.trim();
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (ownerId != null) { where.append(" AND w.created_by = ? "); params.add(ownerId); }
        if (!term.isBlank()) {
            where.append(" AND (w.waybill_number LIKE ? COLLATE NOCASE OR COALESCE(sc.company_name, '') LIKE ? COLLATE NOCASE OR COALESCE(cc.company_name, '') LIKE ? COLLATE NOCASE OR COALESCE(w.carrier_name, '') LIKE ? COLLATE NOCASE) ");
            String like = "%" + term + "%";
            Collections.addAll(params, like, like, like, like);
        }
        if (fromDate != null) { where.append(" AND w.waybill_date >= ? "); params.add(DB_DATE.format(fromDate)); }
        if (toDate != null) { where.append(" AND w.waybill_date <= ? "); params.add(DB_DATE.format(toDate)); }

        String base = " FROM waybill w LEFT JOIN company sc ON sc.id = w.shipper_company_id LEFT JOIN company cc ON cc.id = w.consignee_company_id " + where;
        String countSql = "SELECT COUNT(*)" + base;
        String dataSql = "SELECT w.id, w.waybill_number, w.waybill_date, COALESCE(sc.company_name, ''), COALESCE(cc.company_name, ''), COALESCE(w.carrier_name, '')"
                + base + " ORDER BY w.waybill_date DESC, w.id DESC LIMIT ? OFFSET ?";

        try (Connection connection = Database.getConnection()) {
            long total = 0;
            try (PreparedStatement statement = connection.prepareStatement(countSql)) {
                bind(statement, params);
                try (ResultSet rs = statement.executeQuery()) { if (rs.next()) total = rs.getLong(1); }
            }
            List<WaybillListRow> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(dataSql)) {
                int i = bind(statement, params);
                statement.setInt(i++, safePageSize);
                statement.setInt(i, safePage * safePageSize);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new WaybillListRow(rs.getLong(1), rs.getString(2), LocalDate.parse(rs.getString(3), DB_DATE), rs.getString(4), rs.getString(5), rs.getString(6)));
                    }
                }
            }
            int totalPages = (int)Math.max(1, (total + safePageSize - 1) / safePageSize);
            int normalizedPage = Math.min(safePage, totalPages - 1);
            if (normalizedPage != safePage) return findPage(term, fromDate, toDate, normalizedPage, safePageSize, ownerId);
            return new WaybillPage(rows, safePage, safePageSize, total);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load saved waybills", e);
        }
    }

    public static WaybillDetails findById(long id) {
        String sql = "SELECT w.id, w.waybill_number, w.waybill_date, "
                + "sc.company_name, sc.contact_person, sc.address, sc.phone_number, sc.email_address, "
                + "cc.company_name, cc.contact_person, cc.address, cc.phone_number, cc.email_address, "
                + "w.carrier_name, w.driver_name, w.vehicle_trailer_no, w.origin_loading_point, w.destination_unloading_point, "
                + "w.estimated_delivery_date, w.special_instructions, w.hazardous_materials, w.remarks "
                + "FROM waybill w LEFT JOIN company sc ON sc.id=w.shipper_company_id LEFT JOIN company cc ON cc.id=w.consignee_company_id WHERE w.id=?"
                + (SessionContext.isAdmin() ? "" : " AND w.created_by=?");
        try (Connection connection = Database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            if (!SessionContext.isAdmin()) statement.setLong(2, SessionContext.requireUserId());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return null;
                CompanyData shipper = new CompanyData(rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8));
                CompanyData consignee = new CompanyData(rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13));
                List<WaybillItemData> items = new ArrayList<>();
                try (PreparedStatement itemStatement = connection.prepareStatement("SELECT description, package_type, quantity, weight_kg, volume_m3 FROM waybill_item WHERE waybill_id=? ORDER BY item_number")) {
                    itemStatement.setLong(1, id);
                    try (ResultSet itemRs = itemStatement.executeQuery()) {
                        while (itemRs.next()) items.add(new WaybillItemData(itemRs.getString(1), itemRs.getString(2), nullableDouble(itemRs,3), nullableDouble(itemRs,4), nullableDouble(itemRs,5)));
                    }
                }
                return new WaybillDetails(rs.getLong(1), rs.getString(2), LocalDate.parse(rs.getString(3), DB_DATE), shipper, consignee, rs.getString(14), rs.getString(15), rs.getString(16), rs.getString(17), rs.getString(18), parseDate(rs.getString(19)), rs.getString(20), rs.getInt(21) == 1, rs.getString(22), items);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load waybill", e);
        }
    }

    private static void validateData(WaybillData data) {
        if (data == null || data.waybillDate() == null) throw new IllegalArgumentException("Waybill date is required.");
        validateCompany(data.shipper(), "Shipper / Consignor");
        validateCompany(data.consignee(), "Consignee / Receiver");
        length(data.carrierName(), 300, "Carrier Name");
        length(data.driverName(), 200, "Driver Name");
        length(data.vehicleTrailerNo(), 200, "Vehicle / Trailer No.");
        length(data.originLoadingPoint(), 400, "Origin / Loading Point");
        length(data.destinationUnloadingPoint(), 400, "Destination / Unloading Point");
        
        if (data.items() == null || data.items().isEmpty()) throw new IllegalArgumentException("At least one item is required.");
        for (WaybillItemData item : data.items()) {
            if (item == null) throw new IllegalArgumentException("Invalid item.");
            length(item.description(), 600, "Item Description");
            length(item.packageType(), 200, "Package Type");
            nonNegative(item.quantity(), "Quantity");
            nonNegative(item.weightKg(), "Weight");
            nonNegative(item.volumeM3(), "Volume");
        }
    }

    private static void validateCompany(CompanyData c, String prefix) {
        if (c == null || c.companyName() == null || c.companyName().trim().isEmpty()) throw new IllegalArgumentException(prefix + " company name is required.");
        length(c.companyName(), 300, prefix + " company name");
        length(c.contactPerson(), 200, prefix + " contact person");
        length(c.address(), 1000, prefix + " address");
        length(c.phoneNumber(), 100, prefix + " phone number");
        length(c.emailAddress(), 300, prefix + " email address");
    }

    private static void length(String value, int max, String field) {
        if (value != null && value.length() > max) throw new IllegalArgumentException(field + " cannot exceed " + max + " characters.");
    }

    private static void nonNegative(Double value, String field) {
        if (value != null && (!Double.isFinite(value) || value < 0)) throw new IllegalArgumentException(field + " must be a valid non-negative number.");
    }

    private static LocalDate parseDate(String value) { return value == null || value.isBlank() ? null : LocalDate.parse(value, DB_DATE); }
    private static Double nullableDouble(ResultSet rs, int index) throws SQLException { double value=rs.getDouble(index); return rs.wasNull()?null:value; }
    private static int bind(PreparedStatement statement, List<Object> params) throws SQLException { int i=1; for(Object p:params) statement.setObject(i++,p); return i; }

    /**
     * Saves a complete waybill. Number allocation and all inserts are committed
     * in one SQLite transaction so a failed save does not consume the sequence.
     */
    public static SavedWaybill save(WaybillData data) {
        validateData(data);

        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String waybillNumber = WaybillNumberService.allocateNext(connection, data.waybillDate(), data.createdBy());
                LocalDateTime now = LocalDateTime.now();

                long shipperCompanyId = findOrCreateCompany(connection, data.shipper());
                long consigneeCompanyId = findOrCreateCompany(connection, data.consignee());

                long waybillId;
                String sql = "INSERT INTO waybill "
                        + "(waybill_number, waybill_date, shipper_company_id, consignee_company_id, carrier_name, driver_name, vehicle_trailer_no, "
                        + "origin_loading_point, destination_unloading_point, estimated_delivery_date, "
                        + "special_instructions, hazardous_materials, remarks, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    int i = 1;
                    statement.setString(i++, waybillNumber);
                    statement.setString(i++, DB_DATE.format(data.waybillDate()));
                    statement.setLong(i++, shipperCompanyId);
                    statement.setLong(i++, consigneeCompanyId);
                    statement.setString(i++, blankToNull(data.carrierName()));
                    statement.setString(i++, blankToNull(data.driverName()));
                    statement.setString(i++, blankToNull(data.vehicleTrailerNo()));
                    statement.setString(i++, blankToNull(data.originLoadingPoint()));
                    statement.setString(i++, blankToNull(data.destinationUnloadingPoint()));
                    if (data.estimatedDeliveryDate() == null) statement.setNull(i++, java.sql.Types.VARCHAR);
                    else statement.setString(i++, DB_DATE.format(data.estimatedDeliveryDate()));
                    statement.setString(i++, blankToNull(data.specialInstructions()));
                    statement.setInt(i++, data.hazardousMaterials() ? 1 : 0);
                    statement.setString(i++, blankToNull(data.remarks()));
                    if (data.createdBy() == null) statement.setNull(i++, java.sql.Types.INTEGER);
                    else statement.setLong(i++, data.createdBy());
                    statement.setString(i++, DB_DATE_TIME.format(now));
                    statement.setString(i, DB_DATE_TIME.format(now));
                    statement.executeUpdate();

                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Unable to determine the saved waybill ID.");
                        waybillId = keys.getLong(1);
                    }
                }

                insertItems(connection, waybillId, data.items());
                insertAudit(connection, data.createdBy(), waybillId, waybillNumber);

                connection.commit();
                return new SavedWaybill(waybillId, waybillNumber);
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to save waybill", e);
        }
    }


    /** Updates an existing waybill without changing its generated waybill number. */
    public static void update(long waybillId, WaybillData data) {
        if (waybillId <= 0) throw new IllegalArgumentException("Valid waybill data is required.");
        validateData(data);

        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                assertCanAccessWaybill(connection, waybillId);
                long shipperCompanyId = findOrCreateCompany(connection, data.shipper());
                long consigneeCompanyId = findOrCreateCompany(connection, data.consignee());
                LocalDateTime now = LocalDateTime.now();

                String sql = "UPDATE waybill SET waybill_date=?, shipper_company_id=?, consignee_company_id=?, carrier_name=?, driver_name=?, vehicle_trailer_no=?, "
                        + "origin_loading_point=?, destination_unloading_point=?, estimated_delivery_date=?, special_instructions=?, hazardous_materials=?, remarks=?, updated_at=? "
                        + "WHERE id=?";

                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    int i = 1;
                    statement.setString(i++, DB_DATE.format(data.waybillDate()));
                    statement.setLong(i++, shipperCompanyId);
                    statement.setLong(i++, consigneeCompanyId);
                    statement.setString(i++, blankToNull(data.carrierName()));
                    statement.setString(i++, blankToNull(data.driverName()));
                    statement.setString(i++, blankToNull(data.vehicleTrailerNo()));
                    statement.setString(i++, blankToNull(data.originLoadingPoint()));
                    statement.setString(i++, blankToNull(data.destinationUnloadingPoint()));
                    if (data.estimatedDeliveryDate() == null) statement.setNull(i++, java.sql.Types.VARCHAR);
                    else statement.setString(i++, DB_DATE.format(data.estimatedDeliveryDate()));
                    statement.setString(i++, blankToNull(data.specialInstructions()));
                    statement.setInt(i++, data.hazardousMaterials() ? 1 : 0);
                    statement.setString(i++, blankToNull(data.remarks()));
                    statement.setString(i++, DB_DATE_TIME.format(now));
                    statement.setLong(i, waybillId);
                    if (statement.executeUpdate() == 0) throw new IllegalArgumentException("The selected waybill no longer exists.");
                }

                try (PreparedStatement deleteItems = connection.prepareStatement("DELETE FROM waybill_item WHERE waybill_id=?")) {
                    deleteItems.setLong(1, waybillId);
                    deleteItems.executeUpdate();
                }
                insertItems(connection, waybillId, data.items());

                String number = loadWaybillNumber(connection, waybillId);
                insertAudit(connection, data.createdBy(), waybillId, number);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to update waybill", exception);
        }
    }

    private static void assertCanAccessWaybill(Connection connection, long waybillId) throws SQLException {
        if (SessionContext.isAdmin()) return;
        String sql = "SELECT created_by FROM waybill WHERE id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, waybillId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("The selected waybill no longer exists.");
                long ownerId = rs.getLong(1);
                if (rs.wasNull() || ownerId != SessionContext.requireUserId()) {
                    throw new IllegalArgumentException("You do not have permission to modify this waybill.");
                }
            }
        }
    }

    private static String loadWaybillNumber(Connection connection, long waybillId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT waybill_number FROM waybill WHERE id=?")) {
            statement.setLong(1, waybillId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("The selected waybill no longer exists.");
                return rs.getString(1);
            }
        }
    }

    private static long findOrCreateCompany(Connection connection, CompanyData data) throws SQLException {
        if (data == null || isBlank(data.companyName())) {
            throw new IllegalArgumentException("Company name is required.");
        }
        String find = "SELECT id FROM company WHERE company_name = ? COLLATE NOCASE ORDER BY id LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(find)) {
            statement.setString(1, data.companyName().trim());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        String insert = "INSERT INTO company (company_name, contact_person, address, phone_number, email_address, active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 1, ?, ?)";
        String now = DB_DATE_TIME.format(LocalDateTime.now());
        try (PreparedStatement statement = connection.prepareStatement(insert, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, data.companyName().trim());
            statement.setString(2, blankToNull(data.contactPerson()));
            statement.setString(3, blankToNull(data.address()));
            statement.setString(4, blankToNull(data.phoneNumber()));
            statement.setString(5, blankToNull(data.emailAddress()));
            statement.setString(6, now);
            statement.setString(7, now);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Unable to determine the company ID.");
                return keys.getLong(1);
            }
        }
    }

    private static void insertItems(Connection connection, long waybillId, List<WaybillItemData> items) throws SQLException {
        if (items == null || items.isEmpty()) return;

        String sql = "INSERT INTO waybill_item "
                + "(waybill_id, item_number, description, package_type, quantity, weight_kg, volume_m3) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int itemNumber = 1;
            for (WaybillItemData item : items) {
                if (item == null || isBlank(item.description())) continue;
                statement.setLong(1, waybillId);
                statement.setInt(2, itemNumber++);
                statement.setString(3, blankToNull(item.description()));
                statement.setString(4, blankToNull(item.packageType()));
                setNullableDouble(statement, 5, item.quantity());
                setNullableDouble(statement, 6, item.weightKg());
                setNullableDouble(statement, 7, item.volumeM3());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertAudit(Connection connection, Long userId, long waybillId, String number) throws SQLException {
        String sql = "INSERT INTO audit_log (user_id, action, entity_type, entity_id, details, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (userId == null) statement.setNull(1, java.sql.Types.INTEGER); else statement.setLong(1, userId);
            statement.setString(2, "CREATE");
            statement.setString(3, "WAYBILL");
            statement.setLong(4, waybillId);
            statement.setString(5, "Created waybill " + number);
            statement.setString(6, DB_DATE_TIME.format(LocalDateTime.now()));
            statement.executeUpdate();
        }
    }

    private static void setNullableDouble(PreparedStatement statement, int index, Double value) throws SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.REAL); else statement.setDouble(index, value);
    }

    private static boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
    private static String blankToNull(String value) { return isBlank(value) ? null : value.trim(); }
}
