package com.aks.waybill.service;

import com.aks.waybill.db.Database;
import com.aks.waybill.logging.WaspLogger;
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
                              String remarks, String shipperDeclarationName, LocalDate shipperDeclarationDate,
                              String carrierReceiptDriverName, LocalDate carrierReceiptDate,
                              String consigneePodReceiverName, LocalDate consigneePodDate,
                              Long createdBy, List<WaybillItemData> items) {
    }

    public record SavedWaybill(long id, String waybillNumber) {
    }

    public record WaybillListRow(long id, String waybillNumber, LocalDate waybillDate,
                                 LocalDateTime createdAt, String shipperName, String consigneeName, String carrierName, String status) {
    }

    public record WaybillExportRow(String waybillNumber, LocalDate waybillDate, String shipperName, String consigneeName,
                                   String carrierName, String driverName, String vehicleTrailerNo, String origin, String destination,
                                   LocalDate estimatedDeliveryDate, String createdBy, String createdAt) {}

    public record WaybillDetails(long id, String waybillNumber, LocalDate waybillDate,
                                 CompanyData shipper, CompanyData consignee,
                                 String carrierName, String driverName, String vehicleTrailerNo,
                                 String originLoadingPoint, String destinationUnloadingPoint,
                                 LocalDate estimatedDeliveryDate, String specialInstructions,
                                 boolean hazardousMaterials, String remarks, String shipperDeclarationName, LocalDate shipperDeclarationDate,
                                 String carrierReceiptDriverName, LocalDate carrierReceiptDate,
                                 String consigneePodReceiverName, LocalDate consigneePodDate,
                                 List<WaybillItemData> items, String status) {
    }

    public record WaybillPage(List<WaybillListRow> rows, int page, int pageSize, long totalRows) {
        public int totalPages() {
            return (int) Math.max(1, (totalRows + pageSize - 1) / pageSize);
        }
    }

    public static WaybillPage findPage(String search, LocalDate fromDate, LocalDate toDate, int page, int pageSize) {
        return findPage(search, fromDate, toDate, null, page, pageSize, null);
    }

    /**
     * Loads waybills according to the logged-in user's role. Administrators see
     * all waybills; normal users see only waybills they created.
     */
    public static WaybillPage findPageForCurrentUser(String search, LocalDate fromDate, LocalDate toDate, int page, int pageSize) {
        return findPageForCurrentUser(search, fromDate, toDate, null, page, pageSize);
    }

    public static WaybillPage findPageForCurrentUser(String search, LocalDate fromDate, LocalDate toDate, String status, int page, int pageSize) {
        return findPageForCurrentUser(search, fromDate, toDate, status, "date", false, page, pageSize);
    }

    public static WaybillPage findPageForCurrentUser(String search, LocalDate fromDate, LocalDate toDate, String status, String sortKey, boolean ascending, int page, int pageSize) {
        Long ownerId = SessionContext.isAdmin() ? null : SessionContext.requireUserId();
        return findPage(search, fromDate, toDate, status, sortKey, ascending, page, pageSize, ownerId);
    }

    private static WaybillPage findPage(String search, LocalDate fromDate, LocalDate toDate, String status, int page, int pageSize, Long ownerId) {
        return findPage(search, fromDate, toDate, status, "date", false, page, pageSize, ownerId);
    }

    private static WaybillPage findPage(String search, LocalDate fromDate, LocalDate toDate, String status, String sortKey, boolean ascending, int page, int pageSize, Long ownerId) {
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        int safePage = Math.max(0, page);
        String term = search == null ? "" : search.trim();
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (ownerId != null) { where.append(" AND w.created_by = ? "); params.add(ownerId); }
        if (!term.isBlank()) {
            where.append(" AND (w.waybill_number LIKE ? COLLATE NOCASE OR COALESCE(w.shipper_company_name, '') LIKE ? COLLATE NOCASE OR COALESCE(w.consignee_company_name, '') LIKE ? COLLATE NOCASE OR COALESCE(w.carrier_name, '') LIKE ? COLLATE NOCASE) ");
            String like = "%" + term + "%";
            Collections.addAll(params, like, like, like, like);
        }
        if (fromDate != null) { where.append(" AND w.waybill_date >= ? "); params.add(DB_DATE.format(fromDate)); }
        if (toDate != null) { where.append(" AND w.waybill_date <= ? "); params.add(DB_DATE.format(toDate)); }
        if (status != null && !status.isBlank()) { where.append(" AND UPPER(w.status) = UPPER(?) "); params.add(status.trim()); }

        String base = " FROM waybill w " + where;
        String countSql = "SELECT COUNT(*)" + base;
        String sortColumn = switch (sortKey == null ? "date" : sortKey) {
            case "number" -> "w.waybill_number";
            case "shipper" -> "COALESCE(w.shipper_company_name, '')";
            case "consignee" -> "COALESCE(w.consignee_company_name, '')";
            case "carrier" -> "COALESCE(w.carrier_name, '')";
            case "status" -> "w.status";
            case "created" -> "w.created_at";
            default -> "w.waybill_date";
        };
        String direction = ascending ? " ASC" : " DESC";
        String dataSql = "SELECT w.id, w.waybill_number, w.waybill_date, w.created_at, COALESCE(w.shipper_company_name, ''), COALESCE(w.consignee_company_name, ''), COALESCE(w.carrier_name, ''), w.status"
                + base + " ORDER BY " + sortColumn + direction + ", w.id" + (ascending ? " ASC" : " DESC") + " LIMIT ? OFFSET ?";

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
                        rows.add(new WaybillListRow(rs.getLong(1), rs.getString(2), LocalDate.parse(rs.getString(3), DB_DATE), parseDateTime(rs.getString(4)), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8)));
                    }
                }
            }
            int totalPages = (int)Math.max(1, (total + safePageSize - 1) / safePageSize);
            int normalizedPage = Math.min(safePage, totalPages - 1);
            if (normalizedPage != safePage) return findPage(term, fromDate, toDate, status, sortKey, ascending, normalizedPage, safePageSize, ownerId);
            return new WaybillPage(rows, safePage, safePageSize, total);
        } catch (SQLException e) { WaspLogger.error("Operation failed in WaybillService", e);
            throw new IllegalStateException("Unable to load saved waybills", e);
        }
    }

    public static WaybillDetails findById(long id) {
        String sql = "SELECT w.id, w.waybill_number, w.waybill_date, "
                + "w.shipper_company_name, w.shipper_contact_person, w.shipper_address, w.shipper_phone_number, w.shipper_email_address, "
                + "w.consignee_company_name, w.consignee_contact_person, w.consignee_address, w.consignee_phone_number, w.consignee_email_address, "
                + "w.carrier_name, w.driver_name, w.vehicle_trailer_no, w.origin_loading_point, w.destination_unloading_point, "
                + "w.estimated_delivery_date, w.special_instructions, w.hazardous_materials, w.remarks, "
                + "w.shipper_declaration_name, w.shipper_declaration_date, w.carrier_receipt_driver_name, w.carrier_receipt_date, "
                + "w.consignee_pod_receiver_name, w.consignee_pod_date, w.status "
                + "FROM waybill w WHERE w.id=?"
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
                return new WaybillDetails(rs.getLong(1), rs.getString(2), LocalDate.parse(rs.getString(3), DB_DATE), shipper, consignee, rs.getString(14), rs.getString(15), rs.getString(16), rs.getString(17), rs.getString(18), parseDate(rs.getString(19)), rs.getString(20), rs.getInt(21) == 1, rs.getString(22), rs.getString(23), parseDate(rs.getString(24)), rs.getString(25), parseDate(rs.getString(26)), rs.getString(27), parseDate(rs.getString(28)), items, rs.getString(29));
            }
        } catch (SQLException e) { WaspLogger.error("Operation failed in WaybillService", e);
            throw new IllegalStateException("Unable to load waybill", e);
        }
    }

    public static List<WaybillListRow> findAllForCurrentUser(String search, LocalDate fromDate, LocalDate toDate) {
        List<WaybillListRow> result = new ArrayList<>();
        int page = 0;
        while (true) {
            WaybillPage batch = findPageForCurrentUser(search, fromDate, toDate, page, 100);
            result.addAll(batch.rows());
            if (batch.rows().isEmpty() || page >= batch.totalPages() - 1) break;
            page++;
        }
        return result;
    }

    public static List<WaybillExportRow> findAllForExcelForCurrentUser(String search, LocalDate fromDate, LocalDate toDate) {
        return findAllForExcelForCurrentUser(search, fromDate, toDate, null);
    }

    public static List<WaybillExportRow> findAllForExcelForCurrentUser(String search, LocalDate fromDate, LocalDate toDate, String status) {
        Long ownerId = SessionContext.isAdmin() ? null : SessionContext.requireUserId();
        String term = search == null ? "" : search.trim();
        StringBuilder where = new StringBuilder(" WHERE 1=1 "); List<Object> params = new ArrayList<>();
        if(ownerId!=null){where.append(" AND w.created_by=? ");params.add(ownerId);}
        if(!term.isBlank()){where.append(" AND (w.waybill_number LIKE ? COLLATE NOCASE OR COALESCE(sc.company_name,'') LIKE ? COLLATE NOCASE OR COALESCE(cc.company_name,'') LIKE ? COLLATE NOCASE OR COALESCE(w.carrier_name,'') LIKE ? COLLATE NOCASE) ");String like="%"+term+"%";Collections.addAll(params,like,like,like,like);}
        if(fromDate!=null){where.append(" AND w.waybill_date>=? ");params.add(DB_DATE.format(fromDate));}
        if(toDate!=null){where.append(" AND w.waybill_date<=? ");params.add(DB_DATE.format(toDate));}
        if(status!=null&&!status.isBlank()){where.append(" AND UPPER(w.status)=UPPER(?) ");params.add(status.trim());}
        String sql="SELECT w.waybill_number,w.waybill_date,COALESCE(w.shipper_company_name,''),COALESCE(w.consignee_company_name,''),COALESCE(w.carrier_name,''),COALESCE(w.driver_name,''),COALESCE(w.vehicle_trailer_no,''),COALESCE(w.origin_loading_point,''),COALESCE(w.destination_unloading_point,''),w.estimated_delivery_date,COALESCE(u.username,''),w.created_at FROM waybill w LEFT JOIN app_user u ON u.id=w.created_by"+where+" ORDER BY w.waybill_date DESC,w.id DESC";
        List<WaybillExportRow> rows=new ArrayList<>();
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(sql)){bind(p,params);try(ResultSet r=p.executeQuery()){while(r.next())rows.add(new WaybillExportRow(r.getString(1),LocalDate.parse(r.getString(2),DB_DATE),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getString(9),parseDate(r.getString(10)),r.getString(11),r.getString(12)));}return rows;}catch(SQLException e){throw new IllegalStateException("Unable to load waybills for Excel export",e);}
    }

    public static SavedWaybill duplicate(long waybillId) {
        WaybillDetails source = findById(waybillId);
        if (source == null) throw new IllegalArgumentException("The selected waybill no longer exists or is not accessible.");
        WaybillData data = new WaybillData(
                LocalDate.now(), source.shipper(), source.consignee(), source.carrierName(), source.driverName(),
                source.vehicleTrailerNo(), source.originLoadingPoint(), source.destinationUnloadingPoint(), null,
                source.specialInstructions(), source.hazardousMaterials(), source.remarks(),
                null, null, null, null, null, null, SessionContext.requireUserId(), source.items());
        SavedWaybill saved = save(data);
        AuditLogService.log("DUPLICATE", "WAYBILL", saved.id(), "Duplicated " + source.waybillNumber() + " as " + saved.waybillNumber());
        WaspLogger.info("Waybill duplicated. sourceWaybill=" + source.waybillNumber() + ", newWaybill=" + saved.waybillNumber());
        return saved;
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
        length(data.shipperDeclarationName(), 200, "Shipper declaration name");
        length(data.carrierReceiptDriverName(), 200, "Carrier receipt driver name");
        length(data.consigneePodReceiverName(), 200, "Consignee proof of delivery receiver name");
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

    private static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDateTime.parse(value, DB_DATE_TIME); }
        catch (Exception ignored) { return null; }
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

                saveCarrierMaster(connection, data.carrierName(), data.driverName(), data.vehicleTrailerNo());
                saveLocationMaster(connection, data.originLoadingPoint());
                saveLocationMaster(connection, data.destinationUnloadingPoint());
                // Keep reusable company master data in sync while retaining a snapshot in the waybill itself.
                // Company IDs remain NULL by design so deleting a master never affects historical waybills.
                findOrCreateCompany(connection, data.shipper(), "shipper_company");
                findOrCreateCompany(connection, data.consignee(), "consignee_company");

                long waybillId;
                String sourceInstallationId = loadInstallationId(connection);
                String waybillGlobalId = java.util.UUID.randomUUID().toString();
                String sql = "INSERT INTO waybill "
                        + "(waybill_number, waybill_date, shipper_company_id, consignee_company_id, shipper_company_name, shipper_contact_person, shipper_address, shipper_phone_number, shipper_email_address, consignee_company_name, consignee_contact_person, consignee_address, consignee_phone_number, consignee_email_address, carrier_name, driver_name, vehicle_trailer_no, "
                        + "origin_loading_point, destination_unloading_point, estimated_delivery_date, "
                        + "special_instructions, hazardous_materials, remarks, shipper_declaration_name, shipper_declaration_date, "
                        + "carrier_receipt_driver_name, carrier_receipt_date, consignee_pod_receiver_name, consignee_pod_date, "
                        + "created_by, created_at, updated_at, status, global_id, source_installation_id) "
                        + "VALUES (?, ?, NULL, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, waybillNumber);
                    statement.setString(2, DB_DATE.format(data.waybillDate()));
                    // shipper_company_id and consignee_company_id are explicit NULL literals in the
                    // INSERT because the waybill keeps a historical snapshot of company details.
                    // Therefore the first bind parameter after waybill_date is parameter 3.
                    statement.setString(3, blankToNull(data.shipper().companyName()));
                    statement.setString(4, blankToNull(data.shipper().contactPerson()));
                    statement.setString(5, blankToNull(data.shipper().address()));
                    statement.setString(6, blankToNull(data.shipper().phoneNumber()));
                    statement.setString(7, blankToNull(data.shipper().emailAddress()));
                    statement.setString(8, blankToNull(data.consignee().companyName()));
                    statement.setString(9, blankToNull(data.consignee().contactPerson()));
                    statement.setString(10, blankToNull(data.consignee().address()));
                    statement.setString(11, blankToNull(data.consignee().phoneNumber()));
                    statement.setString(12, blankToNull(data.consignee().emailAddress()));
                    statement.setString(13, blankToNull(data.carrierName()));
                    statement.setString(14, blankToNull(data.driverName()));
                    statement.setString(15, blankToNull(data.vehicleTrailerNo()));
                    statement.setString(16, blankToNull(data.originLoadingPoint()));
                    statement.setString(17, blankToNull(data.destinationUnloadingPoint()));
                    if (data.estimatedDeliveryDate() == null) statement.setNull(18, java.sql.Types.VARCHAR);
                    else statement.setString(18, DB_DATE.format(data.estimatedDeliveryDate()));
                    statement.setString(19, blankToNull(data.specialInstructions()));
                    statement.setInt(20, data.hazardousMaterials() ? 1 : 0);
                    statement.setString(21, blankToNull(data.remarks()));
                    statement.setString(22, blankToNull(data.shipperDeclarationName()));
                    setNullableDate(statement, 23, data.shipperDeclarationDate());
                    statement.setString(24, blankToNull(data.carrierReceiptDriverName()));
                    setNullableDate(statement, 25, data.carrierReceiptDate());
                    statement.setString(26, blankToNull(data.consigneePodReceiverName()));
                    setNullableDate(statement, 27, data.consigneePodDate());
                    if (data.createdBy() == null) statement.setNull(28, java.sql.Types.INTEGER);
                    else statement.setLong(28, data.createdBy());
                    statement.setString(29, DB_DATE_TIME.format(now));
                    statement.setString(30, DB_DATE_TIME.format(now));
                    statement.setString(31, "DRAFT");
                    statement.setString(32, waybillGlobalId);
                    statement.setString(33, sourceInstallationId);
                    statement.executeUpdate();

                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Unable to determine the saved waybill ID.");
                        waybillId = keys.getLong(1);
                    }
                }

                insertItems(connection, waybillId, data.items());
                insertAudit(connection, data.createdBy(), waybillId, waybillNumber, "CREATE");

                connection.commit();
                WaspLogger.info("Waybill created successfully. waybillId=" + waybillId + ", waybillNumber=" + waybillNumber + ", userId=" + data.createdBy());
                return new SavedWaybill(waybillId, waybillNumber);
            } catch (SQLException | RuntimeException e) { WaspLogger.error("Operation failed in WaybillService", e);
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) { WaspLogger.error("Operation failed in WaybillService", e);
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
                saveCarrierMaster(connection, data.carrierName(), data.driverName(), data.vehicleTrailerNo());
                saveLocationMaster(connection, data.originLoadingPoint());
                saveLocationMaster(connection, data.destinationUnloadingPoint());
                // Keep reusable company master data in sync while retaining a snapshot in the waybill itself.
                // Company IDs remain NULL by design so deleting a master never affects historical waybills.
                findOrCreateCompany(connection, data.shipper(), "shipper_company");
                findOrCreateCompany(connection, data.consignee(), "consignee_company");
                LocalDateTime now = LocalDateTime.now();

                String sql = "UPDATE waybill SET waybill_date=?, shipper_company_id=NULL, consignee_company_id=NULL, shipper_company_name=?, shipper_contact_person=?, shipper_address=?, shipper_phone_number=?, shipper_email_address=?, consignee_company_name=?, consignee_contact_person=?, consignee_address=?, consignee_phone_number=?, consignee_email_address=?, carrier_name=?, driver_name=?, vehicle_trailer_no=?, "
                        + "origin_loading_point=?, destination_unloading_point=?, estimated_delivery_date=?, special_instructions=?, hazardous_materials=?, remarks=?, "
                        + "shipper_declaration_name=?, shipper_declaration_date=?, carrier_receipt_driver_name=?, carrier_receipt_date=?, "
                        + "consignee_pod_receiver_name=?, consignee_pod_date=?, updated_at=? WHERE id=?";

                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    int i = 1;
                    statement.setString(i++, DB_DATE.format(data.waybillDate()));
                    statement.setString(i++, blankToNull(data.shipper().companyName()));
                    statement.setString(i++, blankToNull(data.shipper().contactPerson()));
                    statement.setString(i++, blankToNull(data.shipper().address()));
                    statement.setString(i++, blankToNull(data.shipper().phoneNumber()));
                    statement.setString(i++, blankToNull(data.shipper().emailAddress()));
                    statement.setString(i++, blankToNull(data.consignee().companyName()));
                    statement.setString(i++, blankToNull(data.consignee().contactPerson()));
                    statement.setString(i++, blankToNull(data.consignee().address()));
                    statement.setString(i++, blankToNull(data.consignee().phoneNumber()));
                    statement.setString(i++, blankToNull(data.consignee().emailAddress()));
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
                    statement.setString(i++, blankToNull(data.shipperDeclarationName()));
                    setNullableDate(statement, i++, data.shipperDeclarationDate());
                    statement.setString(i++, blankToNull(data.carrierReceiptDriverName()));
                    setNullableDate(statement, i++, data.carrierReceiptDate());
                    statement.setString(i++, blankToNull(data.consigneePodReceiverName()));
                    setNullableDate(statement, i++, data.consigneePodDate());
                    statement.setString(i++, DB_DATE_TIME.format(now));
                    statement.setLong(i, waybillId);
                    if (statement.executeUpdate() == 0) throw new IllegalArgumentException("The selected waybill no longer exists.");
                }

                syncItems(connection, waybillId, data.items());

                String number = loadWaybillNumber(connection, waybillId);
                insertAudit(connection, data.createdBy(), waybillId, number, "UPDATE");
                connection.commit();
                WaspLogger.info("Waybill updated successfully. waybillId=" + waybillId + ", waybillNumber=" + number + ", userId=" + data.createdBy());
            } catch (SQLException | RuntimeException exception) { WaspLogger.error("Operation failed in WaybillService", exception);
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) { WaspLogger.error("Operation failed in WaybillService", exception);
            throw new IllegalStateException("Unable to update waybill", exception);
        }
    }

    private static void assertCanAccessWaybill(Connection connection, long waybillId) throws SQLException {
        String sql = "SELECT created_by, status FROM waybill WHERE id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, waybillId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("The selected waybill no longer exists.");
                long ownerId = rs.getLong(1);
                boolean ownerIdIsNull = rs.wasNull();
                String status = rs.getString(2);
                if (!SessionContext.isAdmin() && "FINAL".equalsIgnoreCase(status)) {
                    throw new IllegalArgumentException("This waybill is finalized and can only be edited by an Administrator.");
                }
                if (!SessionContext.isAdmin() && (ownerIdIsNull || ownerId != SessionContext.requireUserId())) {
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

    public static boolean canEdit(long waybillId) {
        WaybillDetails d=findById(waybillId);
        return d != null && (SessionContext.isAdmin() || ("DRAFT".equalsIgnoreCase(d.status()) && isOwner(waybillId)));
    }

    private static boolean isOwner(Connection connection, long waybillId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT created_by FROM waybill WHERE id=?")) {
            statement.setLong(1, waybillId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return false;
                long ownerId = resultSet.getLong(1);
                boolean ownerIdIsNull = resultSet.wasNull();
                return !ownerIdIsNull && ownerId == SessionContext.requireUserId();
            }
        }
    }

    private static boolean isOwner(long waybillId) {
        try (Connection connection = Database.getConnection()) {
            return isOwner(connection, waybillId);
        } catch (SQLException exception) { WaspLogger.error("Operation failed in WaybillService", exception);
            throw new IllegalStateException("Unable to check waybill ownership", exception);
        }
    }

    public static void finalizeWaybill(long waybillId) {
        if (waybillId <= 0) throw new IllegalArgumentException("Valid waybill is required.");
        long currentUserId = SessionContext.requireUserId();
        WaspLogger.info("Finalization requested. waybillId=" + waybillId + ", userId=" + currentUserId);

        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement busy = connection.prepareStatement("PRAGMA busy_timeout=5000")) {
                busy.execute();
            }

            try {
                if (!SessionContext.isAdmin() && !isOwner(connection, waybillId)) {
                    throw new IllegalArgumentException("You do not have permission to finalize this waybill.");
                }

                // Verify that the authenticated user still exists in this database.
                try (PreparedStatement user = connection.prepareStatement("SELECT 1 FROM app_user WHERE id=?")) {
                    user.setLong(1, currentUserId);
                    try (ResultSet rs = user.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalStateException("The logged-in user no longer exists in the application database.");
                        }
                    }
                }

                String now = DB_DATE_TIME.format(LocalDateTime.now());
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE waybill SET status='FINAL', finalized_at=?, finalized_by=?, final_to_draft_reason=NULL, updated_at=? WHERE id=? AND status='DRAFT'")) {
                    statement.setString(1, now);
                    statement.setLong(2, currentUserId);
                    statement.setString(3, now);
                    statement.setLong(4, waybillId);
                    if (statement.executeUpdate() == 0) {
                        throw new IllegalArgumentException("The waybill is already finalized or no longer exists.");
                    }
                }

                // Write the audit entry using the SAME connection and transaction.
                // This prevents SQLite connection/locking conflicts and makes the
                // finalization + audit entry atomic.
                insertAudit(connection, currentUserId, "FINALIZE", "WAYBILL", waybillId, loadWaybillNumber(connection, waybillId), "Waybill finalized");

                connection.commit();
                WaspLogger.info("Waybill finalized successfully. waybillId=" + waybillId + ", userId=" + currentUserId);
            } catch (SQLException | RuntimeException exception) { WaspLogger.error("Operation failed in WaybillService", exception);
                try { connection.rollback(); } catch (SQLException ignored) { }
                WaspLogger.error("Finalization failed and transaction was rolled back. waybillId=" + waybillId + ", userId=" + currentUserId, exception);
                if (exception instanceof SQLException sqlException) {
                    String detail = sqlException.getMessage() == null ? sqlException.getClass().getSimpleName() : sqlException.getMessage();
                    throw new IllegalStateException("Unable to finalize waybill: " + detail, sqlException);
                }
                throw exception;
            }
        } catch (SQLException exception) { WaspLogger.error("Operation failed in WaybillService", exception);
            WaspLogger.error("Unable to finalize waybill because the database operation failed. waybillId=" + waybillId + ", userId=" + currentUserId, exception);
            String detail = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            throw new IllegalStateException("Unable to finalize waybill: " + detail, exception);
        }
    }

    public static void revertToDraft(long waybillId,String reason) {
        if(!SessionContext.isAdmin()) throw new IllegalArgumentException("Only an Administrator can return a finalized waybill to Draft.");
        if(reason==null||reason.isBlank()) throw new IllegalArgumentException("A reason is required when returning a finalized waybill to Draft.");
        long currentUserId = SessionContext.requireUserId();
        try(Connection connection=Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String now=DB_DATE_TIME.format(LocalDateTime.now());
                try(PreparedStatement statement=connection.prepareStatement("UPDATE waybill SET status='DRAFT', final_to_draft_reason=?, updated_at=? WHERE id=? AND status='FINAL'")) {
                    statement.setString(1,reason.trim());
                    statement.setString(2,now);
                    statement.setLong(3,waybillId);
                    if(statement.executeUpdate()==0) throw new IllegalArgumentException("The waybill is not finalized or no longer exists.");
                }
                insertAudit(connection,currentUserId,"FINAL_TO_DRAFT","WAYBILL",waybillId,loadWaybillNumber(connection, waybillId),"Reason: "+reason.trim());
                connection.commit();
            } catch(SQLException|RuntimeException exception) {
                try { connection.rollback(); } catch(SQLException ignored) { }
                if(exception instanceof SQLException sqlException) {
                    String detail=sqlException.getMessage()==null?sqlException.getClass().getSimpleName():sqlException.getMessage();
                    throw new IllegalStateException("Unable to return waybill to Draft: "+detail,sqlException);
                }
                throw exception;
            }
        } catch(SQLException exception) {
            String detail=exception.getMessage()==null?exception.getClass().getSimpleName():exception.getMessage();
            throw new IllegalStateException("Unable to return waybill to Draft: "+detail,exception);
        }
    }

    private static void insertAudit(Connection connection, long userId, String action, String entityType, Long entityId, String entityLabel, String details) throws SQLException {
        try(PreparedStatement statement=connection.prepareStatement("INSERT INTO audit_log(user_id,action,entity_type,entity_id,entity_label,details,created_at,global_id,source_installation_id) VALUES(?,?,?,?,?,?,?,?,?)")) {
            statement.setLong(1,userId);
            statement.setString(2,action);
            statement.setString(3,entityType);
            if(entityId==null) statement.setNull(4,java.sql.Types.INTEGER); else statement.setLong(4,entityId);
            if(entityLabel==null||entityLabel.isBlank()) statement.setNull(5,java.sql.Types.VARCHAR); else statement.setString(5,entityLabel);
            statement.setString(6,details);
            statement.setString(7,LocalDateTime.now().toString());
            statement.setString(8,java.util.UUID.randomUUID().toString());
            statement.setString(9,loadInstallationId(connection));
            statement.executeUpdate();
        }
    }

    public static void delete(long waybillId) {
        if(!SessionContext.isAdmin()) throw new IllegalArgumentException("Only an Administrator can delete waybills.");
        try(Connection c=Database.getConnection()) {
            String waybillNumber;
            try (PreparedStatement lookup = c.prepareStatement("SELECT waybill_number FROM waybill WHERE id=?")) {
                lookup.setLong(1, waybillId);
                try (ResultSet rs = lookup.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("The selected waybill no longer exists.");
                    waybillNumber = rs.getString(1);
                }
            }
            try (PreparedStatement p=c.prepareStatement("DELETE FROM waybill WHERE id=?")) {
                p.setLong(1,waybillId);
                if(p.executeUpdate()==0) throw new IllegalArgumentException("The selected waybill no longer exists.");
            }
            AuditLogService.logWithEntityLabel("DELETE","WAYBILL",waybillId,waybillNumber,"Waybill deleted by Administrator");
            WaspLogger.info("Waybill deleted. waybillId=" + waybillId + ", waybillNumber=" + waybillNumber);
        } catch(SQLException e){throw new IllegalStateException("Unable to delete waybill",e);}
    }

    private static void saveCarrierMaster(Connection connection, String carrierName, String driverName, String vehicleTrailerNo) throws SQLException {
        if (isBlank(carrierName)) return;

        String normalizedCarrier = carrierName.trim();
        String normalizedDriver = blankToNull(driverName);
        String normalizedVehicle = blankToNull(vehicleTrailerNo);

        // A carrier master represents a reusable Carrier + Driver + Vehicle/Trailer
        // combination. Selecting the exact same combination must not change updated_at.
        String find = "SELECT id, active FROM saved_carrier "
                + "WHERE carrier_name=? COLLATE NOCASE "
                + "AND COALESCE(driver_name,'') COLLATE NOCASE=COALESCE(?,'') COLLATE NOCASE "
                + "AND COALESCE(vehicle_trailer_no,'') COLLATE NOCASE=COALESCE(?,'') COLLATE NOCASE "
                + "ORDER BY id LIMIT 1";
        Long id = null;
        boolean active = false;
        try (PreparedStatement statement = connection.prepareStatement(find)) {
            statement.setString(1, normalizedCarrier);
            statement.setString(2, normalizedDriver);
            statement.setString(3, normalizedVehicle);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    id = rs.getLong(1);
                    active = rs.getInt(2) == 1;
                }
            }
        }

        if (id == null) {
            String insert = "INSERT INTO saved_carrier(carrier_name, driver_name, vehicle_trailer_no, active, created_at, updated_at, global_id, source_installation_id) VALUES(?,?,?,1,?,?,?,?)";
            String now = DB_DATE_TIME.format(LocalDateTime.now());
            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                statement.setString(1, normalizedCarrier);
                statement.setString(2, normalizedDriver);
                statement.setString(3, normalizedVehicle);
                statement.setString(4, now);
                statement.setString(5, now);
                statement.setString(6, java.util.UUID.randomUUID().toString());
                statement.setString(7, loadInstallationId(connection));
                statement.executeUpdate();
            }
        } else if (!active) {
            // Reactivating an inactive exact combination is a genuine master-data change.
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE saved_carrier SET active=1, updated_at=? WHERE id=?")) {
                statement.setString(1, DB_DATE_TIME.format(LocalDateTime.now()));
                statement.setLong(2, id);
                statement.executeUpdate();
            }
        }
        // If the exact active combination already exists, deliberately do nothing so
        // merely selecting/using it from a waybill does not advance updated_at.
    }

    private static void saveLocationMaster(Connection connection, String locationName) throws SQLException {
        if (isBlank(locationName)) return;
        String normalized = locationName.trim();
        String find = "SELECT id, active FROM saved_location WHERE location_name=? COLLATE NOCASE LIMIT 1";
        Long id = null;
        boolean active = false;
        try (PreparedStatement statement = connection.prepareStatement(find)) {
            statement.setString(1, normalized);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) { id = rs.getLong(1); active = rs.getInt(2) == 1; }
            }
        }
        if (id == null) {
            String now = DB_DATE_TIME.format(LocalDateTime.now());
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO saved_location(location_name,active,created_at,updated_at,global_id,source_installation_id) VALUES(?,1,?,?,?,?)")) {
                statement.setString(1, normalized);
                statement.setString(2, now);
                statement.setString(3, now);
                statement.setString(4, java.util.UUID.randomUUID().toString());
                statement.setString(5, loadInstallationId(connection));
                statement.executeUpdate();
            }
        } else if (!active) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE saved_location SET active=1, updated_at=? WHERE id=?")) {
                statement.setString(1, DB_DATE_TIME.format(LocalDateTime.now()));
                statement.setLong(2, id);
                statement.executeUpdate();
            }
        }
    }

    private static long findOrCreateCompany(Connection connection, CompanyData data, String table) throws SQLException {
        if (data == null || isBlank(data.companyName())) {
            throw new IllegalArgumentException("Company name is required.");
        }
        if (!table.equals("shipper_company") && !table.equals("consignee_company")) {
            throw new IllegalArgumentException("Invalid company master.");
        }
        String find = "SELECT id, contact_person, address, phone_number, email_address, active FROM " + table
                + " WHERE company_name = ? COLLATE NOCASE ORDER BY id LIMIT 1";
        String companyName = data.companyName().trim();
        String contact = blankToNull(data.contactPerson());
        String address = blankToNull(data.address());
        String phone = blankToNull(data.phoneNumber());
        String email = blankToNull(data.emailAddress());
        try (PreparedStatement statement = connection.prepareStatement(find)) {
            statement.setString(1, companyName);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    boolean changed = !sameNullable(rs.getString(2), contact)
                            || !sameNullable(rs.getString(3), address)
                            || !sameNullable(rs.getString(4), phone)
                            || !sameNullable(rs.getString(5), email)
                            || rs.getInt(6) != 1;
                    if (changed) {
                        String update = "UPDATE " + table + " SET contact_person=?, address=?, phone_number=?, email_address=?, active=1, updated_at=? WHERE id=?";
                        try (PreparedStatement u = connection.prepareStatement(update)) {
                            u.setString(1, contact);
                            u.setString(2, address);
                            u.setString(3, phone);
                            u.setString(4, email);
                            u.setString(5, DB_DATE_TIME.format(LocalDateTime.now()));
                            u.setLong(6, id);
                            u.executeUpdate();
                        }
                    }
                    return id;
                }
            }
        }
        String insert = "INSERT INTO " + table + " (company_name, contact_person, address, phone_number, email_address, active, created_at, updated_at, global_id, source_installation_id) VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?, ?)";
        String now = DB_DATE_TIME.format(LocalDateTime.now());
        try (PreparedStatement statement = connection.prepareStatement(insert, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, companyName);
            statement.setString(2, contact);
            statement.setString(3, address);
            statement.setString(4, phone);
            statement.setString(5, email);
            statement.setString(6, now);
            statement.setString(7, now);
            statement.setString(8, java.util.UUID.randomUUID().toString());
            statement.setString(9, loadInstallationId(connection));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Unable to determine the company ID.");
                return keys.getLong(1);
            }
        }
    }

    private static boolean sameNullable(String a, String b) {
        return java.util.Objects.equals(blankToNull(a), blankToNull(b));
    }

    private static void insertItems(Connection connection, long waybillId, List<WaybillItemData> items) throws SQLException {
        List<WaybillItemData> normalized = normalizeItems(items);
        if (normalized.isEmpty()) return;

        String sourceInstallationId = loadInstallationId(connection);
        String now = DB_DATE_TIME.format(LocalDateTime.now());
        String sql = "INSERT INTO waybill_item "
                + "(waybill_id, item_number, description, package_type, quantity, weight_kg, volume_m3, global_id, source_installation_id, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int itemNumber = 1;
            for (WaybillItemData item : normalized) {
                statement.setLong(1, waybillId);
                statement.setInt(2, itemNumber++);
                statement.setString(3, blankToNull(item.description()));
                statement.setString(4, blankToNull(item.packageType()));
                setNullableDouble(statement, 5, item.quantity());
                setNullableDouble(statement, 6, item.weightKg());
                setNullableDouble(statement, 7, item.volumeM3());
                statement.setString(8, java.util.UUID.randomUUID().toString());
                statement.setString(9, sourceInstallationId);
                statement.setString(10, now);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /**
     * Updates only genuinely changed waybill items. Unchanged items retain their
     * existing row ID, global ID and updated_at. New items are inserted, and
     * removed items are recorded as Data Exchange tombstones before deletion.
     */
    private static void syncItems(Connection connection, long waybillId, List<WaybillItemData> items) throws SQLException {
        List<WaybillItemData> normalized = normalizeItems(items);
        String sourceInstallationId = loadInstallationId(connection);
        String now = DB_DATE_TIME.format(LocalDateTime.now());

        class ExistingItem {
            final long id; final int itemNumber; final String description; final String packageType;
            final Double quantity; final Double weightKg; final Double volumeM3; final String globalId;
            ExistingItem(long id, int itemNumber, String description, String packageType, Double quantity, Double weightKg, Double volumeM3, String globalId) {
                this.id=id; this.itemNumber=itemNumber; this.description=description; this.packageType=packageType;
                this.quantity=quantity; this.weightKg=weightKg; this.volumeM3=volumeM3; this.globalId=globalId;
            }
        }

        java.util.Map<Integer, ExistingItem> existing = new java.util.LinkedHashMap<>();
        try (PreparedStatement p = connection.prepareStatement(
                "SELECT id, item_number, description, package_type, quantity, weight_kg, volume_m3, global_id FROM waybill_item WHERE waybill_id=? ORDER BY item_number")) {
            p.setLong(1, waybillId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    existing.put(r.getInt(2), new ExistingItem(r.getLong(1), r.getInt(2), r.getString(3), r.getString(4),
                            nullableDouble(r, 5), nullableDouble(r, 6), nullableDouble(r, 7), r.getString(8)));
                }
            }
        }

        java.util.Set<Integer> retained = new java.util.HashSet<>();
        int itemNumber = 1;
        for (WaybillItemData item : normalized) {
            ExistingItem old = existing.get(itemNumber);
            if (old == null) {
                String sql = "INSERT INTO waybill_item (waybill_id, item_number, description, package_type, quantity, weight_kg, volume_m3, global_id, source_installation_id, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement p = connection.prepareStatement(sql)) {
                    p.setLong(1, waybillId); p.setInt(2, itemNumber);
                    p.setString(3, blankToNull(item.description())); p.setString(4, blankToNull(item.packageType()));
                    setNullableDouble(p, 5, item.quantity()); setNullableDouble(p, 6, item.weightKg()); setNullableDouble(p, 7, item.volumeM3());
                    p.setString(8, java.util.UUID.randomUUID().toString()); p.setString(9, sourceInstallationId); p.setString(10, now);
                    p.executeUpdate();
                }
            } else {
                retained.add(itemNumber);
                if (!sameNullable(old.description, item.description())
                        || !sameNullable(old.packageType, item.packageType())
                        || !java.util.Objects.equals(old.quantity, item.quantity())
                        || !java.util.Objects.equals(old.weightKg, item.weightKg())
                        || !java.util.Objects.equals(old.volumeM3, item.volumeM3())) {
                    try (PreparedStatement p = connection.prepareStatement(
                            "UPDATE waybill_item SET description=?, package_type=?, quantity=?, weight_kg=?, volume_m3=?, updated_at=? WHERE id=?")) {
                        p.setString(1, blankToNull(item.description())); p.setString(2, blankToNull(item.packageType()));
                        setNullableDouble(p, 3, item.quantity()); setNullableDouble(p, 4, item.weightKg()); setNullableDouble(p, 5, item.volumeM3());
                        p.setString(6, now); p.setLong(7, old.id); p.executeUpdate();
                    }
                }
            }
            itemNumber++;
        }

        String waybillGlobalId = null;
        try (PreparedStatement p = connection.prepareStatement("SELECT global_id FROM waybill WHERE id=?")) {
            p.setLong(1, waybillId);
            try (ResultSet r = p.executeQuery()) { if (r.next()) waybillGlobalId = r.getString(1); }
        }

        for (ExistingItem old : existing.values()) {
            if (retained.contains(old.itemNumber)) continue;
            String itemGlobalId = old.globalId;
            if (itemGlobalId == null || itemGlobalId.isBlank()) itemGlobalId = java.util.UUID.randomUUID().toString();
            try (PreparedStatement p = connection.prepareStatement(
                    "INSERT OR IGNORE INTO data_exchange_waybill_item_tombstone(global_id, source_installation_id, waybill_item_global_id, waybill_global_id, waybill_id, item_number, deleted_at) VALUES(?,?,?,?,?,?,?)")) {
                p.setString(1, java.util.UUID.randomUUID().toString()); p.setString(2, sourceInstallationId);
                p.setString(3, itemGlobalId); p.setString(4, waybillGlobalId); p.setLong(5, waybillId); p.setInt(6, old.itemNumber); p.setString(7, now);
                p.executeUpdate();
            }
            try (PreparedStatement p = connection.prepareStatement("DELETE FROM waybill_item WHERE id=?")) {
                p.setLong(1, old.id); p.executeUpdate();
            }
        }
    }

    private static List<WaybillItemData> normalizeItems(List<WaybillItemData> items) {
        List<WaybillItemData> normalized = new ArrayList<>();
        if (items == null) return normalized;
        for (WaybillItemData item : items) {
            if (item == null || isBlank(item.description())) continue;
            normalized.add(new WaybillItemData(blankToNull(item.description()), blankToNull(item.packageType()),
                    item.quantity(), item.weightKg(), item.volumeM3()));
        }
        return normalized;
    }

    private static String loadInstallationId(Connection connection) throws SQLException {
        try (PreparedStatement p = connection.prepareStatement(
                "SELECT setting_value FROM application_settings WHERE setting_key='data_exchange.installation_id'")) {
            try (ResultSet r = p.executeQuery()) {
                if (!r.next() || r.getString(1) == null || r.getString(1).isBlank()) {
                    throw new SQLException("W.A.S.P. Data Exchange installation ID is not initialized.");
                }
                return r.getString(1);
            }
        }
    }

    private static void insertAudit(Connection connection, Long userId, long waybillId, String number, String action) throws SQLException {
        String sql = "INSERT INTO audit_log (user_id, action, entity_type, entity_id, entity_label, details, created_at, global_id, source_installation_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (userId == null) statement.setNull(1, java.sql.Types.INTEGER); else statement.setLong(1, userId);
            statement.setString(2, action);
            statement.setString(3, "WAYBILL");
            statement.setLong(4, waybillId);
            statement.setString(5, number);
            statement.setString(6, "Created waybill " + number);
            statement.setString(7, DB_DATE_TIME.format(LocalDateTime.now()));
            statement.setString(8, java.util.UUID.randomUUID().toString());
            statement.setString(9, loadInstallationId(connection));
            statement.executeUpdate();
        }
    }

    private static void setNullableDate(PreparedStatement statement, int index, LocalDate value) throws SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.VARCHAR); else statement.setString(index, DB_DATE.format(value));
    }

    private static void setNullableDouble(PreparedStatement statement, int index, Double value) throws SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.REAL); else statement.setDouble(index, value);
    }

    private static boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
    private static String blankToNull(String value) { return isBlank(value) ? null : value.trim(); }
}
