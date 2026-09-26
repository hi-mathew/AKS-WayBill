package com.aks.waybill.service;

import com.aks.waybill.logging.WaspLogger;

import com.aks.waybill.db.Database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Master data for reusable carriers and loading/unloading locations. */
public final class SavedDataService {
    private SavedDataService() {}

    public record CarrierRecord(long id, String name, String driverName, String vehicleTrailerNo, boolean active) {}
    public record LocationRecord(long id, String name, boolean active) {}

    public static List<CarrierRecord> findCarriers(String search, boolean activeOnly) {
        String term = search == null ? "" : search.trim();
        String sql = "SELECT id, carrier_name, driver_name, vehicle_trailer_no, active FROM saved_carrier WHERE (?='' OR carrier_name LIKE ? COLLATE NOCASE) "
                + (activeOnly ? "AND active=1 " : "") + "ORDER BY carrier_name COLLATE NOCASE";
        List<CarrierRecord> result = new ArrayList<>();
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, term); p.setString(2, "%" + term + "%");
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) result.add(new CarrierRecord(r.getLong(1), r.getString(2), r.getString(3), r.getString(4), r.getInt(5) == 1));
            }
            return result;
        } catch (SQLException e) { WaspLogger.error("Unable to load saved carriers", e); throw new IllegalStateException("Unable to load saved carriers", e); }
    }

    public static CarrierRecord findCarrierByName(String name, boolean activeOnly) {
        String value = name == null ? "" : name.trim();
        if (value.isBlank()) return null;
        String sql = "SELECT id, carrier_name, driver_name, vehicle_trailer_no, active FROM saved_carrier WHERE carrier_name=? COLLATE NOCASE "
                + (activeOnly ? "AND active=1 " : "") + "ORDER BY id LIMIT 1";
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, value);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? new CarrierRecord(r.getLong(1), r.getString(2), r.getString(3), r.getString(4), r.getInt(5) == 1) : null;
            }
        } catch (SQLException e) { WaspLogger.error("Unable to find saved carrier", e); throw new IllegalStateException("Unable to find saved carrier", e); }
    }

    public static List<LocationRecord> findLocations(String search, boolean activeOnly) {
        String term = search == null ? "" : search.trim();
        String sql = "SELECT id, location_name, active FROM saved_location WHERE (?='' OR location_name LIKE ? COLLATE NOCASE) "
                + (activeOnly ? "AND active=1 " : "") + "ORDER BY location_name COLLATE NOCASE";
        List<LocationRecord> result = new ArrayList<>();
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, term); p.setString(2, "%" + term + "%");
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) result.add(new LocationRecord(r.getLong(1), r.getString(2), r.getInt(3) == 1));
            }
            return result;
        } catch (SQLException e) { WaspLogger.error("Unable to load saved locations", e); throw new IllegalStateException("Unable to load saved locations", e); }
    }

    /** Saves a carrier master record, or updates its driver/vehicle details when the carrier already exists. */
    public static CarrierRecord saveOrUpdateCarrier(String name, String driverName, String vehicleTrailerNo) {
        String value = required(name, "Carrier name");
        validateLength(value, 300, "Carrier name");
        validateLength(driverName, 200, "Driver name");
        validateLength(vehicleTrailerNo, 200, "Vehicle / Trailer No.");
        try (Connection c = Database.getConnection()) {
            CarrierRecord existing = findCarrierByName(value, false);
            if (existing != null) {
                try (PreparedStatement p = c.prepareStatement("UPDATE saved_carrier SET driver_name=?, vehicle_trailer_no=?, active=1, updated_at=datetime('now') WHERE id=?")) {
                    p.setString(1, blankToNull(driverName)); p.setString(2, blankToNull(vehicleTrailerNo)); p.setLong(3, existing.id()); p.executeUpdate();
                }
                return new CarrierRecord(existing.id(), existing.name(), blankToNull(driverName), blankToNull(vehicleTrailerNo), true);
            }
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO saved_carrier(carrier_name,driver_name,vehicle_trailer_no,active,created_at,updated_at) VALUES(?,?,?,1,datetime('now'),datetime('now'))",
                    Statement.RETURN_GENERATED_KEYS)) {
                p.setString(1, value); p.setString(2, blankToNull(driverName)); p.setString(3, blankToNull(vehicleTrailerNo)); p.executeUpdate();
                try (ResultSet r = p.getGeneratedKeys()) { r.next(); return new CarrierRecord(r.getLong(1), value, blankToNull(driverName), blankToNull(vehicleTrailerNo), true); }
            }
        } catch (SQLException e) { WaspLogger.error("Unable to save carrier", e); throw new IllegalStateException("Unable to save carrier", e); }
    }

    public static CarrierRecord createCarrier(String name) { return createCarrier(name, null, null, true); }
    public static CarrierRecord createCarrier(String name, String driverName, String vehicleTrailerNo) {
        return createCarrier(name, driverName, vehicleTrailerNo, true);
    }
    public static CarrierRecord createCarrier(String name, String driverName, String vehicleTrailerNo, boolean active) {
        String value = required(name, "Carrier name");
        validateLength(value, 300, "Carrier name");
        validateLength(driverName, 200, "Driver name");
        validateLength(vehicleTrailerNo, 200, "Vehicle / Trailer No.");
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(
                "INSERT INTO saved_carrier(carrier_name,driver_name,vehicle_trailer_no,active,created_at,updated_at) VALUES(?,?,?,?,datetime('now'),datetime('now'))",
                Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, value);
            p.setString(2, blankToNull(driverName));
            p.setString(3, blankToNull(vehicleTrailerNo));
            p.setInt(4, active ? 1 : 0);
            p.executeUpdate();
            try (ResultSet r = p.getGeneratedKeys()) {
                r.next();
                return new CarrierRecord(r.getLong(1), value, blankToNull(driverName), blankToNull(vehicleTrailerNo), active);
            }
        } catch (SQLException e) { WaspLogger.error("Unable to save carrier", e); throw new IllegalStateException("Unable to save carrier", e); }
    }

    public static CarrierRecord updateCarrier(long id, String name, String driverName, String vehicleTrailerNo, boolean active) {
        String value = required(name, "Carrier name");
        validateLength(value, 300, "Carrier name");
        validateLength(driverName, 200, "Driver name");
        validateLength(vehicleTrailerNo, 200, "Vehicle / Trailer No.");
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(
                "UPDATE saved_carrier SET carrier_name=?, driver_name=?, vehicle_trailer_no=?, active=?, updated_at=datetime('now') WHERE id=?")) {
            p.setString(1, value); p.setString(2, blankToNull(driverName)); p.setString(3, blankToNull(vehicleTrailerNo)); p.setInt(4, active ? 1 : 0); p.setLong(5, id);
            if (p.executeUpdate() == 0) throw new IllegalArgumentException("Carrier no longer exists.");
            return new CarrierRecord(id, value, blankToNull(driverName), blankToNull(vehicleTrailerNo), active);
        } catch (SQLException e) { WaspLogger.error("Unable to update carrier", e); throw new IllegalStateException("Unable to update carrier", e); }
    }

    /** Compatibility overload for older callers. */
    public static CarrierRecord updateCarrier(long id, String name, boolean active) {
        CarrierRecord current = findCarriers(null, false).stream().filter(x -> x.id() == id).findFirst().orElse(null);
        return updateCarrier(id, name, current == null ? null : current.driverName(), current == null ? null : current.vehicleTrailerNo(), active);
    }

    public static LocationRecord createLocation(String name) { return createLocation(name, true); }

    public static LocationRecord createLocation(String name, boolean active) {
        String value = required(name, "Location name");
        validateLength(value, 400, "Location name");
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(
                "INSERT INTO saved_location(location_name,active,created_at,updated_at) VALUES(?,?,datetime('now'),datetime('now'))",
                Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, value);
            p.setInt(2, active ? 1 : 0);
            p.executeUpdate();
            try (ResultSet r = p.getGeneratedKeys()) { r.next(); return new LocationRecord(r.getLong(1), value, active); }
        } catch (SQLException e) { WaspLogger.error("Unable to save location", e); throw new IllegalStateException("Unable to save location", e); }
    }

    /** Saves a location if it is not already present. */
    public static LocationRecord saveIfMissingLocation(String name) {
        String value = required(name, "Location name");
        LocationRecord existing = findLocations(value, false).stream().filter(x -> x.name().equalsIgnoreCase(value)).findFirst().orElse(null);
        return existing != null ? existing : createLocation(value);
    }

    public static LocationRecord updateLocation(long id, String name, boolean active) {
        String value = required(name, "Location name");
        validateLength(value, 400, "Location name");
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(
                "UPDATE saved_location SET location_name=?, active=?, updated_at=datetime('now') WHERE id=?")) {
            p.setString(1, value); p.setInt(2, active ? 1 : 0); p.setLong(3, id);
            if (p.executeUpdate() == 0) throw new IllegalArgumentException("Location no longer exists.");
            return new LocationRecord(id, value, active);
        } catch (SQLException e) { WaspLogger.error("Unable to update location", e); throw new IllegalStateException("Unable to update location", e); }
    }

    private static String required(String value, String field) {
        String v = value == null ? "" : value.trim();
        if (v.isBlank()) throw new IllegalArgumentException(field + " is required.");
        return v;
    }
    private static void validateLength(String value, int max, String field) {
        if (value != null && value.length() > max) throw new IllegalArgumentException(field + " cannot exceed " + max + " characters.");
    }
    public static String deleteCarrier(long id) {
        requireAdmin();
        try (Connection c=Database.getConnection(); PreparedStatement p=c.prepareStatement("DELETE FROM saved_carrier WHERE id=?")) {
            p.setLong(1,id); if(p.executeUpdate()==0) throw new IllegalArgumentException("The selected carrier no longer exists.");
            AuditLogService.log("DELETE","SAVED_CARRIER",id,"Saved carrier master deleted by Administrator");
            return "Carrier deleted successfully.";
        } catch(SQLException e){throw new IllegalStateException("Unable to delete carrier",e);}
    }

    public static String deleteLocation(long id) {
        requireAdmin();
        try (Connection c=Database.getConnection(); PreparedStatement p=c.prepareStatement("DELETE FROM saved_location WHERE id=?")) {
            p.setLong(1,id); if(p.executeUpdate()==0) throw new IllegalArgumentException("The selected location no longer exists.");
            AuditLogService.log("DELETE","SAVED_LOCATION",id,"Saved location master deleted by Administrator");
            return "Location deleted successfully.";
        } catch(SQLException e){throw new IllegalStateException("Unable to delete location",e);}
    }

    private static void requireAdmin(){if(!com.aks.waybill.security.SessionContext.isAdmin())throw new IllegalArgumentException("Only an Administrator can delete saved master records.");}

    private static String blankToNull(String value) { return value == null || value.trim().isBlank() ? null : value.trim(); }
}
