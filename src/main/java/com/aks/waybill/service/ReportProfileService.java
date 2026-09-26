package com.aks.waybill.service;

import com.aks.waybill.logging.WaspLogger;

import com.aks.waybill.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Stores the issuing company's report/letterhead profile separately from client companies. */
public final class ReportProfileService {
    public static final String COMPANY_NAME = "report.company.name";
    public static final String CR_NUMBER = "report.company.crNumber";
    public static final String VAT_NUMBER = "report.company.vatNumber";
    public static final String ADDRESS = "report.company.address";
    public static final String PHONE = "report.company.phone";
    public static final String EMAIL = "report.company.email";

    private static final String DEFAULT_NAME = "AKS GLOBAL LOGISTICS CO.";
    private static final String DEFAULT_CR = "1010558527";
    private static final String DEFAULT_VAT = "311676840600003";
    private static final String DEFAULT_ADDRESS = "Zein Tower Building, Salah Ad Din Al Ayyubi Road, Malaz, Riyadh, Saudi Arabia-12836";

    private ReportProfileService() {}

    public record ReportProfile(String companyName, String crNumber, String vatNumber,
                                String address, String phone, String email) {}

    public static ReportProfile get() {
        try (Connection c = Database.getConnection()) {
            ensure(c, COMPANY_NAME, DEFAULT_NAME);
            ensure(c, CR_NUMBER, DEFAULT_CR);
            ensure(c, VAT_NUMBER, DEFAULT_VAT);
            ensure(c, ADDRESS, DEFAULT_ADDRESS);
            ensure(c, PHONE, "");
            ensure(c, EMAIL, "");
            return new ReportProfile(get(c, COMPANY_NAME), get(c, CR_NUMBER), get(c, VAT_NUMBER),
                    get(c, ADDRESS), get(c, PHONE), get(c, EMAIL));
        } catch (Exception e) { WaspLogger.error("Unable to load report company profile", e); throw new IllegalStateException("Unable to load report company profile", e); }
    }

    public static void save(String companyName, String crNumber, String vatNumber, String address, String phone, String email) {
        if (blank(companyName)) throw new IllegalArgumentException("Company Name is required.");
        if (companyName.trim().length() > 300) throw new IllegalArgumentException("Company Name cannot exceed 300 characters.");
        if (blank(crNumber)) throw new IllegalArgumentException("CR Number is required.");
        if (crNumber.trim().length() > 100) throw new IllegalArgumentException("CR Number cannot exceed 100 characters.");
        if (blank(vatNumber)) throw new IllegalArgumentException("VAT Number is required.");
        if (vatNumber.trim().length() > 100) throw new IllegalArgumentException("VAT Number cannot exceed 100 characters.");
        if (blank(address)) throw new IllegalArgumentException("Company Address is required.");
        if (address.trim().length() > 1000) throw new IllegalArgumentException("Company Address cannot exceed 1000 characters.");
        if (phone != null && phone.trim().length() > 100) throw new IllegalArgumentException("Phone Number cannot exceed 100 characters.");
        if (email != null && email.trim().length() > 300) throw new IllegalArgumentException("Email Address cannot exceed 300 characters.");
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                put(c, COMPANY_NAME, companyName.trim()); put(c, CR_NUMBER, crNumber.trim()); put(c, VAT_NUMBER, vatNumber.trim());
                put(c, ADDRESS, address.trim()); put(c, PHONE, safe(phone)); put(c, EMAIL, safe(email));
                c.commit();
            } catch (Exception e) { WaspLogger.error("Operation failed in ReportProfileService", e); c.rollback(); throw e; }
            finally { c.setAutoCommit(true); }
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { WaspLogger.error("Operation failed in ReportProfileService", e); throw new IllegalStateException("Unable to save report company profile", e); }
    }

    private static void ensure(Connection c, String key, String value) throws Exception { put(c, key, get(c, key, value)); }
    private static String get(Connection c, String key) throws Exception { return get(c, key, null); }
    private static String get(Connection c, String key, String fallback) throws Exception {
        try (PreparedStatement p = c.prepareStatement("SELECT setting_value FROM application_settings WHERE setting_key=?")) {
            p.setString(1, key); try (ResultSet r = p.executeQuery()) { return r.next() && r.getString(1) != null ? r.getString(1) : fallback; }
        }
    }
    private static void put(Connection c, String key, String value) throws Exception {
        try (PreparedStatement p = c.prepareStatement("INSERT INTO application_settings(setting_key,setting_value) VALUES(?,?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value")) {
            p.setString(1,key); p.setString(2,value == null ? "" : value); p.executeUpdate();
        }
    }
    private static boolean blank(String v) { return v == null || v.trim().isBlank(); }
    private static String safe(String v) { return v == null ? "" : v.trim(); }
}
