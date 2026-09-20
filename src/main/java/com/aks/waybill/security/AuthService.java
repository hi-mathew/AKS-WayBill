package com.aks.waybill.security;
import com.aks.waybill.db.Database;
import java.sql.*;
import java.time.Instant;
import java.util.Optional;
public class AuthService {
    public boolean hasUsers() {
        try (Connection c=Database.getConnection(); PreparedStatement p=c.prepareStatement("SELECT COUNT(*) FROM app_user"); ResultSet r=p.executeQuery()) { return r.next() && r.getInt(1)>0; }
        catch(SQLException e){ throw new IllegalStateException("Unable to check users",e); }
    }
    public void createInitialAdmin(String username,String displayName,String userCode,String password) {
        if(hasUsers()) throw new IllegalStateException("Initial administrator already exists.");
        if (userCode == null || !userCode.trim().toUpperCase().matches("[A-Z0-9_-]{2,10}")) throw new IllegalArgumentException("User Code must be 2-10 letters, numbers, hyphens or underscores.");
        String now=Instant.now().toString();
        try(Connection c=Database.getConnection(); PreparedStatement p=c.prepareStatement("INSERT INTO app_user(username,password_hash,display_name,user_code,role,created_at,updated_at) VALUES(?,?,?,?,?,?,?)")){
            p.setString(1,username.trim()); p.setString(2,PasswordService.hash(password)); p.setString(3,displayName.trim()); p.setString(4,userCode.trim().toUpperCase()); p.setString(5,"ADMIN"); p.setString(6,now); p.setString(7,now); p.executeUpdate();
        }catch(SQLException e){throw new IllegalStateException("Unable to create administrator",e);}
    }
    public Optional<UserRecord> authenticate(String username,String password) {
        String sql="SELECT id,username,password_hash,display_name,user_code,role,enabled,failed_login_attempts,locked_until,must_change_password FROM app_user WHERE username=? COLLATE NOCASE";
        try(Connection c=Database.getConnection(); PreparedStatement p=c.prepareStatement(sql)){
            p.setString(1,username.trim());
            try(ResultSet r=p.executeQuery()){
                if(!r.next() || r.getInt("enabled")!=1) return Optional.empty();
                String lock=r.getString("locked_until"); if(lock!=null && Instant.parse(lock).isAfter(Instant.now())) return Optional.empty();
                long id=r.getLong("id");
                if(!PasswordService.matches(password,r.getString("password_hash"))){ registerFailedAttempt(id,r.getInt("failed_login_attempts")); return Optional.empty(); }
                resetLoginState(id); return Optional.of(new UserRecord(id,r.getString("username"),r.getString("display_name"),r.getString("user_code"),r.getString("role"),r.getInt("must_change_password") == 1));
            }
        }catch(SQLException e){throw new IllegalStateException("Unable to authenticate user",e);}
    }
    private void registerFailedAttempt(long id,int current)throws SQLException{
        int next=current+1; String lock=next>=5?Instant.now().plusSeconds(900).toString():null;
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("UPDATE app_user SET failed_login_attempts=?,locked_until=?,updated_at=? WHERE id=?")){
            p.setInt(1,next); if(lock==null)p.setNull(2,Types.VARCHAR);else p.setString(2,lock);p.setString(3,Instant.now().toString());p.setLong(4,id);p.executeUpdate();
        }
    }
    private void resetLoginState(long id)throws SQLException{
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("UPDATE app_user SET failed_login_attempts=0,locked_until=NULL,last_login_at=?,updated_at=? WHERE id=?")){
            String now=Instant.now().toString();p.setString(1,now);p.setString(2,now);p.setLong(3,id);p.executeUpdate();
        }
    }
    public record UserRecord(long id,String username,String displayName,String userCode,String role,boolean mustChangePassword){}
}
