package com.aks.waybill.security;
import org.mindrot.jbcrypt.BCrypt;
public final class PasswordService {
    private PasswordService() {}
    public static String hash(String password) { return BCrypt.hashpw(password, BCrypt.gensalt(12)); }
    public static boolean matches(String password, String hash) { return password != null && hash != null && BCrypt.checkpw(password, hash); }
}
